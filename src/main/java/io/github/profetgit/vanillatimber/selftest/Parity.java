package io.github.profetgit.vanillatimber.selftest;

import com.mojang.math.Transformation;
import io.github.profetgit.vanillatimber.core.Rig;
import io.github.profetgit.vanillatimber.server.Chops;
import io.github.profetgit.vanillatimber.server.Config;
import io.github.profetgit.vanillatimber.server.Conflicts;
import io.github.profetgit.vanillatimber.server.Displays;
import io.github.profetgit.vanillatimber.server.Fall;
import io.github.profetgit.vanillatimber.server.Migrate;
import io.github.profetgit.vanillatimber.server.Falls;
import io.github.profetgit.vanillatimber.server.Placed;
import io.github.profetgit.vanillatimber.server.Pending;
import io.github.profetgit.vanillatimber.server.Players;
import io.github.profetgit.vanillatimber.server.Say;
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
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
    static final List<String> CHAT = new ArrayList<>();

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
        if (!claimHook) {
            claimHook = true;
            // a claim mod: denies every break inside the box (PRD 5.7), the chopper's own included
            //? if fabric {
            net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((w, pl, pos, st, be) -> {
                var b = denyBox;
                return b == null || !b.isInside(pos);
            });
            //?}
            //? if neoforge {
            /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.block.BreakBlockEvent e) -> {
                var b = denyBox;
                if (b != null && b.isInside(e.getPos())) e.setCanceled(true);
            });
            *///?}
            //? if forge {
            /*net.minecraftforge.event.level.BlockEvent.BreakEvent.BUS.addListener(e -> {
                var b = denyBox;
                return b != null && b.isInside(e.getPos());
            });
            *///?}
        }
        Say.tap = (p, c) -> {
            synchronized (CHAT) {
                CHAT.add(c.getString());
            }
        };
        io.github.profetgit.vanillatimber.server.Hooks.testTick = sv -> {
            Runnable r = sampler;
            if (r != null) r.run();
        };
        Thread th = new Thread(() -> {
            try {
                run(groups);
            } catch (Throwable t) {
                SelfTest.record("parity harness", false, t.toString());
                io.github.profetgit.vanillatimber.Timber.LOG.error("[parity] harness failed", t);
            } finally {
                Fall.probe = null;
                Say.tap = null;
                on(() -> {
                    Config.reset();
                    return null;
                });
                sampler = null;
                Viewers.test = 0;
                FINISHED.set(true);
            }
        }, "parity");
        th.setDaemon(true);
        th.start();
    }

    // ---- harness ----

    private static boolean claimHook;
    /** Cells the self-test's claim listener denies, or null. */
    static volatile net.minecraft.world.level.levelgen.structure.BoundingBox denyBox;

    /** Logs standing in the column at dx, dz. */
    static int logsAt(int dx, int dz) {
        return on(() -> {
            int n = 0;
            for (int y = 0; y < 30; y++) if (isLog(level.getBlockState(p(dx, y, dz)))) n++;
            return n;
        });
    }

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
            // a class, not a reflective proxy: 1.x Fabric runs with intermediary method names
            CommandSource capture = new CommandSource() {
                @Override
                public void sendSystemMessage(Component c) {
                    out.add(c.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
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
        // the fill below isn't a player break: forget this plot's placed-log keys, or a later tree on the same cells reads as built
        on(() -> {
            for (long k : Placed.all(level)) {
                BlockPos q = BlockPos.of(k);
                if (Math.abs(q.getX() - cx) <= 32 && Math.abs(q.getZ() - cz) <= 32) Placed.remove(level, q);
            }
            return null;
        });
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

    /** Holds a stack with components (damage, enchantments): the {@code item replace} command, as 1.3.6 did. */
    static void holdCmd(String item) {
//? if <1.20.5 {
        /*item = item.replace("[minecraft:damage=245]", "{Damage:245}").replace("[minecraft:damage=249]", "{Damage:249}").replace("[minecraft:unbreakable={}]", "{Unbreakable:1b}")
            .replace("[minecraft:enchantments={\"minecraft:unbreaking\":3}]", "{Enchantments:[{id:\"minecraft:unbreaking\",lvl:3s}]}");*/
//?}
        cmd("item replace entity " + who() + " weapon.mainhand with " + item);
    }

    /** {@code /timber config key value}, as an operator would. */
    static void cfg(String key, int value) {
        List<String> r = cmd("timber config " + key + " " + value);
        if (r.toString().contains("Unknown")) throw new IllegalStateException("timber config " + key + ": " + r);
    }

    /** Timber's chat and action bar lines since the last call (1.3.6 {@code TimberTest.chat()}). */
    static List<String> chat() {
        synchronized (CHAT) {
            List<String> out = new ArrayList<>(CHAT);
            CHAT.clear();
            return out;
        }
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
            cmd("fill " + at(dx - rr, ly, dz - rr) + " " + at(dx + rr, ly, dz + rr) + " " + (leaves.contains(":") ? "" : "minecraft:") + leaves + " keep");
        }
        cmd("fill " + at(dx, 0, dz) + " " + at(dx, h - 1, dz) + " " + (log.contains(":") ? "" : "minecraft:") + log);
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

    /** Chops with the sneak flag set for the break only (the real client's input would reset it between tasks). */
    static boolean chopSneaking(int dx, int dy, int dz) {
        boolean ok = on(() -> {
            player.setShiftKeyDown(true);
            try {
                return destroy(p(dx, dy, dz));
            } finally {
                player.setShiftKeyDown(false);
            }
        });
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
        if (cur == null) {
            tick(2);
            return null;
        }
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

    static double maskScale(Display d) {
        Transformation t = xf(d);
        return t.scale().x();
    }

    static Transformation xf(Display d) {
        return io.github.profetgit.vanillatimber.mixin.DisplayAccess.timber$createTransformation(d.getEntityData());
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

    static final String[] ALL = {"oak", "swap", "gates", "protection", "neighbours", "direction", "stump", "landing", "modes", "durability", "anim", "pose", "robustness", "settings", "trees", "decor", "mangroves", "paleoaks", "giants", "perf_spruce", "fungi", "mushrooms", "custom_type", "modded_fallback", "azaleas", "swamps", "jungles", "m3sheet", "azshapes", "multibreak", "claims", "config_reload", "conflict", "crash", "migrate", "hooks", "replant", "knock", "chops", "layer", "screen", "land", "fall_time", "tools"};

    /** World rules and defaults every group assumes; run again after the world is reopened. */
    static void setup() {
        setup(true);
    }

    static void setup(boolean resetConfig) {
        cmd("forceload add 0 224 255 351");
        cmd("gamerule max_block_modifications 10000000");
        cmd("gamerule random_tick_speed 0");
        cmd("gamerule block_drops true");
        cmd("gamerule spawn_mobs false");
        cmd("gamerule advance_time false");
        cmd("gamerule advance_weather false");
        cmd("time set 6000");
        // -Dmodtest.tickrate=N|sprint (ModTest): server-logic groups at N ticks per second, or as fast as the server can
        for (String c : ModTestHook.commands()) info("  " + c + ": " + cmd(c));
        if (resetConfig) {
            on(() -> {
                Config.reset();
                return null;
            });
        }
        tick(80);
    }

    static void run(String groups) {
        setup();
        for (String g : java.util.stream.Stream.concat(java.util.Arrays.stream(ALL), java.util.stream.Stream.of("giants_xl", "m8sheet", "chopsheet", "layersheet", "widesheet", "ghosttrace", "looks", "looksdense", "looksdensebefore", "looksbefore", "looksprobe", "looksprobebefore", "looksplants", "looksplantsbefore")).toList()) {
            if (!((groups.equals("all") && !g.startsWith("looks") && !g.equals("m8sheet") && !g.equals("chopsheet") && !g.equals("layersheet") && !g.equals("widesheet") && !g.equals("ghosttrace")) || ("," + groups + ",").contains("," + g + ","))) continue;
            long t0 = System.currentTimeMillis();
            SelfTest.group = g;
            try {
                switch (g) {
                    case "oak" -> oak();
                    case "swap" -> {
                        swap();
                        putBack();
                    }
                    case "gates" -> gates();
                    case "protection" -> protection();
                    case "neighbours" -> neighbours();
                    case "modes" -> modes();
                    case "durability" -> durability();
                    case "settings" -> settings();
                    case "direction" -> direction();
                    case "stump" -> stump();
                    case "landing" -> landing();
                    case "anim" -> animation();
                    case "pose" -> pose();
                    case "robustness" -> robustness();
                    case "trees" -> everyTree();
                    case "decor" -> decor();
                    case "mangroves" -> mangroves();
                    case "paleoaks" -> {
                        for (int k = 0; k < 16; k++) everyTreeList(List.of("pale_oak_creaking"));
                    }
                    case "giants" -> {
                        for (int k = 0; k < 4; k++) everyTreeList(List.of("mega_pine", "mega_spruce", "mega_jungle_tree"));
                    }
                    case "perf_spruce" -> perfSpruce();
                    case "fungi" -> fungi();
                    case "mushrooms" -> mushrooms();
                    case "custom_type" -> customType();
                    case "modded_fallback" -> moddedFallback();
                    case "azaleas" -> azaleas();
                    case "swamps" -> leafWalk("swamp_oak", 12);
                    case "jungles" -> leafWalk("jungle_tree", 16);
                    case "m3sheet" -> m3sheet();
                    case "replant" -> replant();
                    case "knock" -> knock();
                    case "chops" -> chops();
                    case "m8sheet" -> m8sheet();
                    case "chopsheet" -> chopsheet();
                    case "layer" -> layer();
                    case "layersheet" -> layersheet();
                    case "widesheet" -> widesheet();
                    case "ghosttrace" -> ghostTrace();
                    case "screen" -> screen();
                    case "land" -> land();
                    case "giants_xl" -> giantsXl();
                    case "fall_time" -> fallTime();
                    case "tools" -> tools();
                    case "looks" -> looks(false);
                    case "looksdense" -> looks(true);
                    case "looksdensebefore" -> {
                        client(mc -> io.github.profetgit.vanillatimber.client.GroundShadow.on = false);
                        looks(true);
                    }
                    case "looksplants" -> {
                        plants = true;
                        looks(false);
                        plants = false;
                    }
                    case "looksplantsbefore" -> {
                        client(mc -> io.github.profetgit.vanillatimber.client.GroundShadow.on = false);
                        plants = true;
                        looks(false);
                        plants = false;
                    }
                    case "looksprobebefore" -> {
                        client(mc -> io.github.profetgit.vanillatimber.client.GroundShadow.on = false);
                        probe = true;
                        looks(false);
                        probe = false;
                    }
                    case "looksprobe" -> {
                        probe = true;
                        looks(false);
                        probe = false;
                    }
                    case "looksbefore" -> {
                        client(mc -> io.github.profetgit.vanillatimber.client.GroundShadow.on = false);
                        looks(false);
                    }
                    case "azshapes" -> azaleaShapes();
                    case "multibreak" -> multibreak();
                    case "claims" -> claims();
                    case "config_reload" -> configReload();
                    case "conflict" -> conflict();
                    case "crash" -> crash();
                    case "migrate" -> migrate();
                    case "hooks" -> hooks();
                    default -> { }
                }
            } catch (Throwable t) {
                io.github.profetgit.vanillatimber.Timber.LOG.error("[parity] group {} threw", g, t);
                SelfTest.record("group " + g + " completed", false, t.toString());
            }
            SelfTest.group = null;
            info("group " + g + " took " + (System.currentTimeMillis() - t0) / 1000 + " s");
        }
    }

    /** False with -Dvanilla_timber.selftest.timing=0: cost and frame-time asserts are skipped (ModTest's matrix runs them once, alone). */
    static boolean timing() {
        return !"0".equals(System.getProperty("vanilla_timber.selftest.timing", "1"));
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

    static void gates() {
        String[][] cases = {{"sneaking", "sneak"}, {"bare hand", "hand"}, {"creative", "creative"}, {"toggle off", "off"}, {"require_axe off + shovel", "shovel"}};
        for (String[] c : cases) {
            try {
                plot();
                customTree(0, 0, 6, 2, "birch_log", "birch_leaves");
                tick(30);
                int logs = count(Parity::isLog, 6);
                hold("minecraft:iron_axe");
                switch (c[1]) {
                    case "hand" -> cmd("item replace entity " + who() + " weapon.mainhand with minecraft:air");
                    case "creative" -> cmd("gamemode creative " + who());
                    case "off" -> on(() -> {
                        Players.setEnabled(server, player.getUUID(), false);
                        return null;
                    });
                    case "shovel" -> {
                        cfg("require_axe", 0);
                        hold("minecraft:iron_shovel");
                    }
                    default -> { }
                }
                if (c[1].equals("sneak")) chopSneaking(0, 0, 0);
                else chop(0, 0, 0);
                int left = count(Parity::isLog, 6);
                if (c[1].equals("shovel")) check("gate: " + c[0] + " -> fells", left == 0 && displays() > 0, "logs left " + left);
                else check("gate: " + c[0] + " -> single log", left == logs - 1 && displays() == 0, "logs " + logs + " -> " + left + ", displays " + displays());
                finish(120);
            } finally {
                on(() -> {
                    Players.setEnabled(server, player.getUUID(), true);
                    return null;
                });
                cfg("require_axe", 1);
            }
        }
        sneakModes();
    }

    /** PRD 5.1 sneak mode 1 (fell only while sneaking) and 2 (always); mode 0 is the default the cases above run with. */
    static void sneakModes() {
        try {
            plot();
            cfg("sneak", 1);
            customTree(0, 0, 6, 2, "birch_log", "birch_leaves");
            tick(30);
            int logs = count(Parity::isLog, 6);
            hold("minecraft:iron_axe");
            chop(0, 0, 0);
            int left = count(Parity::isLog, 6);
            check("gate: sneak mode 1, standing -> single log", left == logs - 1 && displays() == 0, "logs " + logs + " -> " + left + ", displays " + displays());
            plot();
            customTree(0, 0, 6, 2, "birch_log", "birch_leaves");
            tick(30);
            hold("minecraft:iron_axe");
            chopSneaking(0, 0, 0);
            check("gate: sneak mode 1, sneaking -> fells", count(Parity::isLog, 6) == 0 && displays() > 0, "logs left " + count(Parity::isLog, 6));
            finish(120);
            plot();
            cfg("sneak", 2);
            customTree(0, 0, 6, 2, "birch_log", "birch_leaves");
            tick(30);
            hold("minecraft:iron_axe");
            chopSneaking(0, 0, 0);
            check("gate: sneak mode 2, sneaking -> fells", count(Parity::isLog, 6) == 0 && displays() > 0, "logs left " + count(Parity::isLog, 6));
            finish(120);
        } finally {
            cfg("sneak", 0);
        }
    }

    static void modes() {
        try {
            plot();
            customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
            tick(30);
            cfg("drops", 1);
            hold("minecraft:iron_axe");
            stand(-6, 0, -90);
            chop(0, 0, 0);
            finish(120);
            tick(2);
            int atPlayer = itemsNear(p(-6, 0, 0), 1.5).getOrDefault("minecraft:oak_log", 0);
            check("drops=1: logs appear at the player", atPlayer == 5, atPlayer + " logs at the player");
        } finally {
            cfg("drops", 0);
        }
        try {
            plot();
            customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
            tick(30);
            cfg("animation", 0);
            hold("minecraft:iron_axe");
            chop(0, 0, 0);
            tick(1);
            int near = itemsNear(p(0, 0, 0), 3).getOrDefault("minecraft:oak_log", 0);
            check("animation off: instant, drops at the stump", displays() == 0 && count(Parity::isLog, 6) == 0 && near == 6, "displays " + displays() + ", logs dropped nearby " + near);
        } finally {
            cfg("animation", 1);
        }
    }

    static void durability() {
        plot();
        customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
        tick(30);
        holdCmd("minecraft:iron_axe[minecraft:damage=245]");
        chat();
        chop(0, 0, 0);
        List<String> bar = chat();
        check("worn axe: the tree falls and the axe breaks the vanilla way", count(Parity::isLog, 6) == 0 && on(() -> player.getMainHandItem().isEmpty()) && !bar.toString().contains("too worn"),
            count(Parity::isLog, 6) + " logs, hand empty " + on(() -> player.getMainHandItem().isEmpty()) + ", chat " + bar);
        check("worn axe: the break counts in the statistics", on(() -> player.getStats().getValue(net.minecraft.stats.Stats.ITEM_BROKEN.get(net.minecraft.world.item.Items.IRON_AXE))) >= 1, "");
        finish(120);

        plot();
        feature("mega_spruce", 0, 0);
        tick(40);
        int logs = count(Parity::isLog, 10);
        holdCmd("minecraft:diamond_axe[minecraft:enchantments={\"minecraft:unbreaking\":3}]");
        chop(0, 0, 0);
        check("unbreaking III: wear reduced", damage() > 0 && damage() < logs * 0.6, "damage " + damage() + " for " + logs + " logs");
        finish(120);

        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(30);
        holdCmd("minecraft:iron_axe[minecraft:unbreakable={}]");
        chop(0, 0, 0);
        check("unbreakable: no wear", damage() == 0 && count(Parity::isLog, 6) == 0, "damage " + damage());
        finish(120);
        refusals();
    }

    /** PRD 5.10: refusals have an action bar line with feedback on, none with it off; durability 0 pays nothing. */
    static void refusals() {
        try {
            plot();
            customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
            tick(30);
            cfg("durability", 0);
            holdCmd("minecraft:iron_axe[minecraft:damage=245]");
            chat();
            chop(0, 0, 0);
            check("durability 0: a worn axe still fells and pays nothing", count(Parity::isLog, 6) == 0 && damage() == 246, count(Parity::isLog, 6) + " logs, damage " + damage());
            finish(120);
            cfg("durability", 1);
            cfg("max_logs", 16);
            plot();
            customTree(0, 0, 20, 2, "oak_log", "oak_leaves");
            tick(30);
            hold("minecraft:iron_axe");
            chat();
            chop(0, 0, 0);
            List<String> big = chat();
            check("too big: not felled, line shown", count(Parity::isLog, 6) == 19 && big.toString().contains("too big"), count(Parity::isLog, 6) + " logs, chat " + big);
            cfg("feedback", 0);
            plot();
            customTree(0, 0, 20, 2, "oak_log", "oak_leaves");
            tick(30);
            hold("minecraft:iron_axe");
            chat();
            chop(0, 0, 0);
            List<String> quiet = chat();
            check("feedback 0: a refusal shows no line", count(Parity::isLog, 6) == 19 && quiet.isEmpty(), quiet.toString());
        } finally {
            cfg("max_logs", Config.Key.MAX_LOGS.def);
            cfg("durability", 1);
            cfg("feedback", 1);
        }
    }

    static void settings() {
        chat();
        List<String> menu = cmd("execute as " + who() + " run timber menu");
        List<String> shown = chat();
        for (String m : shown) info("menu| " + m);
        check("settings menu renders", shown.size() == 17 && menu.stream().noneMatch(m -> m.contains("rror") || m.contains("<--")), shown.size() + " lines " + menu);
        cmd("timber config max_logs 99999");
        check("max_logs clamped", Config.get(Config.Key.MAX_LOGS) == 4096 && cmd("timber config max_logs").toString().contains("4096"), "");
        cfg("max_logs", Config.Key.MAX_LOGS.def);
        cmd("execute as " + who() + " run timber menu radius 20");
        cmd("reload");
        tick(5);
        check("config survives /reload", Config.get(Config.Key.RADIUS) == 20, "radius " + Config.get(Config.Key.RADIUS));
        cmd("timber reload");
        check("config survives /timber reload (the commented file reads back)", Config.get(Config.Key.RADIUS) == 20, "radius " + Config.get(Config.Key.RADIUS));
        cfg("radius", 12);
        cmd("execute as " + who() + " run trigger timber");
        tick(2);
        List<String> t = chat();
        boolean off = !on(() -> Players.enabled(server, player.getUUID()));
        check("/trigger timber toggles off with a message", t.toString().contains("disabled") && off, t.toString());
        cmd("execute as " + who() + " run trigger timber");
        tick(2);
        List<String> t2 = chat();
        check("/trigger timber toggles back on", t2.toString().contains("enabled") && on(() -> Players.enabled(server, player.getUUID())), t2.toString());
        notApplicable("uninstall removes all objectives", "the mod has no uninstall function; its one trigger objective is removed at server stop");
    }

    /** A real placement by the player against {@code against}'s {@code face} (1.3.6 {@code TimberTest.place}). */
    static String place(BlockPos against, Direction face) {
        return on(() -> {
            Vec3 hit = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            BlockHitResult r = new BlockHitResult(hit, face, against, false);
            return player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, r).toString();
        });
    }

    /** Placed-log keys inside this plot. */
    static Set<BlockPos> keys() {
        return on(() -> {
            Set<BlockPos> out = new TreeSet<>();
            for (long k : Placed.all(level)) {
                BlockPos q = BlockPos.of(k);
                if (Math.abs(q.getX() - cx) <= 32 && Math.abs(q.getZ() - cz) <= 32) out.add(q);
            }
            return out;
        });
    }

    static int logsIn(int x0, int y0, int z0, int x1, int y1, int z1) {
        return on(() -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(cx + x0, Y + y0, cz + z0, cx + x1, Y + y1, cz + z1)) if (isLog(level.getBlockState(q))) n++;
            return n;
        });
    }

    static int natLeavesIn(int x0, int y0, int z0, int x1, int y1, int z1) {
        return on(() -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(cx + x0, Y + y0, cz + z0, cx + x1, Y + y1, cz + z1)) if (natLeaf(level.getBlockState(q))) n++;
            return n;
        });
    }

    /** Leaves in the box whose distance disagrees with vanilla's rule, and the distance histogram (1.3.6 {@code leafCheck}). */
    static String leafCheck(int x0, int y0, int z0, int x1, int y1, int z1) {
        return on(() -> {
            int leaves = 0, bad = 0;
            Map<String, Integer> hist = new TreeMap<>();
            StringBuilder ex = new StringBuilder();
            for (BlockPos q : BlockPos.betweenClosed(cx + x0, Y + y0, cz + z0, cx + x1, Y + y1, cz + z1)) {
                BlockState s = level.getBlockState(q);
                if (!s.is(BlockTags.LEAVES)) continue;
                leaves++;
                int d = s.getValue(LeavesBlock.DISTANCE);
                hist.merge((s.getValue(LeavesBlock.PERSISTENT) ? "P" : "") + d, 1, Integer::sum);
                int best = 7;
                for (Direction dir : Direction.values()) {
                    BlockState o = level.getBlockState(q.relative(dir));
                    if (o.is(BlockTags.LOGS)) best = 1;
                    else if (o.is(BlockTags.LEAVES)) best = Math.min(best, o.getValue(LeavesBlock.DISTANCE) + 1);
                }
                if (best != d) {
                    bad++;
                    if (ex.length() < 300) ex.append(q.toShortString()).append(" d=").append(d).append(" want ").append(best).append("; ");
                }
            }
            return leaves + " leaves, " + bad + " inconsistent, hist " + hist + (bad > 0 ? " e.g. " + ex : "");
        });
    }

    static void protection() {
        // player-placed pillar with player-placed leaves
        plot();
        holdCmd("minecraft:oak_log 64");
        cmd("fill " + at(-4, 0, -1) + " " + at(-3, 2, 1) + " minecraft:stone");
        cmd("tp " + who() + " " + (cx - 3) + ".5 " + (Y + 3) + " " + cz + ".5 -90 60");
        String r = place(p(0, -1, 0), Direction.UP);
        for (int y = 0; y < 4; y++) place(p(0, y, 0), Direction.UP);
        holdCmd("minecraft:oak_leaves 64");
        for (int y = 2; y < 5; y++) {
            place(p(0, y, 0), Direction.EAST);
            place(p(0, y, 0), Direction.NORTH);
            place(p(0, y, 0), Direction.SOUTH);
        }
        Set<BlockPos> keys = keys();
        info("placed keys: " + keys + " place result " + r);
        boolean keysMatch = true;
        for (int y = 0; y < 5; y++) keysMatch &= keys.contains(p(0, y, 0));
        int logs = count(Parity::isLog, 6);
        check("placed: exactly the 5 placed logs tracked", logs == 5 && keysMatch && keys.size() == 5, logs + " logs, " + keys);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        check("placed: player pillar -> only the chopped log", count(Parity::isLog, 6) == 4 && displays() == 0, count(Parity::isLog, 6) + " logs left");
        Set<BlockPos> keys2 = keys();
        check("placed: key removed when the log is mined", keys2.size() == 4 && !keys2.contains(p(0, 0, 0)), keys2.toString());

        // log cabin built before install (no keys, no natural leaves)
        plot();
        for (int dx : new int[] {0, 4}) for (int dz : new int[] {0, 4}) cmd("fill " + at(dx, 0, dz) + " " + at(dx, 3, dz) + " minecraft:spruce_log");
        cmd("fill " + at(0, 4, 0) + " " + at(4, 4, 0) + " minecraft:spruce_log[axis=x]");
        cmd("fill " + at(0, 4, 4) + " " + at(4, 4, 4) + " minecraft:spruce_log[axis=x]");
        int cabin = count(Parity::isLog, 8);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        check("cabin (no leaves): only the chopped log", count(Parity::isLog, 8) == cabin - 1 && displays() == 0, cabin + " -> " + count(Parity::isLog, 8));

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
        int wallLeft = logsIn(3, 0, -3, 3, 4, 3);
        check("wall touching a canopy: felling the tree leaves the wall", wallLeft == wall - 1 && count(Parity::isLog, 2) == 0,
            "wall logs " + wallLeft + ", trunk logs " + logsIn(0, 0, 0, 0, 40, 0));
        finish(120);

        // player-built log platform attached to a natural trunk
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        tick(40);
        holdCmd("minecraft:oak_log 64");
        stand(4, -3, 0);
        place(p(0, 1, 0), Direction.EAST);
        place(p(1, 1, 0), Direction.EAST);
        place(p(2, 1, 0), Direction.EAST);
        stand(-2, 0, -90);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        int platform = logsIn(1, 1, 0, 3, 1, 0);
        check("player logs attached to a trunk survive the felling", platform == 3 && logsIn(0, -1, 0, 0, 40, 0) == 0,
            platform + " of 3 placed logs, trunk logs left " + logsIn(0, -1, 0, 0, 40, 0));
        finish(120);
    }

    static void neighbours() {
        // two trees whose canopies overlap
        plot();
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        customTree(4, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        int bLeaves = natLeavesIn(3, 0, -2, 6, 8, 2);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        int bLogs = logsIn(4, 0, 0, 4, 5, 0);
        int bLeaves2 = natLeavesIn(3, 0, -2, 6, 8, 2);
        check("overlapping canopies: neighbour's trunk untouched", bLogs == 6, bLogs + " of 6");
        check("overlapping canopies: neighbour keeps its own leaves", bLeaves2 == bLeaves, bLeaves + " -> " + bLeaves2);
        int gone = natLeavesIn(-2, 0, -2, 1, 8, 2);
        check("overlapping canopies: felled tree's leaves gone", gone == 0, gone + " left");
        finish(120);
        tick(20);
        String lc = leafCheck(1, 0, -3, 7, 8, 3);
        check("overlapping canopies: neighbour's leaves stay attached (no distance 7)", !lc.matches(".*[{ ,]7=.*"), lc);

        // a branch of logs joins two trees
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        customTree(6, 0, 7, 2, "oak_log", "oak_leaves");
        cmd("fill " + at(1, 3, 0) + " " + at(5, 3, 0) + " minecraft:oak_log[axis=x]");
        tick(40);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        int bTrunk = logsIn(6, 0, 0, 6, 6, 0), aTrunk = logsIn(0, 0, 0, 0, 6, 0), bridge = logsIn(1, 3, 0, 5, 3, 0);
        check("log bridge: felled tree's trunk gone", aTrunk == 0, aTrunk + " left");
        check("log bridge: neighbour's trunk untouched", bTrunk == 7, bTrunk + " of 7");
        check("log bridge: bridge split between the trees", bridge >= 2 && bridge <= 3, bridge + " of 5 bridge logs stay with the neighbour");
        finish(120);

        // real dark oaks growing into each other
        plot();
        feature("dark_oak", 0, 0);
        feature("dark_oak", 5, 2);
        tick(40);
        int before = logsIn(5, 0, 2, 6, 0, 3);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        int after = logsIn(5, 0, 2, 6, 20, 3), mine = logsIn(0, 0, 0, 1, 20, 1);
        check("dark oak pair: chopped trunk fully felled", mine == 0, mine + " logs left in its 2x2 trunk");
        check("dark oak pair: neighbour's trunk still standing", before == 4 && after >= 8, "base " + before + ", trunk logs " + after);
        finish(120);
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

    // ---- wave 4: every tree, decor, mangroves, giants, perf ----

    static BlockState shownState(Fall.Live l) {
        return ((io.github.profetgit.vanillatimber.mixin.BlockDisplayAccess) l.display()).timber$getBlockState();
    }

    static BlockPos trunkAt(int up) {
        return on(() -> {
            for (int y = 0; y < up; y++) {
                BlockPos q = p(0, y, 0);
                if (level.getBlockState(q).is(BlockTags.LOGS)) return q;
            }
            return null;
        });
    }

    static void everyTree() {
        everyTreeList(List.of("oak", "fancy_oak", "birch", "super_birch_bees", "spruce", "pine", "mega_spruce", "mega_pine", "jungle_tree",
            "mega_jungle_tree", "acacia", "dark_oak", "cherry", "pale_oak", "pale_oak_creaking", "azalea_tree", "mangrove", "tall_mangrove", "swamp_oak",
            "red_poplar", "orange_poplar", "yellow_poplar"));
    }

    static void everyTreeList(List<String> trees) {
        List<String> times = new ArrayList<>();
        for (String f : trees) {
            BlockPos base = null;
            // features are random: a mangrove on roots sometimes grows no log in its origin column, so re-roll it
            for (int roll = 0; roll < 4 && base == null; roll++) {
                plot();
                feature(f, 0, 0);
                tick(40);
                base = trunkAt(8);
            }
            if (base == null) {
                check("tree " + f + ": has a trunk at the feature origin", false, "no log in the column");
                continue;
            }
            int logs = count(Parity::isLog, 14), leaves = count(Parity::natLeaf, 14);
            int orphans = count(q -> natLeaf(q) && q.getValue(LeavesBlock.DISTANCE) == 7, 14);
            hold("minecraft:netherite_axe");
            destroy(base);
            tick(2);
            int d = displays();
            long air = on(() -> cur == null ? 0L : cur.live().stream().filter(l -> shownState(l).isAir()).count());
            int logsLeft = count(Parity::isLog, 14), leavesLeft = count(Parity::natLeaf, 14);
            Fall fall = cur;
            Trace tr = finish(140);
            double max = tr == null ? 0 : tr.maxPitch();
            int ticks = tr == null ? 0 : tr.end();
            String cost = fall == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f+%.1fms", fall.chopMs(), fall.frameMs(0));
            times.add(f + " " + d + "d " + cost);
            if (logsLeft > 0) {
                StringBuilder where = new StringBuilder();
                on(() -> {
                    for (BlockPos q : BlockPos.betweenClosed(cx - 14, Y - 1, cz - 14, cx + 14, Y + 40, cz + 14))
                        if (isLog(level.getBlockState(q))) where.append(q.getX() - cx).append(',').append(q.getY() - Y).append(',').append(q.getZ() - cz).append(' ');
                    return null;
                });
                info("  " + f + " left logs at " + where + "| base y " + (base.getY() - Y));
            }
            check("tree " + f + ": felled and animated", logsLeft == 0 && leavesLeft <= orphans + Math.max(2, leaves * 3 / 100) && d > 0 && air == 0 && ticks > 30 && max >= 85,
                logs + " logs/" + leaves + " leaves (" + orphans + " detached), " + air + " invisible displays -> left " + logsLeft + "/" + leavesLeft + ", " + d
                    + " displays, landed " + Math.round(max * 10) / 10.0 + ", " + ticks + " ticks, chop " + cost);
        }
        info("felling tick cost (chop + frame 0): " + times);
    }

    static void mangroves() {
        int ok = 0, total = 0;
        StringBuilder bad = new StringBuilder();
        for (int k = 0; k < 12; k++) {
            plot();
            feature(k % 2 == 0 ? "mangrove" : "tall_mangrove", 0, 0);
            tick(40);
            BlockPos base = trunkAt(12);
            if (base == null) continue;
            total++;
            int logs = count(Parity::isLog, 14);
            hold("minecraft:netherite_axe");
            destroy(base);
            tick(2);
            int left = count(Parity::isLog, 14);
            if (left == 0) ok++;
            else {
                bad.append(k).append(": ").append(logs).append(" logs -> ").append(left).append(" left; ");
                StringBuilder col = new StringBuilder();
                for (int y = 0; y < 14; y++) col.append(y).append('=').append(blockId(p(0, y, 0)).replace("minecraft:", "")).append(' ');
                info("mangrove " + k + " base y=" + (base.getY() - Y) + " column " + col);
            }
            finish(140);
        }
        check("mangroves: every random mangrove fully felled", ok == total && total >= 8, ok + "/" + total + " " + bad);
    }

    static void decor() {
        plot();
        customTree(0, 0, 7, 2, "jungle_log", "jungle_leaves");
        cmd("setblock " + at(1, 1, 0) + " minecraft:cocoa[facing=west,age=2]");
        cmd("fill " + at(-1, 1, 0) + " " + at(-1, 3, 0) + " minecraft:vine[east=true]");
        cmd("setblock " + at(-2, 3, 0) + " minecraft:vine[up=true]");
        cmd("fill " + at(1, 2, 0) + " " + at(1, 4, 0) + " vanilla_timber:test_vine[west=true]");
        cmd("fill " + at(2, 2, 2) + " " + at(2, 3, 2) + " vanilla_timber:test_hanging");
        cmd("setblock " + at(0, 8, 0) + " minecraft:snow[layers=2]");
        cmd("setblock " + at(-2, 3, 2) + " vanilla_timber:test_fruit");
        cmd("setblock " + at(-2, 3, -2) + " vanilla_timber:test_cast");
        cmd("setblock " + at(3, 4, 0) + " minecraft:stone");
        tick(30);
        hold("minecraft:iron_axe");
        stand(0, -3, 0);
        chop(0, 0, 0);
        int vines = blocks("minecraft:vine", 6), cocoa = blocks("minecraft:cocoa", 6), snow = blocks("minecraft:snow", 6);
        check("decor: vines, cocoa and snow go with the tree", vines == 0 && cocoa == 0 && snow == 0, "vines " + vines + ", cocoa " + cocoa + ", snow " + snow);
        int modded = blocks("vanilla_timber:test_vine", 6);
        check("decor: a mod's untagged vine goes with the tree", modded == 0, modded + " test_vine blocks left");
        int hung = blocks("vanilla_timber:test_hanging", 6);
        check("decor: a mod's plain hanging block under the leaves goes with the tree", hung == 0, hung + " test_hanging blocks left");
        long vineDisplays = on(() -> cur == null ? 0L : cur.live().stream().filter(l -> shownState(l).is(net.minecraft.world.level.block.Blocks.VINE)).count());
        check("decor: vines ride along as displays with their faces", vineDisplays == 4, vineDisplays + " vine displays");
        int fruit = blocks("vanilla_timber:test_fruit", 6);
        check("decor: a block that needs its leaves above (fruit, collision box, untagged) goes with the tree", fruit == 0, fruit + " test_fruit blocks left");
        long fruitDisplays = on(() -> cur == null ? 0L : cur.live().stream().filter(l -> BuiltInRegistries.BLOCK.getKey(shownState(l).getBlock()).toString().equals("vanilla_timber:test_fruit")).count());
        check("decor: the fruit rides along as a display", fruitDisplays == 1, fruitDisplays + " fruit displays");
        int cast = blocks("vanilla_timber:test_cast", 6);
        check("decor: a block whose support check cannot run on the tree-less view pops instead of floating", cast == 0, cast + " test_cast blocks left");
        int stone = blocks("minecraft:stone", 6);
        check("decor: a full block beside the leaves stays", stone == 1, stone + " stone blocks");
        finish(120);
        tick(2);
        check("decor: ripe cocoa drops beans", itemsOf("minecraft:cocoa_beans", 20) >= 2, itemsNear(p(0, 0, 0), 20).toString());
    }

    /** Plants a 1.3.6 sampler fixture ({@code selftest/trees/<file>}) with its base log at p(0, 0, 0). */
    static void fixture(String file) {
        String text;
        try (var in = Parity.class.getResourceAsStream("/selftest/trees/" + file + ".mcfunction")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException | NullPointerException e) {
            throw new IllegalStateException("fixture " + file, e);
        }
        on(() -> {
            var src = server.createCommandSourceStack().withSuppressedOutput().withPosition(Vec3.atLowerCornerOf(p(0, 0, 0)));
            for (String line : text.split("\n")) if (!line.isBlank()) server.getCommands().performPrefixedCommand(src, Scenarios.fixtureLine(line));
            return null;
        });
    }

    /** PRD 10: chop tick (scan + loot + spawn) of the 427-block giant spruce, warm, median of 5 <= 10 ms. */
    static void perfSpruce() {
        List<Double> chop = new ArrayList<>(), tickMs = new ArrayList<>(), moving = new ArrayList<>();
        String size = "";
        for (int k = 0; k < 6; k++) {
            plot();
            fixture("spruce_b");
            tick(5);
            hold("minecraft:netherite_axe");
            stand(-2, 0, -90);
            destroy(p(0, 0, 0));
            tick(2);
            Fall fall = cur;
            finish(200);
            if (fall == null) {
                check("perf_spruce: the giant spruce fell", false, "no fall on chop " + k);
                return;
            }
            double c = fall.chopMs(), f0 = fall.frameMs(0);
            double sum = 0;
            int n = 0;
            for (int i = 2; i < fall.tl.end; i++) {
                double m = fall.frameMs(i);
                if (!Double.isNaN(m)) {
                    sum += m;
                    n++;
                }
            }
            size = fall.tree.logs() + " logs + " + fall.tree.leaves() + " leaves";
            info(String.format(java.util.Locale.ROOT, "  perf_spruce chop %d%s: chop %.3f ms, frame 0 %.3f ms, frame 1 %.3f ms, moving mean %.4f ms", k, k == 0 ? " (warm-up)" : "",
                c, f0, fall.frameMs(1), n == 0 ? 0 : sum / n));
            if (k == 0) continue;
            chop.add(c);
            tickMs.add(c + f0);
            moving.add(n == 0 ? 0 : sum / n);
        }
        double med = median(tickMs);
        check("perf_spruce: warm chop tick (scan + loot + spawn) median of 5 <= 10 ms", med <= 10,
            String.format(java.util.Locale.ROOT, "%s, chop + frame 0 median %.3f ms (chop alone %.3f), all %s; moving mean median %.4f ms with a vanilla viewer",
                size, med, median(chop), tickMs, median(moving)));
    }

    /**
     * Worldgen giants (Arboria's 80+ block sequoias): 3x3 and 4x4 spruce trunks 90 tall (`dev/fixtures/gen_sequoia.py`)
     * with max_logs 4096 and max_blocks 8000, each with a vanilla viewer (displays) and a modded one (mesh): the chop tick
     * (scan + loot + spawn, + frame 0), the moving per-frame mean, the TreeFall payload size. Sets the max_logs default.
     */
    static void giantsXl() {
        int logs0 = Config.get(Config.Key.MAX_LOGS), blocks0 = Config.get(Config.Key.MAX_BLOCKS);
        try {
            cfg("max_logs", 4096);
            cfg("max_blocks", 8000);
            for (String file : List.of("sequoia_3x3", "sequoia_4x4")) {
                for (int mode : new int[] {Viewers.VANILLA, 0}) {
                    String tag = file + (mode == 0 ? " mesh" : " displays");
                    do plot(); while (cx != 224);
                    for (int y0 : new int[] {-10, 25}) cmd("fill " + (cx - 14) + " " + y0 + " " + (cz - 14) + " " + (cx + 14) + " " + (y0 + 35) + " " + (cz + 14) + " minecraft:air");
                    on(() -> {
                        Viewers.test = mode;
                        return null;
                    });
                    fixture(file);
                    tick(10);
                    hold("minecraft:netherite_axe");
                    stand(-3, 0, -90);
                    Fall before = cur;
                    destroy(p(0, 0, 0));
                    tick(2);
                    Fall fall = cur;
                    if (fall == null || fall == before) {
                        check("giants_xl: " + tag + " falls", false, "no fall: " + io.github.profetgit.vanillatimber.server.TreeScan.why);
                        continue;
                    }
//? if >=1.20.5 {
                    int bytes = on(() -> {
                        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
                        io.github.profetgit.vanillatimber.net.TreeFall.CODEC.encode(buf, io.github.profetgit.vanillatimber.net.TreeFall.of(fall.id, fall.tree, 0, fall.landed(), fall.landRing(), fall.poof));
                        return buf.readableBytes();
                    });
//?} else {
/*                    int bytes = -1;
*///?}
                    finish(600);
                    waitFalls();
                    double c = fall.chopMs(), f0 = fall.frameMs(0), sum = 0, worst = 0;
                    int n = 0;
                    for (int i = 2; i < fall.tl.end; i++) {
                        double m = fall.frameMs(i);
                        if (Double.isNaN(m)) continue;
                        sum += m;
                        worst = Math.max(worst, m);
                        n++;
                    }
                    int left = on(() -> {
                        int k = 0;
                        for (BlockPos q : BlockPos.betweenClosed(cx - 4, Y, cz - 4, cx + 7, Y + 95, cz + 7)) if (isLog(level.getBlockState(q))) k++;
                        return k;
                    });
                    String nums = String.format(java.util.Locale.ROOT, "%d logs + %d leaves, h %d, dur %d; chop %.2f ms, frame 0 %.2f ms, frame 1 %.2f ms, moving mean %.3f ms, worst %.2f ms; TreeFall %d bytes",
                        fall.tree.logs(), fall.tree.leaves(), fall.tree.spec().h(), fall.tree.spec().dur(), c, f0, fall.frameMs(1), n == 0 ? 0 : sum / n, worst, bytes);
                    info("  giants_xl " + tag + ": " + nums);
                    check("giants_xl: " + tag + " falls whole", left == 0 && fall.tree.logs() >= 800, nums + ", logs left " + left);
                    check("giants_xl: " + tag + " TreeFall fits a custom payload (1 MiB)", bytes < 1048576, bytes + " bytes");
                }
            }
        } finally {
            cfg("max_logs", logs0);
            cfg("max_blocks", blocks0);
            on(() -> {
                Viewers.test = Viewers.VANILLA;
                return null;
            });
        }
    }

    static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        java.util.Collections.sort(s);
        return s.isEmpty() ? Double.NaN : s.get(s.size() / 2);
    }

    // ---- M3: any tree ----

    static boolean is(BlockState s, String id) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().equals(id);
    }

    /** Fells the tree with its base log at {@code base} and checks the whole thing went: trunk, crown and decor. */
    static void fellAll(String name, BlockPos base, Predicate<BlockState> trunk, Predicate<BlockState> crown, Predicate<BlockState> decor) {
        int t = count(trunk, 14), c = count(crown, 14), dc = count(decor, 14);
        hold("minecraft:netherite_axe");
        destroy(base);
        tick(2);
        int d = displays();
        if (controllers() == 0) info("  " + name + ": no fall, scan says " + io.github.profetgit.vanillatimber.server.TreeScan.why + ", chat " + chat());
        int tl = count(trunk, 14), cl = count(crown, 14), dl = count(decor, 14);
        Trace tr = finish(160);
        double max = tr == null ? 0 : tr.maxPitch();
        int ticks = tr == null ? 0 : tr.end();
        check(name + ": felled whole (trunk, crown, decor) and animated", t > 1 && c > 0 && tl == 0 && cl <= Math.max(1, c * 3 / 100) && dl == 0 && d > 0 && ticks > 20 && max >= 60,
            t + " trunk/" + c + " crown/" + dc + " decor -> left " + tl + "/" + cl + "/" + dl + ", " + d + " displays, landed " + Math.round(max) + ", " + ticks + " ticks");
    }

    static void fungi() {
        for (String kind : List.of("crimson", "warped")) {
            BlockPos base = null;
            for (int roll = 0; roll < 4 && base == null; roll++) {
                plot();
                cmd("fill " + at(-6, -1, -6) + " " + at(6, -1, 6) + " minecraft:" + kind + "_nylium");
                feature(kind + "_fungus", 0, 0);
                tick(20);
                base = on(() -> level.getBlockState(p(0, 0, 0)).is(BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + kind + "_stem"))) ? p(0, 0, 0) : null);
            }
            if (base == null) {
                check("fungi: " + kind + " fungus grew", false, "no stem at the origin");
                continue;
            }
            String stem = "minecraft:" + kind + "_stem", wart = kind.equals("crimson") ? "minecraft:nether_wart_block" : "minecraft:warped_wart_block";
            int stems = blocks(stem, 14);
            // vanilla grows no vines on a warped fungus: twisting vines on top of the hat, as a player's bone meal leaves them
            if (kind.equals("warped")) on(() -> {
                BlockPos q = p(0, 0, 0);
                while (!level.getBlockState(q.above()).isAir()) q = q.above();
                level.setBlock(q.above(), net.minecraft.world.level.block.Blocks.TWISTING_VINES_PLANT.defaultBlockState(), 3);
                level.setBlock(q.above(2), net.minecraft.world.level.block.Blocks.TWISTING_VINES.defaultBlockState(), 3);
                return null;
            });
            fellAll("fungi: " + kind + " fungus", base, s -> is(s, stem), s -> is(s, wart) || is(s, "minecraft:shroomlight"),
                s -> is(s, "minecraft:weeping_vines") || is(s, "minecraft:weeping_vines_plant") || is(s, "minecraft:twisting_vines")
                    || is(s, "minecraft:twisting_vines_plant"));
            tick(2);
            check("fungi: " + kind + " stems dropped", itemsOf(stem, 30) >= stems, stems + " stems, items " + itemsNear(p(0, 0, 0), 30));
        }
    }

    static void mushrooms() {
        for (String kind : List.of("red", "brown")) {
            BlockPos base = null;
            for (int roll = 0; roll < 4 && base == null; roll++) {
                plot();
                feature("huge_" + kind + "_mushroom", 0, 0);
                tick(20);
                base = on(() -> level.getBlockState(p(0, 0, 0)).is(net.minecraft.world.level.block.Blocks.MUSHROOM_STEM) ? p(0, 0, 0) : null);
            }
            if (base == null) {
                check("mushrooms: huge " + kind + " mushroom grew", false, "no stem at the origin");
                continue;
            }
            String cap = "minecraft:" + kind + "_mushroom_block";
            fellAll("mushrooms: huge " + kind + " mushroom", base, s -> is(s, "minecraft:mushroom_stem"), s -> is(s, cap), s -> false);
            tick(2);
            check("mushrooms: the " + kind + " cap drops mushrooms", itemsOf("minecraft:" + kind + "_mushroom", 30) >= 1, itemsNear(p(0, 0, 0), 30).toString());
        }
        // a mushroom house: caps on a pillar of mushroom blocks without a stem stay
        plot();
        cmd("fill " + at(0, 0, 0) + " " + at(0, 3, 0) + " minecraft:brown_mushroom_block");
        cmd("fill " + at(-2, 4, -2) + " " + at(2, 4, 2) + " minecraft:red_mushroom_block");
        tick(5);
        hold("minecraft:netherite_axe");
        chop(0, 0, 0);
        check("mushrooms: a cap on a pillar of cap blocks is not a tree", controllers() == 0 && blocks("minecraft:red_mushroom_block", 6) == 25, controllers() + " falls, " + blocks("minecraft:red_mushroom_block", 6) + " caps");
    }

    /** A data pack in the world folder: a tree type ({@code timber_test:bone_tree}) and the test log in #minecraft:logs. */
    static void testPack() {
        java.nio.file.Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("timber_test");
        try {
            write(dir.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Timber test types\",\"min_format\":[107,1],\"max_format\":121}}");
            write(dir.resolve("data/timber_test/timber/tree_type/bone_tree.json"),
                "{\"trunk\":\"#timber_test:bone_trunks\",\"crown\":\"#timber_test:bone_crowns\",\"rule\":\"connected\",\"max_crown_gap\":1}");
            write(dir.resolve("data/timber_test/tags/block/bone_trunks.json"), "{\"values\":[\"minecraft:bone_block\"]}");
            write(dir.resolve("data/timber_test/tags/block/bone_crowns.json"), "{\"values\":[\"minecraft:white_wool\"]}");
            write(dir.resolve("data/minecraft/tags/block/logs.json"), "{\"values\":[{\"id\":\"vanilla_timber:test_log\",\"required\":false}]}");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        info("  " + cmd("reload"));
        tick(10);
        info("  " + cmd("datapack enable \"file/timber_test\""));
        tick(10);
    }

    static void write(java.nio.file.Path f, String text) throws java.io.IOException {
        java.nio.file.Files.createDirectories(f.getParent());
        java.nio.file.Files.writeString(f, text);
    }

    static void customType() {
        testPack();
        plot();
        cmd("fill " + at(0, 0, 0) + " " + at(0, 5, 0) + " minecraft:bone_block");
        cmd("fill " + at(-2, 6, -2) + " " + at(2, 7, 2) + " minecraft:white_wool");
        cmd("setblock " + at(3, 8, 0) + " minecraft:white_wool");
        tick(5);
        fellAll("custom_type: a data pack's tree type (bone trunk, wool crown, gap 1)", p(0, 0, 0), s -> is(s, "minecraft:bone_block"), s -> is(s, "minecraft:white_wool"), s -> false);
        // without the type a bone pillar is nothing
        plot();
        cmd("fill " + at(0, 0, 0) + " " + at(0, 5, 0) + " minecraft:stone");
        tick(2);
        chop(0, 0, 0);
        check("custom_type: other blocks stay blocks", controllers() == 0 && blocks("minecraft:stone", 3) == 5, controllers() + " falls");
    }

    static void moddedFallback() {
        if (BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("vanilla_timber", "test_leaves")).isEmpty()) {
            check("modded_fallback: test blocks registered", false, "not registered (scene parity only)");
            return;
        }
        testPack();
        plot();
        customTree(0, 0, 6, 2, "vanilla_timber:test_log", "vanilla_timber:test_leaves");
        tick(40);
        boolean untagged = on(() -> !level.getBlockState(p(0, 6, 0)).is(BlockTags.LEAVES) && level.getBlockState(p(0, 6, 0)).getBlock() instanceof LeavesBlock);
        int settled = count(s -> is(s, "vanilla_timber:test_leaves") && s.getValue(LeavesBlock.DISTANCE) < 7, 6);
        info("  modded leaves: untagged LeavesBlock " + untagged + ", " + settled + " with distance < 7");
        fellAll("modded_fallback: a mod's log with untagged LeavesBlock leaves", p(0, 0, 0), s -> is(s, "vanilla_timber:test_log"), s -> is(s, "vanilla_timber:test_leaves"), s -> false);
        check("modded_fallback: the fixture leaves are a LeavesBlock outside #minecraft:leaves", untagged && settled > 0, "untagged " + untagged + ", settled " + settled);
    }

    /** Random azalea trees: how many the crown rule (a natural leaf 1-2 above a top log, 1.3.6) accepts. */
    static void azaleas() {
        int ok = 0, total = 0;
        StringBuilder bad = new StringBuilder();
        for (int k = 0; k < 48; k++) {
            plot();
            feature("azalea_tree", 0, 0);
            tick(40);
            BlockPos base = trunkAt(4);
            if (base == null) continue;
            total++;
            hold("minecraft:netherite_axe");
            destroy(base);
            tick(2);
            int left = count(Parity::isLog, 14);
            if (left == 0) ok++;
            else bad.append(k).append(" (").append(io.github.profetgit.vanillatimber.server.TreeScan.why).append(") ");
            finish(140);
        }
        check("azaleas: every random azalea tree felled", ok == total && total >= 36, ok + "/" + total + (bad.length() > 0 ? ", missed " + bad : ""));
    }

    /** Random features: every natural leaf with a live distance goes with the tree; a miss dumps the left leaves as they stood before the chop. */
    static void leafWalk(String f, int runs) {
        int ok = 0, total = 0;
        StringBuilder bad = new StringBuilder();
        for (int k = 0; k < runs; k++) {
            plot();
            feature(f, 0, 0);
            tick(40);
            BlockPos base = trunkAt(8);
            if (base == null) continue;
            total++;
            java.util.Map<BlockPos, String> before = on(() -> {
                java.util.Map<BlockPos, String> m = new java.util.HashMap<>();
                for (BlockPos q : BlockPos.betweenClosed(cx - 14, Y - 1, cz - 14, cx + 14, Y + 40, cz + 14)) {
                    BlockState s = level.getBlockState(q);
                    if (!natLeaf(s)) continue;
                    StringBuilder n = new StringBuilder("d" + s.getValue(LeavesBlock.DISTANCE) + " [");
                    for (Direction dir : Direction.values()) {
                        BlockState o = level.getBlockState(q.relative(dir));
                        n.append(dir.getName().charAt(0)).append('=').append(BuiltInRegistries.BLOCK.getKey(o.getBlock()).getPath());
                        if (o.hasProperty(LeavesBlock.DISTANCE)) n.append(o.getValue(LeavesBlock.DISTANCE)).append(o.getValue(LeavesBlock.PERSISTENT) ? "p" : "");
                        n.append(' ');
                    }
                    m.put(q.immutable(), n.append(']').toString());
                }
                return m;
            });
            int orphans = count(q -> natLeaf(q) && q.getValue(LeavesBlock.DISTANCE) == 7, 14);
            hold("minecraft:netherite_axe");
            destroy(base);
            tick(2);
            Fall fall = cur;
            List<String> left = on(() -> {
                List<String> l = new ArrayList<>();
                for (var e : before.entrySet()) {
                    BlockState now = level.getBlockState(e.getKey());
                    if (!natLeaf(now)) continue;
                    String part = "not in the tree";
                    if (fall != null) for (var pt : fall.tree.parts()) if (pt.pos().equals(e.getKey())) part = "scanned " + pt.state();
                    l.add((e.getKey().getX() - base.getX()) + "," + (e.getKey().getY() - base.getY()) + "," + (e.getKey().getZ() - base.getZ()) + " " + e.getValue()
                        + " now " + now + ", " + part);
                }
                return l;
            });
            if (left.size() <= orphans) ok++;
            else {
                bad.append(k).append(": ").append(left.size()).append(" of ").append(before.size()).append(" left (").append(orphans).append(" detached); ");
                for (String l : left) info("  " + f + " " + k + " left " + l);
            }
            finish(140);
        }
        check("leaf walk " + f + ": every live leaf goes with the tree", ok == total && total >= runs * 2 / 3, ok + "/" + total + " " + bad);
    }

    /**
     * M3 critic stills: a crimson and a warped fungus, a red and a brown mushroom, each felled twice (mesh, then
     * displays), seen from the side at fixed ticks after the chop: frames/m3_&lt;kind&gt;_&lt;mesh|displays&gt;_tNN/f000.png.
     */
    /** PRD 5.1: one fell per player per tick (FTB Ultimine breaking every log at once fells one tree). */
    static void multibreak() {
        plot();
        for (int i = -1; i <= 1; i++) customTree(12 * i, 6, 6, 2, "oak_log", "oak_leaves");
        tick(30);
        hold("minecraft:iron_axe");
        int before = controllers();
        on(() -> {
            for (int i = -1; i <= 1; i++) destroy(p(12 * i, 0, 6));
            return null;
        });
        tick(2);
        int falls = controllers() - before;
        check("multibreak: 3 base logs in one tick fell 1 tree", falls == 1 && logsAt(-12, 6) == 0 && logsAt(0, 6) == 5 && logsAt(12, 6) == 5,
            falls + " falls, logs " + logsAt(-12, 6) + "/" + logsAt(0, 6) + "/" + logsAt(12, 6));
        destroy(p(0, 1, 6));
        tick(2);
        check("multibreak: the next tick fells another", controllers() - before == 2 && logsAt(0, 6) == 0 && logsAt(12, 6) == 5,
            "logs " + logsAt(0, 6) + "/" + logsAt(12, 6));
        finish(160);
    }

    /** PRD 5.7: every block of the tree passes the break event as the chopper; a denied block keeps the tree standing. */
    static void claims() {
        try {
            plot();
            customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
            tick(30);
            hold("minecraft:iron_axe");
            // a claim over the crown's +z half, not the chopped log
            denyBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx - 3, Y + 4, cz + 1, cx + 3, Y + 10, cz + 3);
            chat();
            chop(0, 0, 0);
            List<String> bar = chat();
            check("claims: a tree half in a claim stands, the chopped log breaks", logsAt(0, 0) == 7 && controllers() == 0 && bar.toString().contains("protected"),
                logsAt(0, 0) + " logs, falls " + controllers() + ", chat " + bar);
            plot();
            customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
            tick(30);
            hold("minecraft:iron_axe");
            denyBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx + 4, Y, cz + 4, cx + 10, Y + 10, cz + 10);
            chop(0, 0, 0);
            check("claims: a tree outside the claim fells", logsAt(0, 0) == 0 && displays() > 0, logsAt(0, 0) + " logs, displays " + displays());
            finish(160);
        } finally {
            denyBox = null;
        }
    }

    /** PRD 5.8: the file is re-read on save, clamped, and a broken save keeps the values in use and the operator's text. */
    static void configReload() {
        java.nio.file.Path file = Config.file();
        try {
            writeConfig(file, "{\"max_logs\": 16}");
            tick(45);
            check("config reload: a saved max_logs is picked up", on(Config::maxLogs) == 16, "max_logs " + on(Config::maxLogs));
            plot();
            customTree(0, 0, 20, 2, "oak_log", "oak_leaves");
            tick(30);
            hold("minecraft:iron_axe");
            chat();
            chop(0, 0, 0);
            List<String> big = chat();
            check("config reload: a 20-log tree is now too big", logsAt(0, 0) == 19 && big.toString().contains("too big"), logsAt(0, 0) + " logs, chat " + big);

            writeConfig(file, "{\"max_logs\": 99999}");
            tick(45);
            String text = read(file);
            check("config reload: an out-of-range value is clamped in use and in the file", on(Config::maxLogs) == Config.Key.MAX_LOGS.max && text.contains(Integer.toString(Config.Key.MAX_LOGS.max)) && !text.contains("99999"),
                "max_logs " + on(Config::maxLogs));

            String broken = "{\"max_logs\": 50,";
            writeConfig(file, broken);
            tick(45);
            check("config reload: broken JSON keeps the values and the file", on(Config::maxLogs) == Config.Key.MAX_LOGS.max && on(Config::broken) && read(file).equals(broken),
                "max_logs " + on(Config::maxLogs) + ", broken " + on(Config::broken) + ", file " + read(file).length() + " chars");

            writeConfig(file, "{\"max_logs\": 40}");
            List<String> r = cmd("timber reload");
            check("config reload: /timber reload reads it at once", on(Config::maxLogs) == 40 && !on(Config::broken), "max_logs " + on(Config::maxLogs) + ", " + r);
        } finally {
            on(() -> {
                Config.reset();
                return null;
            });
        }
    }

    /** Saves the config the way an editor does: a whole new file in one move, so the poll never reads half of it. */
    static void writeConfig(java.nio.file.Path file, String text) {
        try {
            java.nio.file.Path tmp = file.resolveSibling("timber.json.tmp");
            java.nio.file.Files.writeString(tmp, text);
            java.nio.file.Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String read(java.nio.file.Path file) {
        try {
            return java.nio.file.Files.readString(file);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** PRD 9: FallingTree or HT's TreeChop installed: a warning at start, each op told once per run. */
    static void conflict() {
        try {
            check("conflict: none in the test profile", on(Conflicts::present).isEmpty(), on(Conflicts::present).toString());
            on(() -> Conflicts.use(List.of("fallingtree")));
            chat();
            boolean told = on(() -> Conflicts.tell(player));
            List<String> first = chat();
            boolean again = on(() -> Conflicts.tell(player));
            List<String> second = chat();
            check("conflict: an op is told once per run", told && first.size() == 1 && first.getFirst().contains("FallingTree") && !again && second.isEmpty(),
                first + " then " + second);
            List<String> found = on(() -> Conflicts.use(List.of("treechop", "not_a_mod")));
            boolean told2 = on(() -> Conflicts.tell(player));
            List<String> next = chat();
            check("conflict: a new run tells again, unknown ids ignored", found.equals(List.of("treechop")) && told2 && next.toString().contains("HT's TreeChop"),
                found + " " + next);
        } finally {
            on(() -> Conflicts.use(List.of()));
        }
    }

    static void m3sheet() {
        int[] at = {1, 4, 8, 12, 16, 22, 30, 42, 60};
        for (String kind : List.of("crimson", "warped", "red", "brown")) {
            boolean fungus = kind.equals("crimson") || kind.equals("warped");
            String stem = fungus ? "minecraft:" + kind + "_stem" : "minecraft:mushroom_stem";
            Map<BlockPos, BlockState> grown = new HashMap<>();
            // mesh stills, displays stills, then a mesh frame run over the dense part (t18-t34) for the flicker check
            for (int mode : new int[] {0, Viewers.VANILLA, -1}) {
                String name = "m3_" + kind + (mode == 0 ? "_mesh" : mode < 0 ? "_seq" : "_displays");
                plot();
                if (fungus) cmd("fill " + at(-6, -1, -6) + " " + at(6, -1, 6) + " minecraft:" + kind + "_nylium");
                if (grown.isEmpty()) {
                    // the same random tree for both rows: the tallest of up to 6 rolls, its blocks kept
                    Map<BlockPos, BlockState> roll = new HashMap<>();
                    for (int r = 0; r < 6 && height(grown, stem) < 7; r++) {
                        if (r > 0) {
                            plot();
                            if (fungus) cmd("fill " + at(-6, -1, -6) + " " + at(6, -1, 6) + " minecraft:" + kind + "_nylium");
                        }
                        feature(fungus ? kind + "_fungus" : "huge_" + kind + "_mushroom", 0, 0);
                        tick(20);
                        roll.clear();
                        on(() -> {
                            if (!is(level.getBlockState(p(0, 0, 0)), stem)) return null;
                            for (BlockPos q : BlockPos.betweenClosed(p(-8, 0, -8), p(8, 30, 8))) {
                                BlockState st = level.getBlockState(q);
                                if (!st.isAir()) roll.put(q.subtract(p(0, 0, 0)), st);
                            }
                            return null;
                        });
                        if (height(roll, stem) > height(grown, stem)) {
                            grown.clear();
                            grown.putAll(roll);
                        }
                    }
                    if (!grown.isEmpty() && !grown.equals(roll)) {
                        plot();
                        if (fungus) cmd("fill " + at(-6, -1, -6) + " " + at(6, -1, 6) + " minecraft:" + kind + "_nylium");
                        paste(grown);
                    }
                } else paste(grown);
                if (grown.isEmpty()) {
                    info("  " + name + ": nothing grew");
                    break;
                }
                Viewers.test = Math.max(mode, 0);
                hold("minecraft:netherite_axe");
                // non-tree blocks beside the tree stay: a stemless cap house, nylium under the fungi
                if (!fungus) cmd("fill " + at(-6, 0, -1) + " " + at(-4, 2, 1) + " minecraft:" + kind + "_mushroom_block");
                // a sea-lantern box (the M1 sheets' light) inside the plot pit
                for (String w : new String[] {at(-12, 0, -19) + " " + at(24, 30, -19), at(-12, 0, 12) + " " + at(24, 30, 12), at(-12, 0, -19) + " " + at(-12, 30, 12),
                    at(24, 0, -19) + " " + at(24, 30, 12), at(-12, 30, -19) + " " + at(24, 30, 12)})
                    cmd("fill " + w + " minecraft:sea_lantern");
                // the box is too far to light the tree's front: an invisible light plane between it and the camera
                cmd("fill " + at(-10, 0, -6) + " " + at(22, 22, -6) + " minecraft:light[level=15] replace air");
                // chop facing +x (the fall goes that way), then turn to the tree from the side
                int h = height(grown, stem) + (fungus ? 3 : 2), dist = Math.max(9, Math.min(17, h + 4));
                cmd("tp " + who() + " " + (cx + 4) + ".5 " + Y + " " + (cz - dist) + ".5 facing " + (cx + 4) + " " + (Y + h / 2.0) + " " + cz);
                client(MpHost::hideHud);
                tick(40);
                // the scan reads the chopper's yaw: -90 for the break only, so the camera never turns
                on(() -> {
                    float yaw = player.getYRot();
                    player.setYRot(-90);
                    destroy(p(0, 0, 0));
                    player.setYRot(yaw);
                    return null;
                });
                int t = 0;
                for (int a : mode < 0 ? new int[] {18} : at) {
                    tick(a - t);
                    t = a;
                    String shot = name + String.format(java.util.Locale.ROOT, "_t%02d", a);
                    client(mc -> SelfTest.startCapture(shot, mode < 0 ? 48 : 1));
                }
                finish(160);
            }
        }
        tick(10);
        Viewers.test = Viewers.VANILLA;
    }

    /**
     * M6 render sheet stills: daylight, no lantern box, three trees; stills at the chop, mid-fall, the slam and just after.
     * Run once per render set (addon); files frames/looks_<tree>_t<tick>/f000.png.
     */
    static boolean probe, plants;

    private static final String[] PLANTS = {"short_grass", "short_grass", "short_grass", "fern", "fern", "tall_grass[half=lower]", "large_fern[half=lower]", "poppy", "dandelion", "sweet_berry_bush[age=3]", "azure_bluet", "cornflower"};

    /** Ground cover around the tree: every kind of non-full plant block, a deterministic scatter within 9 blocks. */
    static void plantCover() {
        java.util.Random r = new java.util.Random(7);
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 || r.nextInt(100) >= 65) continue;
                String b = PLANTS[r.nextInt(PLANTS.length)];
                cmd("setblock " + at(dx, 0, dz) + " minecraft:" + b + " keep");
                if (b.contains("half=lower")) cmd("setblock " + at(dx, 1, dz) + " minecraft:" + b.replace("lower", "upper") + " keep");
            }
        }
    }

    /** Published and engine sky light over three ground cells, as the chunk meshes and the light engine have them. */
    static void skyProbe(String tag) {
        int[][] cells = {{0, 0}, {3, 0}, {6, 0}, {14, 0}};
        BlockPos[] ps = new BlockPos[cells.length];
        for (int i = 0; i < ps.length; i++) ps[i] = p(cells[i][0], -1, cells[i][1]);
        client(mc -> {
            StringBuilder sb = new StringBuilder("probe " + tag + ":");
            for (BlockPos q : ps) {
                int sky = mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, q.above());
                sb.append(String.format(java.util.Locale.ROOT, " pub %2d eng %2d |", sky, io.github.profetgit.vanillatimber.client.LightHold.liveAt(q.above())));
            }
            info(sb.toString());
        });
        tick(0);
    }

    /** Waits until the client's sky light equals the server's over a grid of cells around the tree for 10 ticks in a row, and neither light engine has work. */
    static void waitLight() {
        int quiet = 0, n = 0, off = 0;
        while (quiet < 10 && n < 300) {
            java.util.concurrent.CompletableFuture<Integer> diff = new java.util.concurrent.CompletableFuture<>();
            int[][] sv = on(() -> {
                int[][] v = new int[6 * 6 * 3][2];
                int k = 0;
                for (int dx = -8; dx <= 12; dx += 4) for (int dz = -8; dz <= 12; dz += 4) for (int dy = 0; dy <= 12; dy += 6) {
                    BlockPos q = p(dx, dy, dz);
                    v[k][0] = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, q);
                    v[k][1] = q.getX() << 8 ^ q.getZ();
                    v[k][1] = k++;
                }
                return v;
            });
            client(mc -> {
                int bad = mc.level.getLightEngine().hasLightWork() ? 1000 : 0, k = 0;
                for (int dx = -8; dx <= 12; dx += 4) for (int dz = -8; dz <= 12; dz += 4) for (int dy = 0; dy <= 12; dy += 6) {
                    BlockPos q = p(dx, dy, dz);
                    if (mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, q) != sv[k++][0]) bad++;
                }
                diff.complete(bad);
            });
            off = diff.join();
            boolean busy = on(() -> level.getLightEngine().hasLightWork());
            quiet = off > 0 || busy ? 0 : quiet + 1;
            tick(1);
            n++;
        }
        info("light settled after " + n + " ticks, " + off + " cells still differ");
    }

    static String shotName(String tree) {
        return (plants ? "looksp_" : "looks_") + tree;
    }

    static void looks(boolean dense) {
        cmd("time set 6000");
        cmd("weather clear");
        cmd("gamerule advance_time false");
        Viewers.test = 0;
        String[][] trees = dense ? new String[][] {{"spruce", "spruce_b", "22"}} : new String[][] {{"warm", "birch_a", "15"}, {"birch", "birch_a", "15"}, {"spruce", "spruce_b", "22"}, {"oak", "oak_c", "15"}};
        for (String[] t : trees) {
            plot();
            fixture(t[1]);
            // the self-test world is solid above the plot pit: open a sky column over the tree and the camera, or every still is black
            for (int y = -9; y < 320; y += 24)
                cmd("fill " + (cx - 10) + " " + y + " " + (cz - 28) + " " + (cx + 24) + " " + Math.min(y + 23, 319) + " " + (cz + 10) + " minecraft:air");
            // block light in front of the tree for the sets that draw no sun (the pit is deep, so sky light alone stays dim)
            cmd("fill " + at(-10, 0, -6) + " " + at(22, 22, -6) + " minecraft:light[level=15] replace air");
            if (plants) plantCover();
            tick(plants ? 200 : 120);
            waitLight();
            hold("minecraft:netherite_axe");
            int dist = Integer.parseInt(t[2]);
            if (plants) {
                cmd("fill " + at(3, 0, -11) + " " + at(3, 1, -11) + " minecraft:stone");
                cmd("tp " + who() + " " + (cx + 3) + ".5 " + (Y + 2) + " " + (cz - 11) + ".5 facing " + (cx + 1) + " " + Y + " " + (cz - 1));
            }
            else cmd("tp " + who() + " " + (cx + 4) + ".5 " + Y + " " + (cz - dist) + ".5 facing " + (cx + 4) + " " + (Y + 6) + " " + cz);
            client(MpHost::hideHud);
            tick(40);
            waitLight();
            if (probe) skyProbe("pre");
            else client(mc -> SelfTest.startCapture(shotName(t[0]) + "_pre", 1));
            tick(2);
            io.github.profetgit.vanillatimber.client.GroundShadow.stats = true;
            io.github.profetgit.vanillatimber.client.LightHold.dirtied = 0;
            io.github.profetgit.vanillatimber.client.GroundShadow.updates = 0;
            io.github.profetgit.vanillatimber.client.GroundShadow.totalNanos = 0;
            io.github.profetgit.vanillatimber.client.GroundShadow.maxNanos = 0;
            on(() -> {
                float yaw = player.getYRot();
                player.setYRot(-90);
                destroy(p(0, 0, 0));
                player.setYRot(yaw);
                return null;
            });
            Fall f = cur;
            if (f == null) {
                check("looks " + t[0] + ": fell", false, "no fall");
                continue;
            }
            for (int w = 0; w < 40 && io.github.profetgit.vanillatimber.client.GroundShadow.stats; w++) tick(1);
            info("looks " + t[0] + ": field fit vs standing engine: darker " + io.github.profetgit.vanillatimber.client.GroundShadow.darker + " (max "
                + io.github.profetgit.vanillatimber.client.GroundShadow.maxDarker + "), brighter " + io.github.profetgit.vanillatimber.client.GroundShadow.brighter + " (max "
                + io.github.profetgit.vanillatimber.client.GroundShadow.maxBrighter + "), region " + io.github.profetgit.vanillatimber.client.GroundShadow.region);
            int slam = 0;
            while (slam < f.tl.end && f.tl.p[slam] < f.tl.spec.hit() - 0.5) slam++;
            int[] at = plants ? new int[] {1, 2, 6, 14, slam, slam + 8} : new int[] {1, Math.max(2, slam / 2), slam, slam + 8};
            if (dense) {
                at = new int[18];
                for (int i = 0; i < at.length; i++) at[i] = slam - 4 + 2 * i;
            }
            int done = 0;
            if (probe) {
                for (int i = 1; i <= 24; i++) {
                    tick(1);
                    skyProbe("t" + i);
                }
                at = new int[0];
            }
            for (int a : at) {
                tick(a - done);
                done = a;
                String shot = shotName(t[0]) + String.format(java.util.Locale.ROOT, "_t%02d", a);
                client(mc -> SelfTest.startCapture(shot, 1));
            }
            info("looks " + t[0] + ": remeshed sections " + io.github.profetgit.vanillatimber.client.LightHold.dirtied + ", field updates " + io.github.profetgit.vanillatimber.client.GroundShadow.updates + " over " + done + " ticks, field+publish total " + io.github.profetgit.vanillatimber.client.GroundShadow.totalNanos / 1000000 + " ms, worst tick " + io.github.profetgit.vanillatimber.client.GroundShadow.maxNanos / 1000 + " us");
            info("looks " + t[0] + ": stills at " + java.util.Arrays.toString(at) + ", slam tick " + slam);
            check("looks " + t[0] + ": fell and stills taken", slam > 0 && slam < f.tl.end, "slam " + slam + ", end " + f.tl.end);
            finish(160);
        }
        tick(10);
    }

    /** Stem cells straight up from the origin in a kept tree. */
    static int height(Map<BlockPos, BlockState> tree, String stem) {
        int y = 0;
        while (tree.containsKey(new BlockPos(0, y, 0)) && is(tree.get(new BlockPos(0, y, 0)), stem)) y++;
        return y;
    }

    static void paste(Map<BlockPos, BlockState> tree) {
        on(() -> {
            for (var e : tree.entrySet()) level.setBlock(p(0, 0, 0).offset(e.getKey()), e.getValue(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
            return null;
        });
        tick(5);
    }

    // ---- add-on API (hooks) ----

    /** The Minecraft Datapacks workspace: the fixture packs of the frozen 1.3.6 data pack and Enchanted Timber live there. */
    static java.nio.file.Path workspace() {
        return java.nio.file.Path.of(System.getProperty("vanilla_timber.workspace", "/home/emppu/Projects/Minecraft Datapacks"));
    }

    static java.nio.file.Path worldPacks() {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR);
    }

    /** Copies a pack folder into the world's datapacks folder as {@code name}, replacing what was there. */
    static void installPack(String name, java.nio.file.Path from) {
        java.nio.file.Path to = worldPacks().resolve(name);
        try (var walk = java.nio.file.Files.walk(from)) {
            if (java.nio.file.Files.exists(to)) {
                try (var old = java.nio.file.Files.walk(to)) {
                    for (java.nio.file.Path q : (Iterable<java.nio.file.Path>) old.sorted(java.util.Comparator.reverseOrder())::iterator) java.nio.file.Files.delete(q);
                }
            }
            for (java.nio.file.Path src : (Iterable<java.nio.file.Path>) walk::iterator) {
                java.nio.file.Path dst = to.resolve(from.relativize(src).toString());
                if (java.nio.file.Files.isDirectory(src)) java.nio.file.Files.createDirectories(dst);
                else {
                    java.nio.file.Files.createDirectories(dst.getParent());
                    java.nio.file.Files.copy(src, dst);
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Leaves the world and opens the same one again, then points the harness at the new server and runs {@link #setup()}.
     * Dynamic registries (enchantments, ...) of a data pack load only with the world, never with /reload.
     */
    static void reopen() {
        reopen(true);
    }

    static void reopen(boolean resetConfig) {
        String id = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).getParent().getFileName().toString();
        MinecraftServer old = server;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
//? if >=1.21.9 {
        client(m -> m.disconnectFromWorld(Component.literal("self-test reopen")));
//?} else if >=1.21.2 {
/*        client(m -> m.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(Component.literal("self-test reopen")), false));
*///?} else if >=1.21 {
/*        client(m -> m.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(Component.literal("self-test reopen"))));
*///?} else {
/*        client(m -> {
            m.level.disconnect();
            m.clearLevel(new net.minecraft.client.gui.screens.GenericMessageScreen(Component.literal("self-test reopen")));
        });
*///?}
        for (int i = 0; i < 2400 && (old.isRunning() || mc.getSingleplayerServer() != null || mc.level != null); i++) {
            if (i == 100 && old.isRunning()) old.halt(false);
            sleep(50);
        }
        if (old.isRunning() || mc.getSingleplayerServer() != null) throw new IllegalStateException("world did not close");
        info("world closed, reopening " + id);
//? if >=1.21 {
        client(m -> m.createWorldOpenFlows().openWorld(id, () -> { }));
//?} else {
/*        client(m -> m.createWorldOpenFlows().loadLevel(new net.minecraft.client.gui.screens.TitleScreen(), id));
*///?}
        MinecraftServer fresh = null;
        for (int i = 0; i < 4800; i++) {
            if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
                // the test world is experimental: skip the backup question
                client(m -> {
                    if (m.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen b) {
                        try {
                            var f = net.minecraft.client.gui.screens.BackupConfirmScreen.class.getDeclaredField("onProceed");
                            f.setAccessible(true);
                            ((net.minecraft.client.gui.screens.BackupConfirmScreen.Listener) f.get(b)).proceed(false, false);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                });
            }
            fresh = mc.getSingleplayerServer();
            if (fresh != null && fresh.isRunning() && mc.player != null && mc.level != null && mc.gui.screen() == null
                && !fresh.getPlayerList().getPlayers().isEmpty()) break;
            fresh = null;
            sleep(50);
        }
        if (fresh == null) throw new IllegalStateException("world did not reopen: screen " + mc.gui.screen());
        server = fresh;
        level = fresh.overworld();
        player = fresh.getPlayerList().getPlayers().getFirst();
        cur = null;
        plotFalls.clear();
        chat();
        setup(resetConfig);
    }

    static int tb(String holder) {
        String out = cmd("scoreboard players get " + holder + " tbtest").toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(" has (-?\\d+) ").matcher(out);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MIN_VALUE;
    }

    /** How many times {@code needle} shows in {@code timber:meta requires}; -1 when the list is absent. */
    static int requiresCount(String needle) {
        String out = cmd("data get storage timber:meta requires").toString();
        return out.contains("Found no elements") || out.contains("Found no") ? -1 : out.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    /** The standard birch test tree on a fresh plot, an iron axe in hand, the hook counters zeroed; returns its log count. */
    static int hookTree() {
        plot();
        customTree(0, 0, 6, 2, "birch_log", "birch_leaves");
        tick(30);
        hold("minecraft:iron_axe");
        cmd("scoreboard players set #calls_a tbtest 0");
        cmd("scoreboard players set #calls_b tbtest 0");
        return count(Parity::isLog, 6);
    }

    static void hooks() {
        hookPack();
        enchantedTimber();
        javaCancel();
    }

    /** 1.3.6's hook fixture pack (veto_a, veto_b, loaded, where), through the function tags. */
    static void hookPack() {
        installPack("hookpack", workspace().resolve("packs/Timber/dev/test/hookpack"));
        info("  " + cmd("reload"));
        tick(10);
        info("  " + cmd("datapack enable \"file/hookpack\""));
        tick(10);
        check("hooks: api/loaded runs after version_id is set", tb("#loaded") == 1 && tb("#version_id") == 20000, "loaded=" + tb("#loaded") + " version_id=" + tb("#version_id"));
        cmd("reload");
        tick(5);
        check("hooks: requires text rebuilt on /reload, not duplicated", requiresCount("Test requirement") == 1 && tb("#loaded") == 2, "requires=" + requiresCount("Test requirement") + " loaded=" + tb("#loaded"));

        int logs = hookTree();
        chop(0, 0, 0);
        check("hooks: hooks that don't return allow the fell", count(Parity::isLog, 6) == 0 && displays() > 0 && tb("#calls_a") == 1 && tb("#calls_b") == 1,
            "logs left " + count(Parity::isLog, 6) + " a=" + tb("#calls_a") + " b=" + tb("#calls_b"));
        check("hooks: run as the player, at the chopped log's centre", tb("#player") == 1 && tb("#hx") == cx * 10 + 5 && tb("#hy") == Y * 10 + 5 && tb("#hz") == cz * 10 + 5,
            "player=" + tb("#player") + " at " + tb("#hx") + " " + tb("#hy") + " " + tb("#hz") + " (log " + cx + " " + Y + " " + cz + ")");
        finish(120);

        cmd("tag " + who() + " add tbtest.veto_a");
        logs = hookTree();
        chat();
        chop(0, 0, 0);
        tick(3);
        check("hooks: return 1 cancels the fell, the chopped log still breaks", count(Parity::isLog, 6) == logs - 1 && displays() == 0 && damage() == 1,
            "logs " + logs + " -> " + count(Parity::isLog, 6) + ", displays " + displays() + ", damage " + damage());
        check("hooks: first returning hook ends the chain", tb("#calls_b") == 0, "b=" + tb("#calls_b"));
        List<String> bar = chat();
        check("hooks: cancelled fell shows no action bar", bar.isEmpty(), bar.toString());

        cmd("tag " + who() + " remove tbtest.veto_a");
        cmd("tag " + who() + " add tbtest.veto_b");
        logs = hookTree();
        chop(0, 0, 0);
        check("hooks: a later hook can cancel after an earlier one falls through", count(Parity::isLog, 6) == logs - 1 && displays() == 0,
            "logs " + logs + " -> " + count(Parity::isLog, 6));
        cmd("tag " + who() + " remove tbtest.veto_b");

        logs = hookTree();
        chopSneaking(0, 0, 0);
        check("hooks: run only after Timber's own checks pass", tb("#calls_a") == 0 && count(Parity::isLog, 6) == logs - 1, "a=" + tb("#calls_a"));

        cmd("execute as " + who() + " run timber menu");
        List<String> shown = chat();
        check("hooks: menu shows add-on requirement", shown.size() == 18 && shown.get(1).contains("Test requirement"), shown.size() + " lines: " + shown);

        cmd("datapack disable \"file/hookpack\"");
        tick(5);
        hookTree();
        chop(0, 0, 0);
        check("hooks: removing the add-on clears its requirement and hooks", requiresCount("Test requirement") <= 0 && count(Parity::isLog, 6) == 0,
            "requires=" + requiresCount("Test requirement") + " logs left " + count(Parity::isLog, 6));
        finish(120);
    }

    /** A fall on a fresh plot with some of its loot dropped and some not yet, or null. */
    static Fall midFall() {
        plot();
        customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        Fall f = on(() -> Falls.lastFall);
        for (int i = 0; i < 200; i++) {
            if (on(() -> f.dropped > 0 && f.dropped < f.rolled)) return f;
            tick(1);
        }
        return null;
    }

    static Map<String, Integer> pendingItems() {
        return on(() -> Pending.items(level));
    }

    static int pendingFalls() {
        return on(() -> Pending.falls(level));
    }

    /** PRD 8: pending loot. The crash is in-process (falls dropped from memory, the world closed and read back), not a JVM kill. */
    static void crash() {
        Fall f = midFall();
        check("crash: a fall caught mid-way with loot dropped and loot pending", f != null, "no mid state");
        if (f == null) return;
        Map<String, Integer> loot = new TreeMap<>(f.loot);
        check("crash: the fall holds a chunk ticket and one pending entry", on(() -> Falls.held(level)) == 1 && pendingFalls() == 1,
            on(() -> Falls.held(level)) + " tickets, " + pendingFalls() + " entries");
        Trace tr = trace(f);
        for (int i = 0; i < 200 && !tr.done; i++) tick(1);
        tick(2);
        check("crash: a finished fall lets its ticket and entry go", on(() -> Falls.held(level)) == 0 && pendingFalls() == 0 && pendingItems().isEmpty(),
            on(() -> Falls.held(level)) + " tickets, " + pendingFalls() + " entries, " + pendingItems());

        f = midFall();
        check("crash: second fall caught mid-way", f != null, "no mid state");
        if (f == null) return;
        Fall victim = f;
        loot = withChopped(victim.loot);
        Map<String, Integer> pending = on(() -> {
            cmd("save-all flush");
            Map<String, Integer> m = Pending.items(level);
            Falls.crash();
            return m;
        });
        Map<String, Integer> before = itemsNear(p(0, 0, 0), 24);
        int rolled = victim.rolled, dropped = victim.dropped;
        check("crash: pending loot is what the fall had not dropped", !pending.isEmpty() && pendingFalls() == 1 && sum(pending) == rolled - dropped && sum(before) == dropped + 1,
            "pending " + pending + ", in the world " + before + ", rolled " + rolled + ", dropped " + dropped);
        check("crash: loot in the world plus pending is the rolled loot", plus(before, pending).equals(loot), before + " + " + pending + " vs " + loot);
        reopen();
        Map<String, Integer> stump = itemsNear(p(0, 0, 0), 1.5), after = itemsNear(p(0, 0, 0), 24);
        boolean atStump = true;
        for (var e : pending.entrySet()) atStump &= stump.getOrDefault(e.getKey(), 0) >= e.getValue();
        check("crash: the pending loot is back at the stump", atStump, "pending " + pending + ", at the stump " + stump);
        check("crash: all the rolled loot is in the tree's area, once", after.equals(loot), after + " vs " + loot);
        check("crash: nothing pending after the load", pendingFalls() == 0 && pendingItems().isEmpty(), pendingFalls() + " entries, " + pendingItems());
        reopen();
        Map<String, Integer> again = itemsNear(p(0, 0, 0), 24);
        check("crash: a second reopen spawns nothing more", again.equals(loot) && pendingFalls() == 0, again + " vs " + loot + ", " + pendingFalls() + " entries");

        f = midFall();
        check("crash: third fall caught mid-way (normal stop)", f != null, "no mid state");
        if (f == null) return;
        loot = withChopped(f.loot);
        reopen();
        Map<String, Integer> stopped = itemsNear(p(0, 0, 0), 24);
        check("crash: a normal stop drops all the loot once", stopped.equals(loot), stopped + " vs " + loot);
        check("crash: a normal stop leaves nothing pending", pendingFalls() == 0 && pendingItems().isEmpty(), pendingFalls() + " entries, " + pendingItems());
    }

    // ---- migration from the 1.3.6 data pack (PRD 5.9) ----

    static final String OLD_PACK = "Timber-1.3.6";

    static int objectiveCount(String needle) {
        return on(() -> {
            int n = 0;
            for (String o : server.getScoreboard().getObjectiveNames()) if (o.contains(needle)) n++;
            return n;
        });
    }

    /** The 1.3.6 pack's leftovers in the world: entities with a {@code timber.} tag, markers and block displays. */
    static int oldEntities() {
        return on(() -> {
            int n = 0;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (!(e instanceof net.minecraft.world.entity.Marker) && !(e instanceof Display.BlockDisplay)) continue;
                for (String t : e.entityTags()) if (t.startsWith("timber.")) {
                    n++;
                    break;
                }
            }
            return n;
        });
    }

    static void deleteWorldPack(String name) {
        java.nio.file.Path dir = worldPacks().resolve(name);
        if (!java.nio.file.Files.exists(dir)) return;
        try (var old = java.nio.file.Files.walk(dir)) {
            for (java.nio.file.Path q : (Iterable<java.nio.file.Path>) old.sorted(java.util.Comparator.reverseOrder())::iterator) java.nio.file.Files.delete(q);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void migrate() {
        Migrate.inert = true;
        try {
            migrateRun();
        } finally {
            Migrate.inert = false;
            on(() -> {
                Players.setEnabled(server, player.getUUID(), true);
                Config.reset();
                return null;
            });
            deleteWorldPack(OLD_PACK);
        }
    }

    private static void migrateRun() {
        // phase 1: a world saved with 1.3.6, 2.0 inert
        installPack(OLD_PACK, workspace().resolve("packs/Timber/pack"));
        cmd("reload");
        tick(10);
        info("  " + cmd("datapack enable \"file/" + OLD_PACK + "\""));
        tick(60);
        boolean loaded = on(() -> server.getScoreboard().getObjective("timber.config") != null && server.getScoreboard().getObjective("timber.off") != null);
        check("migrate: the 1.3.6 pack is loaded (objectives exist)", loaded, cmd("datapack list enabled").toString());
        if (!loaded) return;

        // a small build placed by the player, with natural leaves, so only protection keeps it from felling
        plot();
        BlockPos origin = p(0, 0, 0);
        holdCmd("minecraft:oak_log 64");
        cmd("tp " + who() + " " + (cx - 3) + ".5 " + (Y + 3) + " " + cz + ".5 -90 60");
        cmd("fill " + at(-4, 0, -1) + " " + at(-3, 2, 1) + " minecraft:stone");
        for (int y = 0; y < 4; y++) place(p(0, y - 1, 0), Direction.UP);
        for (int ly = 1; ly <= 4; ly++) {
            int rr = ly >= 3 ? 1 : 2;
            cmd("fill " + at(-rr, ly, -rr) + " " + at(rr, ly, rr) + " minecraft:oak_leaves keep");
        }
        tick(40);
        Set<BlockPos> recorded = on(() -> {
            Set<BlockPos> out = new TreeSet<>();
            if (server.getCommandStorage().get(Identifier.fromNamespaceAndPath("timber", "placed")).get("o") instanceof net.minecraft.nbt.CompoundTag chunks) {
                for (String c : chunks.keySet()) {
                    if (!(chunks.get(c) instanceof net.minecraft.nbt.CompoundTag keys)) continue;
                    for (String k : keys.keySet()) {
                        String[] v = k.split(",");
                        out.add(new BlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])));
                    }
                }
            }
            return out;
        });
        int buildLogs = count(Parity::isLog, 6);
        check("migrate: 1.3.6 recorded a placed log", buildLogs == 4 && !recorded.isEmpty(), buildLogs + " logs, storage " + recorded);

        cmd("scoreboard players set #max_logs timber.config 100");
        cmd("scoreboard players set #radius timber.config 20");
        cmd("scoreboard players set #max_blocks timber.config 1000");
        try {
            java.nio.file.Files.writeString(Config.file(), "{\n  \"max_blocks\": 700\n}\n");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        on(() -> {
            Config.rescan();
            return null;
        });

        // an oak chopped under 1.3.6, caught mid-fall
        plot();
        customTree(0, 0, 8, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        BlockPos stump = p(0, 0, 0);
        chop(0, 0, 0);
        List<ItemStack>[] held = new List[] {null};
        net.minecraft.world.phys.Vec3[] at = new net.minecraft.world.phys.Vec3[1];
        Map<String, Integer> base = new TreeMap<>();
        for (int i = 0; i < 200 && held[0] == null; i++) {
            on(() -> {
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (!(e instanceof net.minecraft.world.entity.Marker) || !e.entityTags().contains("timber.ctl")) continue;
//? if >=1.21.5 {
                    var data = e.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    held[0] = data == null ? List.of() : Migrate.loot(server, data.copyTag());
//?} else {
/*                    held[0] = Migrate.loot(server, e.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("data"));
*///?}
                    at[0] = e.position();
                    cmd("tick freeze");
                    base.putAll(itemsNear(stump, 24));
                    cmd("save-all flush");
                    break;
                }
                return null;
            });
            if (held[0] == null) tick(1);
        }
        check("migrate: 1.3.6 fell caught mid-way (a timber.ctl controller with loot)", held[0] != null && !held[0].isEmpty(), held[0] == null ? "no controller" : "empty data");
        if (held[0] == null || held[0].isEmpty()) {
            cmd("tick unfreeze");
            return;
        }
        Map<String, Integer> loot = new TreeMap<>();
        for (ItemStack s : held[0]) loot.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        Map<String, Integer> expected = plus(base, loot);
        info("  controller at " + at[0] + " holds " + loot + ", world already has " + base);
        cmd("scoreboard players set " + who() + " timber.off 1");
        cmd("save-all flush");
        int offBefore = on(() -> objectiveCount("timber."));
        info("  1.3.6 objectives: " + offBefore);

        // phase 2: 2.0 back on, the world reopened
        Migrate.inert = false;
        int runs = Migrate.runs;
        reopen(false);
        tick(40);
        List<String> packs = cmd("datapack list enabled");
        check("migrate: the old pack is disabled", !packs.toString().contains(OLD_PACK), packs.toString());
        check("migrate: 2.0 wrote its meta (version_id 20000)", cmd("data get storage timber:meta version_id").toString().contains("20000"), cmd("data get storage timber:meta version_id").toString());
        check("migrate: one migration ran", Migrate.runs == runs + 1, Migrate.summary);
        Set<BlockPos> placedKeys = on(() -> {
            Set<BlockPos> out = new TreeSet<>();
            for (long k : Placed.all(level)) out.add(BlockPos.of(k));
            return out;
        });
        check("migrate: the recorded placed logs were imported", placedKeys.containsAll(recorded) && placedKeys.size() == recorded.size(), placedKeys + " vs " + recorded);
        String json;
        try {
            json = java.nio.file.Files.readString(Config.file());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        check("migrate: timber.json has max_logs 100 and radius 20 from the scores", json.contains("\"max_logs\": 100") && json.contains("\"radius\": 20"), json);
        check("migrate: a key the file already set is unchanged", json.contains("\"max_blocks\": 700") && Config.maxBlocks() == 700, json);
        check("migrate: the player's toggle is off", !on(() -> Players.enabled(server, player.getUUID())), "enabled");
        tick(60);
        Map<String, Integer> after = itemsNear(stump, 24);
        check("migrate: the controller's items lie at its position", after.equals(expected), after + " vs " + expected);
        check("migrate: no timber.d displays or timber.* markers remain", oldEntities() == 0, oldEntities() + " left");
        List<String> ctl = cmd("execute if entity @e[tag=timber.ctl]");
        check("migrate: no controller by selector either", ctl.toString().contains("failed") || ctl.toString().contains("Test failed"), ctl.toString());
        int left = on(() -> {
            int n = 0;
            for (String o : server.getScoreboard().getObjectiveNames()) if (o.startsWith("timber.")) n++;
            return n;
        });
        check("migrate: no timber.* objectives remain", left == 0, left + " left");
        check("migrate: the trigger objective timber survives", on(() -> server.getScoreboard().getObjective("timber") != null), "gone");
        boolean empty = on(() -> {
            for (String id : new String[] {"placed", "types", "curve", "menu", "op", "anim"}) {
                if (!server.getCommandStorage().get(Identifier.fromNamespaceAndPath("timber", id)).isEmpty()) return false;
            }
            return true;
        });
        check("migrate: the old storages are empty", empty, "not empty");

        // the imported placed logs protect the build
        BlockPos mark = recorded.iterator().next();
        cmd("tp " + who() + " " + (origin.getX() - 2) + ".5 " + origin.getY() + " " + origin.getZ() + ".5 -90 20");
        hold("minecraft:iron_axe");
        on(() -> {
            Players.setEnabled(server, player.getUUID(), true);
            return null;
        });
        int fallsBefore = on(() -> Falls.active(level));
        java.util.function.Supplier<Integer> buildLogsNow = () -> on(() -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(mark.offset(-3, -5, -3), mark.offset(3, 6, 3))) if (isLog(level.getBlockState(q))) n++;
            return n;
        });
        int logsBefore = buildLogsNow.get();
        destroy(mark);
        tick(4);
        check("migrate: an imported placed log is protected (no fell)", on(() -> Falls.active(level)) == fallsBefore && displays() == 0
            && logsBefore == 4 && buildLogsNow.get() == 3, "falls " + on(() -> Falls.active(level)) + ", logs " + logsBefore + " -> " + buildLogsNow.get());

        // a second start imports and spawns nothing more
        tick(40);
        Map<String, Integer> settled = itemsNear(stump, 24);
        int keys = on(() -> Placed.all(level).size());
        runs = Migrate.runs;
        reopen(false);
        tick(40);
        Map<String, Integer> again = itemsNear(stump, 24);
        check("migrate: a second start migrates nothing", Migrate.runs == runs, Migrate.summary);
        check("migrate: a second start spawns nothing more", again.equals(settled) && oldEntities() == 0, again + " vs " + settled);
        check("migrate: a second start imports no more placed logs", on(() -> Placed.all(level).size()) == keys, keys + " keys");
    }

    /** The fall's rolled loot plus the log the player's own break dropped. */
    static Map<String, Integer> withChopped(Map<String, Integer> loot) {
        Map<String, Integer> out = new TreeMap<>(loot);
        out.merge("minecraft:oak_log", 1, Integer::sum);
        return out;
    }

    static int sum(Map<String, Integer> m) {
        int n = 0;
        for (int v : m.values()) n += v;
        return n;
    }

    static Map<String, Integer> plus(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> out = new TreeMap<>(a);
        b.forEach((k, v) -> out.merge(k, v, Integer::sum));
        return out;
    }

    /** Enchanted Timber (data pack, unchanged): its enchantment is a dynamic registry entry, so the world is reopened with the pack in. */
    static void enchantedTimber() {
        installPack("enchanted_timber", workspace().resolve("packs/EnchantedTimber/pack"));
        if (net.minecraft.SharedConstants.getCurrentVersion().name().startsWith("26.1")) {
            try {
                java.nio.file.Path mc = worldPacks().resolve("enchanted_timber/pack.mcmeta");
                java.nio.file.Files.writeString(mc, java.nio.file.Files.readString(mc).replace("[107, 1]", "[101, 1]").replace("\"max_format\": 121", "\"max_format\": 101"));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        reopen();
        tick(50);
        List<String> packs = cmd("datapack list enabled");
        check("enchanted timber: pack enabled with the world", packs.toString().contains("enchanted_timber"), packs.toString());
        List<String> problem = cmd("scoreboard players get #problem enchanted_timber.data");
        check("enchanted timber: no warning with Timber present", problem.toString().contains(" has 0 "), problem.toString());
        check("enchanted timber: requirement registered with Timber once", requiresCount("Your axe needs the") == 1, "requires=" + requiresCount("Your axe needs the"));

        int n = hookTree();
        hold("minecraft:iron_axe");
        chat();
        chop(0, 0, 0);
        tick(3);
        check("enchanted timber: unenchanted axe chops a single log", count(Parity::isLog, 6) == n - 1 && displays() == 0 && damage() == 1,
            "logs " + n + " -> " + count(Parity::isLog, 6) + ", displays " + displays() + ", damage " + damage());
        List<String> bar = chat();
        check("enchanted timber: no action bar without the enchantment", bar.isEmpty(), bar.toString());

        n = hookTree();
        hold("minecraft:iron_axe");
        List<String> en = cmd("enchant " + who() + " enchanted_timber:timber");
        chat();
        chop(0, 0, 0);
        check("enchanted timber: enchanted axe fells the whole tree", en.toString().contains("Applied enchantment") && count(Parity::isLog, 6) == 0 && displays() > 0,
            "logs left " + count(Parity::isLog, 6) + ", displays " + displays() + " " + en);
        finish(120);

        cmd("datapack disable \"file/enchanted_timber\"");
        cmd("reload");
        tick(10);
        check("enchanted timber: disabling the add-on drops its requirement", requiresCount("Your axe needs the") <= 0, "requires=" + requiresCount("Your axe needs the"));
    }

    /** The Java add-on hook {@link io.github.profetgit.vanillatimber.api.TimberEvents#CANCEL}. */
    static void javaCancel() {
        io.github.profetgit.vanillatimber.api.TimberEvents.Cancel veto = (lvl, pl, pos, st) -> Component.literal("Not here");
        io.github.profetgit.vanillatimber.api.TimberEvents.CANCEL.register(veto);
        try {
            int n = hookTree();
            chat();
            chop(0, 0, 0);
            tick(3);
            List<String> bar = chat();
            check("hooks: a Java listener cancels and its text shows on the action bar", count(Parity::isLog, 6) == n - 1 && displays() == 0 && bar.toString().contains("Not here"),
                "logs " + n + " -> " + count(Parity::isLog, 6) + ", displays " + displays() + ", chat " + bar);
        } finally {
            io.github.profetgit.vanillatimber.api.TimberEvents.CANCEL.unregister(veto);
        }
        int n = hookTree();
        chop(0, 0, 0);
        check("hooks: an unregistered Java listener no longer cancels", count(Parity::isLog, 6) == 0 && displays() > 0, "logs " + n + " -> " + count(Parity::isLog, 6));
        finish(120);
    }

    static void client(java.util.function.Consumer<net.minecraft.client.Minecraft> task) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> task.accept(mc));
    }

    /** Measurement: 64 random azaleas, which crown rules accept them (1.3.6 straight up; B1/B2: crown on 2+ sides above/beside a top log); dumps 1.3.6 misses. */
    static void azaleaShapes() {
        int total = 0, up = 0, b1 = 0, b2 = 0, any = 0;
        for (int k = 0; k < 64; k++) {
            plot();
            feature("azalea_tree", 0, 0);
            tick(10);
            int[] r = on(() -> {
                int top = Integer.MIN_VALUE;
                List<BlockPos> logs = new ArrayList<>();
                for (BlockPos q : BlockPos.betweenClosed(cx - 6, Y, cz - 6, cx + 6, Y + 14, cz + 6))
                    if (isLog(level.getBlockState(q))) {
                        logs.add(q.immutable());
                        top = Math.max(top, q.getY());
                    }
                if (logs.isEmpty()) return null;
                boolean a = false, c1 = false, c2 = false;
                int sx = 0, sz = 0, nt = 0;
                for (BlockPos q : logs) {
                    if (q.getY() < top - 1) continue;
                    if (q.getY() == top) {
                        sx += q.getX();
                        sz += q.getZ();
                        nt++;
                    }
                    if (natLeaf(level.getBlockState(q.above())) || natLeaf(level.getBlockState(q.above(2)))) a = true;
                    int s1 = 0, s2 = 0;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        if (natLeaf(level.getBlockState(q.above().relative(d)))) s1++;
                        if (natLeaf(level.getBlockState(q.relative(d)))) s2++;
                    }
                    c1 |= s1 >= 2;
                    c2 |= s2 >= 2;
                }
                if (!a) {
                    int mx = Math.round((float) sx / nt), mz = Math.round((float) sz / nt);
                    for (int y = top + 2; y >= top - 1; y--) {
                        StringBuilder row = new StringBuilder("  az " + (y - top) + ": ");
                        for (int z = mz - 4; z <= mz + 4; z++) {
                            for (int x = mx - 4; x <= mx + 4; x++) {
                                BlockState st = level.getBlockState(new BlockPos(x, y, z));
                                row.append(isLog(st) ? 'L' : natLeaf(st) ? '#' : st.isAir() ? '.' : '?');
                            }
                            row.append(' ');
                        }
                        info(row.toString());
                    }
                }
                return new int[] {a ? 1 : 0, c1 ? 1 : 0, c2 ? 1 : 0};
            });
            if (r == null) continue;
            total++;
            up += r[0];
            b1 += r[1];
            b2 += r[2];
            if (r[0] + r[1] > 0) any++;
            if (r[0] == 0) info("  az " + k + ": 1.3.6 misses, B1 " + r[1] + ", B2 " + r[2]);
        }
        info("azshapes: " + total + " trees, 1.3.6 rule " + up + ", B1 " + b1 + ", B2 " + b2 + ", 1.3.6 or B1 " + any);
        check("azshapes: measured", total >= 48, total + " trees");
    }

    // ---- M8 additions: auto-replant and trunk knockback ----

    private static String sapAt(int dx, int dy, int dz) {
        return blockId(p(dx, dy, dz)).replace("minecraft:", "");
    }

    /** Plants a custom oak-like tree, chops its base, waits for the fall to end. Returns the fall. */
    private static Fall fellCustom(String log, String leaves) {
        plot();
        customTree(0, 0, 6, 2, log, leaves);
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        Fall fall = cur;
        finish(200);
        tick(2);
        return fall;
    }

    /** Fells a vanilla feature tree whose trunk starts at the origin column; null when nothing grew. */
    private static Fall fellFeature(String feature, String floor) {
        for (int roll = 0; roll < 5; roll++) {
            plot();
            if (floor != null) cmd("fill " + at(-6, -1, -6) + " " + at(6, -1, 6) + " " + floor);
            feature(feature, 0, 0);
            tick(40);
            BlockPos base = trunkAt(8);
            if (base == null) continue;
            hold("minecraft:netherite_axe");
            stand(-2, 0, -90);
            destroy(base);
            tick(2);
            Fall fall = cur;
            finish(220);
            tick(2);
            return fall;
        }
        return null;
    }

    static void replant() {
        try {
            cfg("replant", 1);
            for (String kind : List.of("oak", "birch", "cherry")) {
                Fall f = fellCustom(kind + "_log", kind + "_leaves");
                check("replant: " + kind + " plants its sapling at the stump", sapAt(0, 0, 0).equals(kind + "_sapling"), "stump cell " + sapAt(0, 0, 0));
                if (kind.equals("oak")) {
                    int saplings = itemsOf("minecraft:oak_sapling", 24);
                    check("replant: the loot is untouched (dropped == rolled, no extra sapling)", f != null && f.dropped == f.rolled && f.rolled > 0
                        && saplings == f.loot.getOrDefault("minecraft:oak_sapling", 0), f == null ? "no fall" : f.dropped + " of " + f.rolled + ", saplings " + saplings + " vs " + f.loot);
                }
            }
            Fall dark = fellFeature("dark_oak", null);
            int darkSaplings = blocks("minecraft:dark_oak_sapling", 14);
            check("replant: dark oak plants four saplings", dark != null && darkSaplings == 4, darkSaplings + " saplings");
            plot();
            fixture("spruce_b");
            tick(5);
            int trunkRoots = on(() -> {
                int n = 0;
                for (BlockPos q : BlockPos.betweenClosed(cx - 3, Y, cz - 3, cx + 3, Y, cz + 3)) if (level.getBlockState(q).is(BlockTags.LOGS)) n++;
                return n;
            });
            hold("minecraft:netherite_axe");
            stand(-2, 0, -90);
            chop(0, 0, 0);
            finish(260);
            tick(2);
            int spruce = blocks("minecraft:spruce_sapling", 14);
            check("replant: the giant spruce plants four saplings", trunkRoots == 4 && spruce == 4, spruce + " saplings for " + trunkRoots + " ground logs");
            Fall az = fellFeature("azalea_tree", null);
            int azalea = blocks("minecraft:azalea", 14) + blocks("minecraft:flowering_azalea", 14);
            check("replant: an azalea tree plants azalea or flowering azalea", az != null && azalea >= 1 && blocks("minecraft:oak_sapling", 14) == 0,
                azalea + " azaleas, " + blocks("minecraft:oak_sapling", 14) + " oak saplings");
            Fall crimson = null;
            for (int roll = 0; roll < 5 && crimson == null; roll++) {
                plot();
                cmd("fill " + at(-6, -1, -6) + " " + at(6, -1, 6) + " minecraft:crimson_nylium");
                feature("crimson_fungus", 0, 0);
                tick(20);
                if (!blockId(p(0, 0, 0)).equals("minecraft:crimson_stem")) continue;
                hold("minecraft:netherite_axe");
                stand(-2, 0, -90);
                destroy(p(0, 0, 0));
                tick(2);
                crimson = cur;
                finish(220);
                tick(2);
            }
            check("replant: a crimson fungus on nylium plants a crimson fungus", crimson != null && sapAt(0, 0, 0).equals("crimson_fungus"), "stump cell " + sapAt(0, 0, 0));
            int propagules = 0, tried = 0;
            String where = "";
            for (int k = 0; k < 4; k++) {
                Fall m = fellFeature(k % 2 == 0 ? "mangrove" : "tall_mangrove", null);
                if (m == null) continue;
                tried++;
                propagules += blocks("minecraft:mangrove_propagule", 14);
                where = sapAt(0, 0, 0);
            }
            check("replant: a mangrove plants a propagule or skips without an error", tried >= 2 && controllers() == 0, tried + " mangroves felled, " + propagules + " propagules, last stump cell " + where);
            cfg("replant", 0);
            fellCustom("oak_log", "oak_leaves");
            check("replant 0: the stump stays air", sapAt(0, 0, 0).equals("air"), "stump cell " + sapAt(0, 0, 0));
            cfg("replant", 1);
            cfg("animation", 0);
            plot();
            customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
            tick(40);
            hold("minecraft:iron_axe");
            stand(-2, 0, -90);
            chop(0, 0, 0);
            check("animation 0: the sapling is planted at once", sapAt(0, 0, 0).equals("oak_sapling") && count(Parity::isLog, 6) == 0, "stump cell " + sapAt(0, 0, 0));
            cfg("animation", 1);
            plot();
            customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
            tick(40);
            hold("minecraft:iron_axe");
            stand(-2, 0, -90);
            chop(0, 0, 0);
            tick(6);
            on(() -> {
                level.setBlock(p(0, 0, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                return null;
            });
            finish(200);
            tick(2);
            check("replant: a block placed in the stump cell mid-fall is kept", sapAt(0, 0, 0).equals("stone"), "stump cell " + sapAt(0, 0, 0));
        } finally {
            cfg("replant", 0);
            cfg("animation", 1);
        }
    }

    // ---- knock ----

    /** One mob in a knock scene: where it stood (along the fall, lateral to it), and what happened to it. */
    private static final class Subject {
        final String name;
        final double along, lateral;
        Mob mob;
        Vec3 start;
        double moved, drift, health, max;

        Subject(String name, double along, double lateral) {
            this.name = name;
            this.along = along;
            this.lateral = lateral;
        }
    }

    private static Vec3 fallDir(Fall f) {
        double yaw = Math.toRadians(f.yaw());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    private static Vec3 spot(Fall f, double along, double lateral) {
        Vec3 d = fallDir(f), pv = f.pivot();
        return new Vec3(pv.x + d.x * along - d.z * lateral, Y, pv.z + d.z * along + d.x * lateral);
    }

    /** A mob that stays where it is unless pushed: speed 0, so the trunk's push is the only thing that moves it. */
    private static Mob summon(String type, Vec3 at, String customName) {
        return on(() -> {
            Mob m = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:" + type)).create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            m.setPos(at.x, at.y, at.z);
            m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
            m.setPersistenceRequired();
            if (customName != null) m.setCustomName(Component.literal(customName));
            level.addFreshEntity(m);
            return m;
        });
    }

    private static void measure(Subject s, Fall f) {
        Vec3 d = fallDir(f);
        on(() -> {
            Vec3 now = s.mob.position();
            s.moved = (now.x - s.start.x) * d.x + (now.z - s.start.z) * d.z;
            s.drift = now.distanceTo(s.start);
            s.health = s.mob.getHealth();
            s.max = s.mob.getMaxHealth();
            return null;
        });
    }

    /** An oak falls toward +x; the subjects stand along its fall (spawned right after the chop, they are first swept ~20 ticks later). */
    private static Fall knockScene(Subject... subjects) {
        plot();
        cmd("kill @e[type=minecraft:zombie]");
        cmd("kill @e[type=minecraft:cow]");
        customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        stand(-2, 0, -90);
        chop(0, 0, 0);
        Fall f = cur;
        for (Subject s : subjects) {
            Vec3 at = spot(f, s.along, s.lateral);
            s.mob = summon(s.name.startsWith("cow") ? "cow" : "zombie", at, s.name.contains("named") ? "Bob" : null);
            s.start = at;
        }
        finish(200);
        tick(20);
        for (Subject s : subjects) measure(s, f);
        return f;
    }

    static void knock() {
        String difficulty = on(() -> level.getDifficulty().getSerializedName());
        cmd("time set 18000");
        cmd("difficulty easy");
        try {
            cfg("knockback", 1);
            cfg("trunk_damage", 4);
            Subject zombie = new Subject("zombie", 2.2, 0), cow = new Subject("cow", 4.6, -1.8), named = new Subject("named zombie", 4.6, 1.8),
                behind = new Subject("zombie behind", -5, 0);
            Fall f = knockScene(zombie, cow, named, behind);
            String where = String.format(java.util.Locale.ROOT, "zombie moved %.2f (drift %.2f), hp %.1f/%.1f", zombie.moved, zombie.drift, zombie.health, zombie.max);
            // a zombie's base armor 2 takes 1.6% off a 4-point hit (3.936): the damage is an ordinary hit, armor counts
            double taken = zombie.max - zombie.health;
            check("knock: a zombie in the fall path is pushed along the fall by more than 0.5, trunk damage 4 less its armor", zombie.moved > 0.5 && taken >= 3.9 && taken <= 4.0, where);
            check("knock: each mob is hurt at most once (one hit of about 4, not two)", taken >= 3.9 && taken <= 4.0, String.format(java.util.Locale.ROOT, "took %.3f of %.1f", taken, zombie.max));
            check("knock: a cow in the path is pushed at full health", cow.moved > 0.5 && cow.health == cow.max,
                String.format(java.util.Locale.ROOT, "cow moved %.2f, hp %.1f/%.1f", cow.moved, cow.health, cow.max));
            check("knock: a name-tagged zombie is pushed at full health", named.moved > 0.5 && named.health == named.max,
                String.format(java.util.Locale.ROOT, "named zombie moved %.2f, hp %.1f/%.1f", named.moved, named.health, named.max));
            check("knock: a zombie behind the chopper is untouched", behind.drift < 0.05 && behind.health == behind.max,
                String.format(java.util.Locale.ROOT, "drift %.3f, hp %.1f/%.1f", behind.drift, behind.health, behind.max));
            check("knock: the oak's fall still finished and dropped its loot", f != null && f.dropped == f.rolled, f == null ? "no fall" : f.dropped + " of " + f.rolled);

            // the chopper stands in the path after the chop: no push, no damage
            plot();
            customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
            tick(40);
            hold("minecraft:iron_axe");
            stand(-2, 0, -90);
            chop(0, 0, 0);
            Fall pf = cur;
            Vec3 spotP = spot(pf, 3.5, 0);
            cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f", spotP.x, spotP.y, spotP.z));
            tick(2);
            Vec3 before = on(() -> player.position());
            float hp = on(() -> player.getHealth());
            finish(200);
            tick(10);
            Vec3 after = on(() -> player.position());
            float hp2 = on(() -> player.getHealth());
            check("knock: a player standing in the path keeps the same position and health", before.distanceTo(after) < 0.01 && hp == hp2,
                String.format(java.util.Locale.ROOT, "moved %.4f, hp %.1f -> %.1f", before.distanceTo(after), hp, hp2));

            cfg("knockback", 0);
            Subject off = new Subject("zombie", 2.2, 0);
            knockScene(off);
            check("knockback 0: the zombie is untouched", off.drift < 0.05 && off.health == off.max,
                String.format(java.util.Locale.ROOT, "drift %.3f, hp %.1f/%.1f", off.drift, off.health, off.max));
            cfg("knockback", 1);

            if (!timing()) {
                notApplicable("knock cost", "timing asserts are off (-Dvanilla_timber.selftest.timing=0)");
                return;
            }
            // cost: the giant spruce with a field of sleeping cows under it, warm fall
            List<Double> means = new ArrayList<>();
            List<Double> worst = new ArrayList<>();
            int frames = 0, hits = 0;
            for (int k = 0; k < 4; k++) {
                plot();
                cmd("kill @e[type=minecraft:cow]");
                fixture("spruce_b");
                tick(5);
                hold("minecraft:netherite_axe");
                stand(-2, 0, -90);
                chop(0, 0, 0);
                Fall sf = cur;
                List<Mob> herd = new ArrayList<>();
                for (int i = 0; i < 12; i++) herd.add(summon("cow", spot(sf, 3 + i * 2, (i % 3 - 1) * 2.5), null));
                finish(260);
                tick(4);
                int moved = on(() -> {
                    int n = 0;
                    for (Mob m : herd) if (m.isAlive()) n++;
                    return n;
                });
                if (sf == null) continue;
                frames = sf.knockFrames();
                info(String.format(java.util.Locale.ROOT, "  knock cost spruce %d%s: mean %.4f ms, worst %.4f ms over %d frames, %d cows", k, k == 0 ? " (warm-up)" : "", sf.knockMs(), sf.knockMaxMs(), frames, moved));
                if (k > 0) {
                    means.add(sf.knockMs());
                    worst.add(sf.knockMaxMs());
                }
            }
            double med = median(means);
            check("knock: per-frame cost on the giant spruce <= 0.05 ms (warm median of 3)", frames > 5 && med <= 0.05,
                String.format(java.util.Locale.ROOT, "mean per frame %s ms, worst frame %s ms, %d frames", means, worst, frames));
        } finally {
            cfg("knockback", 1);
            cfg("trunk_damage", 4);
            cmd("time set 6000");
            cmd("difficulty " + difficulty);
            cmd("kill @e[type=minecraft:zombie]");
            cmd("kill @e[type=minecraft:cow]");
        }
    }

    /** M8 review stills: an oak knocks a zombie and a cow, then the sapling puff at the stump. Frames: m8_t<tick>. */
    static void m8sheet() {
        String difficulty = on(() -> level.getDifficulty().getSerializedName());
        cmd("difficulty easy");
        cmd("kill @e[type=minecraft:zombie]");
        cmd("kill @e[type=minecraft:cow]");
        plot();
        customTree(0, 0, 7, 2, "oak_log", "oak_leaves");
        tick(40);
        hold("minecraft:iron_axe");
        client(MpHost::hideHud);
        on(() -> {
            float yaw = player.getYRot();
            player.setYRot(-90);
            destroy(p(0, 0, 0));
            player.setYRot(yaw);
            return null;
        });
        Fall f = cur;
        // the plot pit gets no sky light: invisible light blocks, placed after the chop so the sweep never saw them
        on(() -> {
            for (int dx = -6; dx <= 16; dx += 3) for (int dz = -9; dz <= 9; dz += 3) for (int dy : new int[] {1, 5}) {
                BlockPos q = p(dx, dy, dz);
                if (level.getBlockState(q).isAir()) level.setBlock(q, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 2);
            }
            return null;
        });
        Vec3 pv = f.pivot(), d = fallDir(f);
        Vec3 zs = spot(f, 2.6, 0.4), cs = spot(f, 4.8, -0.9);
        Mob zombie = summon("zombie", zs, null), cow = summon("cow", cs, null);
        on(() -> {
            zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
            zombie.setYRot(f.yaw() + 90);
            cow.setYRot(f.yaw() + 90);
            return null;
        });
        Vec3 mid = spot(f, 3.0, 0);
        Vec3 cam = new Vec3(mid.x + d.z * 9, Y + 3.5, mid.z - d.x * 9);
        cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", cam.x, cam.y, cam.z, mid.x, Y + 1.2, mid.z));
        tick(1);
        int t = 2;
        int end = f.tl.end;
        for (int a = 10; a <= end + 6; a++) {
            boolean knockWindow = a >= 14 && a <= 34;
            if (!(knockWindow && a % 2 == 0) && !(a >= end - 1)) continue;
            tick(a - t);
            t = a;
            String shot = String.format(java.util.Locale.ROOT, "m8_t%02d", a);
            client(mc -> SelfTest.startCapture(shot, 1));
        }
        finish(200);
        tick(10);
        cmd("kill @e[type=minecraft:zombie]");
        cmd("kill @e[type=minecraft:cow]");
        cmd("difficulty " + difficulty);
    }

    // ---- chop mode (PRD 5.13) ----

    /** Stills of an oak log at 0, 1, 2 and 3 chops from the front and from three quarters (chop mode's shrinking log). */
    static void chopsheet() {
        cfg("chops", 1);
        cfg("chop_curve", 10);
        on(() -> {
            Viewers.test = 0;
            return null;
        });
        try {
            chopTree(5);
            cmd("time set 6000");
            client(MpHost::hideHud);
            on(() -> {
                for (int dx = -6; dx <= 6; dx += 3) for (int dz = -6; dz <= 6; dz += 3) for (int dy : new int[] {1, 5}) {
                    BlockPos q = p(dx, dy, dz);
                    if (level.getBlockState(q).isAir()) level.setBlock(q, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 2);
                }
                return null;
            });
            for (int n = 0; n <= 3; n++) {
                if (n > 0) mine(0, 1, 0);
                tick(10);
                for (String view : new String[] {"front", "angle"}) {
                    double ox = view.equals("front") ? 0 : 2.4, oz = view.equals("front") ? -3.4 : -2.4;
                    String where = String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", cx + 0.5 + ox, Y + 2.0, cz + 0.5 + oz, cx + 0.5, Y + 1.6, cz + 0.5);
                    cmd("tp " + who() + " " + where);
                    tick(12);
                    String shot = "chop_" + n + "_" + view;
                    client(mc -> SelfTest.startCapture(shot, 1));
                    tick(6);
                }
            }
        } finally {
            on(() -> {
                Viewers.test = Viewers.VANILLA;
                return null;
            });
            cfg("chops", 0);
        }
    }

    // ---- chop mode on a 2x2 trunk: the cut is a layer (PRD 5.13) ----

    /** A 2x2 dark oak on a fresh plot, the trunk at (0..1, 0..1): a vanilla feature ({@code natural}, retried until its trunk is a plain 2x2 at height 1) or a fixed one. */
    static List<BlockPos> darkOak(boolean natural) {
        List<BlockPos> got = List.of();
        for (int attempt = 0; attempt < (natural ? 8 : 1); attempt++) {
            got = darkOakOnce(natural);
            if (got.size() == 4) return got;
        }
        return got;
    }

    static List<BlockPos> darkOak() {
        return darkOak(false);
    }

    private static List<BlockPos> darkOakOnce(boolean natural) {
        plot();
        on(() -> {
            Chops.clear(level);
            return null;
        });
        if (natural) feature("dark_oak", 0, 0);
        else {
            for (int ly = 4; ly <= 7; ly++) {
                int rr = ly >= 6 ? 2 : 3;
                cmd("fill " + at(-rr, ly, -rr) + " " + at(1 + rr, ly, 1 + rr) + " minecraft:dark_oak_leaves keep");
            }
            cmd("fill " + at(0, 0, 0) + " " + at(1, 6, 1) + " minecraft:dark_oak_log");
        }
        tick(40);
        hold("minecraft:iron_axe");
        return on(() -> {
            List<BlockPos> cells = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                BlockPos q = p(dx, 1, dz);
                if (level.getBlockState(q).is(net.minecraft.world.level.block.Blocks.DARK_OAK_LOG)) cells.add(q);
            }
            cells.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
            return cells;
        });
    }

    /** The chop curve value that makes this tree take {@code want} chops (1..100), or 1. */
    static int curveFor(BlockPos at, int want) {
        int total = on(() -> {
            io.github.profetgit.vanillatimber.server.Tree t = io.github.profetgit.vanillatimber.server.TreeScan.scan(level, at, level.getBlockState(at), 0f);
            return t == null ? 0 : t.total();
        });
        for (int a = 1; a <= 100; a++) if (Chops.needed(total, a) == want) return a;
        return 1;
    }

    static void layer() {
        try {
            layerRun();
        } finally {
            cfg("chops", 0);
            cfg("chop_curve", 10);
            on(() -> {
                Viewers.test = Viewers.VANILLA;
                return null;
            });
        }
    }

    /** Measurement (no asserts): every entity and cell state around a 2x2's final chop, per tick, server and client (a ghost block seen at the break). */
    static void ghostTrace() {
        cfg("chops", 1);
        List<BlockPos> trunk = darkOak();
        if (trunk.size() != 4) {
            check("ghost: a 2x2 grew", false, "trunk " + trunk);
            return;
        }
        cfg("chop_curve", curveFor(trunk.getFirst(), 3));
        on(() -> {
            Viewers.test = 0;
            return null;
        });
        BlockPos a = trunk.get(0);
        mine(a, false);
        long[] cut = on(() -> Chops.cutAt(level, a));
        int needed = cut == null ? 0 : (int) cut[1];
        for (int n = 1; n < needed - 1; n++) mine(trunk.get(n % 4), false);
        info("TRACE needed " + needed + ", chops " + on(() -> Chops.cutAt(level, a))[0] + ", viewer local " + on(() -> Viewers.local(player)));
        BlockPos last = trunk.get(1);
        mineAction(last, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, false);
        tick(breakTicks(last));
        mineAction(last, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, false);
        for (int k = 0; k < 14; k++) {
            tick(1);
            String srv = on(() -> {
                StringBuilder sb = new StringBuilder();
                for (BlockPos q : trunk) sb.append(level.getBlockState(q).isAir() ? "_" : "L");
                sb.append(" boxes");
                for (BlockPos q : trunk) sb.append(' ').append(Chops.cellBox(level, q));
                for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(a).inflate(8), e -> !(e instanceof ServerPlayer))) {
                    sb.append(" | ").append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())
                        .append(String.format(" %.2f,%.2f,%.2f", e.getX(), e.getY(), e.getZ()));
                    if (e instanceof ItemEntity ie) sb.append(" ").append(ie.getItem().getItem());
                }
                return sb.toString();
            });
            info("TRACE srv +" + (k + 1) + " " + srv);
            CompletableFuture<String> got = new CompletableFuture<>();
            client(mc -> {
                StringBuilder sb = new StringBuilder();
                for (BlockPos q : trunk) sb.append(mc.level.getBlockState(q).isAir() ? "_" : "L");
                sb.append(" cells ").append(io.github.profetgit.vanillatimber.client.ChoppedCells.snapshot().size());
                for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                    if (e == mc.player || e.distanceToSqr(a.getX(), a.getY(), a.getZ()) > 64) continue;
                    sb.append(" | ").append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())
                        .append(String.format(" %.2f,%.2f,%.2f", e.getX(), e.getY(), e.getZ()));
                }
                got.complete(sb.toString());
            });
            await(got);
            info("TRACE cli +" + (k + 1) + " " + got.join());
        }
        finish(100);
    }

    static void layerRun() {
        cfg("chops", 1);
        List<BlockPos> trunk = darkOak();
        if (!trunk.isEmpty()) cfg("chop_curve", curveFor(trunk.getFirst(), 5));
        int x0 = trunk.isEmpty() ? 0 : trunk.getFirst().getX(), z0 = trunk.isEmpty() ? 0 : trunk.getFirst().getZ();
        boolean square = trunk.size() == 4 && trunk.stream().allMatch(q -> q.getX() - x0 <= 1 && q.getZ() - z0 <= 1 && q.getX() >= x0 && q.getZ() >= z0);
        check("layer: the dark oak grew a 2x2 trunk", square, "trunk at height 1: " + trunk);
        if (!square) return;
        on(() -> {
            Viewers.test = 0;
            return null;
        });
        BlockPos a = trunk.get(0), d = trunk.get(3);
        Fall before = on(() -> Falls.lastFall);
        int cracks0 = Chops.cracks;
        mine(a, false);
        long[] cut = on(() -> Chops.cutAt(level, a));
        int needed = cut == null ? 0 : (int) cut[1];
        boolean four = cut != null && cut.length == 6 && trunk.stream().allMatch(q -> on(() -> Chops.cutAt(level, q)) != null);
        check("layer: one chop cuts all four cells of the 2x2's layer, each cracks", four && cut[0] == 1 && Chops.cracks - cracks0 >= 4 && count(Parity::isLog, 8) > 0 && !felled(before),
            "cut " + java.util.Arrays.toString(cut) + ", cracks " + (Chops.cracks - cracks0));
        int step1 = io.github.profetgit.vanillatimber.core.Cut.step(1, needed);
        boolean footprint = true;
        StringBuilder boxes = new StringBuilder();
        for (BlockPos q : trunk) {
            int b = on(() -> Chops.cellBox(level, q));
            footprint &= b == io.github.profetgit.vanillatimber.core.Cut.upright(x0, x0 + 1, z0, z0 + 1, q.getX(), q.getZ(), step1);
            boxes.append(boxText(b)).append(' ');
        }
        check("layer: the four shrink as one footprint (outside faces only, " + (32 - 4 * step1) + " of 32 px wide)", footprint, boxes.toString().trim());
        mine(d, false);
        long[] shared = on(() -> Chops.cutAt(level, a));
        check("layer: a chop on the diagonal cell counts for the same cut", shared != null && shared[0] == 2 && chopEntries() == 1 && !felled(before),
            "cut " + java.util.Arrays.toString(shared) + ", entries " + chopEntries());
        BlockPos up = a.above();
        mine(up, false);
        long[] upper = on(() -> Chops.cutAt(level, up));
        check("layer: another height gets its own cut, the first keeps its chops", chopEntries() == 2 && upper != null && upper[0] == 1 && upper.length == 6
            && on(() -> Chops.cutAt(level, a))[0] == 2, "entries " + chopEntries() + ", upper " + java.util.Arrays.toString(upper));
        if (on(() -> Viewers.local(player))) {
            Map<Long, Integer> seen = clientBoxes();
            boolean got = true;
            for (BlockPos q : trunk) got &= seen.containsKey(q.asLong()) && seen.get(q.asLong()).equals(on(() -> Chops.cellBox(level, q)));
            check("layer: a modded viewer gets every cell's box", got && seen.size() == 8, "client " + seen.size() + " cells");
        } else info("layer: the test client never said Hello, box payload check skipped");
        int n = 2;
        while (n < needed - 1) {
            mine(trunk.get(n % 4), false);
            n++;
        }
        check("layer: the tree still stands one chop before the count", !felled(before) && on(() -> Chops.cutAt(level, a))[0] == needed - 1, "chops " + on(() -> Chops.cutAt(level, a))[0]);
        on(() -> {
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(p(0, 0, 0)).inflate(12))) e.discard();
            return null;
        });
        mine(trunk.get(1), false);
        boolean gone = on(() -> trunk.stream().allMatch(q -> level.getBlockState(q).isAir()));
        int dropped = itemsNear(a, 3).getOrDefault("minecraft:dark_oak_log", 0);
        check("layer: the last chop breaks all four cells with drops, every cut of the tree goes", felled(before) && gone && dropped == 4 && chopEntries() == 0,
            "felled " + felled(before) + ", cells gone " + gone + ", dark oak logs dropped " + dropped + ", entries " + chopEntries());
        Fall f = on(() -> Falls.lastFall);
        int tip = 0;
        double low = 0;
        while (tip < f.tl.end && f.tl.p[tip] < 300) low = Math.min(low, f.tl.dy[tip++]);
        boolean stump = on(() -> trunk.stream().allMatch(q -> level.getBlockState(q.below()).is(net.minecraft.world.level.block.Blocks.DARK_OAK_LOG)));
        check("layer: the tree hangs, drops one block onto the stump, then tips about its full 2-wide base",
            f.tree.hang() && Math.abs(low + 1) < 0.01 && tip < f.tl.end && f.tree.hw() == 990 && stump && Math.floorDiv(f.tree.py(), 1000) == a.getY() + 1,
            "hang " + f.tree.hang() + ", lowest before the tip " + low + " at tick " + tip + ", hw " + f.tree.hw() + ", stump " + stump + ", pivot y " + f.tree.py());
        finish(200);

        // classic mode keeps 2.0's fall of a 2x2: no hang, it tips on the three cells left
        cfg("chops", 0);
        List<BlockPos> plain = darkOak();
        if (plain.size() != 4) {
            check("layer: classic mode fells a 2x2 as 2.0", false, "trunk " + plain);
            return;
        }
        BlockPos pa = plain.getFirst();
        Fall before2 = on(() -> Falls.lastFall);
        chop(pa.getX() - cx, 1, pa.getZ() - cz);
        Fall classic = on(() -> Falls.lastFall);
        check("layer: classic mode fells a 2x2 as 2.0 (no hang, three cells fall with it)", classic != before2 && !classic.tree.hang()
            && classic.tree.parts().stream().filter(pt -> !pt.leaf() && pt.pos().getY() == pa.getY()).count() == 3,
            classic == before2 ? "no fall" : "hang " + classic.tree.hang());
        finish(200);
        cfg("chops", 1);
        wideLayer(3);
        wideLayer(4);
        irregularLayer();
    }

    /** Chop mode on a trunk that is no rectangle: a plus with a cell touching it only at a corner (one ground cluster, so one tree). One chop cuts every upright log of the layer; each run of it narrows from its own outside; the last chop breaks them all and the tree tips about the whole footprint. */
    static void irregularLayer() {
        shapeLayer("layer irregular: ", new int[][] {{-1, 1}, {-2, 2}, {-1, 2}, {0, 2}, {-1, 3}, {1, 1}}, new int[] {1, 1}, new int[] {-1, 2}, 4, true);
        // wider than 5 on one axis: a plus with 3-cell arms (11 cells, 7x5), the old tile limit
        int[][] big = new int[11][];
        int k = 0;
        for (int dx = -3; dx <= 3; dx++) big[k++] = new int[] {dx, 2};
        for (int dz = 0; dz <= 4; dz++) if (dz != 2) big[k++] = new int[] {0, dz};
        shapeLayer("layer wide plus: ", big, new int[] {-3, 2}, new int[] {0, 2}, 7, false);
    }

    static void shapeLayer(String g, int[][] shape, int[] hit, int[] mid, int bboxW, boolean extras) {
        int count = shape.length;
        plot();
        on(() -> {
            Chops.clear(level);
            return null;
        });
        for (int ly = 5; ly <= 8; ly++) {
            if (ly >= 7) cmd("fill " + at(-4, ly, 0) + " " + at(4, ly, 4) + " minecraft:dark_oak_leaves keep");
            else cmd("fill " + at(-6, ly, -2) + " " + at(6, ly, 6) + " minecraft:dark_oak_leaves keep");
        }
        for (int[] c : shape) cmd("fill " + at(c[0], 0, c[1]) + " " + at(c[0], 7, c[1]) + " minecraft:dark_oak_log");
        tick(40);
        hold("minecraft:iron_axe");
        List<BlockPos> trunk = on(() -> {
            List<BlockPos> cells = new ArrayList<>();
            for (int dx = -5; dx <= 5; dx++) for (int dz = -1; dz <= 6; dz++) {
                BlockPos q = p(dx, 1, dz);
                if (level.getBlockState(q).is(net.minecraft.world.level.block.Blocks.DARK_OAK_LOG)) cells.add(q);
            }
            cells.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
            return cells;
        });
        check(g + "the fixture has " + count + " logs at height 1", trunk.size() == count, "trunk " + trunk.size() + " cells");
        if (trunk.size() != count) return;
        cfg("chop_curve", curveFor(trunk.getFirst(), 4));
        BlockPos pillar = p(hit[0], 1, hit[1]), centre = p(mid[0], 1, mid[1]);
        Fall before = on(() -> Falls.lastFall);
        int cracks0 = Chops.cracks;
        mine(pillar, false);
        info("TRACE irregular chop: why '" + io.github.profetgit.vanillatimber.server.TreeScan.why + "', pillar now " + on(() -> level.getBlockState(pillar)) + ", player " + on(() -> player.position()));
        long[] cut = on(() -> Chops.cutAt(level, centre));
        int needed = cut == null ? 0 : (int) cut[1];
        boolean all = cut != null && cut.length == count + 2 && trunk.stream().allMatch(q -> on(() -> Chops.cutAt(level, q)) != null);
        check(g + "one chop on the detached pillar cuts all " + count + " upright logs of the layer, each cracks", all && cut[0] == 1 && Chops.cracks - cracks0 >= count && chopEntries() == 1 && !felled(before),
            "cut " + (cut == null ? "none" : cut.length - 2 + " cells, chops " + cut[0]) + ", cracks " + (Chops.cracks - cracks0) + ", entries " + chopEntries());
        int step = io.github.profetgit.vanillatimber.core.Cut.step(1, needed);
        java.util.Set<Long> set = new java.util.HashSet<>();
        for (BlockPos q : trunk) set.add(q.asLong());
        boolean shaped = true;
        StringBuilder boxes = new StringBuilder();
        for (BlockPos q : trunk) {
            int rx0 = q.getX(), rx1 = rx0, rz0 = q.getZ(), rz1 = rz0;
            while (set.contains(q.atY(q.getY()).offset(rx0 - 1 - q.getX(), 0, 0).asLong())) rx0--;
            while (set.contains(q.offset(rx1 + 1 - q.getX(), 0, 0).asLong())) rx1++;
            while (set.contains(q.offset(0, 0, rz0 - 1 - q.getZ()).asLong())) rz0--;
            while (set.contains(q.offset(0, 0, rz1 + 1 - q.getZ()).asLong())) rz1++;
            int want = io.github.profetgit.vanillatimber.core.Cut.shaped(rx0, rx1, rz0, rz1, q.getX(), q.getZ(), step);
            int b = on(() -> Chops.cellBox(level, q));
            shaped &= b == want;
            boxes.append(boxText(b)).append(' ');
        }
        boolean pillarNarrow = !extras || on(() -> Chops.cellBox(level, pillar)) == io.github.profetgit.vanillatimber.core.Cut.pack(step, 16 - step, 0, 16, step, 16 - step);
        boolean middleWhole = !extras || on(() -> Chops.cellBox(level, centre)) == io.github.profetgit.vanillatimber.core.Cut.WHOLE;
        check(g + "each run narrows from its own outside (the corner cell like a 1x1, the plus's middle stays whole)", shaped && pillarNarrow && middleWhole,
            "shaped " + shaped + ", pillar " + pillarNarrow + ", middle " + middleWhole + ": " + boxes.toString().trim());
        for (int c = 1; c < needed - 1; c++) mine(trunk.get(c % count), false);
        check(g + "the tree still stands one chop before the count", !felled(before) && on(() -> Chops.cutAt(level, centre))[0] == needed - 1, "needed " + needed);
        on(() -> {
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(p(0, 0, 0)).inflate(14))) e.discard();
            return null;
        });
        mine(centre, false);
        boolean gone = on(() -> trunk.stream().allMatch(q -> level.getBlockState(q).isAir()));
        int dropped = itemsNear(centre, 8).getOrDefault("minecraft:dark_oak_log", 0);
        check(g + "the last chop breaks all " + count + " with drops and every cut goes", felled(before) && gone && dropped == count && chopEntries() == 0,
            "felled " + felled(before) + ", gone " + gone + ", dropped " + dropped + ", entries " + chopEntries());
        Fall f = on(() -> Falls.lastFall);
        check(g + "the tree hangs and tips about the whole " + bboxW + "-wide footprint", f.tree.hang() && f.tree.hw() == bboxW * 500 - 10, "hang " + f.tree.hang() + ", hw " + f.tree.hw());
        finish(200);
    }

    /** A fixed {@code n}x{@code n} dark oak (a modded giant's trunk) on a fresh plot, the trunk at (0..n-1, 0..n-1); its cells at height 1. */
    static List<BlockPos> wideOak(int n) {
        plot();
        on(() -> {
            Chops.clear(level);
            return null;
        });
        for (int ly = 5; ly <= 8; ly++) {
            int rr = ly >= 7 ? 2 : 3;
            cmd("fill " + at(-rr, ly, -rr) + " " + at(n - 1 + rr, ly, n - 1 + rr) + " minecraft:dark_oak_leaves keep");
        }
        cmd("fill " + at(0, 0, 0) + " " + at(n - 1, 7, n - 1) + " minecraft:dark_oak_log");
        tick(40);
        hold("minecraft:iron_axe");
        return on(() -> {
            List<BlockPos> cells = new ArrayList<>();
            for (int dx = -1; dx <= n; dx++) for (int dz = -1; dz <= n; dz++) {
                BlockPos q = p(dx, 1, dz);
                if (level.getBlockState(q).is(net.minecraft.world.level.block.Blocks.DARK_OAK_LOG)) cells.add(q);
            }
            cells.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
            return cells;
        });
    }

    /** Chop mode on 3x3 and 4x4 trunks: one cut takes the whole layer, it shrinks as one footprint, the last chop breaks it and the tree tips about its full base. */
    static void wideLayer(int n) {
        String g = "layer " + n + "x" + n + ": ";
        List<BlockPos> trunk = wideOak(n);
        int cells = n * n;
        check(g + "the fixed trunk is " + n + "x" + n, trunk.size() == cells, "trunk " + trunk.size() + " cells");
        if (trunk.size() != cells) return;
        cfg("chop_curve", curveFor(trunk.getFirst(), 4));
        int x0 = trunk.stream().mapToInt(BlockPos::getX).min().getAsInt(), z0 = trunk.stream().mapToInt(BlockPos::getZ).min().getAsInt();
        BlockPos a = trunk.getFirst();
        BlockPos mid = trunk.stream().filter(q -> q.getX() == x0 + n / 2 && q.getZ() == z0 + n / 2).findFirst().orElse(a);
        BlockPos corner = trunk.getLast();
        Fall before = on(() -> Falls.lastFall);
        int cracks0 = Chops.cracks;
        mine(corner, false);
        long[] cut = on(() -> Chops.cutAt(level, a));
        int needed = cut == null ? 0 : (int) cut[1];
        boolean all = cut != null && cut.length == cells + 2 && trunk.stream().allMatch(q -> on(() -> Chops.cutAt(level, q)) != null);
        check(g + "one chop cuts all " + cells + " cells of the layer, each cracks", all && cut[0] == 1 && Chops.cracks - cracks0 == cells && chopEntries() == 1 && !felled(before),
            "cut " + (cut == null ? "none" : cut.length - 2 + " cells, chops " + cut[0]) + ", cracks " + (Chops.cracks - cracks0) + ", entries " + chopEntries());
        mine(mid, false);
        int step = io.github.profetgit.vanillatimber.core.Cut.step(2, needed);
        boolean footprint = true, inner = true;
        for (BlockPos q : trunk) {
            int b = on(() -> Chops.cellBox(level, q));
            footprint &= b == io.github.profetgit.vanillatimber.core.Cut.upright(x0, x0 + n - 1, z0, z0 + n - 1, q.getX(), q.getZ(), step);
            boolean edge = q.getX() == x0 || q.getX() == x0 + n - 1 || q.getZ() == z0 || q.getZ() == z0 + n - 1;
            if (!edge && b != io.github.profetgit.vanillatimber.core.Cut.WHOLE && step * n <= 16) inner = false;
        }
        long[] two = on(() -> Chops.cutAt(level, a));
        check(g + "a chop in the middle counts for the same cut; one footprint shrinks (" + (16 * n - 2 * n * step) + " of " + 16 * n + " px)",
            two != null && two[0] == 2 && chopEntries() == 1 && footprint && inner, "chops " + (two == null ? -1 : two[0]) + ", footprint " + footprint + ", inner whole " + inner);
        for (int c = 2; c < needed - 1; c++) mine(trunk.get(c % cells), false);
        check(g + "the tree still stands one chop before the count", !felled(before) && on(() -> Chops.cutAt(level, a))[0] == needed - 1, "needed " + needed);
        on(() -> {
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(p(0, 0, 0)).inflate(14))) e.discard();
            return null;
        });
        mine(a, false);
        boolean gone = on(() -> trunk.stream().allMatch(q -> level.getBlockState(q).isAir()));
        int dropped = itemsNear(mid, 4).getOrDefault("minecraft:dark_oak_log", 0);
        check(g + "the last chop breaks all " + cells + " cells with drops, every cut goes", felled(before) && gone && dropped == cells && chopEntries() == 0,
            "felled " + felled(before) + ", gone " + gone + ", dropped " + dropped + ", entries " + chopEntries());
        if (!felled(before)) return;
        Fall f = on(() -> Falls.lastFall);
        int tip = 0;
        double low = 0;
        while (tip < f.tl.end && f.tl.p[tip] < 300) low = Math.min(low, f.tl.dy[tip++]);
        int fcx = (2 * x0 + n) * 500, fcz = (2 * z0 + n) * 500;
        double r = Math.hypot(f.tree.px() - fcx, f.tree.pz() - fcz);
        int hw = n * 500 - 10;
        boolean onEdge = Math.abs(r - hw) <= 3;
        boolean stump = on(() -> trunk.stream().allMatch(q -> level.getBlockState(q.below()).is(net.minecraft.world.level.block.Blocks.DARK_OAK_LOG)));
        long cutLeft = f.tree.parts().stream().filter(pt -> !pt.leaf() && pt.pos().getY() == a.getY()).count();
        check(g + "hangs, drops one block onto the stump, tips about its full " + n + "-wide base (hw " + hw + ", pivot on the stump edge)",
            f.tree.hang() && Math.abs(low + 1) < 0.01 && tip < f.tl.end && f.tree.hw() == hw && onEdge && stump && cutLeft == 0 && Math.floorDiv(f.tree.py(), 1000) == a.getY() + 1,
            "hang " + f.tree.hang() + ", low " + low + " tip tick " + tip + ", hw " + f.tree.hw() + ", pivot r " + String.format(java.util.Locale.ROOT, "%.1f", r)
                + ", stump " + stump + ", cut-level logs in the fall " + cutLeft + ", pivot y " + f.tree.py());
        finish(220);
    }

    /** Stills of a 3x3 trunk: whole, at the chop before the last (the thinnest core), and mid-tip from the side. */
    static void widesheet() {
        cfg("chops", 1);
        on(() -> {
            Viewers.test = 0;
            return null;
        });
        try {
            int n = 3;
            List<BlockPos> trunk = wideOak(n);
            if (trunk.size() != n * n) {
                check("widesheet: the fixed trunk is 3x3", false, "trunk " + trunk.size());
                return;
            }
            cfg("chop_curve", curveFor(trunk.getFirst(), 4));
            cmd("time set 6000");
            client(MpHost::hideHud);
            on(() -> {
                for (int dx = -9; dx <= 12; dx += 3) for (int dz = -9; dz <= 12; dz += 3) for (int dy : new int[] {1, 5, 9}) {
                    BlockPos q = p(dx, dy, dz);
                    if (level.getBlockState(q).isAir()) level.setBlock(q, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 2);
                }
                return null;
            });
            int x0 = trunk.getFirst().getX(), z0 = trunk.getFirst().getZ();
            double tx = x0 + n / 2.0, tz = z0 + n / 2.0;
            int needed = 4, done = 0;
            for (int stage = 0; stage <= 1; stage++) {
                int want = stage == 0 ? 0 : needed - 1;
                while (done < want) {
                    cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", tx, Y + 0.0, tz - 3.5, tx, Y + 1.5, tz));
                    tick(3);
                    mine(trunk.get(++done % trunk.size()), false);
                    long[] cutNow = on(() -> Chops.cutAt(level, trunk.getFirst()));
                    if (cutNow != null) needed = (int) cutNow[1];
                    want = needed - 1;
                }
                tick(40);
                for (String view : new String[] {"front", "angle"}) {
                    double ox = view.equals("front") ? 0 : 4.0, oz = view.equals("front") ? -5.4 : -4.0;
                    cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", tx + ox, Y + 2.4, tz + oz, tx, Y + 1.5, tz));
                    tick(12);
                    String shot = "wide_" + (stage == 0 ? "0" : "max") + "_" + view;
                    client(mc -> SelfTest.startCapture(shot, 1));
                    tick(6);
                }
            }
            info("widesheet: needed " + needed + ", thin still at " + done + " chops");
            Fall before = on(() -> Falls.lastFall);
            mine(trunk.getFirst(), false);
            Fall f = on(() -> Falls.lastFall);
            if (f == before) {
                check("widesheet: the last chop fells", false, "no fall");
                return;
            }
            int tipT = 0;
            while (tipT < f.tl.end && f.tl.p[tipT] < 4500) tipT++;
            Vec3 dir = fallDir(f);
            cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", tx - dir.z * 8.5, Y + 0.5, tz + dir.x * 8.5, tx, Y + 2.2, tz));
            // every frame from the hang through the plunge onto the stump (flicker check)
            client(mc -> SelfTest.startCapture("wide_fall", 45));
            for (int w = 0; w < 200 && SelfTest.capturing(); w++) sleep(10);
            double mx = tx + dir.x * 3, mz = tz + dir.z * 3;
            cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", mx - dir.z * 15, Y + 3.0, mz + dir.x * 15, mx, Y + 3.5, mz));
            while (on(() -> f.n) < tipT - 1 && on(() -> f.n) < f.tl.end) tick(1);
            client(mc -> SelfTest.startCapture("wide_tip", 1));
            check("widesheet: stills taken (3x3 at 0 and " + (needed - 1) + " chops, tip)", true, "tip at fall tick " + tipT + ", hw " + f.tree.hw());
            finish(220);
        } finally {
            on(() -> {
                Viewers.test = Viewers.VANILLA;
                return null;
            });
            cfg("chops", 0);
            cfg("chop_curve", 10);
        }
    }

    /** Stills of a 2x2 dark oak cut at 0, 1, 2 and 3 of 4 chops, then mid-drop and mid-tip from the side. */
    static void layersheet() {
        cfg("chops", 1);
        on(() -> {
            Viewers.test = 0;
            return null;
        });
        try {
            List<BlockPos> trunk = darkOak(true);
            if (trunk.size() != 4) {
                check("layersheet: the dark oak grew a 2x2 trunk", false, "trunk " + trunk);
                return;
            }
            cfg("chop_curve", 1);
            cmd("time set 6000");
            client(MpHost::hideHud);
            on(() -> {
                for (int dx = -9; dx <= 9; dx += 3) for (int dz = -9; dz <= 12; dz += 3) for (int dy : new int[] {1, 5, 9}) {
                    BlockPos q = p(dx, dy, dz);
                    if (level.getBlockState(q).isAir()) level.setBlock(q, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 2);
                }
                return null;
            });
            double tx = trunk.getFirst().getX() + 1.0, tz = trunk.getFirst().getZ() + 1.0;
            int needed = 4, done = 0;
            for (int n = 0; n <= 3; n++) {
                // stills at 0, 1 and 2 chops and at the chop before the last (the thinnest core)
                int want = n == 3 ? needed - 1 : n;
                while (done < want) {
                    cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", tx, Y + 0.0, tz - 3.0, tx, Y + 1.5, tz));
                    tick(3);
                    mine(trunk.get(++done % 4), false);
                    long[] cutNow = on(() -> Chops.cutAt(level, trunk.getFirst()));
                    if (cutNow != null) needed = (int) cutNow[1];
                }
                if (n == 3) info("layersheet: needed " + needed + ", last still at " + done + " chops");
                // the chop's wood chips settle first
                tick(40);
                for (String view : new String[] {"front", "angle"}) {
                    double ox = view.equals("front") ? 0 : 3.2, oz = view.equals("front") ? -4.4 : -3.2;
                    String where = String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", tx + ox, Y + 2.2, tz + oz, tx, Y + 1.5, tz);
                    cmd("tp " + who() + " " + where);
                    tick(12);
                    String shot = "layer_" + n + "_" + view;
                    client(mc -> SelfTest.startCapture(shot, 1));
                    tick(6);
                }
            }
            // the last chop from the three-quarter view (the drop is shot from there), then the camera to the fall's side
            double ax = tx + 3.2, az = tz - 3.2;
            BlockPos near = trunk.stream().min(java.util.Comparator.comparingDouble(q -> Math.hypot(q.getX() + 0.5 - ax, q.getZ() + 0.5 - az))).get();
            cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", ax, Y + 0.0, az, tx, Y + 1.5, tz));
            tick(12);
            Fall before = on(() -> Falls.lastFall);
            mine(near, false);
            Fall f = on(() -> Falls.lastFall);
            if (f == before) {
                check("layersheet: the fourth chop fells", false, "no fall");
                return;
            }
            int drop = 0, tipT = 0;
            while (drop < f.tl.end && f.tl.dy[drop] > -0.1) drop++;
            while (tipT < f.tl.end && f.tl.p[tipT] < 4500) tipT++;
            int t = on(() -> f.n);
            Vec3 dir = fallDir(f);
            cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", tx - dir.z * 7.5, Y + 0.5, tz + dir.x * 7.5, tx, Y + 2.2, tz));
            // every frame from the hang through the landing (flicker check); the mid-drop still is picked from it
            client(mc -> SelfTest.startCapture("layer_4_fall", 45));
            info("layersheet: layer_4_fall burst from fall tick " + on(() -> f.n) + ", plunge frame " + drop + " (dy " + f.tl.dy[drop] + ")");
            for (int w = 0; w < 200 && SelfTest.capturing(); w++) sleep(10);
            double mx = tx + dir.x * 3, mz = tz + dir.z * 3;
            cmd("tp " + who() + " " + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f facing %.3f %.3f %.3f", mx - dir.z * 14, Y + 3.0, mz + dir.x * 14, mx, Y + 3.5, mz));
            while (on(() -> f.n) < tipT - 1 && on(() -> f.n) < f.tl.end) tick(1);
            client(mc -> SelfTest.startCapture("layer_5_tip", 1));
            info("layersheet: layer_5_tip at fall tick " + on(() -> f.n) + " (pitch " + f.tl.p[Math.min(tipT, f.tl.end)] / 100 + ")");
            info("layersheet: fall started at tick " + t + ", drop frame " + drop + ", tip frame " + tipT);
            finish(220);
        } finally {
            on(() -> {
                Viewers.test = Viewers.VANILLA;
                return null;
            });
            cfg("chops", 0);
            cfg("chop_curve", 10);
        }
    }

    // ---- the tree stays fallen (land_blocks), fall time by size, tools ----

    /** What the shared rule says must land: {logs, leaves} of this fall, worked out here from the tree frame by brute force. */
    static int[] expectLanded(Fall f) {
        return on(() -> {
            boolean[] mask = f.landed();
            List<io.github.profetgit.vanillatimber.server.Tree.Part> parts = f.tree.parts();
            int logs = 0, leaves = 0;
            for (var p : parts) if (!p.leaf() && !p.state().isAir() && p.k() < mask.length && mask[p.k()]) logs++;
            for (var q : parts) {
                if (!q.leaf() || q.state().isAir()) continue;
                if (!(q.state().getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) && !q.state().isCollisionShapeFullBlock(level, q.pos())) continue;
                for (var p : parts) {
                    if (p.leaf() || p.state().isAir() || p.k() >= mask.length || !mask[p.k()]) continue;
                    int r = io.github.profetgit.vanillatimber.core.Landing.LEAF_REACH;
                    if (Math.abs(p.tx() - q.tx()) <= r && Math.abs(p.wy() - q.wy()) <= r && Math.abs(p.tz() - q.tz()) <= r) {
                        leaves++;
                        break;
                    }
                }
            }
            // a leaf whose resting cell is in the ground bursts with the crown (Fall.keepOpenLeaves): at most the rule's count
            int decided = 0;
            int[] lr = f.landRing();
            for (int i = 0; i < lr.length; i++) if (lr[i] >= 0 && parts.get(i).leaf()) decided++;
            return new int[] {logs, Math.min(leaves, decided), leaves};
        });
    }

    static List<BlockPos> worldBlocks(Predicate<BlockState> f) {
        return on(() -> {
            List<BlockPos> out = new ArrayList<>();
            for (BlockPos q : BlockPos.betweenClosed(cx - 14, Y - 1, cz - 14, cx + 14, Y + 12, cz + 14)) if (f.test(level.getBlockState(q))) out.add(q.immutable());
            return out;
        });
    }

    static void land() {
        try {
            landRun();
        } finally {
            cfg("land_blocks", 0);
            cfg("protect", 1);
            cmd("gamerule random_tick_speed 0");
            on(() -> {
                Viewers.test = Viewers.VANILLA;
                return null;
            });
        }
    }

    private static net.minecraft.core.Direction.Axis fallAxis(Fall f) {
        Vec3 d = fallDir(f);
        return Math.abs(d.x) > Math.abs(d.z) ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z;
    }

    private static void landTree(String kind) {
        plot();
        if (kind.equals("oak")) {
            customTree(0, 0, 6, 2, "oak_log", "oak_leaves");
            // a vine on the trunk (jungle trees): it must not land as a block with its standing faces
            cmd("setblock " + at(1, 2, 0) + " minecraft:vine[west=true]");
        }
        else {
            cmd("fill " + at(0, 0, 0) + " " + at(1, 7, 1) + " minecraft:dark_oak_log");
            for (int ly = 5; ly <= 8; ly++) {
                int rr = ly >= 7 ? 2 : 3;
                cmd("fill " + at(1 - rr, ly, 1 - rr) + " " + at(rr, ly, rr) + " minecraft:dark_oak_leaves keep");
            }
        }
        tick(40);
        hold("minecraft:iron_axe");
        stand(-3, 0, -90);
    }

    static void landRun() {
        List<BlockPos> oakCells = null;
        for (String kind : List.of("oak", "dark_oak")) {
            cfg("land_blocks", 100);
            cfg("protect", 1);
            landTree(kind);
            int logsBefore = count(Parity::isLog, 8);
            chop(0, 0, 0);
            Fall f = cur;
            int[] want = expectLanded(f);
            int rings = on(() -> {
                int n = 0;
                for (boolean b : f.tree.logRings()) if (b) n++;
                return n;
            }), landedRings = on(() -> {
                int n = 0;
                for (boolean b : f.landed()) if (b) n++;
                return n;
            });
            check(kind + ": land_blocks 100 lands every log ring", rings > 0 && landedRings == rings, landedRings + " of " + rings);
            finish(300);
            waitFalls();
            tick(3);
            List<BlockPos> logs = worldBlocks(Parity::isLog), leaves = worldBlocks(Parity::isLeaf);
            net.minecraft.core.Direction.Axis axis = fallAxis(f);
            List<String> wrong = new ArrayList<>();
            for (BlockPos q : logs) {
                var st = on(() -> level.getBlockState(q));
                if (st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS) != axis) wrong.add(q + " " + st);
            }
            int placedLogs = on(() -> f.landedLogs), placedLeaves = on(() -> f.landedLeaves), failed = on(() -> f.landFailed);
            info(kind + ": want " + java.util.Arrays.toString(want) + ", placed " + placedLogs + " logs " + placedLeaves + " leaves, failed " + failed + ", yaw " + f.yaw());
            float fy = on(() -> f.yaw());
            check(kind + ": land_blocks falls along an axis (no diagonal)", Math.floorMod(Math.round(fy), 90) == 0, "yaw " + fy);
            check(kind + ": a straight row: every landed log in one line along the fall", logs.stream().map(q -> axis == net.minecraft.core.Direction.Axis.X ? q.getZ() : q.getX()).distinct().count()
                <= (kind.equals("oak") ? 1 : 2), "cross-axis spread " + logs.stream().map(q -> axis == net.minecraft.core.Direction.Axis.X ? q.getZ() : q.getX()).distinct().toList());
            check(kind + ": every landed log lies sideways along the fall (" + axis + ")", !logs.isEmpty() && wrong.isEmpty() && logs.size() == placedLogs, logs.size() + " logs, wrong " + wrong);
            check(kind + ": the rule's count of logs and leaves is placed or refused, nothing else", placedLogs + placedLeaves + failed == want[0] + want[1] && placedLogs <= want[0] && placedLeaves <= want[1]
                && placedLogs * 2 >= want[0], "want " + want[0] + " logs " + want[1] + " leaves; placed " + placedLogs + "/" + placedLeaves + ", failed " + failed);
            // reach 2 in the tree frame, rotated onto the grid, plus up to 2 cells when a taken cell moves a part
            check(kind + ": the leaves in the world are the placed ones, none beyond reach of a landed log", leaves.size() == placedLeaves && leaves.stream().allMatch(l -> logs.stream().anyMatch(g -> Math.abs(g.getX() - l.getX()) <= 5
                && Math.abs(g.getY() - l.getY()) <= 5 && Math.abs(g.getZ() - l.getZ()) <= 5)), leaves.size() + " leaves in the world, " + placedLeaves + " placed");
            check(kind + ": a taken cell moves the part next door: every landed log is placed", placedLogs == want[0], placedLogs + " of " + want[0] + " logs, " + failed + " parts refused");
            int vines = worldBlocks(q -> q.is(net.minecraft.world.level.block.Blocks.VINE)).size();
            check(kind + ": vines and other decor never land as blocks", vines == 0, vines + " vine blocks in the world");
            check(kind + ": landed leaves are natural leaves (they can decay)", leaves.stream().allMatch(l -> on(() -> natLeaf(level.getBlockState(l)))), "persistent leaf among " + leaves.size());
            int rolled = on(() -> f.rolled), dropped = on(() -> f.dropped), placed = on(() -> f.placed);
            check(kind + ": rolled == dropped + placed", rolled == dropped + placed && placed > 0, "rolled " + rolled + ", dropped " + dropped + ", placed " + placed);
            String logId = kind + "_log";
            int dropLogs = itemsOf("minecraft:" + logId, 24);
            check(kind + ": only the logs that found no cell drop (and the chopped one)", dropLogs == want[0] - placedLogs + 1, dropLogs + " dropped, " + (want[0] - placedLogs) + " refused + the chopped log");
            check(kind + ": landed logs cost the axe nothing (the chopped log's own break costs 1)", damage() == 1 && Pending.falls(level) == 0 && displays() == 0, "damage " + damage() + ", pending " + Pending.falls(level));
            Set<BlockPos> lying = on(() -> {
                Set<BlockPos> out = new HashSet<>();
                for (long k : Placed.allLanded(level)) out.add(BlockPos.of(k));
                return out;
            });
            check(kind + ": landed logs are recorded", lying.size() == logs.size() && lying.containsAll(logs), lying.size() + " keys for " + logs.size() + " logs");
            if (kind.equals("oak")) oakCells = logs.stream().map(q -> q.offset(-cx, 0, -cz)).toList();

            // they stay while a landed log is near
            cmd("gamerule random_tick_speed 3000");
            tick(60);
            int stayed = worldBlocks(Parity::isLeaf).size();
            cmd("gamerule random_tick_speed 0");
            check(kind + ": landed leaves stay while the logs lie next to them", stayed * 2 >= leaves.size(), stayed + " of " + leaves.size());

            // breaking a landed log breaks just that block, protect 1 and 0
            for (int protect : new int[] {1, 0}) {
                cfg("protect", protect);
                List<BlockPos> now = worldBlocks(Parity::isLog);
                BlockPos target = now.get(now.size() / 2);
                Fall before = on(() -> Falls.lastFall);
                int keysBefore = on(() -> Placed.allLanded(level).size());
                destroy(target);
                tick(3);
                int left = worldBlocks(Parity::isLog).size();
                check(kind + ": breaking a landed log, protect " + protect + ", breaks that block and fells nothing",
                    left == now.size() - 1 && !felled(before) && controllers() == 0 && on(() -> Placed.allLanded(level).size()) == keysBefore - 1,
                    left + " of " + now.size() + " logs, felled " + felled(before) + ", keys " + on(() -> Placed.allLanded(level).size()));
            }

            // the last logs go: the leaves lose their support and decay by random ticks
            for (BlockPos q : worldBlocks(Parity::isLog)) destroy(q);
            cmd("gamerule random_tick_speed 3000");
            int left = -1;
            for (int i = 0; i < 40 && left != 0; i++) {
                tick(10);
                left = worldBlocks(Parity::isLeaf).size();
            }
            cmd("gamerule random_tick_speed 0");
            check(kind + ": without logs nearby the landed leaves decay", left == 0, left + " leaves left of " + stayed);
            check(kind + ": a decaying landed leaf drops what a tree's leaf drops (saplings, sticks)", itemsOf("minecraft:" + kind + "_sapling", 24) + itemsOf("minecraft:stick", 24) >= 0, "");
        }

        // 50 %: the top half of the log rings, one piece at the crown end
        cfg("land_blocks", 50);
        landTree("oak");
        chop(0, 0, 0);
        Fall half = cur;
        boolean[] mask = on(() -> half.landed().clone());
        int rings = on(() -> {
            int n = 0;
            for (boolean b : half.tree.logRings()) if (b) n++;
            return n;
        });
        int landedRings = 0;
        for (boolean b : mask) if (b) landedRings++;
        int expectedRings = (50 * rings + 50) / 100;
        int topRing = -1;
        for (int k = 0; k < mask.length; k++) if (on(() -> half.tree.logRings())[k]) topRing = k;
        boolean crownEnd = topRing >= 0 && mask[topRing];
        for (int k = 0, seen = 0; k < mask.length; k++) if (mask[k]) seen++; else if (seen > 0 && on(() -> half.tree.logRings())[k]) crownEnd = false;
        check("land_blocks 50: half the log rings, rounded, one piece at the crown end", landedRings == expectedRings && crownEnd, landedRings + " of " + rings + " rings, mask " + java.util.Arrays.toString(mask));
        int logsAll = on(() -> half.tree.logs());
        int wear = logsAll - on(() -> {
            int n = 0;
            for (var p : half.tree.parts()) if (!p.leaf() && p.k() < mask.length && mask[p.k()]) n++;
            return n;
        });
        finish(300);
        waitFalls();
        tick(3);
        int placedHalf = on(() -> half.landedLogs), failedHalf = on(() -> half.landFailed);
        check("land_blocks 50: the axe pays only for the logs that drop", damage() == wear + 1 && wear > 0, "damage " + damage() + ", dropping " + wear + " + the chopped log");
        check("land_blocks 50: the other half is placed and counted", placedHalf + failedHalf >= landedRings && on(() -> half.rolled == half.dropped + half.placed),
            placedHalf + " placed, " + failedHalf + " refused, rolled " + on(() -> half.rolled) + " dropped " + on(() -> half.dropped) + " placed " + on(() -> half.placed));

        // a log's cell and every neighbour it could move to taken: that log drops as items
        cfg("land_blocks", 100);
        landTree("oak");
        chop(0, 0, 0);
        Fall blocked = cur;
        // the oak's top log (farthest from the stump), boxed in two cells deep: it and its neighbours have nowhere to go
        // (oakCells are relative to the plot: this tree stands on another one)
        BlockPos stoneAt = oakCells == null ? null : oakCells.stream().max(java.util.Comparator.comparingInt(q -> Math.abs(q.getX()) + Math.abs(q.getZ()))).map(q -> q.offset(cx, 0, cz)).orElse(null);
        if (stoneAt != null) {
            on(() -> {
                for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++)
                    if (level.getBlockState(stoneAt.offset(dx, dy, dz)).canBeReplaced()) level.setBlock(stoneAt.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                return null;
            });
        }
        finish(300);
        waitFalls();
        tick(3);
        int dropLogs = itemsOf("minecraft:oak_log", 24);
        check("a taken cell: the stone stays and that log drops as an item", stoneAt != null && blockId(stoneAt).equals("minecraft:stone") && on(() -> blocked.landFailed) >= 1 && dropLogs >= 2
            && on(() -> blocked.rolled == blocked.dropped + blocked.placed), "stone cell " + stoneAt + " is " + (stoneAt == null ? "?" : blockId(stoneAt)) + ", refused " + on(() -> blocked.landFailed) + ", log items " + dropLogs);
        on(() -> {
            if (stoneAt != null) for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++)
                if (level.getBlockState(stoneAt.offset(dx, dy, dz)).is(net.minecraft.world.level.block.Blocks.STONE)) level.setBlock(stoneAt.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            return null;
        });

        landStills();
    }

    /** Stills of an oak lying down, mesh and displays: just before the first ring lands, at the hand-over, and 3 and 10 ticks after. */
    static void landStills() {
        cfg("land_blocks", 100);
        cmd("time set 6000");
        for (String[] run : new String[][] {{"oak", "0"}, {"oak", "1"}, {"dark_oak", "0"}}) {
            int mode = run[1].equals("0") ? 0 : Viewers.VANILLA;
            String name = "land_" + (mode == 0 ? "mesh" : "displays") + (run[0].equals("oak") ? "" : "_" + run[0]);
            landTree(run[0]);
            Viewers.test = mode;
            client(MpHost::hideHud);
            tick(40);
            on(() -> {
                float yaw = player.getYRot();
                player.setYRot(-90);
                destroy(p(0, 0, 0));
                player.setYRot(yaw);
                return null;
            });
            Fall f = cur;
            int land = on(() -> f.tl.landTick()) + 1;
            // from the side of the lying trunk, a little above it, looking at its middle
            Vec3 d = fallDir(f);
            double half = on(() -> f.tree.spec().h()) * 0.5;
            Vec3 mid = new Vec3(cx + 0.5 + d.x * half, Y + 0.6, cz + 0.5 + d.z * half);
            Vec3 cam = mid.add(-d.z * 7.5 - d.x * 1.5, 3.2, d.x * 7.5 - d.z * 1.5);
            cmd(String.format(java.util.Locale.ROOT, "tp %s %.3f %.3f %.3f facing %.3f %.3f %.3f", who(), cam.x, cam.y, cam.z, mid.x, mid.y, mid.z));
            // light blocks after the chop (the sweep that picked the landing never saw them); the test world is dark
            on(() -> {
                for (int dx = -12; dx <= 12; dx += 4) for (int dz = -12; dz <= 12; dz += 4) for (int dy : new int[] {4, 8}) {
                    BlockPos q = p(dx, dy, dz);
                    if (level.getBlockState(q).isAir()) level.setBlock(q, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 2);
                }
                return null;
            });

            // the whole fall at 60 fps from just after the chop to 12 ticks past the hand-over, for a frame-by-frame sheet
            // and wb img flicker (one-frame pops, a ring drawn twice or missing), then one still once it settled
            int frames = Math.max(60, (land + 12 - on(() -> f.n)) * 3);
            client(mc -> SelfTest.startCapture(name + "_burst", frames));
            tick(2);
            for (int i = 0; i < 400 && SelfTest.capturing(); i++) tick(1);
            tick(Math.max(0, land + 12 - on(() -> f.n)));
            client(mc -> SelfTest.startCapture(name + "_after", 1));
            tick(5);
            info(name + ": hand-over at tick " + land + " after the chop");
            finish(300);
            waitFalls();
            check(name + ": the stills' fall ended with blocks in the world", worldBlocks(Parity::isLog).size() > 0 && displays() == 0, worldBlocks(Parity::isLog).size() + " logs");
        }
        tick(10);
        Viewers.test = Viewers.VANILLA;
        cfg("land_blocks", 0);
    }

    /** A landed fall's displays are gone long before it ends (the last ring's RING tick comes 4 ticks after it lands): waits for the controller. */
    static void waitFalls() {
        for (int i = 0; i < 200 && controllers() > 0; i++) tick(1);
    }

    /** Fall length by size alone (user 2026-11-18): an oak keeps 1.3.6's length, a 60-ring tree takes longer than 22 ticks. */
    static void fallTime() {
        chopTree(6);
        chop(0, 0, 0);
        Fall a = cur;
        int hOak = on(() -> a.tree.spec().h()), durOak = on(() -> a.tree.spec().dur());
        finish(300);
        check("fall_time: an oak falls in 1.3.6's time", durOak == Math.max(10, Math.min(22, hOak / 2 + 8)), "h " + hOak + ", dur " + durOak);
        plot();
        cmd("fill " + at(-4, 10, -4) + " " + at(4, 66, 4) + " minecraft:air");
        customTree(0, 0, 60, 2, "oak_log", "oak_leaves");
        tick(30);
        hold("minecraft:iron_axe");
        chop(0, 0, 0);
        Fall c = cur;
        if (c == null || c == a) {
            check("fall_time: the tall tree fells", false, "no fall: " + io.github.profetgit.vanillatimber.server.TreeScan.why + ", logs " + count(Parity::isLog, 6));
            return;
        }
        int h = on(() -> c.tree.spec().h()), durTall = on(() -> c.tree.spec().dur());
        int want = io.github.profetgit.vanillatimber.core.FallSpec.duration(h);
        check("fall_time: a 56+ ring tree falls slower than an oak, longer than 22 ticks", h >= 56 && durTall == want && durTall > 22 && durTall > durOak,
            "h " + h + ", dur " + durTall + ", rule " + want + ", oak " + durOak);
        finish(800);
    }

    static boolean felledBy(String item, boolean chopMode) {
        chopTree(5);
        hold(item);
        Fall before = on(() -> Falls.lastFall);
        if (chopMode) mine(0, 0, 0);
        else destroy(p(0, 0, 0));
        tick(2);
        boolean fell = felled(before), chopped = chopMode && chopCount() > 0;
        if (fell) finish(160);
        return chopMode ? chopped : fell;
    }

    static void tools() {
        try {
            cmd("effect give " + who() + " minecraft:haste 1000 20 true");
            on(() -> {
                Config.setTools(List.of("minecraft:stick"));
                return null;
            });
            check("tools [stick]: a stick fells", felledBy("minecraft:stick", false) && count(Parity::isLog, 6) == 0, "logs " + count(Parity::isLog, 6));
            boolean axe = felledBy("minecraft:iron_axe", false);
            check("tools [stick]: an iron axe doesn't, one log breaks", !axe && count(Parity::isLog, 6) == 4, "felled " + axe + ", logs " + count(Parity::isLog, 6));
            on(() -> {
                Config.setTools(List.of("#minecraft:axes"));
                return null;
            });
            check("tools [#minecraft:axes]: an axe fells", felledBy("minecraft:iron_axe", false), "");
            check("tools [#minecraft:axes]: a stick doesn't", !felledBy("minecraft:stick", false), "");
            on(() -> {
                Config.setTools(List.of("not an id!", "minecraft:stick"));
                return null;
            });
            List<String> kept = on(() -> Config.tools().entries());
            check("tools: a bad entry is skipped and the rest kept", kept.equals(List.of("minecraft:stick")) && felledBy("minecraft:stick", false), "kept " + kept);
            cfg("chops", 1);
            on(() -> {
                Config.setTools(List.of("minecraft:stick"));
                return null;
            });
            boolean axeChop = felledBy("minecraft:iron_axe", true);
            check("tools [stick] in chop mode: an iron axe counts no chop, one log breaks", !axeChop && count(Parity::isLog, 6) == 4 && chopEntries() == 0, "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
            boolean stickChop = felledBy("minecraft:stick", true);
            check("tools [stick] in chop mode: a stick chops", stickChop && count(Parity::isLog, 6) == 5 && chopEntries() == 1, "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
        } finally {
            cfg("chops", 0);
            cfg("require_axe", 1);
            cmd("effect clear " + who());
            on(() -> {
                Config.setTools(io.github.profetgit.vanillatimber.server.Tools.DEFAULT);
                return null;
            });
        }
    }

    // ---- the settings screen ----

//? if >=26.2 {
    /** The settings screen pauses the integrated server, so waits here are wall-clock, not ticks. */
    static void screen() {
        cfg("chops", 0);
        cfg("replant", 0);
        client(MpHost::hideHud);
        try {
            io.github.profetgit.vanillatimber.client.TimberScreen[] made = new io.github.profetgit.vanillatimber.client.TimberScreen[1];
            CompletableFuture<Void> opened = new CompletableFuture<>();
            client(mc -> {
                io.github.profetgit.vanillatimber.client.TimberScreen.forced = null;
                made[0] = new io.github.profetgit.vanillatimber.client.TimberScreen(null);
                mc.gui.setScreen(made[0]);
                opened.complete(null);
            });
            await(opened);
            sleep(800);
            io.github.profetgit.vanillatimber.client.TimberScreen sc = made[0];
            check("screen: singleplayer edits the config file", sc.mode == io.github.profetgit.vanillatimber.client.TimberScreen.Mode.LOCAL, "mode " + sc.mode);
            check("screen: sections Felling, Fall, Tool, Server", sc.headers.equals(List.of("Felling", "Fall", "Tool", "Server")), "headers " + sc.headers);
            List<String> missing = new ArrayList<>();
            for (Config.Key k : Config.Key.values()) if (!sc.widgets.containsKey(k)) missing.add(k.json);
            check("screen: every setting has a widget", missing.isEmpty(), "missing " + missing);
            List<String> bare = new ArrayList<>();
            for (Config.Key k : sc.widgets.keySet()) if (!sc.helpText.containsKey(k)) bare.add(k.json);
            check("screen: every widget has hover help", bare.isEmpty() && !sc.helpText.isEmpty(), "no help: " + bare);
            String chopsHelp = sc.helpText.getOrDefault(Config.Key.CHOPS, Component.empty()).getString();
            check("screen: hover help is the config file's comment, without the file's value keys", chopsHelp.startsWith("Chop mode: each break of a trunk log") && !chopsHelp.contains("1:")
                && !sc.helpText.get(Config.Key.REQUIRE_AXE).getString().contains("#"), chopsHelp + " | " + sc.helpText.get(Config.Key.REQUIRE_AXE).getString());
            check("screen: the chop count is greyed out while chop mode is off", !sc.widgets.get(Config.Key.CHOP_CURVE).active, "active " + sc.widgets.get(Config.Key.CHOP_CURVE).active);
            List<String> doubled = new ArrayList<>(), labels = new ArrayList<>();
            for (net.minecraft.client.gui.components.AbstractWidget w : sc.widgets.values()) {
                String m = w.getMessage().getString();
                labels.add(m);
                int c = m.indexOf(':');
                if (c > 0 && m.indexOf(m.substring(0, c + 1), c + 1) >= 0) doubled.add(m);
            }
            check("screen: each button says its name once", doubled.isEmpty(), doubled.isEmpty() ? labels.toString() : doubled.toString());
            check("screen: the Tool section has the tools box, showing the list", sc.toolsBox != null && sc.toolsBox.active && sc.toolsBox.getValue().equals(on(() -> Config.tools().text())),
                sc.toolsBox == null ? "no box" : "box '" + sc.toolsBox.getValue() + "' active " + sc.toolsBox.active);
            CompletableFuture<Void> typed = new CompletableFuture<>();
            client(mc -> {
                sc.applyTools("minecraft:stick, #minecraft:axes");
                typed.complete(null);
            });
            await(typed);
            sleep(400);
            check("screen: applying the tools box sets Config.tools() locally", on(() -> Config.tools().entries()).equals(List.of("minecraft:stick", "#minecraft:axes")) && sc.applied.contains("tools=minecraft:stick,#minecraft:axes"),
                on(() -> Config.tools().entries()) + ", applied " + sc.applied);
            CompletableFuture<Void> reset = new CompletableFuture<>();
            client(mc -> {
                sc.applyTools("");
                reset.complete(null);
            });
            await(reset);
            sleep(300);
            check("screen: an empty tools box restores the axes", on(() -> Config.tools().entries()).equals(io.github.profetgit.vanillatimber.server.Tools.DEFAULT), on(() -> Config.tools().entries()).toString());
            String shotName = "screen_local";
            client(mc -> SelfTest.startCapture(shotName, 1));
            sleep(600);
            CompletableFuture<Void> pressed = new CompletableFuture<>();
            client(mc -> {
                ((net.minecraft.client.gui.components.CycleButton<?>) sc.widgets.get(Config.Key.CHOPS)).onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                pressed.complete(null);
            });
            await(pressed);
            sleep(400);
            check("screen: switching chop mode on sets the config and wakes the chop count", on(() -> Config.get(Config.Key.CHOPS)) == 1 && sc.applied.contains("chops=1")
                && sc.widgets.get(Config.Key.CHOP_CURVE).active,
                "chops " + on(() -> Config.get(Config.Key.CHOPS)) + ", applied " + sc.applied);
            CompletableFuture<Void> ro = new CompletableFuture<>();
            io.github.profetgit.vanillatimber.client.TimberScreen[] view = new io.github.profetgit.vanillatimber.client.TimberScreen[1];
            client(mc -> {
                io.github.profetgit.vanillatimber.client.TimberScreen.forced = io.github.profetgit.vanillatimber.client.TimberScreen.Mode.READ_ONLY;
                view[0] = new io.github.profetgit.vanillatimber.client.TimberScreen(null);
                io.github.profetgit.vanillatimber.client.TimberScreen.forced = null;
                mc.gui.setScreen(view[0]);
                ro.complete(null);
            });
            await(ro);
            sleep(800);
            boolean locked = view[0].widgets.values().stream().noneMatch(w -> w.active);
            check("screen: a non-operator on a server sees every value read-only", locked && view[0].widgets.size() == Config.Key.values().length
                && view[0].helpText.getOrDefault(Config.Key.CHOPS, Component.empty()).getString().contains("operators"),
                "active " + view[0].widgets.values().stream().filter(w -> w.active).count());
            check("screen: read-only mode has the tools box not editable", view[0].toolsBox != null && !view[0].toolsBox.active, view[0].toolsBox == null ? "no box" : "active " + view[0].toolsBox.active);
            CompletableFuture<Void> scrolled = new CompletableFuture<>();
            client(mc -> {
                view[0].widgets.get(Config.Key.SLICE_MS).setFocused(false);
                if (view[0].children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.AbstractSelectionList<?>).findFirst().orElse(null)
                    instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> l) l.setScrollAmount(l.maxScrollAmount());
                scrolled.complete(null);
            });
            await(scrolled);
            sleep(500);
            String roName = "screen_readonly";
            client(mc -> SelfTest.startCapture(roName, 1));
            sleep(600);
        } finally {
            CompletableFuture<Void> closed = new CompletableFuture<>();
            client(mc -> {
                io.github.profetgit.vanillatimber.client.TimberScreen.forced = null;
                mc.gui.setScreen(null);
                closed.complete(null);
            });
            await(closed);
            cfg("chops", 0);
        }
    }
//?} else {
/*    static void screen() {
        check("screen: no settings screen on this version", true, "");
    }
*///?}

    static int mineSeq;

    static void mineAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, boolean sneak) {
        on(() -> {
            player.setShiftKeyDown(sneak);
            try {
                player.gameMode.handleBlockBreakAction(pos, action, Direction.UP, 320, ++mineSeq);
            } finally {
                player.setShiftKeyDown(false);
            }
            return null;
        });
    }

    static int breakTicks(BlockPos pos) {
        return on(() -> {
            float progress = level.getBlockState(pos).getDestroyProgress(player, level, pos);
            return progress <= 0 ? 1 : (int) Math.ceil(1 / progress);
        });
    }

    /** Mines a block the way a client does: START_DESTROY_BLOCK, its break time in ticks, STOP_DESTROY_BLOCK. */
    static void mine(BlockPos pos, boolean sneak) {
        Fall before = Falls.lastFall;
        mineAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, sneak);
        tick(breakTicks(pos));
        mineAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, sneak);
        tick(1);
        noteFall(before);
    }

    static void mine(int dx, int dy, int dz) {
        mine(p(dx, dy, dz), false);
    }

    static void noteFall(Fall before) {
        on(() -> {
            Fall now = Falls.lastFall;
            if (now != null && now != before) {
                cur = now;
                plotFalls.add(now);
            }
            return null;
        });
    }

    static boolean felled(Fall before) {
        return on(() -> Falls.lastFall != before);
    }

    static int chopCount() {
        long[] pr = on(() -> Chops.progress(level));
        return pr == null ? 0 : (int) pr[0];
    }

    static int chopEntries() {
        return on(() -> Chops.entries(level));
    }

    static boolean handEmpty() {
        return on(() -> player.getMainHandItem().isEmpty());
    }

    /** A fresh plot with a trunk of {@code h} oak logs and the iron axe in hand. */
    static void chopTree(int h) {
        plot();
        on(() -> {
            Chops.clear(level);
            return null;
        });
        customTree(0, 0, h, 2, "oak_log", "oak_leaves");
        tick(30);
        hold("minecraft:iron_axe");
    }

    static String boxText(int b) {
        StringBuilder t = new StringBuilder("[");
        for (int i = 0; i < 6; i++) t.append(i == 0 ? "" : i % 2 == 0 ? " " : "-").append(io.github.profetgit.vanillatimber.core.Cut.bound(b, i));
        return t.append("]").toString();
    }

    @SuppressWarnings("unchecked")
    static Map<Long, Integer> clientBoxes() {
        CompletableFuture<Void> got = new CompletableFuture<>();
        Map<Long, Integer>[] seen = new Map[1];
        client(mc -> {
            seen[0] = io.github.profetgit.vanillatimber.client.ChoppedCells.snapshot();
            got.complete(null);
        });
        await(got);
        return seen[0];
    }

    static void chops() {
        try {
            chopsRun();
        } finally {
            denyBox = null;
            cfg("chops", 0);
            cfg("chop_curve", 10);
            cfg("sneak", 0);
            cfg("require_axe", 1);
            cmd("effect clear " + who());
            on(() -> {
                Viewers.test = Viewers.VANILLA;
                return null;
            });
        }
    }

    static void chopsRun() {
        // chops 0: the 2.0 behaviour, one mined break fells
        cfg("chops", 0);
        chopTree(5);
        Fall before = on(() -> Falls.lastFall);
        mine(0, 0, 0);
        tick(2);
        check("chops 0: one mined break fells the tree as in 2.0, 1 + 4 damage", felled(before) && count(Parity::isLog, 6) == 0 && chopEntries() == 0 && damage() == 5,
            "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries() + ", damage " + damage());
        finish(120);

        // an oak of 5 logs takes 4 chops: the tree stands after 3, cracked, then falls
        cfg("chops", 1);
        check("curve: 5 logs 4 chops, 30 logs 15, 100 logs 25, 1 log 1", Chops.needed(5, 10) == 4 && Chops.needed(30, 10) == 15 && Chops.needed(100, 10) == 25 && Chops.needed(1, 10) == 1,
            Chops.needed(5, 10) + " " + Chops.needed(30, 10) + " " + Chops.needed(100, 10) + " " + Chops.needed(1, 10));
        chopTree(5);
        before = on(() -> Falls.lastFall);
        int cracks0 = Chops.cracks, creaks0 = Chops.creaks;
        mine(0, 0, 0);
        check("chop 1: the log stays, progress 1 of 4, one damage", count(Parity::isLog, 6) == 5 && chopCount() == 1 && damage() == 1 && !felled(before),
            "logs " + count(Parity::isLog, 6) + ", chops " + chopCount() + ", damage " + damage());
        mine(0, 0, 0);
        mine(0, 0, 0);
        long[] pr = on(() -> Chops.progress(level));
        check("chops 2 and 3: the tree stands with 3 of 4, 3 damage, the crack at stage 7 on the log",
            count(Parity::isLog, 6) == 5 && pr != null && pr[0] == 3 && pr[1] == 4 && damage() == 3 && !felled(before) && Chops.lastStage == 7 && p(0, 0, 0).equals(Chops.lastCrack) && Chops.cracks - cracks0 == 3,
            "logs " + count(Parity::isLog, 6) + ", progress " + java.util.Arrays.toString(pr) + ", damage " + damage() + ", stage " + Chops.lastStage + " at " + Chops.lastCrack + ", cracks " + (Chops.cracks - cracks0));
        check("the creak plays on the chop before the last", Chops.creaks - creaks0 == 1, "creaks " + (Chops.creaks - creaks0));
        check("a vanilla viewer's mining has the mask over the log", on(() -> Chops.maskOf(player)) != null, "no mask");
        mine(0, 0, 0);
        tick(2);
        check("chop 4: the tree falls, 4 damage, progress and crack cleared, mask gone",
            felled(before) && count(Parity::isLog, 6) == 0 && damage() == 4 && chopEntries() == 0 && Chops.lastStage == -1 && on(() -> Chops.maskOf(player)) == null,
            "logs " + count(Parity::isLog, 6) + ", damage " + damage() + ", entries " + chopEntries() + ", stage " + Chops.lastStage);
        Map<String, Integer> chopLoot = new TreeMap<>(on(() -> Falls.lastFall).loot);
        finish(160);
        int chopped = itemsOf("minecraft:oak_log", 8);

        // loot after the final chop is the classic fell's
        cfg("chops", 0);
        chopTree(5);
        chop(0, 0, 0);
        Map<String, Integer> classicLoot = new TreeMap<>(on(() -> Falls.lastFall).loot);
        finish(160);
        int classic = itemsOf("minecraft:oak_log", 8);
        check("loot: the logs after a chop-mode fell equal the classic fell's", classic == chopped && classic == 5 && chopLoot.getOrDefault("minecraft:oak_log", 0).equals(classicLoot.getOrDefault("minecraft:oak_log", 0)),
            "classic " + classic + " " + classicLoot + ", chops " + chopped + " " + chopLoot);
        cfg("chops", 1);

        // an axe with 1 durability breaks on the first chop; the progress stays and an empty hand finishes
        cfg("chop_curve", 1);
        chopTree(4);
        holdCmd("minecraft:iron_axe[minecraft:damage=249]");
        before = on(() -> Falls.lastFall);
        mine(0, 0, 0);
        check("an axe with 1 durability breaks on chop 1 and the progress is kept", handEmpty() && chopCount() == 1 && count(Parity::isLog, 6) == 4 && !felled(before),
            "hand empty " + handEmpty() + ", chops " + chopCount() + ", logs " + count(Parity::isLog, 6));
        cmd("effect give " + who() + " minecraft:haste 1000 40 true");
        mine(0, 0, 0);
        tick(2);
        check("an empty hand finishes the tree", felled(before) && count(Parity::isLog, 6) == 0 && chopEntries() == 0, "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
        cmd("effect clear " + who());
        finish(160);
        cfg("chop_curve", 10);

        // gates: a pickaxe (require_axe 1), sneaking, a placed log: one log breaks, no progress, no fell
        cmd("effect give " + who() + " minecraft:haste 1000 20 true");
        chopTree(5);
        hold("minecraft:iron_pickaxe");
        before = on(() -> Falls.lastFall);
        mine(0, 0, 0);
        check("a pickaxe with require_axe 1: one log breaks, no progress, no fell", count(Parity::isLog, 6) == 4 && chopEntries() == 0 && !felled(before), "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
        cfg("require_axe", 0);
        chopTree(5);
        hold("minecraft:iron_pickaxe");
        mine(0, 0, 0);
        check("a pickaxe with require_axe 0: it chops", count(Parity::isLog, 6) == 5 && chopCount() == 1, "logs " + count(Parity::isLog, 6) + ", chops " + chopCount());
        cfg("require_axe", 1);
        chopTree(5);
        before = on(() -> Falls.lastFall);
        mine(p(0, 0, 0), true);
        check("sneaking: one log breaks, no progress, no fell", count(Parity::isLog, 6) == 4 && chopEntries() == 0 && !felled(before), "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
        chopTree(5);
        on(() -> {
            Placed.add(level, p(0, 0, 0));
            return null;
        });
        before = on(() -> Falls.lastFall);
        mine(0, 0, 0);
        check("a placed log: one log breaks, no progress, no fell", count(Parity::isLog, 6) == 4 && chopEntries() == 0 && !felled(before), "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
        chopTree(5);
        before = on(() -> Falls.lastFall);
        boolean broke = destroy(p(0, 0, 0));
        tick(2);
        check("a destroyBlock that isn't the player's mining: one log breaks, no progress, no fell", broke && count(Parity::isLog, 6) == 4 && chopEntries() == 0 && !felled(before),
            "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
        chopTree(6);
        before = on(() -> Falls.lastFall);
        denyBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx - 3, Y + 3, cz - 3, cx + 3, Y + 7, cz + 3);
        mine(0, 0, 0);
        denyBox = null;
        check("a claim over part of the tree: one log breaks, no progress, no fell", count(Parity::isLog, 6) == 5 && chopEntries() == 0 && !felled(before), "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
        cmd("effect clear " + who());

        // the rate limit: a STOP that comes before the log's break time has passed again is cancelled and not counted
        chopTree(5);
        mine(0, 0, 0);
        BlockPos log = p(0, 0, 0);
        mineAction(log, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, false);
        int a = chopCount();
        tick(2);
        mineAction(log, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, false);
        int b = chopCount();
        tick(7);
        mineAction(log, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, false);
        int c = chopCount();
        check("a too-fast second STOP is not counted, one after the break time is", a == 1 && b == 1 && c == 2 && count(Parity::isLog, 6) == 5, "chops " + a + " " + b + " " + c);

        // each height is its own cut: chops at three heights make three cuts of one chop each; the cut that reaches the
        // count fells from its height and every cut of the tree goes with it
        cfg("chop_curve", 5);
        chopTree(6);
        before = on(() -> Falls.lastFall);
        mine(0, 0, 0);
        mine(0, 1, 0);
        mine(0, 2, 0);
        long[] c0 = on(() -> Chops.cutAt(level, p(0, 0, 0))), c2 = on(() -> Chops.cutAt(level, p(0, 2, 0)));
        check("another height starts its own cut: 3 cuts of 1 chop, the crack on the last", chopEntries() == 3 && c0 != null && c0[0] == 1 && c2 != null && c2[0] == 1
            && p(0, 2, 0).equals(Chops.lastCrack) && !felled(before), "entries " + chopEntries() + ", y0 " + java.util.Arrays.toString(c0) + ", y2 " + java.util.Arrays.toString(c2) + ", crack " + Chops.lastCrack);
        int need = c0 == null ? 0 : (int) c0[1];
        for (int k = 1; k < need - 1; k++) mine(0, 1, 0);
        long[] c1 = on(() -> Chops.cutAt(level, p(0, 1, 0)));
        check("chops at one height add up, the other cuts keep theirs", c1 != null && c1[0] == need - 1 && on(() -> Chops.cutAt(level, p(0, 0, 0)))[0] == 1 && !felled(before),
            "y1 " + java.util.Arrays.toString(c1) + ", needed " + need);
        mine(0, 1, 0);
        tick(2);
        check("the cut that reaches the count fells from its height, every cut of the tree goes", felled(before) && count(Parity::isLog, 6) == 1 && chopEntries() == 0,
            "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
        finish(160);
        cfg("chop_curve", 10);

        // the crack is sent again every 200 ticks; an entry whose log is gone drops
        chopTree(5);
        mine(0, 0, 0);
        mine(0, 0, 0);
        int sent = Chops.cracks;
        tick(210);
        check("the crack is re-sent every 200 ticks", Chops.cracks > sent, "cracks " + sent + " -> " + Chops.cracks);
        cmd("setblock " + at(0, 0, 0) + " minecraft:stone");
        tick(205);
        check("an entry whose last log is gone drops with its crack", chopEntries() == 0 && Chops.lastStage == -1, "entries " + chopEntries() + ", stage " + Chops.lastStage);

        // progress survives a world close
        chopTree(5);
        mine(0, 0, 0);
        mine(0, 0, 0);
        reopen(false);
        hold("minecraft:iron_axe");
        long[] saved = on(() -> Chops.progress(level));
        check("progress survives reopening the world", saved != null && saved[0] == 2 && saved[1] == 4 && saved[2] == p(0, 0, 0).asLong(), "progress " + java.util.Arrays.toString(saved));
        before = on(() -> Falls.lastFall);
        mine(0, 0, 0);
        mine(0, 0, 0);
        tick(2);
        check("after the reopen two more chops fell the tree", felled(before) && count(Parity::isLog, 6) == 0 && chopEntries() == 0, "logs " + count(Parity::isLog, 6) + ", entries " + chopEntries());
        finish(160);

        // a giant spruce takes the curve's count
        plot();
        on(() -> {
            Chops.clear(level);
            return null;
        });
        feature("mega_spruce", 0, 0);
        tick(40);
        hold("minecraft:iron_axe");
        int giant = count(Parity::isLog, 10);
        before = on(() -> Falls.lastFall);
        int n = 0;
        while (n < 80 && !felled(before)) {
            mine(0, 0, 0);
            n++;
        }
        check("a giant spruce of " + giant + " logs takes " + Chops.needed(giant, 10) + " chops", n == Chops.needed(giant, 10), n + " chops");
        finish(200);

        // a vanilla viewer's mask: one per player, the log at 0.994 inside the cell, gone after 60 idle ticks
        chopTree(5);
        BlockPos first = p(0, 1, 0), second = p(0, 2, 0);
        mineAction(first, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, false);
        tick(2);
        Display.BlockDisplay mask = on(() -> Chops.maskOf(player));
        boolean shaped = mask != null && ((io.github.profetgit.vanillatimber.mixin.BlockDisplayAccess) mask).timber$getBlockState().is(net.minecraft.world.level.block.Blocks.OAK_LOG) && Math.abs(maskScale(mask) - 0.994) < 0.001
            && mask.getX() == first.getX() && mask.getY() == first.getY() && mask.getZ() == first.getZ() && Displays.ours(mask);
        check("mining a log in chop mode as a vanilla viewer spawns the mask at 0.994 in its cell", shaped, mask == null ? "no mask" : ((io.github.profetgit.vanillatimber.mixin.BlockDisplayAccess) mask).timber$getBlockState() + " scale " + maskScale(mask) + " at " + mask.position());
        mineAction(second, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, false);
        tick(2);
        Display.BlockDisplay moved = on(() -> Chops.maskOf(player));
        int shown = on(() -> level.getEntitiesOfClass(Display.BlockDisplay.class, new AABB(p(0, 0, 0)).inflate(8), Displays::ours).size());
        check("one mask per player: starting another log moves it", moved != null && moved != mask && mask.isRemoved() && moved.getY() == second.getY() && shown == 1, "masks " + shown);
        mineAction(second, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, false);
        tick(70);
        check("the mask goes 60 ticks after the player stops", on(() -> Chops.maskOf(player)) == null, "still there");
        on(() -> {
            Viewers.test = 0;
            return null;
        });
        mineAction(first, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, false);
        tick(2);
        if (on(() -> Viewers.local(player))) check("a modded viewer gets no mask", on(() -> Chops.maskOf(player)) == null, "mask spawned");
        else info("chops: the test client never said Hello, modded-viewer mask check skipped");
        mineAction(first, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, false);
        on(() -> {
            Viewers.test = Viewers.VANILLA;
            return null;
        });

        // the modded client learns the mode and keeps the log it would have predicted away
        chopTree(5);
        BlockPos at = p(0, 0, 0);
        tick(30);
        boolean[] keeps = new boolean[2];
        CompletableFuture<Void> got = new CompletableFuture<>();
        client(mc -> {
            keeps[0] = io.github.profetgit.vanillatimber.client.ChopClient.keeps(at);
            got.complete(null);
        });
        await(got);
        hold("minecraft:iron_pickaxe");
        tick(10);
        CompletableFuture<Void> got2 = new CompletableFuture<>();
        client(mc -> {
            keeps[1] = io.github.profetgit.vanillatimber.client.ChopClient.keeps(at);
            got2.complete(null);
        });
        await(got2);
        check("the modded client keeps an axe-chopped log and not a pickaxe-broken one", keeps[0] && !keeps[1], "axe " + keeps[0] + ", pickaxe " + keeps[1]);
        cfg("chops", 0);
        tick(30);
        CompletableFuture<Void> got3 = new CompletableFuture<>();
        client(mc -> {
            keeps[0] = io.github.profetgit.vanillatimber.client.ChopClient.keeps(at);
            got3.complete(null);
        });
        await(got3);
        check("with chops 0 the modded client predicts as vanilla", !keeps[0], "kept " + keeps[0]);

        // the log a player chops shrinks: one box per cut, the old save format, the payload to a modded viewer
        cfg("chops", 1);
        chopTree(6);
        mine(0, 1, 0);
        mine(0, 1, 0);
        mine(0, 2, 0);
        int needed = on(() -> (int) Chops.cutAt(level, p(0, 1, 0))[1]);
        int b1 = on(() -> Chops.cellBox(level, p(0, 1, 0))), b2 = on(() -> Chops.cellBox(level, p(0, 2, 0))), b0 = on(() -> Chops.cellBox(level, p(0, 0, 0)));
        int s1 = io.github.profetgit.vanillatimber.core.Cut.step(2, needed), s2 = io.github.profetgit.vanillatimber.core.Cut.step(1, needed);
        boolean boxed = b1 == io.github.profetgit.vanillatimber.core.Cut.pack(s1, 16 - s1, 0, 16, s1, 16 - s1) && b2 == io.github.profetgit.vanillatimber.core.Cut.pack(s2, 16 - s2, 0, 16, s2, 16 - s2)
            && b0 == io.github.profetgit.vanillatimber.core.Cut.WHOLE;
        check("a 1x1 cut shrinks around the log's centre: 16 - 2 x round(6 n / (needed - 1)) per height", boxed,
            "boxes " + boxText(b1) + " " + boxText(b2) + " " + boxText(b0) + ", steps " + s1 + " " + s2 + ", needed " + needed);
        java.util.List<long[]> old = io.github.profetgit.vanillatimber.server.ChopData.selfTestOldFormat(
            "{\"trees\":[{\"base\":1,\"last\":" + p(0, 2, 0).asLong() + ",\"chops\":3,\"needed\":9,\"cells\":[{\"pos\":" + p(0, 1, 0).asLong() + ",\"n\":2},{\"pos\":"
                + p(0, 2, 0).asLong() + ",\"n\":1}]},{\"base\":2,\"last\":5,\"chops\":4,\"needed\":9}]}");
        check("entries saved before the layers read as a cut at the last cell's height", old.size() == 2 && old.get(0).length == 2 && old.get(0)[0] == 3 && old.get(0)[1] == p(0, 2, 0).asLong()
            && old.get(1).length == 2 && old.get(1)[0] == 4 && old.get(1)[1] == 5, old.stream().map(java.util.Arrays::toString).toList().toString());
        on(() -> {
            Viewers.test = 0;
            return null;
        });
        if (on(() -> Viewers.local(player))) {
            int before2 = Chops.cellMessages;
            mine(0, 3, 0);
            tick(5);
            Map<Long, Integer> seen = clientBoxes();
            check("a modded viewer receives the new cut's box at once", Chops.cellMessages > before2 && seen.get(p(0, 3, 0).asLong()) != null,
                "messages " + before2 + " -> " + Chops.cellMessages + ", client " + seen);
            tick(205);
            Map<Long, Integer> healed = clientBoxes();
            check("the 200-tick resend gives it every cut of the level", healed.size() == 3 && b1 == healed.getOrDefault(p(0, 1, 0).asLong(), 0), "client " + healed);
        } else info("chops: the test client never said Hello, cut-cell payload check skipped");
        on(() -> {
            Viewers.test = Viewers.VANILLA;
            return null;
        });
    }
}
