package io.github.profetgit.vanillatimber.selftest;

import com.mojang.math.Transformation;
import io.github.profetgit.vanillatimber.core.Rig;
import io.github.profetgit.vanillatimber.server.Fall;
import io.github.profetgit.vanillatimber.server.Falls;
import io.github.profetgit.vanillatimber.server.Viewers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * The 1.3.6 TimberTest groups on the Java mod (M2 wave 1): a harness thread like 1.3.6's, so the group bodies read the
 * same as {@code packs/Timber/dev/test/TimberTest.java}. Assert labels are 1.3.6's. The thread talks to the server with
 * {@link #on}, waits with {@link #tick}, and reads each fall's frames from {@link Fall#probe}.
 */
final class Parity {
    static final int G = -60, Y = -59;
    private static final AtomicBoolean FINISHED = new AtomicBoolean();

    static MinecraftServer server;
    static ServerLevel level;
    static ServerPlayer player;
    static int plotIdx, cx, cz;
    static long destroyedAt;
    static Fall cur;
    static final List<Fall> plotFalls = new ArrayList<>();
    static final Map<Fall, Trace> TRACES = new ConcurrentHashMap<>();
    static volatile Runnable sampler;

    private Parity() {}

    static boolean finished() {
        return FINISHED.get();
    }

    /** One server frame of a fall, as the probe saw it. {@code t} = ticks since the chop (frame 0 is t = 1). */
    static final class Frame {
        int n, t, displays, leaves, logs;
        double pitch, y, far;
        float yaw;
    }

    static final class Trace {
        final Fall fall;
        final List<Frame> frames = new ArrayList<>();
        volatile boolean done;
        boolean seen;

        Trace(Fall fall) {
            this.fall = fall;
        }

        synchronized List<Frame> copy() {
            return new ArrayList<>(frames);
        }

        /** Frames with displays still in the world: 1.3.6 traced the top log until it went. */
        List<Frame> shown() {
            List<Frame> out = new ArrayList<>();
            for (Frame f : copy()) if (f.displays > 0) out.add(f);
            return out;
        }

        double maxPitch() {
            double max = -999;
            for (Frame f : copy()) max = Math.max(max, f.pitch);
            return max;
        }

        int end() {
            List<Frame> c = copy();
            return c.isEmpty() ? 0 : c.get(c.size() - 1).t;
        }
    }

    private static void onFrame(Fall f) {
        Trace t = TRACES.computeIfAbsent(f, Trace::new);
        if (t.done) return;
        Frame r = new Frame();
        r.n = f.n;
        r.t = f.n + 1;
        r.pitch = f.pitch();
        r.yaw = f.yaw();
        Vec3 pv = f.pivot();
        r.y = pv.y;
        for (Fall.Live l : f.live()) {
            r.displays++;
            if (l.part().leaf()) r.leaves++;
            else r.logs++;
            r.far = Math.max(r.far, l.display().position().distanceTo(pv));
        }
        synchronized (t) {
            t.frames.add(r);
        }
        if (r.displays > 0) t.seen = true;
        if (t.seen && r.displays == 0 || f.n >= f.tl.end) t.done = true;
    }

    static void begin(MinecraftServer s, String groups) {
        server = s;
        level = s.overworld();
        player = s.getPlayerList().getPlayers().getFirst();
        Viewers.test = Viewers.VANILLA;
        Fall.probe = Parity::onFrame;
        ServerTickEvents.END_SERVER_TICK.register(sv -> {
            Runnable r = sampler;
            if (r != null) r.run();
        });
        Thread th = new Thread(() -> {
            try {
                run(groups);
            } catch (Throwable t) {
                SelfTest.record("parity harness", false, t.toString());
                io.github.profetgit.vanillatimber.Timber.LOG.error("[parity] harness failed", t);
            } finally {
                Fall.probe = null;
                sampler = null;
                Viewers.test = 0;
                FINISHED.set(true);
            }
        }, "parity");
        th.setDaemon(true);
        th.start();
    }

    // ---- harness ----

    static <T> T on(Supplier<T> task) {
        if (server.isSameThread()) return task.get();
        return server.submit(task).join();
    }

    static void tick(int n) {
        int target = server.getTickCount() + n;
        while (server.getTickCount() < target) {
            if (!server.isRunning()) throw new IllegalStateException("server stopped");
            sleep(2);
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<String> cmd(String command) {
        return on(() -> {
            List<String> out = new ArrayList<>();
            CommandSource capture = (CommandSource) java.lang.reflect.Proxy.newProxyInstance(CommandSource.class.getClassLoader(),
                new Class<?>[] {CommandSource.class}, (proxy, m, a) -> switch (m.getName()) {
                    case "sendSystemMessage" -> {
                        out.add(((Component) a[0]).getString());
                        yield null;
                    }
                    case "acceptsSuccess", "acceptsFailure" -> true;
                    case "shouldInformAdmins" -> false;
                    default -> m.invoke(server, a);
                });
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), command);
            return out;
        });
    }

    /** Runs {@code step} on the server thread now, then at the end of every tick, until it returns false. */
    static CompletableFuture<Void> everyTick(java.util.function.LongPredicate step) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Runnable run = new Runnable() {
            @Override
            public void run() {
                try {
                    if (step.test(level.getGameTime())) return;
                    sampler = null;
                    done.complete(null);
                } catch (Throwable t) {
                    sampler = null;
                    done.completeExceptionally(t);
                }
            }
        };
        on(() -> {
            run.run();
            if (!done.isDone()) sampler = run;
            return null;
        });
        return done;
    }

    static void await(CompletableFuture<?> done) {
        while (!done.isDone()) {
            if (!server.isRunning()) throw new IllegalStateException("server stopped while sampling ticks");
            sleep(2);
        }
        done.join();
    }

    static void check(String name, boolean ok, String detail) {
        SelfTest.record(name, ok, ok ? (detail.isEmpty() ? "ok" : detail) : detail);
    }

    static void info(String msg) {
        io.github.profetgit.vanillatimber.Timber.LOG.info("[parity] {}", msg);
    }

    static void missing(String name, String why) {
        SelfTest.record(name, false, "MISSING FEATURE: " + why);
    }

    static void notApplicable(String name, String why) {
        info("N/A " + name + ": " + why);
    }

    // ---- world helpers ----

    static BlockPos p(int dx, int dy, int dz) {
        return new BlockPos(cx + dx, Y + dy, cz + dz);
    }

    static String at(int dx, int dy, int dz) {
        return (cx + dx) + " " + (Y + dy) + " " + (cz + dz);
    }

    static String who() {
        return player.getScoreboardName();
    }

    static void plot() {
        int i = plotIdx++ % 8;
        cx = 32 + 64 * (i % 4);
        cz = 256 + 64 * (i / 4);
        for (int w = 0; w < 400 && Falls.active(level) > 0; w++) tick(1);
        cmd("kill @e[type=block_display]");
        cmd("fill " + (cx - 32) + " " + Y + " " + (cz - 32) + " " + (cx + 31) + " -10 " + (cz + 31) + " minecraft:air");
        List<String> ground = cmd("fill " + (cx - 32) + " " + G + " " + (cz - 32) + " " + (cx + 31) + " " + G + " " + (cz + 31) + " minecraft:grass_block");
        if (!ground.toString().contains("filled") && !ground.toString().contains("No blocks")) throw new IllegalStateException("plot ground: " + ground);
        cmd("kill @e[type=item]");
        cmd("gamemode survival " + who());
        cmd("clear " + who());
        plotFalls.clear();
        cur = null;
        stand(-2, 0, -90);
        tick(2);
    }

    static void stand(int dx, int dz, float yaw) {
        cmd("tp " + who() + " " + (cx + dx) + ".5 " + Y + " " + (cz + dz) + ".5 " + yaw + " 20");
    }

    static void hold(String item) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item)));
        on(() -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return null;
        });
    }

    static void feature(String f, int dx, int dz) {
        info("  " + cmd("place feature minecraft:" + f + " " + at(dx, 0, dz)));
    }

    static int count(Predicate<BlockState> f, int r) {
        return on(() -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(cx - r, Y - 1, cz - r, cx + r, Y + 40, cz + r)) if (f.test(level.getBlockState(q))) n++;
            return n;
        });
    }

    static int enclosedLeaves(int r) {
        return on(() -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(cx - r, Y - 1, cz - r, cx + r, Y + 40, cz + r)) {
                if (!natLeaf(level.getBlockState(q))) continue;
                boolean shut = true;
                for (Direction dir : Direction.values()) {
                    BlockState o = level.getBlockState(q.relative(dir));
                    if (!o.is(BlockTags.LEAVES) && !isLog(o)) {
                        shut = false;
                        break;
                    }
                }
                if (shut) n++;
            }
            return n;
        });
    }

    static boolean isLog(BlockState s) {
        return s.is(BlockTags.LOGS);
    }

    static boolean isLeaf(BlockState s) {
        return s.getBlock() instanceof LeavesBlock;
    }

    static boolean natLeaf(BlockState s) {
        return s.is(BlockTags.LEAVES) && !s.getValue(LeavesBlock.PERSISTENT);
    }

    static int blocks(String id, int r) {
        return count(s -> BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().equals(id), r);
    }

    static String blockId(BlockPos pos) {
        return on(() -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
    }

    /** A simple natural tree: trunk of height h, 4-layer canopy of radius r; leaves settle their distances by ticking. */
    static void customTree(int dx, int dz, int h, int r, String log, String leaves) {
        for (int ly = h - 3; ly <= h; ly++) {
            int rr = ly >= h - 1 ? r - 1 : r;
            cmd("fill " + at(dx - rr, ly, dz - rr) + " " + at(dx + rr, ly, dz + rr) + " minecraft:" + leaves + " keep");
        }
        cmd("fill " + at(dx, 0, dz) + " " + at(dx, h - 1, dz) + " minecraft:" + log);
    }

    static boolean destroy(BlockPos pos) {
        return on(() -> {
            destroyedAt = level.getGameTime();
            Fall before = Falls.lastFall;
            boolean ok = player.gameMode.destroyBlock(pos);
            Fall now = Falls.lastFall;
            if (now != null && now != before) {
                cur = now;
                plotFalls.add(now);
            }
            return ok;
        });
    }

    /** Chops and waits two ticks: frame 0 spawned the displays and took the anchor, frame 1 took every other block. */
    static boolean chop(int dx, int dy, int dz) {
        boolean ok = destroy(p(dx, dy, dz));
        tick(2);
        return ok;
    }

    static int displays() {
        return on(() -> {
            int n = 0;
            for (Fall f : plotFalls) n += f.displays();
            return n;
        });
    }

    static int controllers() {
        return on(() -> Falls.active(level));
    }

    static double ctlY() {
        return on(() -> cur == null || Falls.active(level) == 0 ? Double.NaN : cur.pivotY());
    }

    static float ctlYaw() {
        return on(() -> cur == null ? Float.NaN : cur.yaw());
    }

    static Trace trace(Fall f) {
        return TRACES.computeIfAbsent(f, Trace::new);
    }

    /** Waits until the last fall's displays are gone (or n ticks). */
    static Trace finish(int n) {
        Trace t = trace(cur);
        for (int i = 0; i < n && !t.done; i++) tick(1);
        return t;
    }

    static int itemsOf(String id, int r) {
        return itemsNear(p(0, 0, 0), r).getOrDefault(id, 0);
    }

    /** Item entities near pos plus what the player picked up (the real player collects drops 2 blocks away; 1.3.6's fake one never ticked). */
    static Map<String, Integer> itemsNear(BlockPos pos, double r) {
        return on(() -> {
            Map<String, Integer> m = new TreeMap<>();
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(r))) {
                ItemStack s = e.getItem();
                m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && !s.is(net.minecraft.tags.ItemTags.AXES)) m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
            return m;
        });
    }

    static int damage() {
        return on(() -> player.getMainHandItem().getDamageValue());
    }

    static String pitches(Trace tr) {
        StringBuilder b = new StringBuilder();
        for (Frame f : tr.shown()) b.append(Math.round(f.pitch)).append(' ');
        return b.toString().trim();
    }

    static double normYaw(float y) {
        return ((y % 360) + 360) % 360;
    }

    // ---- display geometry (server side, from the entity data) ----

    private static java.lang.reflect.Method xfMethod;

    static Transformation xf(Display d) {
        try {
            if (xfMethod == null) {
                xfMethod = Display.class.getDeclaredMethod("createTransformation", net.minecraft.network.syncher.SynchedEntityData.class);
                xfMethod.setAccessible(true);
            }
            return (Transformation) xfMethod.invoke(null, d.getEntityData());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** World position of a display's block-local point v as the client draws it: entity yaw/pitch, then the transformation. */
    static Vector3d drawn(Display d, Vector3f v) {
        Transformation t = xf(d);
        Vector3f c = new Vector3f(v);
        t.rightRotation().transform(c);
        c.mul(t.scale());
        t.leftRotation().transform(c);
        c.add(t.translation());
        new Quaternionf().rotationYXZ((float) Math.toRadians(-d.getYRot()), (float) Math.toRadians(d.getXRot()), 0f).transform(c);
        return new Vector3d(d.getX() + c.x, d.getY() + c.y, d.getZ() + c.z);
    }

    // ---- groups ----

    static final String[] ALL = {"oak", "swap", "direction", "stump", "landing", "anim", "pose", "robustness"};

    static void run(String groups) {
        cmd("forceload add 0 224 255 351");
        cmd("gamerule max_block_modifications 10000000");
        cmd("gamerule random_tick_speed 0");
        cmd("gamerule block_drops true");
        cmd("gamerule spawn_mobs false");
        cmd("gamerule advance_time false");
        cmd("gamerule advance_weather false");
        cmd("time set 6000");
        tick(80);
        for (String g : ALL) {
            if (!(groups.equals("all") || ("," + groups + ",").contains("," + g + ","))) continue;
            long t0 = System.currentTimeMillis();
            try {
                switch (g) {
                    case "oak" -> oak();
                    case "swap" -> {
                        swap();
                        putBack();
                        hideLeaves();
                    }
                    case "direction" -> direction();
                    case "stump" -> stump();
                    case "landing" -> landing();
                    case "anim" -> animation();
                    case "pose" -> pose();
                    case "robustness" -> robustness();
                    default -> { }
                }
            } catch (Throwable t) {
                io.github.profetgit.vanillatimber.Timber.LOG.error("[parity] group {} threw", g, t);
                SelfTest.record("group " + g + " completed", false, t.toString());
            }
            info("group " + g + " took " + (System.currentTimeMillis() - t0) / 1000 + " s");
        }
    }

    static void oak() {
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        int logs = count(Parity::isLog, 6), leaves = count(Parity::natLeaf, 6), shut = enclosedLeaves(6), hidden = 0;
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        long chopped = destroyedAt;
        Trace tr = trace(cur);
        int d = displays();
        check("oak: world blocks replaced by displays", count(Parity::isLog, 6) == 0 && count(Parity::isLeaf, 6) == 0,
            "logs left " + count(Parity::isLog, 6) + ", leaves left " + count(Parity::isLeaf, 6));
        check("oak: one display per felled block, enclosed leaves included", shut > 0 && d == logs - 1 + leaves - hidden,
            d + " displays for " + (logs - 1) + " logs + " + leaves + " leaves (" + shut + " enclosed)");
        check("oak: no stand-in blocks left behind", blocks("minecraft:structure_void", 8) == 0, blocks("minecraft:structure_void", 8) + " structure voids");
        check("oak: falls away from the player, tipped 33.75 deg to one side", Math.abs(Math.abs(normYaw(ctlYaw()) - 270) - 33.75) < 0.01, "yaw " + ctlYaw());
        while (!tr.done) tick(1);
        info("oak pitch per tick: " + pitches(tr));
        List<Frame> shownFrames = tr.shown();
        List<Double> pitch = new ArrayList<>();
        for (Frame f : shownFrames) pitch.add(f.pitch);
        double min0 = 0, max = -99;
        int maxAt = -1;
        for (int i = 0; i < pitch.size(); i++) {
            if (shownFrames.get(i).t <= 20) min0 = Math.min(min0, pitch.get(i));
            if (pitch.get(i) > max + 0.01) {
                max = pitch.get(i);
                maxAt = i;
            }
        }
        int dips = 0;
        for (int i = maxAt + 1; i < pitch.size() - 1; i++) if (pitch.get(i) < pitch.get(i - 1) && pitch.get(i) <= pitch.get(i + 1)) dips++;
        int rest = 0;
        for (int i = pitch.size() - 1; i > 0 && Math.abs(pitch.get(i) - max) < 0.01; i--) rest++;
        check("oak: leans back first (anticipation, 10 deg plus the top's bend)", min0 < -7 && min0 > -14, "min pitch in first 20 ticks " + min0);
        check("oak: lands flat on open ground", max > 89.9, "impact pitch " + max + " at tick " + maxAt);
        check("oak: two bounces", dips == 2, dips + " dips after impact");
        check("oak: lies still while the trunk pops apart", rest >= 10, rest + " still ticks");
        int gag = tr.end();
        check("oak: whole gag lasts 2.3-3.5 s", gag >= 46 && gag <= 70, gag + " ticks");
        tick(2);
        check("oak: displays and controller gone at the end", displays() == 0 && controllers() == 0, displays() + " displays, " + controllers() + " controllers");
        int dropped = itemsOf("minecraft:oak_log", 24);
        check("oak: every log dropped", dropped == logs, dropped + " of " + logs);
        check("oak: axe used 1 per log", damage() == logs, "damage " + damage() + " for " + logs + " logs");
    }

    /** 1.3.6 {@code #hide_leaves 1}: the mod has no such option yet. */
    static void hideLeaves() {
        missing("hide_leaves 1: no display for leaves shut in on all sides", "no hide-leaves option");
        missing("hide_leaves 1: only displayed leaves are put back", "no hide-leaves option");
        missing("hide_leaves 1: no stand-in blocks left behind", "no hide-leaves option");
    }

    /** The chop tick (frame 0) ends with the displays spawned and the visible blocks still in the world, except the anchor cell. */
    static void putBack() {
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        int logs = count(Parity::isLog, 6), leaves = count(Parity::natLeaf, 6), hidden = 0;
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        List<int[]> samples = new ArrayList<>();
        int[] waited = {0};
        CompletableFuture<Void> done = everyTick(gt -> {
            int n = 0;
            for (Fall f : plotFalls) n += f.displays();
            if (n == 0 && samples.isEmpty()) return ++waited[0] < 400;
            int lg = 0, lf = 0;
            for (BlockPos q : BlockPos.betweenClosed(cx - 6, Y - 1, cz - 6, cx + 6, Y + 40, cz + 6)) {
                BlockState st = level.getBlockState(q);
                if (isLog(st)) lg++;
                if (isLeaf(st)) lf++;
            }
            samples.add(new int[] {n, lg, lf, level.getBlockState(p(0, 1, 0).immutable()).isAir() ? 1 : 0});
            return samples.size() < 2;
        });
        destroy(p(0, 0, 0));
        await(done);
        int[] a = samples.get(0), b = samples.size() > 1 ? samples.get(1) : new int[] {-1, -1, -1};
        check("put-back: the chop tick ends with the displays spawned and the visible blocks still in place, except the displays' anchor cell",
            a[0] > 0 && a[1] == logs - 2 && a[2] == leaves - hidden && a[3] == 1,
            a[0] + " displays, logs " + a[1] + " of " + (logs - 2) + ", leaves " + a[2] + " of " + (leaves - hidden) + ", anchor cell air " + a[3]);
        check("put-back: the next tick removes them, displays stay", b[0] == a[0] && b[1] == 0 && b[2] == 0, b[0] + " displays, logs " + b[1] + ", leaves " + b[2]);
        finish(120);
    }

    /** At frame 0 every display must sit exactly on the block it replaced (entity yaw + left rotation + translation). */
    static void swap() {
        for (float yaw : new float[] {-90f, -67.5f, 45f}) {
            plot();
            feature("fancy_oak", 0, 0);
            tick(40);
            Set<BlockPos> before = on(() -> {
                Set<BlockPos> s = new HashSet<>();
                for (BlockPos q : BlockPos.betweenClosed(cx - 14, Y - 1, cz - 14, cx + 14, Y + 40, cz + 14)) {
                    BlockState st = level.getBlockState(q);
                    if (isLog(st) || natLeaf(st)) s.add(q.immutable());
                }
                return s;
            });
            hold("minecraft:iron_axe");
            stand(-2, 0, yaw);
            destroy(p(0, 0, 0));
            tick(1);
            Fall f = cur;
            int[] res = on(() -> {
                int ok = 0, bad = 0;
                for (Fall.Live l : f.live()) {
                    Vector3d c = drawn(l.display(), new Vector3f(0.5f, 0.5f, 0.5f));
                    BlockPos q = BlockPos.containing(c.x, c.y, c.z);
                    boolean centred = Math.abs(c.x - q.getX() - 0.5) < 0.01 && Math.abs(c.y - q.getY() - 0.5) < 0.01 && Math.abs(c.z - q.getZ() - 0.5) < 0.01;
                    if (centred && before.contains(q)) ok++;
                    else bad++;
                }
                return new int[] {ok, bad};
            });
            check("swap at yaw " + normYaw(ctlYaw()) + ": every display sits exactly on its old block", res[1] == 0 && res[0] > 0, res[0] + " exact, " + res[1] + " off");
            finish(120);
        }
    }

    static void direction() {
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        cmd("fill " + at(3, 0, -4) + " " + at(3, 7, 4) + " minecraft:stone");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        double yaw = normYaw(ctlYaw());
        Trace tr = finish(120);
        double max = Math.max(0, tr.maxPitch());
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
        max = Math.max(0, tr.maxPitch());
        info("boxed tree pitch: " + pitches(tr));
        check("boxed in: tree lands on the wall and bounces there", max > 20 && max < 70, "landed at " + max);
    }

    static void stump() {
        plot();
        customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 2, 0);
        int stumpLogs = on(() -> {
            int n = 0;
            for (int y = 0; y < 2; y++) if (level.getBlockState(new BlockPos(cx, Y + y, cz)).is(BlockTags.LOGS)) n++;
            return n;
        });
        int above = on(() -> {
            int n = 0;
            for (int y = 2; y < 8; y++) if (level.getBlockState(new BlockPos(cx, Y + y, cz)).is(BlockTags.LOGS)) n++;
            return n;
        });
        double pivotY = ctlY();
        check("cut mid-trunk: the stump stays", stumpLogs == 2, stumpLogs + " of 2 stump logs");
        check("cut mid-trunk: the part above the cut falls", above == 0 && displays() > 0, above + " logs left above the cut, " + displays() + " displays");
        tick(6);
        double settled = ctlY();
        check("cut mid-trunk: it hangs a block over the cut, then hinges at the cut", Math.abs(pivotY - (Y + 3)) < 0.01 && Math.abs(settled - (Y + 2)) < 0.01,
            "pivot y " + pivotY + " then " + settled + ", cut at " + (Y + 2));
        Trace tr = finish(120);
        double max = Math.max(0, tr.maxPitch());
        check("cut mid-trunk: the top tips past level to rest on the ground", max > 95 && max < 125, "landed at " + max);
        tick(2);
        hold("minecraft:iron_axe");
        chop(0, 1, 0);
        check("stump afterwards: breaks log by log", blockId(p(0, 0, 0)).equals("minecraft:oak_log") && displays() == 0, blockId(p(0, 0, 0)));
    }

    static void landing() {
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
        Trace tr = finish(120);
        double max = Math.max(0, tr.maxPitch());
        info("cliff pitch: " + pitches(tr));
        check("cliff: tips past level down onto the lower ground instead of hovering", max > 110, "landed at " + max);

        // cut high on a tall trunk: the short top can't reach the ground by tipping, so it slides off and drops
        plot();
        customTree(0, 0, 11, 2, "spruce_log", "spruce_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        cmd("tp " + who() + " " + (cx - 2) + ".5 " + (Y + 5) + " " + cz + ".5 -90 10");
        cmd("fill " + at(-3, 4, -1) + " " + at(-1, 4, 1) + " minecraft:stone");
        chop(0, 6, 0);
        Trace t = trace(cur);
        double y0 = t.copy().getLast().y, yMin = y0, stray = 0;
        for (int i = 0; i < 120 && !t.done; i++) {
            Frame last = t.copy().getLast();
            yMin = Math.min(yMin, last.y);
            stray = Math.max(stray, last.far);
            tick(1);
        }
        for (Frame f : t.copy()) {
            if (f.n >= 1) {
                yMin = Math.min(yMin, f.y);
                stray = Math.max(stray, f.far);
            }
        }
        tick(1);
        int left = displays();
        check("high cut: the top slides off the stump and drops to the ground", y0 - yMin >= 3, "pivot " + y0 + " -> lowest " + yMin);
        check("high cut: the tree's displays ride the drop down with it and are gone when it ends", stray < 1.01 && left == 0 && controllers() == 0,
            "farthest display " + stray + " from its controller, " + left + " displays left, " + controllers() + " controllers");

        // the mod never saves a display (EntityMixin), so an orphan can't exist after a save + reload; other packs' displays save as usual
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        cmd("summon block_display " + at(6, 1, 6) + " {Tags:[\"other.pack\"],block_state:{Name:\"minecraft:oak_log\"}}");
        Fall f = cur;
        int[] saved = on(() -> {
            int ours = 0, ourSaved = 0, others = 0, otherSaved = 0;
            for (Fall.Live l : f.live()) {
                ours++;
                if (l.display().shouldBeSaved()) ourSaved++;
            }
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof Display.BlockDisplay d && d.entityTags().contains("other.pack")) {
                    others++;
                    if (d.shouldBeSaved()) otherSaved++;
                }
            }
            return new int[] {ours, ourSaved, others, otherSaved};
        });
        check("no Timber display survives a save + reload (displays are never saved; other packs' still are)",
            saved[0] > 0 && saved[1] == 0 && saved[2] == 1 && saved[3] == 1,
            saved[0] + " tree displays, " + saved[1] + " would be saved; other pack's " + saved[2] + " display, " + saved[3] + " would be saved");
        finish(160);
        cmd("kill @e[type=block_display,tag=other.pack]");

        // leaves in the fall path do not stop the trunk; stone does
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        cmd("fill " + at(3, 0, -1) + " " + at(6, 4, 1) + " minecraft:oak_leaves[persistent=true]");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        double yaw = normYaw(ctlYaw());
        tr = finish(120);
        max = Math.max(0, tr.maxPitch());
        check("hedge in the way: falls through leaves and lies flat", Math.abs(Math.abs(yaw - 270) - 33.75) < 0.01 && max > 89, "yaw " + yaw + ", landed at " + max);
    }

    /** Mines the bottom `mined` logs by hand, then chops the next log with an axe: the tree drops onto the ground before it leans. */
    static void plungeCase(String name, int mined, boolean big) {
        plot();
        int h = 7 + mined;
        if (big) {
            cmd("fill " + at(-2, h - 3, -2) + " " + at(3, h, 3) + " minecraft:spruce_leaves keep");
            cmd("fill " + at(0, 0, 0) + " " + at(1, h - 1, 1) + " minecraft:spruce_log");
            cmd("fill " + at(0, 0, 0) + " " + at(1, mined - 1, 1) + " minecraft:air");
        } else {
            customTree(0, 0, h, 2, "oak_log", "oak_leaves");
            cmd("fill " + at(0, 0, 0) + " " + at(0, mined - 1, 0) + " minecraft:air");
        }
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        int logs = count(Parity::isLog, 6);
        chop(0, mined, 0);
        Trace t = trace(cur);
        for (int i = 0; i < 40 && !t.done; i++) tick(1);
        double ground = Y + 0.002, low = 1e9, stray = 0, leanedAt = Double.NaN, rose = 0, last = Double.NaN;
        for (Frame f : t.copy()) {
            if (f.n < 1) continue;
            if (f.displays == 0) break;
            low = Math.min(low, f.y);
            if (!Double.isNaN(last)) rose = Math.max(rose, f.y - last);
            last = f.y;
            if (f.n < 14) stray = Math.max(stray, f.far);
            if (Rig.step(f.pitch) != 0 && Double.isNaN(leanedAt)) leanedAt = f.y;
        }
        check("plunge (" + name + "): the controller reaches the ground", Math.abs(low - ground) < 0.01, "lowest controller y " + low + ", ground " + ground);
        check("plunge (" + name + "): the tree is on the ground before it leans", !Double.isNaN(leanedAt) && leanedAt < ground + 0.01, "leaning began at y " + leanedAt);
        check("plunge (" + name + "): the displays ride with the controller and it never rises", stray < 0.001 && rose < 0.001, "farthest display " + stray + ", largest rise " + rose);
        finish(200);
        tick(2);
        int dropped = itemsOf(big ? "minecraft:spruce_log" : "minecraft:oak_log", 24);
        check("plunge (" + name + "): it ends clean and every log drops", displays() == 0 && controllers() == 0 && dropped == logs,
            dropped + " of " + logs + " logs, " + displays() + " displays, " + controllers() + " controllers");
    }

    static void animation() {
        // the tree hangs a block up over the chopped log for a beat, then drops into the gap; the controller rides with it
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        double cy = ctlY();
        List<Fall.Live> ds = on(() -> cur.live());
        long up = on(() -> ds.stream().filter(l -> Math.abs(l.display().getY() - cy) < 0.001).count());
        check("hang: at the chop the controller and the whole tree stand one block up, over the gap", up == ds.size() && up > 0 && Math.abs(cy - (Y + 1.002)) < 0.01,
            up + " of " + ds.size() + " displays on the controller at y " + cy);
        tick(5);
        double cy1 = ctlY();
        long down = on(() -> ds.stream().filter(l -> !l.display().isRemoved() && Math.abs(l.display().getY() - cy1) < 0.001).count());
        long alive = on(() -> ds.stream().filter(l -> !l.display().isRemoved()).count());
        check("hang: then both drop into the gap", down == alive && alive > 0 && Math.abs(cy1 - (Y + 0.002)) < 0.01,
            down + " of " + alive + " displays on the controller, controller y " + cy1);
        finish(160);

        plungeCase("1 log mined", 1, false);
        plungeCase("3 logs mined", 3, false);
        plungeCase("2x2 trunk, 2 layers mined", 2, true);

        // a stump stays under the cut: nothing to fall through, the tree stays on it
        plot();
        customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 1, 0);
        double low = 1e9;
        for (int i = 0; i < 20; i++) {
            double y = ctlY();
            if (!Double.isNaN(y)) low = Math.min(low, y);
            tick(1);
        }
        check("plunge: a cut above a stump doesn't fall through it", Math.abs(low - (Y + 1.002)) < 0.01, "lowest controller y " + low + ", stump top " + (Y + 1.002));
        finish(160);

        // a 2x2 trunk still stands on its other three logs, so it doesn't drop
        plot();
        feature("mega_spruce", 0, 0);
        tick(40);
        hold("minecraft:netherite_axe");
        destroy(on(() -> {
            for (int y = 0; y < 6; y++) {
                BlockPos q = new BlockPos(cx, Y + y, cz);
                if (isLog(level.getBlockState(q))) return q;
            }
            return p(0, 0, 0);
        }));
        tick(1);
        double cy2 = ctlY();
        List<Fall.Live> ds2 = on(() -> cur == null ? new ArrayList<>() : cur.live());
        long raised = on(() -> ds2.stream().filter(l -> Math.abs(l.display().getY() - cy2) > 0.001).count());
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
        double yaw = normYaw(ctlYaw());
        check("tip: tips 33.75 deg to the open side", Math.abs(yaw - 236.25) < 0.01, "yaw " + yaw);
        finish(160);

        // burst, ripple and fairness on one tree
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        tick(40);
        int logs = count(Parity::isLog, 6);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        long chopped = destroyedAt;
        Fall fall = cur;
        Map<String, Integer> want = new TreeMap<>(fall.loot);
        want.merge("minecraft:oak_log", 1, Integer::sum);
        Map<Display.BlockDisplay, Integer> height = new HashMap<>();
        int lfBefore = on(() -> {
            int leaves = 0;
            for (Fall.Live l : fall.live()) {
                if (l.part().leaf()) leaves++;
                else height.put(l.display(), l.part().k());
            }
            return leaves;
        });
        Map<Display.BlockDisplay, Integer> gone = new HashMap<>();
        int[] burst = {-1, 0}, lastLogs = {itemsOf("minecraft:oak_log", 24)};
        TreeSet<Integer> logSteps = new TreeSet<>();
        await(everyTick(gt -> {
            int t = (int) (gt - chopped);
            Trace tr = trace(fall);
            List<Frame> fr = tr.copy();
            Frame lastF = fr.isEmpty() ? null : fr.getLast();
            if (burst[0] < 0 && lastF != null && lastF.leaves == 0 && lastF.displays > 0) {
                burst[0] = t;
                burst[1] = lastF.logs;
            }
            for (var e : height.keySet()) if (!gone.containsKey(e) && e.isRemoved()) gone.put(e, t);
            int n = 0;
            for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, new AABB(p(0, 0, 0)).inflate(24))) {
                if (BuiltInRegistries.ITEM.getKey(it.getItem().getItem()).toString().equals("minecraft:oak_log")) n += it.getItem().getCount();
            }
            if (n > lastLogs[0]) logSteps.add(t);
            lastLogs[0] = n;
            return t < 160 && Falls.active(level) > 0;
        }));
        int burstAt = burst[0], lgAtBurst = burst[1];
        check("burst: at the slam the crown's leaves burst while the trunk still lies there", lfBefore > 0 && burstAt > 20 && lgAtBurst == logs - 1,
            lfBefore + " leaf displays gone at tick " + burstAt + ", " + lgAtBurst + " log displays left");
        boolean ordered = true;
        int first = Integer.MAX_VALUE, last = -1;
        for (var e : height.entrySet()) {
            int g = gone.getOrDefault(e.getKey(), -1);
            first = Math.min(first, g);
            last = Math.max(last, g);
            for (var o : height.entrySet()) if (o.getValue() > e.getValue() && gone.getOrDefault(o.getKey(), -1) < g) ordered = false;
        }
        check("ripple: logs pop one ring at a time from the stump end to the tip", ordered && first > burstAt && last - first >= 8,
            "popped between tick " + first + " and " + last + ", burst at " + burstAt + ", " + new TreeSet<>(height.values()).size() + " rings");
        check("ripple: each ring throws its log as it pops (not all at once)", logSteps.size() >= logs - 2, "log items appeared at ticks " + logSteps);
        tick(3);
        Map<String, Integer> got = itemsNear(p(0, 0, 0), 24);
        check("fair: the ground holds exactly the loot rolled at the chop, plus the chopped log", got.equals(want), "got " + got + ", rolled " + want);

        // blocks changing mid-animation (someone rebuilds the trunk, the landing spot gets walled over) change nothing
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        tick(40);
        logs = count(Parity::isLog, 6);
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
        int rebuilt = count(Parity::isLog, 1);
        check("fair: blocks changed mid-animation don't add or lose drops", dropped == logs && rebuilt == 4,
            dropped + " of " + logs + " logs dropped, " + rebuilt + " rebuilt logs untouched");

        notApplicable("upgrade: a 1.1.0 tree mid-animation drops its loot and ends", "no 1.1.0 trees exist for the mod");
        notApplicable("uninstall mid-fall: nothing left behind, every log still drops", "no uninstall function in a mod");
    }

    static void pose() {
        // mid-fall the entity pitch is a byte step and each transformation carries the rest: together the unbent bottom
        // ring must sit exactly where the true pitch puts it, and the bent rings stay joined
        plot();
        customTree(0, 0, 9, 2, "birch_log", "birch_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        Fall fall = cur;
        double[] res = null;
        for (int t = 0; t < 60 && res == null; t++) {
            tick(1);
            res = on(() -> {
                if (fall.pitch() < 20 || fall.n < 1 || fall.displays() == 0) return null;
                var tl = fall.tl;
                int n = fall.n, kn = 0, kt = 0;
                for (var part : fall.tree.parts()) {
                    kn = Math.min(kn, part.k());
                    kt = Math.max(kt, part.k());
                }
                double pitch = tl.p[n] / 100.0, gy = tl.gy[n] / 1000.0, gx = tl.gx[n] / 1000.0, gz = tl.gz[n] / 1000.0;
                final int kn0 = kn;
                double[] oy = new double[kt - kn + 1], oz = new double[kt - kn + 1], corner = new double[3];
                Rig.origins(pitch, tl.bn[n] / 100.0, gy, fall.tree.spec().h(), kn, kt, oy, oz);
                Vec3 pv = fall.pivot();
                double worst = 0, gap = 0;
                Map<Integer, Fall.Live> logAt = new HashMap<>();
                for (Fall.Live l : fall.live()) {
                    if (l.part().leaf()) continue;
                    int k = l.part().k();
                    logAt.putIfAbsent(k, l);
                    if (k != 0) continue;
                    Rig.corner(l.part().tx() / 1000.0, l.part().wy() / 1000.0, l.part().tz() / 1000.0, k, gx, gy, gz, pitch, oy[k - kn0], oz[k - kn0], corner);
                    Transformation tr = xf(l.display());
                    Vector3f c = new Vector3f(0.5f, 0.5f, 0.5f);
                    tr.rightRotation().transform(c);
                    c.mul((float) gx, (float) gy, (float) gz);
                    new Quaternionf().rotationX((float) Math.toRadians(pitch)).transform(c);
                    c.add((float) corner[0], (float) corner[1], (float) corner[2]);
                    new Quaternionf().rotationYXZ((float) Math.toRadians(-fall.yaw()), 0f, 0f).transform(c);
                    Vector3d want = new Vector3d(pv.x + c.x, pv.y + c.y, pv.z + c.z);
                    worst = Math.max(worst, want.distance(drawn(l.display(), new Vector3f(0.5f, 0.5f, 0.5f))));
                }
                for (int k : logAt.keySet()) {
                    Fall.Live up = logAt.get(k + 1), lo = logAt.get(k);
                    if (up == null || up.part().tx() != lo.part().tx() || up.part().tz() != lo.part().tz()) continue;
                    Vector3d a0 = drawn(lo.display(), new Vector3f(0.5f, 0f, 0.5f)), a1 = drawn(lo.display(), new Vector3f(0.5f, 1f, 0.5f));
                    Vector3d b0 = drawn(up.display(), new Vector3f(0.5f, 0f, 0.5f)), b1 = drawn(up.display(), new Vector3f(0.5f, 1f, 0.5f));
                    Vector3d da = new Vector3d(a1).sub(a0), db = new Vector3d(b1).sub(b0);
                    gap = Math.max(gap, a1.distance(b0) + 10 * da.angle(db));
                }
                Display any = fall.live().getFirst().display();
                return new double[] {pitch, any.getXRot(), worst, gap, tl.bn[n] / 100.0};
            });
        }
        check("pose: mid-fall the trunk sits exactly at its true pitch (byte step + residual)", res != null && res[2] < 0.01 && res[1] % 1.40625 == 0,
            res == null ? "never saw the fall" : "pitch " + res[0] + ", entity " + res[1] + ", worst offset " + res[2]);
        check("pose: the bent trunk's rings stay flush (parallel, sliding a small step)", res != null && res[3] < 0.15,
            res == null ? "never saw the fall" : "bend " + res[4] + " deg, widest gap " + res[3]);
        finish(160);
    }

    static void robustness() {
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
        check("two trees animate at once", controllers() == 2, controllers() + " controllers");
        tick(90);
        check("both finish after a /reload mid-animation", controllers() == 0 && displays() == 0, controllers() + " controllers, " + displays() + " displays");
        check("both trees dropped their logs", itemsOf("minecraft:oak_log", 30) == 6 && itemsOf("minecraft:spruce_log", 30) == 6,
            itemsNear(p(5, 0, 0), 30).toString());
    }
}
