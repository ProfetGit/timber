import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.network.syncher.EntityDataAccessor;

public class TimberTest {
    static MinecraftServer server;
    static ServerLevel level;
    static ServerPlayer player;
    static EmbeddedChannel channel;
    static int passed, failed;
    static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        server = boot();
        long deadline = System.currentTimeMillis() + 180_000;
        while (!server.isReady()) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("server never became ready");
            Thread.sleep(50);
        }
        ticks(20);
        level = server.overworld();
        try {
            if (args.length >= 2 && args[0].equals("explore")) {
                explore(Path.of(args[1]));
            } else {
                Scenarios.run();
                System.out.println("SUMMARY " + passed + " passed, " + failed + " failed");
                for (String f : failures) System.out.println("  - " + f);
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            failed++;
        } finally {
            on(() -> { server.halt(false); return null; });
            Thread.sleep(3000);
            System.exit(failed == 0 ? 0 : 1);
        }
    }

    /** Vanilla by default; -Dharness.main=<class> boots a plugin platform in-process instead (PLATFORM= in run.sh). */
    static MinecraftServer boot() throws Exception {
        String main = System.getProperty("harness.main");
        if (main == null) {
            net.minecraft.server.Main.main(new String[] {"--nogui"});
            return findServer();
        }
        Class.forName(main).getMethod("main", String[].class).invoke(null, (Object) new String[] {"--nogui"});
        java.lang.reflect.Method get = MinecraftServer.class.getMethod("getServer");
        long deadline = System.currentTimeMillis() + 180_000;
        Object s;
        while ((s = get.invoke(null)) == null) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("server never started");
            Thread.sleep(50);
        }
        return (MinecraftServer) s;
    }

    @SuppressWarnings("unchecked")
    static MinecraftServer findServer() throws Exception {
        Class<?> hooksClass = Class.forName("java.lang.ApplicationShutdownHooks");
        Field hooksField = hooksClass.getDeclaredField("hooks");
        hooksField.setAccessible(true);
        Map<Thread, Thread> hooks = (Map<Thread, Thread>) hooksField.get(null);
        for (Thread t : hooks.keySet()) {
            for (Field f : t.getClass().getDeclaredFields()) {
                if (MinecraftServer.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (MinecraftServer) f.get(t);
                }
            }
        }
        throw new IllegalStateException("server instance not found in shutdown hooks");
    }

    static <T> T on(Callable<T> task) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        server.submit(() -> {
            try {
                out.set(task.call());
            } catch (Throwable t) {
                err.set(t);
            }
        }).join();
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }

    static void ticks(int n) throws InterruptedException {
        int target = server.getTickCount() + n;
        while (server.getTickCount() < target) Thread.sleep(2);
    }

    /** Runs a console command on the server thread and returns its chat output. */
    static List<String> cmd(String command) {
        return on(() -> {
            List<String> out = new ArrayList<>();
            // a proxy, not an anonymous class: plugin platforms add methods (getBukkitSender), answered by the server
            CommandSource capture = (CommandSource) java.lang.reflect.Proxy.newProxyInstance(CommandSource.class.getClassLoader(),
                new Class<?>[] {CommandSource.class}, (proxy, m, a) -> switch (m.getName()) {
                    case "sendSystemMessage" -> { out.add(((Component) a[0]).getString()); yield null; }
                    case "acceptsSuccess", "acceptsFailure" -> true;
                    case "shouldInformAdmins" -> false;
                    default -> m.invoke(server, a);
                });
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), command);
            return out;
        });
    }

    /** A /data get source as full SNBT: Paper and Purpur cut /data get output at 128 characters. */
    static String full(String source) {
        cmd("data remove storage harness:full v");
        cmd("data modify storage harness:full v set from " + source);
        return on(() -> String.valueOf(server.getCommandStorage().get(net.minecraft.resources.Identifier.parse("harness:full")).get("v")));
    }

    static void explore(Path file) throws Exception {
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("//")) continue;
            if (line.startsWith("!tick ")) { ticks(Integer.parseInt(line.substring(6).trim())); continue; }
            if (line.equals("!player")) { spawnPlayer(); continue; }
            if (line.startsWith("!sneak ")) { sneak(Boolean.parseBoolean(line.substring(7).trim())); continue; }
            if (line.startsWith("!find ")) {
                String[] p = line.substring(6).trim().split(" ");
                int[] v = new int[6];
                for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(p[i + 1]);
                String want = p[0];
                System.out.println("[EXPLORE] find " + want + ": " + on(() -> {
                    StringBuilder sb = new StringBuilder();
                    int n = 0;
                    for (BlockPos q : BlockPos.betweenClosed(v[0], v[1], v[2], v[3], v[4], v[5])) {
                        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(q).getBlock()).toString();
                        if (id.contains(want)) { n++; if (n <= 60) sb.append(q.toShortString()).append(" ").append(level.getBlockState(q).toString().replace("Block{minecraft:", "").replace("}", "")).append("; "); }
                    }
                    return n + " -> " + sb;
                }));
                continue;
            }
            if (line.startsWith("!leafcheck ")) {
                String[] p = line.substring(11).trim().split(" ");
                int[] v = new int[6];
                for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(p[i]);
                System.out.println("[EXPLORE] leafcheck " + leafCheck(v));
                continue;
            }
            if (line.startsWith("!time ")) {
                String c = line.substring(6).trim();
                long t0 = System.nanoTime();
                List<String> out = cmd(c);
                System.out.println("[EXPLORE] " + String.format("%7.2f ms", (System.nanoTime() - t0) / 1e6) + "  " + c + (out.isEmpty() ? "" : "  -> " + out));
                continue;
            }
            if (line.startsWith("!timed ")) {
                long[] before = tickTimes();
                ticks(Integer.parseInt(line.substring(7).trim()));
                System.out.println("[EXPLORE] worst tick " + worstTickSince(before) / 100_000 / 10.0 + " ms");
                continue;
            }
            if (line.startsWith("!place ")) {
                String[] p = line.substring(7).trim().split(" ");
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                System.out.println("[EXPLORE] place on " + pos + " " + p[3] + " -> " + place(pos, Direction.byName(p[3])));
                continue;
            }
            if (line.startsWith("!anim ")) {
                String[] p = line.substring(6).trim().split(" ");
                AnimTrace tr = trace(Integer.parseInt(p[0]));
                System.out.println("[EXPLORE] anim " + tr.summary());
                if (p.length > 1) Files.writeString(Path.of(p[1]), tr.json());
                continue;
            }
            if (line.startsWith("!dump ")) {
                String[] p = line.substring(6).trim().split(" ", 7);
                int[] v = new int[6];
                for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(p[i]);
                String out = on(() -> {
                    StringBuilder sb = new StringBuilder();
                    for (BlockPos q : BlockPos.betweenClosed(v[0], v[1], v[2], v[3], v[4], v[5])) {
                        BlockState s = level.getBlockState(q);
                        if (s.isAir()) continue;
                        sb.append("setblock ~").append(q.getX() - v[0]).append(" ~").append(q.getY() - v[1]).append(" ~").append(q.getZ() - v[2])
                            .append(' ').append(net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(s)).append(" strict\n");
                    }
                    return sb.toString();
                });
                Files.writeString(Path.of(p[6]), out);
                System.out.println("[EXPLORE] dump " + out.lines().count() + " blocks -> " + p[6]);
                continue;
            }
            if (line.startsWith("!destroy ")) {
                String[] p = line.substring(9).trim().split(" ");
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                System.out.println("[EXPLORE] destroy " + pos + " -> " + destroy(pos) + " items " + itemsNear(pos, 3));
                continue;
            }
            List<String> out = cmd(line);
            System.out.println("[EXPLORE] > " + line);
            for (String o : out) System.out.println("[EXPLORE]     " + o.replace("\n", "\n[EXPLORE]     "));
        }
    }

    static void spawnPlayer() {
        if (player != null) return;
        on(() -> {
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("TimberTester".getBytes()), "TimberTester");
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            ServerPlayer p = new ServerPlayer(server, level, profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(new ChannelHandler[] {connection});
            server.getPlayerList().placeNewPlayer(connection, p, cookie);
            player = p;
            return null;
        });
    }

    /** Drains chat/action-bar packets sent to the mock player: "text {clicks=n}". */
    static List<String> chat() {
        return on(() -> {
            List<String> out = new ArrayList<>();
            Object o;
            while ((o = channel.readOutbound()) != null) {
                if (o instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket p)
                    out.add((p.overlay() ? "[actionbar] " : "") + p.content().getString().replace("\n", "⏎") + " {clicks=" + clicks(p.content()) + "}");
                else if (o instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket p)
                    out.add("[actionbar] " + p.text().getString());
            }
            return out;
        });
    }

    static int clicks(Component c) {
        int n = c.getStyle().getClickEvent() != null ? 1 : 0;
        for (Component s : c.getSiblings()) n += clicks(s);
        return n;
    }

    static void sneak(boolean on) {
        on(() -> {
            player.setShiftKeyDown(on);
            player.setPose(on ? Pose.CROUCHING : Pose.STANDING);
            return null;
        });
    }

    static boolean destroy(BlockPos pos) {
        return on(() -> player.gameMode.destroyBlock(pos));
    }

    static String blockId(BlockPos pos) {
        return on(() -> {
            BlockState s = level.getBlockState(pos);
            return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        });
    }

    static Map<String, Integer> itemsNear(BlockPos pos, double r) {
        return on(() -> {
            Map<String, Integer> m = new java.util.TreeMap<>();
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(r))) {
                ItemStack s = e.getItem();
                m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
            return m;
        });
    }

    static int xpNear(BlockPos pos, double r) {
        return on(() -> {
            int total = 0;
            for (ExperienceOrb o : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(r))) total += o.getValue();
            return total;
        });
    }

    /** Places the held item against a block face through the vanilla use-item path, looking at the face first. */
    static String place(BlockPos against, Direction face) {
        return on(() -> {
            Vec3 hit = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            lookAt(hit);
            BlockHitResult r = new BlockHitResult(hit, face, against, false);
            return player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, r).toString();
        });
    }

    static void lookAt(Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    @SuppressWarnings("unchecked")
    static <T> T displayData(Display d, String field) {
        try {
            Field f = Display.class.getDeclaredField(field);
            f.setAccessible(true);
            return d.getEntityData().get((EntityDataAccessor<T>) f.get(null));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static BlockState displayBlock(Display.BlockDisplay d) {
        try {
            Field f = Display.BlockDisplay.class.getDeclaredField("DATA_BLOCK_STATE_ID");
            f.setAccessible(true);
            @SuppressWarnings("unchecked") EntityDataAccessor<BlockState> a = (EntityDataAccessor<BlockState>) f.get(null);
            return d.getEntityData().get(a);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static List<Display.BlockDisplay> treeDisplays() {
        return on(() -> {
            List<Display.BlockDisplay> out = new ArrayList<>();
            for (Entity e : level.getAllEntities()) if (e instanceof Display.BlockDisplay d && d.entityTags().contains("timber.d")) out.add(d);
            return out;
        });
    }

    static int controllers() {
        return on(() -> {
            int n = 0;
            for (Entity e : level.getAllEntities()) if (e instanceof Marker && e.entityTags().contains("timber.ctl")) n++;
            return n;
        });
    }

    /** Per-tick record of the first felled tree: pitch/yaw of its displays until they are gone (or maxTicks). */
    static class AnimTrace {
        final List<float[]> rot = new ArrayList<>();
        final List<String> blocks = new ArrayList<>();
        final List<float[]> offsets = new ArrayList<>();
        double[] pivot;
        int displays;

        String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(displays).append(" displays, ").append(rot.size()).append(" ticks, pitch:");
            for (float[] r : rot) sb.append(' ').append(Math.round((r[1] + r[3]) * 10) / 10.0);
            if (!rot.isEmpty()) sb.append(" yaw=").append(rot.get(0)[0]);
            return sb.toString();
        }

        String json() {
            StringBuilder sb = new StringBuilder("{\"pivot\":[");
            sb.append(pivot == null ? "" : pivot[0] + "," + pivot[1] + "," + pivot[2]).append("],\"rot\":[");
            for (int i = 0; i < rot.size(); i++) sb.append(i > 0 ? "," : "").append("[").append(rot.get(i)[0]).append(",").append(rot.get(i)[1] + rot.get(i)[3]).append(",").append(rot.get(i)[2]).append("]");
            sb.append("],\"blocks\":[");
            for (int i = 0; i < blocks.size(); i++) {
                float[] o = offsets.get(i);
                sb.append(i > 0 ? "," : "").append("{\"b\":\"").append(blocks.get(i)).append("\",\"t\":[").append(o[0]).append(",").append(o[1]).append(",").append(o[2]).append("]}");
            }
            return sb.append("]}").toString();
        }
    }

    static AnimTrace trace(int maxTicks) throws InterruptedException {
        AnimTrace tr = new AnimTrace();
        List<Display.BlockDisplay> ds = new ArrayList<>(treeDisplays());
        tr.displays = ds.size();
        if (ds.isEmpty()) return tr;
        on(() -> {
            Display.BlockDisplay top = null;
            float best = -1e9f;
            for (Display.BlockDisplay d : ds) {
                if (!d.entityTags().contains("timber.lg")) continue;
                float y = TimberTest.<org.joml.Vector3fc>displayData(d, "DATA_TRANSLATION_ID").y();
                if (y > best) { best = y; top = d; }
            }
            if (top != null) { ds.remove(top); ds.add(0, top); }
            return null;
        });
        on(() -> {
            Display.BlockDisplay first = ds.get(0);
            tr.pivot = new double[] {first.getX(), first.getY(), first.getZ()};
            for (Display.BlockDisplay d : ds) {
                org.joml.Vector3fc t = displayData(d, "DATA_TRANSLATION_ID");
                tr.blocks.add(displayBlock(d).toString().replace("\"", "'"));
                tr.offsets.add(new float[] {t.x(), t.y(), t.z()});
            }
            return null;
        });
        org.joml.Quaternionf l0 = on(() -> new org.joml.Quaternionf(TimberTest.<org.joml.Quaternionfc>displayData(ds.get(0), "DATA_LEFT_ROTATION_ID")));
        for (int i = 0; i < maxTicks; i++) {
            float[] r = on(() -> {
                Display.BlockDisplay d = ds.get(0);
                org.joml.Quaternionf q = new org.joml.Quaternionf(TimberTest.<org.joml.Quaternionfc>displayData(d, "DATA_LEFT_ROTATION_ID")).mul(new org.joml.Quaternionf(l0).conjugate());
                float extra = (float) Math.toDegrees(2 * Math.atan2(q.x, q.w));
                return new float[] {d.getYRot(), d.getXRot(), d.isRemoved() ? 1 : 0, extra};
            });
            tr.rot.add(r);
            if (r[2] == 1) break;
            ticks(1);
        }
        return tr;
    }

    /** Leaves whose distance disagrees with vanilla's rule (1 + min over 6 neighbours, logs = 0, capped at 7). */
    static String leafCheck(int[] b) {
        return on(() -> {
            int leaves = 0, bad = 0;
            java.util.Map<String, Integer> hist = new java.util.TreeMap<>();
            StringBuilder ex = new StringBuilder();
            for (BlockPos p : BlockPos.betweenClosed(b[0], b[1], b[2], b[3], b[4], b[5])) {
                BlockState s = level.getBlockState(p);
                if (!s.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
                leaves++;
                int d = s.getValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE);
                boolean pers = s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT);
                hist.merge((pers ? "P" : "") + d, 1, Integer::sum);
                int best = 7;
                for (Direction dir : Direction.values()) {
                    BlockState n = level.getBlockState(p.relative(dir));
                    if (n.is(net.minecraft.tags.BlockTags.LOGS)) best = 1;
                    else if (n.is(net.minecraft.tags.BlockTags.LEAVES)) best = Math.min(best, n.getValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE) + 1);
                }
                if (best != d) { bad++; if (ex.length() < 300) ex.append(p.toShortString()).append(" d=").append(d).append(" want ").append(best).append("; "); }
            }
            return leaves + " leaves, " + bad + " inconsistent, hist " + hist + (bad > 0 ? " e.g. " + ex : "");
        });
    }

    static long worstTickSince(long[] before) {
        long[] after = on(() -> server.getTickTimesNanos().clone());
        long w = 0;
        for (int i = 0; i < after.length; i++) if (after[i] != before[i]) w = Math.max(w, after[i]);
        return w;
    }

    static long[] tickTimes() {
        return on(() -> server.getTickTimesNanos().clone());
    }

    static ItemStack mainhand() {
        return on(() -> player.getMainHandItem().copy());
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
        } else {
            failed++;
            failures.add(name + ": " + detail);
            System.out.println("[FAIL] " + name + "  (" + detail + ")");
        }
    }

    static void info(String msg) {
        System.out.println("[INFO] " + msg);
    }
}

class Scenarios {
    static final int G = -60, Y = -59;
    static int plotIdx = 0, cx, cz;

    static List<String> cmd(String c) { return TimberTest.cmd(c); }
    static void tick(int n) throws InterruptedException { TimberTest.ticks(n); }
    static BlockPos p(int dx, int dy, int dz) { return new BlockPos(cx + dx, Y + dy, cz + dz); }
    static String at(int dx, int dy, int dz) { return (cx + dx) + " " + (Y + dy) + " " + (cz + dz); }
    static void check(String n, boolean ok, String d) { TimberTest.check(n, ok, d); }
    static void info(String m) { TimberTest.info(m); }

    static void plot() throws Exception {
        int i = plotIdx++ % 8;
        cx = 32 + 64 * (i % 4);
        cz = 32 + 64 * (i / 4);
        cmd("kill @e[type=block_display]");
        cmd("kill @e[type=marker]");
        cmd("fill " + (cx - 32) + " " + Y + " " + (cz - 32) + " " + (cx + 31) + " -10 " + (cz + 31) + " minecraft:air");
        List<String> ground = cmd("fill " + (cx - 32) + " " + G + " " + (cz - 32) + " " + (cx + 31) + " " + G + " " + (cz + 31) + " minecraft:grass_block");
        if (!ground.toString().contains("filled") && !ground.toString().contains("No blocks")) throw new IllegalStateException("plot ground: " + ground);
        cmd("kill @e[type=item]");
        cmd("scoreboard players reset * timber.config");
        cmd("function timber:config/defaults");
        cmd("gamemode survival TimberTester");
        cmd("scoreboard players set TimberTester timber.off 0");
        cmd("clear TimberTester");
        cmd("data remove storage timber:placed o");
        TimberTest.sneak(false);
        stand(-2, 0, -90);
        tick(2);
    }

    static void stand(int dx, int dz, float yaw) { cmd("tp TimberTester " + (cx + dx) + ".5 " + Y + " " + (cz + dz) + ".5 " + yaw + " 20"); }
    static void hold(String item) { cmd("item replace entity TimberTester weapon.mainhand with " + item); }
    static void feature(String f, int dx, int dz) { info("  " + cmd("place feature minecraft:" + f + " " + at(dx, 0, dz))); }

    static int count(java.util.function.Predicate<BlockState> f, int r) {
        return TimberTest.on(() -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(cx - r, Y - 1, cz - r, cx + r, Y + 40, cz + r))
                if (f.test(TimberTest.level.getBlockState(q))) n++;
            return n;
        });
    }
    /** Natural leaves with leaves or logs on all six sides: never seen, so Timber gives them no display. */
    static int enclosedLeaves(int r) {
        return TimberTest.on(() -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(cx - r, Y - 1, cz - r, cx + r, Y + 40, cz + r)) {
                if (!natLeaf(TimberTest.level.getBlockState(q))) continue;
                boolean shut = true;
                for (Direction dir : Direction.values()) {
                    BlockState o = TimberTest.level.getBlockState(q.relative(dir));
                    if (!o.is(net.minecraft.tags.BlockTags.LEAVES) && !isLog(o)) { shut = false; break; }
                }
                if (shut) n++;
            }
            return n;
        });
    }
    static boolean isLog(BlockState s) { return s.is(net.minecraft.tags.BlockTags.LOGS); }
    static boolean natLeaf(BlockState s) { return s.is(net.minecraft.tags.BlockTags.LEAVES) && !s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT); }
    static boolean is(BlockState s, String id) { return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().equals(id); }
    static int blocks(String id, int r) { return count(s -> is(s, id), r); }

    /** A simple natural tree: trunk of height h, 4-layer canopy of radius r; leaves settle their distances by ticking. */
    static void customTree(int dx, int dz, int h, int r, String log, String leaves) {
        for (int ly = h - 3; ly <= h; ly++) {
            int rr = ly >= h - 1 ? r - 1 : r;
            cmd("fill " + at(dx - rr, ly, dz - rr) + " " + at(dx + rr, ly, dz + rr) + " minecraft:" + leaves + " keep");
        }
        cmd("fill " + at(dx, 0, dz) + " " + at(dx, h - 1, dz) + " minecraft:" + log);
    }

    static boolean chop(int dx, int dy, int dz) throws Exception {
        boolean ok = TimberTest.destroy(p(dx, dy, dz));
        tick(1);
        return ok;
    }

    static int displays() { return TimberTest.treeDisplays().size(); }

    static float ctlYaw() {
        return TimberTest.on(() -> {
            for (net.minecraft.world.entity.Entity e : TimberTest.level.getAllEntities())
                if (e instanceof net.minecraft.world.entity.Marker && e.entityTags().contains("timber.ctl")) return e.getYRot();
            return Float.NaN;
        });
    }

    static int score(String holder) {
        List<String> out = cmd("scoreboard players get " + holder + " timber.data");
        String o = out.toString().replaceAll("[^0-9-]+", " ").trim();
        try { return Integer.parseInt(o.split(" ")[0]); } catch (Exception e) { return Integer.MIN_VALUE; }
    }

    static int damage() { return TimberTest.mainhand().getDamageValue(); }

    static int itemsOf(String id, int r) {
        return TimberTest.itemsNear(p(0, 0, 0), r).getOrDefault(id, 0);
    }

    /** Waits until the tree's displays are gone (or n ticks). */
    static TimberTest.AnimTrace finish(int n) throws Exception { return TimberTest.trace(n); }

    static String pitches(TimberTest.AnimTrace tr) {
        StringBuilder b = new StringBuilder();
        for (float[] r : tr.rot) b.append(Math.round(r[1] + r[3])).append(' ');
        return b.toString().trim();
    }

    static void run() throws Exception {
        TimberTest.spawnPlayer();
        cmd("forceload add 0 0 255 127");
        cmd("gamerule max_block_modifications 10000000");
        cmd("gamerule random_tick_speed 0");
        cmd("gamerule block_drops true");
        tick(80);
        List<String> ver = cmd("data get storage timber:meta version");
        info("pack version: " + ver);
        check("pack loaded (config defaults present)", cmd("scoreboard players get #max_logs timber.config").toString().contains("256"), ver.toString());
        // the hook fixture (dev/test/hookpack) is off for everything except hooks()
        cmd("datapack disable \"file/hookpack\"");
        tick(5);
        List<String> packs = cmd("datapack list enabled");
        check("base pack runs alone", !packs.toString().contains("hookpack") && packs.toString().contains(System.getProperty("harness.packs", "Timber-")), packs.toString());
        check("version_id stored for add-ons", cmd("data get storage timber:meta version_id").toString().contains("10300"),
            cmd("data get storage timber:meta version_id").toString());
        check("no requirement text without add-ons", requiresCount() == -1, "requires=" + requiresCount());
        boolean v263 = cmd("place feature minecraft:red_poplar 2000 -59 2000").toString().contains("Unknown") == false
            && !cmd("data get storage timber:types poplar").toString().contains("Found no");
        info("26.3 content (poplar): " + v263);

        String only = System.getProperty("scn", "");
        if (only.isEmpty() || only.contains("oak")) oakFullCycle();
        if (only.isEmpty() || only.contains("swap")) swap();
        if (only.isEmpty() || only.contains("gates")) gates();
        if (only.isEmpty() || only.contains("protection")) protection();
        if (only.isEmpty() || only.contains("neighbours")) neighbours();
        if (only.isEmpty() || only.contains("direction")) direction();
        if (only.isEmpty() || only.contains("stump")) stump();
        if (only.isEmpty() || only.contains("landing")) landing();
        if (only.isEmpty() || only.contains("modes")) modes();
        if (only.isEmpty() || only.contains("durability")) durability();
        if (only.isEmpty() || only.contains("robustness")) robustness();
        if (only.isEmpty() || only.contains("trees")) everyTree(v263);
        if (only.isEmpty() || only.contains("decor")) decor();
        if (only.isEmpty() || only.contains("mangroves")) mangroves();
        if (only.contains("paleoaks")) for (int k = 0; k < 16; k++) everyTreeList(List.of("pale_oak_creaking"));
        if (only.contains("giants")) for (int k = 0; k < 4; k++) everyTreeList(List.of("mega_pine", "mega_spruce", "mega_jungle_tree"));
        if (only.isEmpty() || only.contains("hooks")) hooks();
        if (only.isEmpty() || only.contains("anim")) animation();
        if (only.isEmpty() || only.contains("pose")) pose();
        if (only.isEmpty() || only.contains("settings")) settingsAndUninstall();
    }

    static void oakFullCycle() throws Exception {
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        info("oak leaves after settle: " + TimberTest.leafCheck(new int[] {cx - 4, Y, cz - 4, cx + 4, Y + 8, cz + 4}));
        int logs = count(Scenarios::isLog, 6), leaves = count(Scenarios::natLeaf, 6), hidden = enclosedLeaves(6);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        int d = displays();
        check("oak: world blocks replaced by displays in one tick", count(Scenarios::isLog, 6) == 0 && count(Scenarios::natLeaf, 6) == 0,
            "logs left " + count(Scenarios::isLog, 6) + ", leaves left " + count(Scenarios::natLeaf, 6));
        check("oak: one display per felled block, none for leaves shut in on all sides", hidden > 0 && d == logs - 1 + leaves - hidden,
            d + " displays for " + (logs - 1) + " logs + " + leaves + " leaves - " + hidden + " enclosed");
        check("oak: no stand-in blocks left behind", blocks("minecraft:structure_void", 8) == 0, blocks("minecraft:structure_void", 8) + " structure voids");
        check("oak: falls away from the player, tipped 33.75 deg to one side", Math.abs(Math.abs(((ctlYaw() % 360) + 360) % 360 - 270) - 33.75) < 0.01, "yaw " + ctlYaw());
        TimberTest.AnimTrace tr = finish(120);
        info("oak pitch per tick: " + pitches(tr));
        List<Float> pitch = new ArrayList<>();
        for (float[] r : tr.rot) if (r[2] == 0) pitch.add(r[1] + r[3]);
        float min0 = 0, max = -99;
        int maxAt = -1;
        for (int i = 0; i < pitch.size(); i++) {
            if (i < 20) min0 = Math.min(min0, pitch.get(i));
            if (pitch.get(i) > max + 0.01f) { max = pitch.get(i); maxAt = i; }
        }
        int dips = 0;
        for (int i = maxAt + 1; i < pitch.size() - 1; i++)
            if (pitch.get(i) < pitch.get(i - 1) && pitch.get(i) <= pitch.get(i + 1)) dips++;
        int rest = 0;
        for (int i = pitch.size() - 1; i > 0 && Math.abs(pitch.get(i) - max) < 0.01f; i--) rest++;
        check("oak: leans back first (anticipation, 10 deg plus the top's bend)", min0 < -7f && min0 > -14f, "min pitch in first 20 ticks " + min0);
        check("oak: lands flat on open ground", max > 89.9f, "impact pitch " + max + " at tick " + maxAt);
        check("oak: two bounces", dips == 2, dips + " dips after impact");
        check("oak: lies still while the trunk pops apart", rest >= 10, rest + " still ticks");
        check("oak: whole gag lasts 2.3-3.5 s", tr.rot.size() >= 46 && tr.rot.size() <= 70, tr.rot.size() + " ticks");
        tick(2);
        check("oak: displays and controller gone at the end", displays() == 0 && TimberTest.controllers() == 0, displays() + " displays, " + TimberTest.controllers() + " controllers");
        int dropped = itemsOf("minecraft:oak_log", 24);
        check("oak: every log dropped", dropped == logs, dropped + " of " + logs);
        check("oak: axe used 1 per log", damage() == logs, "damage " + damage() + " for " + logs + " logs");
    }

    /** At the swap tick every display must sit exactly on the block it replaced (entity yaw + left_rotation + translation). */
    static void swap() throws Exception {
        for (float yaw : new float[] {-90f, -67.5f, 45f}) {
            plot();
            feature("fancy_oak", 0, 0);
            tick(40);
            java.util.Set<BlockPos> before = new java.util.HashSet<>();
            TimberTest.on(() -> {
                for (BlockPos q : BlockPos.betweenClosed(cx - 14, Y - 1, cz - 14, cx + 14, Y + 40, cz + 14)) {
                    BlockState st = TimberTest.level.getBlockState(q);
                    if (isLog(st) || natLeaf(st)) before.add(q.immutable());
                }
                return null;
            });
            hold("minecraft:iron_axe");
            stand(-2, 0, yaw);
            chop(0, 0, 0);
            List<net.minecraft.world.entity.Display.BlockDisplay> ds = TimberTest.treeDisplays();
            int[] res = TimberTest.on(() -> {
                int ok = 0, bad = 0;
                for (net.minecraft.world.entity.Display.BlockDisplay d : ds) {
                    // the rest pose each tick's transformation starts from: the stored offset (timber.x/y/z) and the
                    // right rotation that counter-turns the block to the world grid (the chop shiver may already bend it)
                    net.minecraft.world.scores.Scoreboard sb = TimberTest.server.getScoreboard();
                    java.util.function.ToIntFunction<String> sc = o -> sb.getPlayerScoreInfo(d, sb.getObjective(o)).value();
                    org.joml.Vector3f tr = new org.joml.Vector3f(sc.applyAsInt("timber.x") / 1000f, sc.applyAsInt("timber.y") / 1000f, sc.applyAsInt("timber.z") / 1000f);
                    org.joml.Quaternionfc right = TimberTest.displayData(d, "DATA_RIGHT_ROTATION_ID");
                    org.joml.Vector3f c = new org.joml.Vector3f(0.5f, 0.5f, 0.5f);
                    right.transform(c);
                    c.add(tr);
                    new org.joml.Quaternionf().rotationYXZ((float) Math.toRadians(-d.getYRot()), 0f, 0f).transform(c);
                    double x = d.getX() + c.x, y = d.getY() + c.y, z = d.getZ() + c.z;
                    BlockPos q = BlockPos.containing(x, y, z);
                    boolean centred = Math.abs(x - q.getX() - 0.5) < 0.01 && Math.abs(y - q.getY() - 0.5) < 0.01 && Math.abs(z - q.getZ() - 0.5) < 0.01;
                    if (centred && before.contains(q)) ok++; else bad++;
                }
                return new int[] {ok, bad};
            });
            float fall = ((ctlYaw() % 360) + 360) % 360;
            check("swap at yaw " + fall + ": every display sits exactly on its old block", res[1] == 0 && res[0] > 0, res[0] + " exact, " + res[1] + " off");
            finish(120);
        }
    }

    static void gates() throws Exception {
        String[][] cases = {{"sneaking", "sneak"}, {"bare hand", "hand"}, {"creative", "creative"}, {"toggle off", "off"}, {"require_axe off + shovel", "shovel"}};
        for (String[] c : cases) {
            plot();
            customTree(0, 0, 6, 2, "birch_log", "birch_leaves");
            tick(30);
            int logs = count(Scenarios::isLog, 6);
            hold("minecraft:iron_axe");
            switch (c[1]) {
                case "sneak" -> TimberTest.sneak(true);
                case "hand" -> cmd("item replace entity TimberTester weapon.mainhand with minecraft:air");
                case "creative" -> cmd("gamemode creative TimberTester");
                case "off" -> cmd("scoreboard players set TimberTester timber.off 1");
                case "shovel" -> { cmd("scoreboard players set #require_axe timber.config 0"); hold("minecraft:iron_shovel"); }
            }
            chop(0, 0, 0);
            int left = count(Scenarios::isLog, 6);
            if (c[1].equals("shovel")) check("gate: " + c[0] + " -> fells", left == 0 && displays() > 0, "logs left " + left);
            else check("gate: " + c[0] + " -> single log", left == logs - 1 && displays() == 0, "logs " + logs + " -> " + left + ", displays " + displays());
            TimberTest.sneak(false);
            finish(120);
        }
    }

    static void protection() throws Exception {
        // player-placed pillar with player-placed leaves
        plot();
        hold("minecraft:oak_log 64");
        cmd("fill " + at(-4, 0, -1) + " " + at(-3, 2, 1) + " minecraft:stone");
        cmd("tp TimberTester " + (cx - 3) + ".5 " + (Y + 3) + " " + cz + ".5 -90 60");
        String r = TimberTest.place(p(0, -1, 0), Direction.UP);
        for (int y = 0; y < 4; y++) TimberTest.place(p(0, y, 0), Direction.UP);
        hold("minecraft:oak_leaves 64");
        for (int y = 2; y < 5; y++) { TimberTest.place(p(0, y, 0), Direction.EAST); TimberTest.place(p(0, y, 0), Direction.NORTH); TimberTest.place(p(0, y, 0), Direction.SOUTH); }
        String keys = TimberTest.full("storage timber:placed o");
        info("placed keys: " + keys + " place result " + r);
        boolean keysMatch = true;
        for (int y = 0; y < 5; y++) keysMatch &= keys.toString().contains((cx) + "," + (Y + y) + "," + cz + "\"");
        int logs = count(Scenarios::isLog, 6);
        check("placed: exactly the 5 placed logs tracked", logs == 5 && keysMatch && keys.toString().split("1b").length - 1 == 5, logs + " logs, " + keys);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        check("placed: player pillar -> only the chopped log", count(Scenarios::isLog, 6) == 4 && displays() == 0, count(Scenarios::isLog, 6) + " logs left");
        String keys2 = TimberTest.full("storage timber:placed o");
        check("placed: key removed when the log is mined", keys2.toString().split("1b").length - 1 == 4, keys2.toString());

        // log cabin built before install (no keys, no natural leaves)
        plot();
        for (int dx : new int[] {0, 4}) for (int dz : new int[] {0, 4}) cmd("fill " + at(dx, 0, dz) + " " + at(dx, 3, dz) + " minecraft:spruce_log");
        cmd("fill " + at(0, 4, 0) + " " + at(4, 4, 0) + " minecraft:spruce_log[axis=x]");
        cmd("fill " + at(0, 4, 4) + " " + at(4, 4, 4) + " minecraft:spruce_log[axis=x]");
        int cabin = count(Scenarios::isLog, 8);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        check("cabin (no leaves): only the chopped log", count(Scenarios::isLog, 8) == cabin - 1 && displays() == 0, cabin + " -> " + count(Scenarios::isLog, 8));

        // cabin wall touching a natural tree's canopy
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        cmd("fill " + at(3, 0, -3) + " " + at(3, 4, 3) + " minecraft:oak_log");
        tick(40);
        int wall = 35;
        hold("minecraft:iron_axe");
        chop(3, 0, 0);
        check("wall touching a canopy: chopping the wall takes one log", blocks("minecraft:oak_log", 8) == 7 + wall - 1 && displays() == 0,
            "oak logs " + blocks("minecraft:oak_log", 8) + " (tree 7 + wall " + wall + ")");
        chop(0, 0, 0);
        int wallLeft = TimberTest.on(() -> { int n = 0; for (BlockPos q : BlockPos.betweenClosed(cx + 3, Y, cz - 3, cx + 3, Y + 4, cz + 3)) if (TimberTest.level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        check("wall touching a canopy: felling the tree leaves the wall", wallLeft == wall - 1 && count(s -> isLog(s), 2) == 0, "wall logs " + wallLeft + ", trunk logs " + count(Scenarios::isLog, 2));
        finish(120);

        // player-built log platform attached to a natural trunk
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:oak_log 64");
        stand(4, -3, 0);
        TimberTest.place(p(0, 1, 0), Direction.EAST);
        TimberTest.place(p(1, 1, 0), Direction.EAST);
        TimberTest.place(p(2, 1, 0), Direction.EAST);
        stand(-2, 0, -90);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        int platform = TimberTest.on(() -> { int n = 0; for (int i = 1; i <= 3; i++) if (TimberTest.level.getBlockState(new BlockPos(cx + i, Y + 1, cz)).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        check("player logs attached to a trunk survive the felling", platform == 3 && count(s -> isLog(s), 0) == 0, platform + " of 3 placed logs, trunk logs left " + count(Scenarios::isLog, 0));
        finish(120);
    }

    static void neighbours() throws Exception {
        // two trees whose canopies overlap
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        customTree(4, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        int bLeaves = TimberTest.on(() -> { int n = 0; for (BlockPos q : BlockPos.betweenClosed(cx + 3, Y, cz - 2, cx + 6, Y + 8, cz + 2)) if (natLeaf(TimberTest.level.getBlockState(q))) n++; return n; });
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        int bLogs = TimberTest.on(() -> { int n = 0; for (int y = 0; y < 6; y++) if (TimberTest.level.getBlockState(new BlockPos(cx + 4, Y + y, cz)).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        int bLeaves2 = TimberTest.on(() -> { int n = 0; for (BlockPos q : BlockPos.betweenClosed(cx + 3, Y, cz - 2, cx + 6, Y + 8, cz + 2)) if (natLeaf(TimberTest.level.getBlockState(q))) n++; return n; });
        check("overlapping canopies: neighbour's trunk untouched", bLogs == 6, bLogs + " of 6");
        check("overlapping canopies: neighbour keeps its own leaves", bLeaves2 == bLeaves, bLeaves + " -> " + bLeaves2);
        check("overlapping canopies: felled tree's leaves gone", TimberTest.on(() -> { int n = 0; for (BlockPos q : BlockPos.betweenClosed(cx - 2, Y, cz - 2, cx + 1, Y + 8, cz + 2)) if (natLeaf(TimberTest.level.getBlockState(q))) n++; return n; }) == 0, "");
        finish(120);
        tick(20);
        String lc = TimberTest.leafCheck(new int[] {cx + 1, Y, cz - 3, cx + 7, Y + 8, cz + 3});
        check("overlapping canopies: neighbour's leaves stay attached (no distance 7)", !lc.matches(".*[{ ,]7=.*"), lc);

        // a branch of logs joins two trees
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        customTree(6, 0, 7, 2, "oak_log", "oak_leaves");
        cmd("fill " + at(1, 3, 0) + " " + at(5, 3, 0) + " minecraft:oak_log[axis=x]");
        tick(40);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        int bTrunk = TimberTest.on(() -> { int n = 0; for (int y = 0; y < 7; y++) if (TimberTest.level.getBlockState(new BlockPos(cx + 6, Y + y, cz)).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        int aTrunk = TimberTest.on(() -> { int n = 0; for (int y = 0; y < 7; y++) if (TimberTest.level.getBlockState(new BlockPos(cx, Y + y, cz)).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        int bridge = TimberTest.on(() -> { int n = 0; for (int x = 1; x <= 5; x++) if (TimberTest.level.getBlockState(new BlockPos(cx + x, Y + 3, cz)).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        check("log bridge: felled tree's trunk gone", aTrunk == 0, aTrunk + " left");
        check("log bridge: neighbour's trunk untouched", bTrunk == 7, bTrunk + " of 7");
        check("log bridge: bridge split between the trees", bridge >= 2 && bridge <= 3, bridge + " of 5 bridge logs stay with the neighbour");
        finish(120);

        // real dark oaks growing into each other
        plot();
        feature("dark_oak", 0, 0);
        feature("dark_oak", 5, 2);
        tick(40);
        int before = TimberTest.on(() -> { int n = 0; for (BlockPos q : BlockPos.betweenClosed(cx + 5, Y, cz + 2, cx + 6, Y, cz + 3)) if (TimberTest.level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        int after = TimberTest.on(() -> { int n = 0; for (BlockPos q : BlockPos.betweenClosed(cx + 5, Y, cz + 2, cx + 6, Y + 20, cz + 3)) if (TimberTest.level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        int mine = TimberTest.on(() -> { int n = 0; for (BlockPos q : BlockPos.betweenClosed(cx, Y, cz, cx + 1, Y + 20, cz + 1)) if (TimberTest.level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        check("dark oak pair: chopped trunk fully felled", mine == 0, mine + " logs left in its 2x2 trunk");
        check("dark oak pair: neighbour's trunk still standing", before == 4 && after >= 8, "base " + before + ", trunk logs " + after);
        finish(120);
    }

    static void direction() throws Exception {
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        cmd("fill " + at(3, 0, -4) + " " + at(3, 7, 4) + " minecraft:stone");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        float yaw = ((ctlYaw() % 360) + 360) % 360;
        TimberTest.AnimTrace tr = finish(120);
        float max = 0;
        for (float[] r : tr.rot) max = Math.max(max, r[1] + r[3]);
        check("wall in the way: tree falls into the open instead", Math.abs(yaw - 270) > 1 && max > 70, "yaw " + yaw + ", landed at " + max);

        plot();
        customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
        cmd("fill " + at(-4, 0, -4) + " " + at(4, 3, -4) + " minecraft:stone");
        cmd("fill " + at(-4, 0, 4) + " " + at(4, 3, 4) + " minecraft:stone");
        cmd("fill " + at(-4, 0, -4) + " " + at(-4, 3, 4) + " minecraft:stone");
        cmd("fill " + at(4, 0, -4) + " " + at(4, 3, 4) + " minecraft:stone");
        tick(40);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        tr = finish(120);
        max = 0;
        for (float[] r : tr.rot) max = Math.max(max, r[1] + r[3]);
        info("boxed tree pitch: " + pitches(tr));
        check("boxed in: tree lands on the wall and bounces there", max > 20 && max < 70, "landed at " + max);
    }

    static void stump() throws Exception {
        plot();
        customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 2, 0);
        int stumpLogs = TimberTest.on(() -> { int n = 0; for (int y = 0; y < 2; y++) if (TimberTest.level.getBlockState(new BlockPos(cx, Y + y, cz)).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        int above = TimberTest.on(() -> { int n = 0; for (int y = 2; y < 8; y++) if (TimberTest.level.getBlockState(new BlockPos(cx, Y + y, cz)).is(net.minecraft.tags.BlockTags.LOGS)) n++; return n; });
        double pivotY = TimberTest.on(() -> { for (net.minecraft.world.entity.Entity e : TimberTest.level.getAllEntities()) if (e.entityTags().contains("timber.ctl")) return e.getY(); return Double.NaN; });
        check("cut mid-trunk: the stump stays", stumpLogs == 2, stumpLogs + " of 2 stump logs");
        check("cut mid-trunk: the part above the cut falls", above == 0 && displays() > 0, above + " logs left above the cut, " + displays() + " displays");
        check("cut mid-trunk: it hinges at the cut", Math.abs(pivotY - (Y + 2)) < 0.01, "pivot y " + pivotY + ", cut at " + (Y + 2));
        TimberTest.AnimTrace tr = finish(120);
        float max = 0;
        for (float[] r : tr.rot) max = Math.max(max, r[1] + r[3]);
        check("cut mid-trunk: the top tips past level to rest on the ground", max > 95 && max < 125, "landed at " + max);
        tick(2);
        hold("minecraft:iron_axe");
        chop(0, 1, 0);
        check("stump afterwards: breaks log by log", TimberTest.blockId(p(0, 0, 0)).equals("minecraft:oak_log") && displays() == 0, TimberTest.blockId(p(0, 0, 0)));
    }

    static void landing() throws Exception {
        // tree at a cliff edge: the ground drops 5 blocks right in front of it
        plot();
        cmd("fill " + at(-8, -1, -8) + " " + at(1, -6, 8) + " minecraft:dirt");
        cmd("fill " + at(2, -1, -8) + " " + at(20, -5, 8) + " minecraft:air");
        cmd("fill " + at(2, -6, -8) + " " + at(20, -6, 8) + " minecraft:grass_block");
        cmd("fill " + at(-8, 0, -8) + " " + at(-2, 8, 8) + " minecraft:stone");
        customTree(0, 0, 9, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-1, -3, -90);
        chop(0, 0, 0);
        TimberTest.AnimTrace tr = finish(120);
        float max = 0;
        for (float[] r : tr.rot) max = Math.max(max, r[1] + r[3]);
        info("cliff pitch: " + pitches(tr));
        check("cliff: tips past level down onto the lower ground instead of hovering", max > 110, "landed at " + max);

        // cut high on a tall trunk: the short top can't reach the ground by tipping, so it slides off and drops
        plot();
        customTree(0, 0, 11, 2, "spruce_log", "spruce_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        cmd("tp TimberTester " + (cx - 2) + ".5 " + (Y + 5) + " " + cz + ".5 -90 10");
        cmd("fill " + at(-3, 4, -1) + " " + at(-1, 4, 1) + " minecraft:stone");
        chop(0, 6, 0);
        double y0 = TimberTest.on(() -> { for (net.minecraft.world.entity.Entity e : TimberTest.level.getAllEntities()) if (e.entityTags().contains("timber.ctl")) return e.getY(); return Double.NaN; });
        double yMin = y0;
        tr = new TimberTest.AnimTrace();
        for (int i = 0; i < 90; i++) {
            double yy = TimberTest.on(() -> { for (net.minecraft.world.entity.Entity e : TimberTest.level.getAllEntities()) if (e.entityTags().contains("timber.ctl")) return e.getY(); return Double.NaN; });
            if (Double.isNaN(yy)) break;
            yMin = Math.min(yMin, yy);
            tick(1);
        }
        check("high cut: the top slides off the stump and drops to the ground", y0 - yMin >= 3, "pivot " + y0 + " -> lowest " + yMin);

        // leaves in the fall path do not stop the trunk; stone does
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        cmd("fill " + at(3, 0, -1) + " " + at(6, 4, 1) + " minecraft:oak_leaves[persistent=true]");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        float yaw = ((ctlYaw() % 360) + 360) % 360;
        tr = finish(120);
        max = 0;
        for (float[] r : tr.rot) max = Math.max(max, r[1] + r[3]);
        check("hedge in the way: falls through leaves and lies flat", Math.abs(Math.abs(yaw - 270) - 33.75) < 0.01 && max > 89, "yaw " + yaw + ", landed at " + max);
    }

    static void modes() throws Exception {
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(30);
        cmd("scoreboard players set #drops timber.config 1");
        hold("minecraft:iron_axe");
        stand(-6, 0, -90);
        chop(0, 0, 0);
        finish(120);
        tick(2);
        int atPlayer = TimberTest.itemsNear(p(-6, 0, 0), 1.5).getOrDefault("minecraft:oak_log", 0);
        check("drops=1: logs appear at the player", atPlayer == 5, atPlayer + " logs at the player");

        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(30);
        cmd("scoreboard players set #animation timber.config 0");
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        tick(1);
        int near = TimberTest.itemsNear(p(0, 0, 0), 3).getOrDefault("minecraft:oak_log", 0);
        check("animation off: instant, drops at the stump", displays() == 0 && count(Scenarios::isLog, 6) == 0 && near == 6, "displays " + displays() + ", logs dropped nearby " + near);
    }

    static void durability() throws Exception {
        plot();
        customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
        tick(30);
        hold("minecraft:iron_axe[minecraft:damage=245]");
        TimberTest.chat();
        chop(0, 0, 0);
        List<String> bar = TimberTest.chat();
        check("worn axe: tree not felled, warning shown", count(Scenarios::isLog, 6) == 7 && bar.toString().contains("too worn"), count(Scenarios::isLog, 6) + " logs, chat " + bar);
        check("worn axe: survives with the vanilla single-block wear", damage() == 246, "damage " + damage());

        plot();
        feature("mega_spruce", 0, 0);
        tick(40);
        int logs = count(Scenarios::isLog, 10);
        hold("minecraft:diamond_axe[minecraft:enchantments={\"minecraft:unbreaking\":3}]");
        chop(0, 0, 0);
        check("unbreaking III: wear reduced", damage() > 0 && damage() < logs * 0.6, "damage " + damage() + " for " + logs + " logs");
        finish(120);

        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(30);
        hold("minecraft:iron_axe[minecraft:unbreakable={}]");
        chop(0, 0, 0);
        check("unbreakable: no wear", damage() == 0 && count(Scenarios::isLog, 6) == 0, "damage " + damage());
        finish(120);
    }

    static void robustness() throws Exception {
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        customTree(10, 0, 6, 2, "spruce_log", "spruce_leaves");
        tick(30);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        tick(20);
        cmd("reload");
        tick(3);
        stand(8, 0, -90);
        chop(10, 0, 0);
        check("two trees animate at once", TimberTest.controllers() == 2, TimberTest.controllers() + " controllers");
        tick(90);
        check("both finish after a /reload mid-animation", TimberTest.controllers() == 0 && displays() == 0, TimberTest.controllers() + " controllers, " + displays() + " displays");
        check("both trees dropped their logs", itemsOf("minecraft:oak_log", 30) == 6 && itemsOf("minecraft:spruce_log", 30) == 6,
            TimberTest.itemsNear(p(5, 0, 0), 30).toString());
    }

    static void everyTree(boolean v263) throws Exception {
        List<String> trees = new ArrayList<>(List.of("oak", "fancy_oak", "birch", "super_birch_bees", "spruce", "pine", "mega_spruce", "mega_pine", "jungle_tree",
            "mega_jungle_tree", "acacia", "dark_oak", "cherry", "pale_oak", "pale_oak_creaking", "azalea_tree", "mangrove", "tall_mangrove", "swamp_oak"));
        if (v263) trees.addAll(List.of("red_poplar", "orange_poplar", "yellow_poplar"));
        everyTreeList(trees);
    }

    static void everyTreeList(List<String> trees) throws Exception {
        List<String> times = new ArrayList<>();
        for (String f : trees) {
            BlockPos base = null;
            // features are random: a mangrove on roots sometimes grows no log in its origin column, so re-roll it
            for (int roll = 0; roll < 4 && base == null; roll++) {
                plot();
                feature(f, 0, 0);
                tick(40);
                base = TimberTest.on(() -> {
                    for (int y = 0; y < 8; y++) {
                        BlockPos q = new BlockPos(cx, Y + y, cz);
                        if (TimberTest.level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) return q;
                    }
                    return null;
                });
            }
            if (base == null) { check("tree " + f + ": has a trunk at the feature origin", false, "no log in the column"); continue; }
            int logs = count(Scenarios::isLog, 14), leaves = count(Scenarios::natLeaf, 14);
            int orphans = count(q -> natLeaf(q) && q.getValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE) == 7, 14);
            hold("minecraft:netherite_axe");
            long[] t0 = TimberTest.tickTimes();
            TimberTest.destroy(base);
            tick(2);
            long worst = TimberTest.worstTickSince(t0);
            int d = displays();
            long air = TimberTest.treeDisplays().stream().filter(x -> TimberTest.displayBlock(x).isAir()).count();
            int logsLeft = count(Scenarios::isLog, 14), leavesLeft = count(Scenarios::natLeaf, 14);
            TimberTest.AnimTrace tr = finish(140);
            float max = 0;
            for (float[] r : tr.rot) max = Math.max(max, r[1] + r[3]);
            times.add(f + " " + d + "d " + worst / 100_000 / 10.0 + "ms");
            if (logsLeft > 0) {
                StringBuilder where = new StringBuilder();
                TimberTest.on(() -> { for (BlockPos q : BlockPos.betweenClosed(cx - 14, Y - 1, cz - 14, cx + 14, Y + 40, cz + 14)) if (isLog(TimberTest.level.getBlockState(q))) where.append(q.getX() - cx).append(',').append(q.getY() - Y).append(',').append(q.getZ() - cz).append(' '); return null; });
                info("  " + f + " left logs at " + where + "| base y " + (base.getY() - Y) + " n " + score("#n") + " l1 " + score("#l1") + " crown " + score("#crown") + " tree " + score("#tree") + " lab " + score("#lab") + " logs " + score("#logs"));
            }
            check("tree " + f + ": felled and animated", logsLeft == 0 && leavesLeft <= orphans + Math.max(2, leaves * 3 / 100) && d > 0 && air == 0 && tr.rot.size() > 30 && max >= 85,
                logs + " logs/" + leaves + " leaves (" + orphans + " detached), " + air + " invisible displays -> left " + logsLeft + "/" + leavesLeft + ", " + d + " displays, landed " + max + ", " + tr.rot.size() + " ticks, worst tick " + worst / 1_000_000 + " ms");
        }
        info("felling tick cost: " + times);
    }

    static void mangroves() throws Exception {
        int ok = 0, total = 0;
        StringBuilder bad = new StringBuilder();
        for (int k = 0; k < 12; k++) {
            plot();
            feature(k % 2 == 0 ? "mangrove" : "tall_mangrove", 0, 0);
            tick(40);
            BlockPos base = TimberTest.on(() -> {
                for (int y = 0; y < 12; y++) {
                    BlockPos q = new BlockPos(cx, Y + y, cz);
                    if (TimberTest.level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) return q;
                }
                return null;
            });
            if (base == null) continue;
            total++;
            int logs = count(Scenarios::isLog, 14);
            hold("minecraft:netherite_axe");
            TimberTest.destroy(base);
            tick(2);
            int left = count(Scenarios::isLog, 14);
            if (left == 0) ok++;
            else {
                bad.append(k).append(": ").append(logs).append(" logs -> ").append(left).append(" left; ");
                StringBuilder col = new StringBuilder();
                for (int y = 0; y < 14; y++) col.append(y).append('=').append(TimberTest.blockId(p(0, y, 0)).replace("minecraft:", "")).append(' ');
                info("mangrove " + k + " base y=" + (base.getY() - Y) + " column " + col + " | n " + score("#n") + " l1 " + score("#l1") + " crown " + score("#crown") + " tree " + score("#tree") + " lab " + score("#lab") + " logs " + score("#logs"));
            }
            finish(140);
        }
        check("mangroves: every random mangrove fully felled", ok == total && total >= 8, ok + "/" + total + " " + bad);
    }

    static void decor() throws Exception {
        plot();
        customTree(0, 0, 7, 2, "jungle_log", "jungle_leaves");
        cmd("setblock " + at(1, 1, 0) + " minecraft:cocoa[facing=west,age=2]");
        cmd("fill " + at(-1, 1, 0) + " " + at(-1, 3, 0) + " minecraft:vine[east=true]");
        cmd("setblock " + at(-2, 3, 0) + " minecraft:vine[up=true]");
        cmd("setblock " + at(0, 8, 0) + " minecraft:snow[layers=2]");
        tick(30);
        hold("minecraft:iron_axe");
        stand(0, -3, 0);
        chop(0, 0, 0);
        int vines = blocks("minecraft:vine", 6), cocoa = blocks("minecraft:cocoa", 6), snow = blocks("minecraft:snow", 6);
        check("decor: vines, cocoa and snow go with the tree", vines == 0 && cocoa == 0 && snow == 0, "vines " + vines + ", cocoa " + cocoa + ", snow " + snow);
        long vineDisplays = TimberTest.treeDisplays().stream().filter(x -> TimberTest.displayBlock(x).toString().contains("vine")).count();
        check("decor: vines ride along as displays with their faces", vineDisplays == 4, vineDisplays + " vine displays");
        finish(120);
        tick(2);
        check("decor: ripe cocoa drops beans", itemsOf("minecraft:cocoa_beans", 20) >= 2, TimberTest.itemsNear(p(0, 0, 0), 20).toString());
    }

    static int tb(String holder) {
        String out = cmd("scoreboard players get " + holder + " tbtest").toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(" has (-?\\d+) ").matcher(out);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MIN_VALUE;
    }

    static int requiresCount() {
        String out = cmd("data get storage timber:meta requires").toString();
        return out.contains("Found no elements") ? -1 : out.split("Test requirement", -1).length - 1;
    }

    /** Plants the standard birch test tree on a fresh plot and returns its log count. */
    static int hookTree() throws Exception {
        plot();
        customTree(0, 0, 6, 2, "birch_log", "birch_leaves");
        tick(30);
        hold("minecraft:iron_axe");
        cmd("scoreboard players set #calls_a tbtest 0");
        cmd("scoreboard players set #calls_b tbtest 0");
        return count(Scenarios::isLog, 6);
    }

    /** Add-on hooks, via the fixture pack dev/test/hookpack. */
    static void hooks() throws Exception {
        cmd("scoreboard players reset * tbtest");
        cmd("datapack enable \"file/hookpack\"");
        tick(5);
        check("hooks: api/loaded runs after version_id is set", tb("#loaded") == 1 && tb("#version_id") == 10300,
            "loaded=" + tb("#loaded") + " version_id=" + tb("#version_id"));
        cmd("reload");
        tick(5);
        check("hooks: requires text rebuilt on /reload, not duplicated", requiresCount() == 1 && tb("#loaded") == 2, "requires=" + requiresCount());

        int logs = hookTree();
        chop(0, 0, 0);
        check("hooks: hooks that don't return allow the fell", count(Scenarios::isLog, 6) == 0 && displays() > 0 && tb("#calls_a") == 1 && tb("#calls_b") == 1,
            "logs left " + count(Scenarios::isLog, 6) + " a=" + tb("#calls_a") + " b=" + tb("#calls_b"));
        check("hooks: run as the player, at the chopped log's centre", tb("#player") == 1 && tb("#hx") == cx * 10 + 5 && tb("#hy") == Y * 10 + 5 && tb("#hz") == cz * 10 + 5,
            "player=" + tb("#player") + " at " + tb("#hx") + " " + tb("#hy") + " " + tb("#hz") + " (log " + cx + " " + Y + " " + cz + ")");
        finish(120);

        cmd("tag TimberTester add tbtest.veto_a");
        logs = hookTree();
        TimberTest.chat();
        chop(0, 0, 0);
        tick(3);
        check("hooks: return 1 cancels the fell, the chopped log still breaks", count(Scenarios::isLog, 6) == logs - 1 && displays() == 0 && damage() == 1,
            "logs " + logs + " -> " + count(Scenarios::isLog, 6) + ", displays " + displays() + ", damage " + damage());
        check("hooks: first returning hook ends the chain", tb("#calls_b") == 0, "b=" + tb("#calls_b"));
        List<String> bar = TimberTest.chat();
        check("hooks: cancelled fell shows no action bar", bar.stream().noneMatch(m -> m.startsWith("[actionbar]")), bar.toString());

        cmd("tag TimberTester remove tbtest.veto_a");
        cmd("tag TimberTester add tbtest.veto_b");
        logs = hookTree();
        chop(0, 0, 0);
        check("hooks: a later hook can cancel after an earlier one falls through", count(Scenarios::isLog, 6) == logs - 1 && displays() == 0,
            "logs " + logs + " -> " + count(Scenarios::isLog, 6));
        cmd("tag TimberTester remove tbtest.veto_b");

        logs = hookTree();
        TimberTest.sneak(true);
        chop(0, 0, 0);
        TimberTest.sneak(false);
        check("hooks: run only after Timber's own checks pass", tb("#calls_a") == 0 && count(Scenarios::isLog, 6) == logs - 1, "a=" + tb("#calls_a"));

        TimberTest.chat();
        cmd("execute as TimberTester run function timber:player/welcome");
        List<String> hello = TimberTest.chat();
        check("hooks: join hint shows add-on requirement", hello.stream().anyMatch(m -> m.contains("Sneak to take a single log. Test requirement. [Toggle]")), hello.toString());
        cmd("execute as TimberTester run function timber:settings");
        List<String> shown = TimberTest.chat();
        check("hooks: menu shows add-on requirement", shown.size() == 13 && shown.get(1).contains("Test requirement"), shown.size() + " lines: " + shown);

        cmd("datapack disable \"file/hookpack\"");
        tick(5);
        hookTree();
        chop(0, 0, 0);
        check("hooks: removing the add-on clears its requirement and hooks", requiresCount() == -1 && count(Scenarios::isLog, 6) == 0,
            "requires=" + requiresCount() + " logs left " + count(Scenarios::isLog, 6));
        finish(120);
    }

    static double ctlY() {
        return TimberTest.on(() -> {
            for (net.minecraft.world.entity.Entity e : TimberTest.level.getAllEntities()) if (e.entityTags().contains("timber.ctl")) return e.getY();
            return Double.NaN;
        });
    }

    /** Item id -> total count in an SNBT dump of item stacks (the controller's recorded drops). */
    static Map<String, Integer> lootOf(String snbt) {
        Map<String, Integer> m = new java.util.TreeMap<>();
        java.util.regex.Matcher c = java.util.regex.Pattern.compile("\\{[^{}]*\\}").matcher(snbt);
        while (c.find()) {
            java.util.regex.Matcher id = java.util.regex.Pattern.compile("id: ?\"([a-z0-9_:]+)\"").matcher(c.group());
            java.util.regex.Matcher n = java.util.regex.Pattern.compile("count: ?(\\d+)").matcher(c.group());
            if (id.find()) m.merge(id.group(1), n.find() ? Integer.parseInt(n.group(1)) : 1, Integer::sum);
        }
        return m;
    }

    static int tagged(String tag) {
        return (int) TimberTest.treeDisplays().stream().filter(d -> d.entityTags().contains(tag)).count();
    }

    /** 1.2.0 animation: hang + drop, tip to the side, crown burst at the slam, ring-by-ring pop, fair drops. */
    /** World position of a display's block-local point v, as the client draws it (entity yaw/pitch, then the transformation). */
    static org.joml.Vector3d drawn(net.minecraft.world.entity.Display.BlockDisplay d, org.joml.Vector3f v) {
        org.joml.Vector3fc tr = TimberTest.displayData(d, "DATA_TRANSLATION_ID");
        org.joml.Quaternionfc left = TimberTest.displayData(d, "DATA_LEFT_ROTATION_ID");
        org.joml.Quaternionfc right = TimberTest.displayData(d, "DATA_RIGHT_ROTATION_ID");
        org.joml.Vector3fc scale = TimberTest.displayData(d, "DATA_SCALE_ID");
        org.joml.Vector3f c = new org.joml.Vector3f(v);
        right.transform(c);
        c.mul(scale);
        left.transform(c);
        c.add(tr);
        new org.joml.Quaternionf().rotationYXZ((float) Math.toRadians(-d.getYRot()), (float) Math.toRadians(d.getXRot()), 0f).transform(c);
        return new org.joml.Vector3d(d.getX() + c.x, d.getY() + c.y, d.getZ() + c.z);
    }

    static void pose() throws Exception {
        // mid-fall the entity pitch is a byte step (what the client receives) and each transformation carries the rest:
        // together the unbent bottom ring must sit exactly where the true pitch puts it, and the bent rings stay joined
        plot();
        customTree(0, 0, 9, 2, "birch_log", "birch_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        double[] res = null;
        for (int t = 0; t < 60 && res == null; t++) {
            tick(1);
            res = TimberTest.on(() -> {
                net.minecraft.world.entity.Entity ctl = null;
                for (net.minecraft.world.entity.Entity e : TimberTest.level.getAllEntities()) if (e.entityTags().contains("timber.ctl")) ctl = e;
                if (ctl == null) return null;
                net.minecraft.world.scores.Scoreboard sb = TimberTest.server.getScoreboard();
                java.util.function.ToIntBiFunction<net.minecraft.world.entity.Entity, String> sc = (e, o) -> sb.getPlayerScoreInfo(e, sb.getObjective(o)).value();
                int ph = sc.applyAsInt(ctl, "timber.ph"), pt = sc.applyAsInt(ctl, "timber.p");
                if (ph != 1 || pt < 2000) return null;
                double worst = 0, gap = 0, pitch = pt / 100.0;
                java.util.Map<Integer, net.minecraft.world.entity.Display.BlockDisplay> logAt = new java.util.HashMap<>();
                for (net.minecraft.world.entity.Entity e : TimberTest.level.getAllEntities()) {
                    if (!(e instanceof net.minecraft.world.entity.Display.BlockDisplay d) || !d.entityTags().contains("timber.lg")) continue;
                    int k = sc.applyAsInt(d, "timber.k");
                    logAt.putIfAbsent(k, d);
                    if (k != 0) continue;
                    org.joml.Vector3f c = new org.joml.Vector3f(0.5f, 0.5f, 0.5f);
                    ((org.joml.Quaternionfc) TimberTest.displayData(d, "DATA_RIGHT_ROTATION_ID")).transform(c);
                    c.add(sc.applyAsInt(d, "timber.x") / 1000f, sc.applyAsInt(d, "timber.y") / 1000f, sc.applyAsInt(d, "timber.z") / 1000f);
                    new org.joml.Quaternionf().rotationYXZ((float) Math.toRadians(-ctl.getYRot()), (float) Math.toRadians(pitch), 0f).transform(c);
                    org.joml.Vector3d want = new org.joml.Vector3d(ctl.getX() + c.x, ctl.getY() + c.y, ctl.getZ() + c.z);
                    worst = Math.max(worst, want.distance(drawn(d, new org.joml.Vector3f(0.5f, 0.5f, 0.5f))));
                }
                for (int k : logAt.keySet()) {
                    var up = logAt.get(k + 1);
                    if (up == null || sc.applyAsInt(up, "timber.x") != sc.applyAsInt(logAt.get(k), "timber.x") || sc.applyAsInt(up, "timber.z") != sc.applyAsInt(logAt.get(k), "timber.z")) continue;
                    // rings are stacked flush: the same orientation, each sliding at most a bent step along the one below
                    org.joml.Vector3d a0 = drawn(logAt.get(k), new org.joml.Vector3f(0.5f, 0f, 0.5f)), a1 = drawn(logAt.get(k), new org.joml.Vector3f(0.5f, 1f, 0.5f));
                    org.joml.Vector3d b0 = drawn(up, new org.joml.Vector3f(0.5f, 0f, 0.5f)), b1 = drawn(up, new org.joml.Vector3f(0.5f, 1f, 0.5f));
                    org.joml.Vector3d da = new org.joml.Vector3d(a1).sub(a0), db = new org.joml.Vector3d(b1).sub(b0);
                    gap = Math.max(gap, a1.distance(b0) + 10 * da.angle(db));
                }
                return new double[] {pitch, ctl.getXRot(), worst, gap, sc.applyAsInt(ctl, "timber.bn") / 100.0};
            });
        }
        check("pose: mid-fall the trunk sits exactly at its true pitch (byte step + residual)", res != null && res[2] < 0.01 && res[1] % 1.40625 == 0,
            res == null ? "never saw the fall" : "pitch " + res[0] + ", entity " + res[1] + ", worst offset " + res[2]);
        check("pose: the bent trunk's rings stay flush (parallel, sliding a small step)", res != null && res[3] < 0.15,
            res == null ? "never saw the fall" : "bend " + res[4] + " deg, widest gap " + res[3]);
        finish(160);
    }

    static void animation() throws Exception {
        // the tree hangs a block up over the chopped log for a beat, then drops into the gap
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        double cy = ctlY();
        List<net.minecraft.world.entity.Display.BlockDisplay> ds = TimberTest.treeDisplays();
        long up = TimberTest.on(() -> ds.stream().filter(d -> Math.abs(d.getY() - cy - 1) < 0.001).count());
        check("hang: at the chop the whole tree hangs one block up, over the gap", up == ds.size() && up > 0, up + " of " + ds.size() + " displays one block up");
        tick(5);
        long down = TimberTest.on(() -> ds.stream().filter(d -> Math.abs(d.getY() - cy) < 0.001).count());
        check("hang: then it drops into the gap", down == ds.size(), down + " of " + ds.size() + " displays on the pivot after 5 ticks");
        finish(160);

        // a 2x2 trunk still stands on its other three logs, so it doesn't drop
        plot();
        feature("mega_spruce", 0, 0);
        tick(40);
        hold("minecraft:netherite_axe");
        TimberTest.destroy(TimberTest.on(() -> {
            for (int y = 0; y < 6; y++) { BlockPos q = new BlockPos(cx, Y + y, cz); if (isLog(TimberTest.level.getBlockState(q))) return q; }
            return p(0, 0, 0);
        }));
        tick(1);
        double cy2 = ctlY();
        List<net.minecraft.world.entity.Display.BlockDisplay> ds2 = TimberTest.treeDisplays();
        long raised = TimberTest.on(() -> ds2.stream().filter(d -> Math.abs(d.getY() - cy2) > 0.001).count());
        check("hang: a 2x2 trunk cut in one corner doesn't drop", raised == 0 && !ds2.isEmpty(), raised + " of " + ds2.size() + " displays off the pivot");
        finish(200);

        // the tip: with the right-hand side (south-east) walled off, it tips to the left instead
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        cmd("fill " + at(2, 0, 2) + " " + at(9, 7, 9) + " minecraft:stone");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        float yaw = ((ctlYaw() % 360) + 360) % 360;
        check("tip: tips 33.75 deg to the open side", Math.abs(yaw - 236.25) < 0.01, "yaw " + yaw);
        finish(160);

        // burst, ripple and fairness on one tree
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        tick(40);
        int logs = count(Scenarios::isLog, 6);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        Map<String, Integer> want = lootOf(TimberTest.full("entity @e[type=marker,tag=timber.ctl,limit=1] data.drops"));
        lootOf(TimberTest.full("entity @e[type=marker,tag=timber.ctl,limit=1] data.ldrops")).forEach((k, v) -> want.merge(k, v, Integer::sum));
        want.merge("minecraft:oak_log", 1, Integer::sum);
        java.util.Map<net.minecraft.world.entity.Display.BlockDisplay, Float> height = new java.util.HashMap<>();
        List<net.minecraft.world.entity.Display.BlockDisplay> all = TimberTest.treeDisplays();
        TimberTest.on(() -> { for (var d : all) if (d.entityTags().contains("timber.lg")) height.put(d, TimberTest.<org.joml.Vector3fc>displayData(d, "DATA_TRANSLATION_ID").y()); return null; });
        java.util.Map<net.minecraft.world.entity.Display.BlockDisplay, Integer> gone = new java.util.HashMap<>();
        int burstAt = -1, lfBefore = tagged("timber.lf"), lgAtBurst = 0;
        java.util.TreeSet<Integer> logSteps = new java.util.TreeSet<>();
        int lastLogs = itemsOf("minecraft:oak_log", 24);
        for (int t = 1; t <= 160 && TimberTest.controllers() > 0; t++) {
            tick(1);
            if (burstAt < 0 && tagged("timber.lf") == 0) { burstAt = t; lgAtBurst = tagged("timber.lg"); }
            for (var e : height.keySet()) if (!gone.containsKey(e) && TimberTest.on(e::isRemoved)) gone.put(e, t);
            int n = itemsOf("minecraft:oak_log", 24);
            if (n > lastLogs) logSteps.add(t);
            lastLogs = n;
        }
        check("burst: at the slam the crown's leaves burst while the trunk still lies there", lfBefore > 0 && burstAt > 20 && lgAtBurst == logs - 1,
            lfBefore + " leaf displays gone at tick " + burstAt + ", " + lgAtBurst + " log displays left");
        List<Float> hs = new ArrayList<>(new java.util.TreeSet<>(height.values()));
        boolean ordered = true;
        int first = Integer.MAX_VALUE, last = -1;
        for (var e : height.entrySet()) {
            int g = gone.getOrDefault(e.getKey(), -1);
            first = Math.min(first, g); last = Math.max(last, g);
            for (var o : height.entrySet()) if (o.getValue() > e.getValue() + 0.5f && gone.getOrDefault(o.getKey(), -1) < g) ordered = false;
        }
        check("ripple: logs pop one ring at a time from the stump end to the tip", ordered && first > burstAt && last - first >= 8,
            "popped between tick " + first + " and " + last + ", burst at " + burstAt + ", " + hs.size() + " rings");
        check("ripple: each ring throws its log as it pops (not all at once)", logSteps.size() >= logs - 2, "log items appeared at ticks " + logSteps);
        tick(3);
        Map<String, Integer> got = TimberTest.itemsNear(p(0, 0, 0), 24);
        check("fair: the ground holds exactly the loot rolled at the chop, plus the chopped log", got.equals(want), "got " + got + ", rolled " + want);

        // blocks changing mid-animation (someone rebuilds the trunk, the landing spot gets walled over) change nothing
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        tick(40);
        logs = count(Scenarios::isLog, 6);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        tick(10);
        cmd("fill " + at(0, 1, 0) + " " + at(0, 4, 0) + " minecraft:oak_log");
        cmd("fill " + at(1, 0, -6) + " " + at(9, 1, 6) + " minecraft:stone");
        finish(160);
        tick(3);
        cmd("fill " + at(1, 0, -6) + " " + at(9, 1, 6) + " minecraft:air");
        tick(20);
        int dropped = itemsOf("minecraft:oak_log", 24);
        int rebuilt = count(Scenarios::isLog, 1);
        check("fair: blocks changed mid-animation don't add or lose drops", dropped == logs && rebuilt == 4, dropped + " of " + logs + " logs dropped, " + rebuilt + " rebuilt logs untouched");

        // a tree still falling in a world saved by 1.1.0 (no ring data, drops as a plain item list) lands its drops and ends
        plot();
        cmd("summon marker " + at(0, 0, 0) + " {Tags:[\"timber.ctl\"],data:{drops:[{id:\"minecraft:oak_log\",count:3},{id:\"minecraft:stick\",count:2}]}}");
        cmd("summon block_display " + at(0, 0, 0) + " {Tags:[\"timber.d\",\"timber.b0\"],block_state:{Name:\"minecraft:oak_log\",id:\"minecraft:oak_log\"}}");
        cmd("scoreboard players set @e[type=marker,tag=timber.ctl] timber.ph 3");
        tick(3);
        check("upgrade: a 1.1.0 tree mid-animation drops its loot and ends", TimberTest.controllers() == 0 && displays() == 0 && itemsOf("minecraft:oak_log", 8) == 3 && itemsOf("minecraft:stick", 8) == 2,
            TimberTest.controllers() + " controllers, " + displays() + " displays, " + TimberTest.itemsNear(p(0, 0, 0), 8));

        // uninstall while a tree is falling: its drops still land
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        logs = count(Scenarios::isLog, 6);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        tick(12);
        cmd("function timber:uninstall");
        tick(3);
        check("uninstall mid-fall: nothing left behind, every log still drops", displays() == 0 && TimberTest.controllers() == 0 && itemsOf("minecraft:oak_log", 24) == logs,
            displays() + " displays, " + TimberTest.controllers() + " controllers, " + itemsOf("minecraft:oak_log", 24) + " of " + logs + " logs");
        cmd("reload");
        tick(5);
    }

    static void settingsAndUninstall() throws Exception {
        TimberTest.chat();
        List<String> menu = cmd("execute as TimberTester run function timber:settings");
        List<String> shown = TimberTest.chat();
        for (String m : shown) info("menu| " + m);
        check("settings menu renders", shown.size() == 12 && menu.stream().noneMatch(m -> m.contains("rror") || m.contains("<--")), shown.size() + " lines " + menu);
        cmd("function timber:settings/set {key:max_logs,value:99999}");
        check("max_logs clamped", cmd("scoreboard players get #max_logs timber.config").toString().contains("1024"), "");
        cmd("scoreboard players set #radius timber.config 20");
        cmd("reload");
        tick(5);
        check("config survives /reload", cmd("scoreboard players get #radius timber.config").toString().contains("20"), "");
        cmd("execute as TimberTester run trigger timber");
        tick(2);
        List<String> t = TimberTest.chat();
        check("/trigger timber toggles off with a message", t.toString().contains("disabled") && cmd("scoreboard players get TimberTester timber.off").toString().contains("has 1"), t.toString());
        cmd("execute as TimberTester run function timber:player/welcome");
        List<String> w = TimberTest.chat();
        check("welcome hint", w.toString().contains("Sneak to take a single log"), w.toString());
        List<String> un = cmd("execute as TimberTester run function timber:uninstall");
        List<String> objs = cmd("scoreboard objectives list");
        check("uninstall removes all objectives", objs.stream().noneMatch(o -> o.contains("timber")), objs + " " + un);
    }
}
