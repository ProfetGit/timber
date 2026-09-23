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
        net.minecraft.server.Main.main(new String[] {"--nogui"});
        server = findServer();
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
            CommandSource capture = new CommandSource() {
                public void sendSystemMessage(Component c) { out.add(c.getString()); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean shouldInformAdmins() { return false; }
            };
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), command);
            return out;
        });
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
        List<Display.BlockDisplay> ds = treeDisplays();
        tr.displays = ds.size();
        if (ds.isEmpty()) return tr;
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
        check("base pack runs alone", !packs.toString().contains("hookpack") && packs.toString().contains("Timber-"), packs.toString());
        check("version_id stored for add-ons", cmd("data get storage timber:meta version_id").toString().contains("10100"),
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
        if (only.isEmpty() || only.contains("settings")) settingsAndUninstall();
    }

    static void oakFullCycle() throws Exception {
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        info("oak leaves after settle: " + TimberTest.leafCheck(new int[] {cx - 4, Y, cz - 4, cx + 4, Y + 8, cz + 4}));
        int logs = count(Scenarios::isLog, 6), leaves = count(Scenarios::natLeaf, 6);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        int d = displays();
        check("oak: world blocks replaced by displays in one tick", count(Scenarios::isLog, 6) == 0 && count(Scenarios::natLeaf, 6) == 0,
            "logs left " + count(Scenarios::isLog, 6) + ", leaves left " + count(Scenarios::natLeaf, 6));
        check("oak: one display per felled block", d == logs - 1 + leaves, d + " displays for " + (logs - 1) + " logs + " + leaves + " leaves");
        check("oak: falls away from the player (east)", Math.abs(((ctlYaw() % 360) + 360) % 360 - 270) < 0.5, "yaw " + ctlYaw());
        TimberTest.AnimTrace tr = finish(120);
        info("oak pitch per tick: " + pitches(tr));
        List<Float> pitch = new ArrayList<>();
        for (float[] r : tr.rot) if (r[2] == 0) pitch.add(r[1] + r[3]);
        float min0 = 0, max = -99;
        int maxAt = -1;
        for (int i = 0; i < pitch.size(); i++) {
            if (i < 10) min0 = Math.min(min0, pitch.get(i));
            if (pitch.get(i) > max + 0.01f) { max = pitch.get(i); maxAt = i; }
        }
        int dips = 0;
        for (int i = maxAt + 1; i < pitch.size() - 1; i++)
            if (pitch.get(i) < pitch.get(i - 1) && pitch.get(i) <= pitch.get(i + 1)) dips++;
        int rest = 0;
        for (int i = pitch.size() - 1; i > 0 && Math.abs(pitch.get(i) - max) < 0.01f; i--) rest++;
        check("oak: leans back first (anticipation)", min0 < -2 && min0 > -3, "min pitch in first 10 ticks " + min0);
        check("oak: lands flat on open ground", max > 89.9f, "impact pitch " + max + " at tick " + maxAt);
        check("oak: three bounces", dips == 3, dips + " dips after impact");
        check("oak: holds still before the poof", rest >= 10, rest + " still ticks");
        check("oak: whole gag lasts 2.5-3.5 s", tr.rot.size() >= 50 && tr.rot.size() <= 70, tr.rot.size() + " ticks");
        tick(2);
        check("oak: displays and controller gone after poof", displays() == 0 && TimberTest.controllers() == 0, displays() + " displays, " + TimberTest.controllers() + " controllers");
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
                    org.joml.Vector3fc tr = TimberTest.displayData(d, "DATA_TRANSLATION_ID");
                    org.joml.Quaternionfc left = TimberTest.displayData(d, "DATA_LEFT_ROTATION_ID");
                    org.joml.Vector3f c = new org.joml.Vector3f(0.5f, 0.5f, 0.5f);
                    left.transform(c);
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
        List<String> keys = cmd("data get storage timber:placed o");
        info("placed keys: " + keys + " place result " + r);
        boolean keysMatch = true;
        for (int y = 0; y < 5; y++) keysMatch &= keys.toString().contains((cx) + "," + (Y + y) + "," + cz + "\"");
        int logs = count(Scenarios::isLog, 6);
        check("placed: exactly the 5 placed logs tracked", logs == 5 && keysMatch && keys.toString().split("1b").length - 1 == 5, logs + " logs, " + keys);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        check("placed: player pillar -> only the chopped log", count(Scenarios::isLog, 6) == 4 && displays() == 0, count(Scenarios::isLog, 6) + " logs left");
        List<String> keys2 = cmd("data get storage timber:placed o");
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
        check("hedge in the way: falls through leaves and lies flat", Math.abs(yaw - 270) < 0.5 && max > 89, "yaw " + yaw + ", landed at " + max);
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
            plot();
            feature(f, 0, 0);
            tick(40);
            BlockPos base = TimberTest.on(() -> {
                for (int y = 0; y < 8; y++) {
                    BlockPos q = new BlockPos(cx, Y + y, cz);
                    if (TimberTest.level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) return q;
                }
                return null;
            });
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
            check("tree " + f + ": felled and animated", logsLeft == 0 && leavesLeft <= orphans + Math.max(2, leaves * 3 / 100) && d > 0 && air == 0 && tr.rot.size() > 40 && max >= 85,
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
        check("hooks: api/loaded runs after version_id is set", tb("#loaded") == 1 && tb("#version_id") == 10100,
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
