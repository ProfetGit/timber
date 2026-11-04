package io.github.profetgit.vanillatimber.selftest;

import io.github.profetgit.vanillatimber.core.FallSpec;
import io.github.profetgit.vanillatimber.server.Falls;
import io.github.profetgit.vanillatimber.server.Tree;
import io.github.profetgit.vanillatimber.server.Fall;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import io.github.profetgit.vanillatimber.platform.Platform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

final class Scenarios {
    private Scenarios() {}

    static Script build() {
        Script s = new Script();
//? if >=26.1 {
        s.run(mc -> mc.createWorldOpenFlows().createFreshLevel("vanilla_timbertest-" + System.currentTimeMillis(),
                new LevelSettings("vanilla_timbertest", GameType.SURVIVAL,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false), WorldPresets::createTestWorldDimensions, mc.gui.screen()))
//?} else if >=1.21 {
/*        s.run(mc -> mc.createWorldOpenFlows().createFreshLevel("vanilla_timbertest-" + System.currentTimeMillis(),
                new LevelSettings("vanilla_timbertest", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, freshRules(),
                    WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false),
                access -> access.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.gui.screen()))
*///?} else {
/*        s.run(mc -> mc.createWorldOpenFlows().createFreshLevel("vanilla_timbertest-" + System.currentTimeMillis(),
                new LevelSettings("vanilla_timbertest", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, freshRules(),
                    WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()))
*///?}
            .until("world to load", 2400, mc -> mc.player != null && mc.level != null && mc.gui.screen() == null)
            .waitTicks(40)
            .check("world loaded", mc -> mc.player.isAlive() ? null : "player not alive");
        String only = System.getProperty("vanilla_timber.selftest.scene", "all");
        if (only.contains("capture")) capture(s);
        if (only.equals("all") || only.contains("smoke")) smoke(s);
        if (only.equals("all") || only.contains("scan")) scan(s);
        if (only.equals("all") || only.contains("fall")) fall(s);
        if (only.equals("all") || only.contains("mesh")) mesh(s);
        if (only.equals("all") || only.contains("sync")) sync(s);
        if (only.contains("perfmeshoff")) s.run(mc -> io.github.profetgit.vanillatimber.client.GroundShadow.on = false);
        if (only.contains("perfmesh")) perfMesh(s);
        if (only.contains("parity")) {
            String groups = System.getProperty("vanilla_timber.selftest.groups", "all");
            s.server(server -> Parity.begin(server, groups)).until("parity groups", 20 * 60 * 60, mc -> Parity.finished());
        }
        return s;
    }

    /** One 1.3.6 sampler fixture: tree file, x offset from the sampler's spot, and the start it recorded. */
    record Fix(String name, String file, int dx, int hit, int dur, int h, double amp, int km, double fd, int ft, int dr,
               int rs, int px, int py, int pz) {}

    // dev/m1/sample136.py: tree at (8, -60, 8), chopped there by a player at yaw -90 (falls at 303.75)
//? if >=1.21.2 <26.1 {
/*    private static net.minecraft.world.level.gamerules.GameRules freshRules() {
        return new net.minecraft.world.level.gamerules.GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
    }

*///?} else if <1.21.2 {
/*    private static net.minecraft.world.level.gamerules.GameRules freshRules() {
        return new net.minecraft.world.level.gamerules.GameRules();
    }

*///?}
    static final Fix[] FIXES = {
        new Fix("oak", "oak_c", 0, 93, 11, 7, 10.80, 4, 1, 1, 0, 2, 8908, -58998, 8772),
        new Fix("birch", "birch_a", 48, 93, 12, 8, 9.45, 5, 1, 1, 0, 3, 8908, -58998, 8772),
        new Fix("spruce", "spruce_b", 96, 90, 21, 26, 2.90, 24, 0, 1, 0, 9, 9824, -59998, 9550),
    };

    /**
     * Places a sampler fixture tree with its base at (x, -60, 8) on the sampler's ground: grass at y -61 and open air
     * over every cell its fall can sweep (the self-test world is solid there).
     */
    static void plant(MinecraftServer server, String file, int x) {
        CommandSourceStack root = server.createCommandSourceStack().withSuppressedOutput();
        String box = (x - 8) + " %d -12 " + (x + 27) + " %d 28 ";
        server.getCommands().performPrefixedCommand(root, "fill " + box.formatted(-61, -61) + "minecraft:grass_block");
        server.getCommands().performPrefixedCommand(root, "fill " + box.formatted(-60, -46) + "minecraft:air");
        server.getCommands().performPrefixedCommand(root, "fill " + box.formatted(-45, -30) + "minecraft:air");
        CommandSourceStack src = root.withPosition(new Vec3(x, -60, 8));
        try (var in = Scenarios.class.getResourceAsStream("/selftest/trees/" + file + ".mcfunction")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) server.getCommands().performPrefixedCommand(src, fixtureLine(line));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("fixture " + file, e);
        }
    }

    /** A fixture line: they use 1.21.5's "setblock ... strict"; older versions only know replace (as the 1.3.6 pack's 1.21.4 build). */
    static String fixtureLine(String line) {
        String s = line.trim();
//? if <1.21.5 {
        /*if (s.endsWith(" strict")) s = s.substring(0, s.length() - 7) + " replace";*/
//?}
        return s;
    }

    /** Chops the log at (x, -60, 8) as the sampler's player: survival, netherite axe, yaw -90. */
    static void chop(MinecraftServer server, int x) {
        ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_AXE));
        p.setYRot(-90);
        p.setXRot(20);
        p.gameMode.destroyBlock(new BlockPos(x, -60, 8));
    }

    static final String[] FEATURES = {"oak", "fancy_oak", "birch", "super_birch_bees", "spruce", "pine", "mega_spruce", "mega_pine",
        "jungle_tree", "mega_jungle_tree", "acacia", "dark_oak", "cherry", "pale_oak", "pale_oak_creaking", "azalea_tree", "mangrove",
        "tall_mangrove", "swamp_oak", "red_poplar", "orange_poplar", "yellow_poplar"};

    static <T extends Comparable<T>> String propValue(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    static String stateString(BlockState s) {
        StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        List<Property<?>> props = new ArrayList<>(s.getProperties());
        props.sort(java.util.Comparator.comparing(Property::getName));
        for (int i = 0; i < props.size(); i++) {
            sb.append(i == 0 ? '[' : ',').append(props.get(i).getName()).append('=').append(propValue(s, props.get(i)));
        }
        return props.isEmpty() ? sb.toString() : sb.append(']').toString();
    }

    /** First log in the origin column, 0..7 blocks over the ground, or null. */
    static BlockPos baseLog(ServerLevel level) {
        for (int y = -60; y <= -53; y++) {
            if (level.getBlockState(new BlockPos(8, y, 8)).is(BlockTags.LOGS)) return new BlockPos(8, y, 8);
        }
        return null;
    }

    /**
     * Grows each overworld tree feature on a cleared grass spot (the origin where the 1.3.6 sampler plants), re-rolling
     * up to 4 times until the origin column has a log, and writes it as a fixture to the out dir (f_&lt;feature&gt;.mcfunction,
     * the format of dev/fixtures/trees). Only runs when named.
     */
    private static void capture(Script s) {
        s.server(server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
            "forceload add -16 -16 48 48"));
        for (String f : FEATURES) {
            int[] tries = {0};
            s.server(server -> {
                ServerLevel level = server.overworld();
                CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput().withPosition(new Vec3(8, -60, 8));
                for (tries[0] = 1; tries[0] <= 4; tries[0]++) {
                    for (BlockPos q : BlockPos.betweenClosed(-12, -61, -12, 28, -20, 28)) {
                        level.setBlock(q, q.getY() == -61 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
                    }
                    server.getCommands().performPrefixedCommand(src, "place feature minecraft:" + f);
                    if (baseLog(level) != null) break;
                }
            })
                .waitTicks(40)
                .server(server -> {
                    ServerLevel level = server.overworld();
                    BlockPos base = baseLog(level);
                    if (base == null) {
                        SelfTest.record("capture " + f, false, "no log in the origin column after " + Math.min(tries[0], 4) + " tries");
                        return;
                    }
                    List<String> lines = new ArrayList<>();
                    int logs = 0, leaves = 0, other = 0, below = 0;
                    for (BlockPos q : BlockPos.betweenClosed(-12, base.getY() - 1, -12, 28, -20, 28)) {
                        BlockState st = level.getBlockState(q);
                        if (st.isAir() || (q.getY() < base.getY() && st.is(Blocks.GRASS_BLOCK))) continue;
                        if (q.getY() < base.getY()) below++;
                        else if (st.is(BlockTags.LOGS)) logs++;
                        else if (st.is(BlockTags.LEAVES)) leaves++;
                        else other++;
                        lines.add("setblock ~" + (q.getX() - base.getX()) + " ~" + (q.getY() - base.getY()) + " ~" + (q.getZ() - base.getZ())
                            + " " + stateString(st) + " strict");
                    }
                    try {
                        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("vanilla_timber.selftest"),
                            "f_" + f + ".mcfunction"), String.join("\n", lines) + "\n");
                    } catch (java.io.IOException e) {
                        SelfTest.record("capture " + f, false, e.toString());
                        return;
                    }
                    SelfTest.record("capture " + f, logs > 0, "try " + tries[0] + ", base y " + base.getY() + ", " + logs + " logs, "
                        + leaves + " leaves, " + other + " other, " + below + " below base");
                });
        }
    }

    /**
     * The scan scene's fixtures: the three lab trees, then one per tree feature that has a 1.3.6 start beside its
     * fixture (f_&lt;feature&gt;.start.json, written by dev/m1/sample136.py from fixtures/136/f_&lt;feature&gt;.json).
     */
    static List<Fix> scanFixes() {
        List<Fix> all = new ArrayList<>(List.of(FIXES));
        for (String f : FEATURES) {
            try (var in = Scenarios.class.getResourceAsStream("/selftest/trees/f_" + f + ".start.json")) {
                if (in == null) continue;
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                com.google.gson.JsonArray pos = o.getAsJsonArray("pos");
                all.add(new Fix("f_" + f, "f_" + f, 0, o.get("hit").getAsInt() / 100, o.get("dur").getAsInt(), o.get("h").getAsInt(),
                    o.get("b").getAsDouble() / 100, o.get("km").getAsInt(), o.get("fd").getAsDouble() / 1000, o.get("ft").getAsInt(),
                    o.get("dr").getAsInt(), o.get("rs").getAsInt(), (int) Math.round(pos.get(0).getAsDouble() * 1000),
                    (int) Math.round(pos.get(1).getAsDouble() * 1000), (int) Math.round(pos.get(2).getAsDouble() * 1000)));
            } catch (java.io.IOException e) {
                throw new IllegalStateException("start of " + f, e);
            }
        }
        return all;
    }

    private static void scan(Script s) {
        s.server(server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
            "forceload add -16 -16 160 32"));
        for (Fix f : scanFixes()) {
            s.server(server -> {
                Falls.last = null;
                plant(server, f.file, 8 + f.dx);
            })
                .waitTicks(5)
                .server(server -> chop(server, 8 + f.dx))
                .check("scan " + f.name + " found", mc -> Falls.last != null ? null : "no tree scanned")
                .check("scan " + f.name + " pivot", mc -> {
                    Tree t = Falls.last;
                    if (t == null) return "no tree";
                    int px = f.px + f.dx * 1000;
                    return t.px() == px && t.py() == f.py && t.pz() == f.pz && t.bin() == 27 ? null
                        : "pivot " + t.px() + "," + t.py() + "," + t.pz() + " bin " + t.bin() + ", 1.3.6 " + px + "," + f.py + "," + f.pz + " bin 27";
                })
                .check("scan " + f.name + " spec", mc -> {
                    Tree t = Falls.last;
                    if (t == null) return "no tree";
                    FallSpec p = t.spec();
                    boolean ok = p.h() == f.h && p.hit() == f.hit && p.dur() == f.dur && Math.abs(p.amp() - f.amp) < 0.006
                        && p.km() == f.km && Math.abs(p.fd() - f.fd) < 1e-9 && p.ft() == f.ft && p.dr() == f.dr && p.rs() == f.rs;
                    return ok ? null : p + " vs 1.3.6 h=" + f.h + " hit=" + f.hit + " dur=" + f.dur + " amp=" + f.amp + " km=" + f.km
                        + " fd=" + f.fd + " ft=" + f.ft + " dr=" + f.dr + " rs=" + f.rs;
                })
                .check("scan " + f.name + " ring mask", mc -> {
                    Tree t = Falls.last;
                    if (t == null) return "no tree";
                    for (boolean b : t.logRings()) if (!b) return "ring without a log: " + java.util.Arrays.toString(t.logRings());
                    return t.logRings().length == f.km + 1 ? null : "mask length " + t.logRings().length;
                });
            if (f.dx == 0) s.waitTicks(100);
        }
    }

    /** Sea-lantern walls and ceiling around a fixture's box (the self-test world is solid, so its boxes are dark). */
    static void light(MinecraftServer server, int x) {
        CommandSourceStack root = server.createCommandSourceStack().withSuppressedOutput();
        String[] walls = {(x - 9) + " -60 -13 " + (x - 9) + " -30 29", (x + 28) + " -60 -13 " + (x + 28) + " -30 29",
            (x - 9) + " -60 -13 " + (x + 28) + " -30 -13", (x - 9) + " -60 29 " + (x + 28) + " -30 29",
            (x - 9) + " -29 -13 " + (x + 28) + " -29 29"};
        for (String w : walls) server.getCommands().performPrefixedCommand(root, "fill " + w + " minecraft:sea_lantern");
    }

    static int clientDisplays(net.minecraft.client.Minecraft mc) {
        int c = 0;
        for (var e : mc.level.entitiesForRendering()) if (e instanceof Display.BlockDisplay) c++;
        return c;
    }

    static double percentile(List<Double> v, double q) {
        if (v.isEmpty()) return Double.NaN;
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(Math.min(s.size() - 1, (int) Math.floor(q * (s.size() - 1) + 0.5)));
    }

    /**
     * A modded viewer (this client said Hello): the server sends the payload and no displays, the client builds the same
     * timeline and draws the mesh until the displays would have gone. Frames for the flicker check; render time per frame.
     */
    private static void mesh(Script s) {
        s.waitTicks(40).server(server -> server.getCommands().performPrefixedCommand(
            server.createCommandSourceStack().withSuppressedOutput(), "forceload add -16 -16 160 32"));
        s.check("mesh hello reached the server", mc -> {
            ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            return io.github.profetgit.vanillatimber.server.Viewers.local(p) ? null : "no Hello from " + p.getName().getString();
        });
        for (Fix f : FIXES) {
            int x = 8 + f.dx;
            List<double[]> frames = Collections.synchronizedList(new ArrayList<>());
            List<Double> ms = Collections.synchronizedList(new ArrayList<>());
            int[] serverDisplays = {0}, clientMax = {0}, end = {-1};
            s.server(server -> {
                Falls.lastFall = null;
                plant(server, f.file, x);
                light(server, x);
                CommandSourceStack root = server.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().performPrefixedCommand(root, "kill @e[type=item]");
                server.getCommands().performPrefixedCommand(root, "tp @p " + (x - 4.5) + " -60 21.8 facing " + (x + 3) + " -55 10");
            })
                .waitTicks(30)
                .run(mc -> {
                    io.github.profetgit.vanillatimber.client.ClientFalls.last = null;
                    io.github.profetgit.vanillatimber.client.ClientFalls.spent = 0;
                    SelfTest.frameHook = m -> {
                        double p = io.github.profetgit.vanillatimber.client.ClientFalls.drawnPitch();
                        long ns = io.github.profetgit.vanillatimber.client.ClientFalls.spent;
                        io.github.profetgit.vanillatimber.client.ClientFalls.spent = 0;
                        if (!Double.isNaN(p)) {
                            frames.add(new double[] {io.github.profetgit.vanillatimber.client.ClientFalls.now(), p,
                                io.github.profetgit.vanillatimber.client.ClientFalls.drawnTau()});
                            ms.add(ns / 1e6);
                        }
                        clientMax[0] = Math.max(clientMax[0], clientDisplays(m));
                    };
                })
                .capture("mesh_" + f.name, 240)
                .server(server -> {
                    Fall.probe = fl -> {
                        serverDisplays[0] = Math.max(serverDisplays[0], fl.displays());
                        end[0] = fl.n >= fl.tl.end ? fl.n : -1;
                    };
                    chop(server, x);
                })
                .until("mesh " + f.name + " payload", 100, mc -> io.github.profetgit.vanillatimber.client.ClientFalls.last != null)
                .check("mesh " + f.name + " payload", mc -> {
                    var msg = io.github.profetgit.vanillatimber.client.ClientFalls.last;
                    Fall fl = Falls.lastFall;
                    if (fl == null) return "no fall on the server";
                    long solid = fl.tree.parts().stream().filter(p -> !p.state().isAir()).count();
                    return msg.blocks() == solid && msg.e() == 0 && msg.id() == fl.id ? null
                        : msg.blocks() + " blocks (server " + solid + "), e " + msg.e() + ", id " + msg.id() + " vs " + fl.id;
                })
                .check("mesh " + f.name + " same timeline", mc -> {
                    var a = io.github.profetgit.vanillatimber.client.ClientFalls.timeline();
                    var b = Falls.lastFall.tl;
                    if (a == null) return "no client fall";
                    if (a.end != b.end || a.leavesGone != b.leavesGone || !java.util.Arrays.equals(a.popTick, b.popTick)) {
                        return "end " + a.end + "/" + b.end + " leaves " + a.leavesGone + "/" + b.leavesGone;
                    }
                    for (int i = 0; i <= a.end; i++) {
                        if (a.p[i] != b.p[i] || a.bn[i] != b.bn[i] || a.gy[i] != b.gy[i] || a.dy[i] != b.dy[i]) return "frame " + i + " differs";
                    }
                    return null;
                })
                .check("mesh " + f.name + " baked", mc -> io.github.profetgit.vanillatimber.client.ClientFalls.quads() > 0 ? null : "no quads")
                .until("mesh " + f.name + " to finish", 600, mc -> end[0] >= 0)
                .waitTicks(30)
                .run(mc -> SelfTest.frameHook = null)
                .server(server -> Fall.probe = null)
                .check("mesh " + f.name + " no displays", mc -> serverDisplays[0] == 0 && clientMax[0] == 0 ? null
                    : "server spawned " + serverDisplays[0] + ", client saw " + clientMax[0])
                .check("mesh " + f.name + " drawn to the slam", mc -> {
                    double max = frames.stream().mapToDouble(r -> r[1]).max().orElse(Double.NaN);
                    double hit = Falls.lastFall.tl.spec.hit();
                    return max >= hit - 0.5 ? null : "max pitch " + max + ", hit " + hit + ", " + frames.size() + " frames";
                })
                .check("mesh " + f.name + " gone at the end", mc -> {
                    double last = frames.isEmpty() ? Double.NaN : frames.getLast()[2];
                    int e = Falls.lastFall.tl.end;
                    return io.github.profetgit.vanillatimber.client.ClientFalls.active() == 0 && last < e - 1 && last >= e - 2.2 ? null
                        : "active " + io.github.profetgit.vanillatimber.client.ClientFalls.active() + ", last drawn tau " + last + " (end " + e + ")";
                })
                .run(mc -> SelfTest.record("mesh " + f.name + " frame ms", true, String.format(java.util.Locale.ROOT,
                    "p50 %.3f p95 %.3f max %.3f over %d frames, %d quads, bake %.2f ms", percentile(ms, 0.5), percentile(ms, 0.95),
                    percentile(ms, 1.0), ms.size(), io.github.profetgit.vanillatimber.client.ClientFalls.quads(),
                    io.github.profetgit.vanillatimber.client.ClientFalls.bakeNanos / 1e6)));
        }
    }

    /**
     * PRD 10 client budgets on a big tree (216 logs + ~1130 leaves (the most the 26-step ownership and max_blocks 1200 allow)): per fall the mesh
     * bake (payload handler) and the mod's render-thread ms per frame, warm = the runs after the first.
     */
    private static void perfMesh(Script s) {
        s.waitTicks(40).server(server -> {
            CommandSourceStack root = server.createCommandSourceStack().withSuppressedOutput();
            for (String c : new String[] {"timber config max_logs 1024", "timber config max_blocks 1200", "timber config display_cap 4000",
                    "gamerule random_tick_speed 0", "forceload add -16 -16 160 32"}) {
                server.getCommands().performPrefixedCommand(root, c);
            }
        });
        List<Double> bake = Collections.synchronizedList(new ArrayList<>()), frameMean = new ArrayList<>(), frameP50 = new ArrayList<>(), frameP95 = new ArrayList<>(),
            frameMax = new ArrayList<>();
        int[] quads = {0}, blocks = {0};
        for (int k0 = 0; k0 < 4; k0++) {
            final int k = k0;
            int x = 8;
            List<Double> ms = Collections.synchronizedList(new ArrayList<>());
            int[] end = {-1};
            s.server(server -> {
                Falls.lastFall = null;
                CommandSourceStack root = server.createCommandSourceStack().withSuppressedOutput();
                String box = (x - 12) + " %d -4 " + (x + 16) + " %d 20 ";
                server.getCommands().performPrefixedCommand(root, "fill " + box.formatted(-61, -61) + "minecraft:grass_block");
                server.getCommands().performPrefixedCommand(root, "fill " + box.formatted(-60, -30) + "minecraft:air");
                server.getCommands().performPrefixedCommand(root, "fill " + (x - 1) + " -60 7 " + (x + 1) + " -37 9 minecraft:oak_log");
                server.getCommands().performPrefixedCommand(root, "fill " + (x - 4) + " -45 3 " + (x + 5) + " -35 13 minecraft:oak_leaves[persistent=false,distance=7] replace minecraft:air");
                light(server, x);
                server.getCommands().performPrefixedCommand(root, "kill @e[type=item]");
                server.getCommands().performPrefixedCommand(root, "tp @p " + (x - 6.5) + " -60 24 facing " + (x + 3) + " -45 8");
            })
                .waitTicks(30)
                .run(mc -> {
                    io.github.profetgit.vanillatimber.client.ClientFalls.last = null;
                    io.github.profetgit.vanillatimber.client.ClientFalls.spent = 0;
                    SelfTest.frameHook = m -> {
                        double p = io.github.profetgit.vanillatimber.client.ClientFalls.drawnPitch();
                        long ns = io.github.profetgit.vanillatimber.client.ClientFalls.spent;
                        io.github.profetgit.vanillatimber.client.ClientFalls.spent = 0;
                        if (!Double.isNaN(p)) ms.add(ns / 1e6);
                    };
                })
                .capture("perfmesh_" + k, k == 3 ? 300 : 1)
                .server(server -> {
                    Fall.probe = fl -> end[0] = fl.n >= fl.tl.end ? fl.n : -1;
                    chop(server, x);
                })
                .until("perfmesh " + k + " payload", 100, mc -> io.github.profetgit.vanillatimber.client.ClientFalls.last != null)
                .until("perfmesh " + k + " to finish", 1200, mc -> end[0] >= 0)
                .waitTicks(20)
                .run(mc -> {
                    SelfTest.frameHook = null;
                    var msg = io.github.profetgit.vanillatimber.client.ClientFalls.last;
                    blocks[0] = msg == null ? 0 : msg.blocks();
                    quads[0] = io.github.profetgit.vanillatimber.client.ClientFalls.quads();
                    SelfTest.record("perfmesh " + k + " raw", true, String.format(java.util.Locale.ROOT, "%d blocks, bake %.2f ms, frame p50 %.3f p95 %.3f max %.3f over %d frames",
                        blocks[0], io.github.profetgit.vanillatimber.client.ClientFalls.bakeNanos / 1e6, percentile(ms, 0.5), percentile(ms, 0.95), percentile(ms, 1.0), ms.size()));
                    if (k > 0) {
                        bake.add(io.github.profetgit.vanillatimber.client.ClientFalls.bakeNanos / 1e6);
                        frameMean.add(ms.stream().mapToDouble(Double::doubleValue).average().orElse(0));
                        frameP50.add(percentile(ms, 0.5));
                        frameP95.add(percentile(ms, 0.95));
                        frameMax.add(percentile(ms, 1.0));
                    }
                })
                .server(server -> Fall.probe = null);
        }
        s.check("perfmesh: big tree fell (>= 1300 blocks)", mc -> blocks[0] >= 1300 ? null : blocks[0] + " blocks")
            .check("perfmesh: bake <= 10 us per block warm (median of 3; PRD 10's 4 ms re-set, Calls link 17)", mc -> {
                double v = percentile(bake, 0.5);
                return v * 1000 / blocks[0] <= 10 ? null : String.format(java.util.Locale.ROOT, "bake median %.2f ms for %d blocks = %.1f us per block, %d quads",
                    v, blocks[0], v * 1000 / blocks[0], quads[0]);
            })
            .check("perfmesh: mean frame ms scaled to 2000 blocks <= 1 ms (median of 3)", mc -> {
                double v = percentile(frameMean, 0.5) * 2000 / blocks[0];
                return v <= 1 ? null : String.format(java.util.Locale.ROOT, "mean %.3f ms for %d blocks (x%.2f to 2000 = %.3f), p95 %.3f, %d quads",
                    percentile(frameMean, 0.5), blocks[0], 2000.0 / blocks[0], v, percentile(frameP95, 0.5), quads[0]);
            })
            .run(mc -> SelfTest.record("perfmesh summary", true, String.format(java.util.Locale.ROOT,
                "%d blocks, %d quads: bake median %.2f ms (all %s), frame mean %.3f p50 %.3f p95 %.3f max %.3f ms", blocks[0], quads[0], percentile(bake, 0.5), bake,
                percentile(frameMean, 0.5), percentile(frameP50, 0.5), percentile(frameP95, 0.5), percentile(frameMax, 0.5))));
    }

    /**
     * A display's pitch and model matrix as the renderer draws it this frame (DisplayRenderer.submit), or with
     * {@code pt} 1 the pose it is heading for: its last update fully reached (the server frame it belongs to).
     */
    static org.joml.Matrix4f drawn(Display.BlockDisplay d, float pt, double[] pitch) {
        Display.RenderState rs = d.renderState();
        if (rs == null) return null;
        com.mojang.math.Transformation tr = rs.transformation().get(pt >= 1 ? 1 : d.calculateInterpolationProgress(pt));
        Vec3 pos = d.getPosition(pt);
//? if >=1.21.4 {
        float yr = d.getYRot(pt), xr = d.getXRot(pt);
//?} else {
/*        float yr = net.minecraft.util.Mth.rotLerp(pt, d.yRotO, d.getYRot()), xr = net.minecraft.util.Mth.lerp(pt, d.xRotO, d.getXRot());
*///?}
        org.joml.Quaternionfc l = tr.leftRotation();
        pitch[0] = xr + Math.toDegrees(2 * Math.atan2(l.x(), l.w()));
        return new org.joml.Matrix4f().translation((float) pos.x, (float) pos.y, (float) pos.z)
            .rotateYXZ((float) Math.toRadians(-yr), (float) Math.toRadians(xr), 0).mul(tr.getMatrix());
    }

    /**
     * Both drawings in one client (the server sends this modded viewer the displays as well). Three numbers per frame:
     * <ul>
     * <li>clock: the slam (first frame at hit - 0.5 degrees, median display pitch) on each; PRD 13 wants the drift
     * within 1 tick;</li>
     * <li>geometry: where each display is heading (its last update fully reached) against the mesh at that server
     * frame, popping rings left out (their pop keys run over 3/1/2 ticks): the same pose maths, so ~0;</li>
     * <li>as drawn: each display's centre against the nearest mesh block this frame. The mesh copies the displays'
     * trail (core.DrawnPose), so what is left is this client's tick phase against the mesh's average-client clock
     * (MeshFall.LAG): the tree's speed times the clock offset (0.0.8: ~0.03 blocks at the tick-5 key, up to a block
     * on the giant spruce's tip mid-fall).</li>
     * </ul>
     */
    private static void sync(Script s) {
        s.waitTicks(40).server(server -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -16 -16 160 32");
            io.github.profetgit.vanillatimber.server.Viewers.test = io.github.profetgit.vanillatimber.server.Viewers.BOTH;
        });
        for (Fix f : FIXES) {
            int x = 8 + f.dx;
            // per frame: client time, mesh pitch, display pitch (median; NaN without displays), drawn gap, displays,
            // mesh blocks, heading-for gap, its frame offset from the mesh's, mesh tau
            List<double[]> frames = Collections.synchronizedList(new ArrayList<>());
            int[] end = {-1};
            s.server(server -> {
                Falls.lastFall = null;
                plant(server, f.file, x);
                light(server, x);
                CommandSourceStack root = server.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().performPrefixedCommand(root, "kill @e[type=item]");
                server.getCommands().performPrefixedCommand(root, "tp @p " + (x - 4.5) + " -60 21.8 facing " + (x + 3) + " -55 10");
            })
                .waitTicks(30)
                .run(mc -> {
                    io.github.profetgit.vanillatimber.client.ClientFalls.last = null;
                    double[] mesh = new double[3 * 4096], at = new double[3 * 4096], pitch = new double[1];
                    int[] rings = new int[4096];
                    double[] ps = new double[4096], dc = new double[3 * 4096];
                    org.joml.Vector3f c = new org.joml.Vector3f();
                    SelfTest.frameHook = m -> {
                        double mp = io.github.profetgit.vanillatimber.client.ClientFalls.drawnPitch();
                        if (Double.isNaN(mp)) return;
                        double tau = io.github.profetgit.vanillatimber.client.ClientFalls.drawnTau();
                        float pt = m.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                        int nm = io.github.profetgit.vanillatimber.client.ClientFalls.centres(mesh);
                        double gap = 0;
                        int nd = 0;
                        for (var e : m.level.entitiesForRendering()) {
                            if (!(e instanceof Display.BlockDisplay d) || nd >= ps.length) continue;
                            org.joml.Matrix4f mat = drawn(d, pt, pitch);
                            if (mat == null) continue;
                            ps[nd] = pitch[0];
                            mat.transformPosition(0.5f, 0.5f, 0.5f, c);
                            if (nm > 0) gap = Math.max(gap, Math.sqrt(nearest(mesh, nm, c.x, c.y, c.z, null)));
                            drawn(d, 1, pitch).transformPosition(0.5f, 0.5f, 0.5f, c);
                            dc[nd * 3] = c.x;
                            dc[nd * 3 + 1] = c.y;
                            dc[nd * 3 + 2] = c.z;
                            nd++;
                        }
                        double dp = Double.NaN, best = Double.NaN, prog = Double.NaN;
                        int off = 0;
                        if (nd > 0) {
                            double[] sorted = java.util.Arrays.copyOf(ps, nd);
                            java.util.Arrays.sort(sorted);
                            dp = sorted[nd / 2];
                            for (var e : m.level.entitiesForRendering()) {
                                if (e instanceof Display.BlockDisplay d && d.renderState() != null) {
                                    prog = d.calculateInterpolationProgress(pt);
                                    break;
                                }
                            }
                            int n0 = Math.max(0, (int) Math.ceil(tau - 1e-6)), last = io.github.profetgit.vanillatimber.client.ClientFalls.timeline().end;
                            // the mesh's own frame first: a neighbour wins only when strictly closer (poses repeat at rest)
                            for (int n : new int[] {n0, n0 - 1, n0 + 1}) {
                                if (n < 0 || n > last) continue;
                                int na = io.github.profetgit.vanillatimber.client.ClientFalls.centresAt(n, at, rings);
                                double worst = 0;
                                int[] hit = new int[1];
                                for (int i = 0; i < nd; i++) {
                                    double d2 = nearest(at, na, dc[i * 3], dc[i * 3 + 1], dc[i * 3 + 2], hit);
                                    if (rings[hit[0]] != -2) worst = Math.max(worst, Math.sqrt(d2));
                                }
                                if (Double.isNaN(best) || worst < best - 1e-3) {
                                    best = worst;
                                    off = n - n0;
                                }
                            }
                        }
                        // the displays' own drawing time: heading for frame n0 + off, prog of the way from the frame before
                        double tauD = nd == 0 ? Double.NaN : Math.max(0, (int) Math.ceil(tau - 1e-6)) + off - 1 + prog;
                        frames.add(new double[] {io.github.profetgit.vanillatimber.client.ClientFalls.now(), mp, dp, nd == 0 ? Double.NaN : gap, nd, nm,
                            best, off, tau, tauD});
                    };
                })
                .capture("sync_" + f.name, 240)
                .server(server -> {
                    Fall.probe = fl -> end[0] = fl.n >= fl.tl.end ? fl.n : -1;
                    chop(server, x);
                })
                .until("sync " + f.name + " to finish", 600, mc -> end[0] >= 0)
                .waitTicks(30)
                .run(mc -> SelfTest.frameHook = null)
                .server(server -> Fall.probe = null)
                .run(mc -> {
                    double hit = Falls.lastFall.tl.spec.hit();
                    double tm = cross(frames, 1, hit - 0.5), td = cross(frames, 2, hit - 0.5);
                    double drift = td - tm, maxGeo = 0, geoAt = 0;
                    List<Double> drawnGaps = new ArrayList<>();
                    int both = 0;
                    List<Double> offsets = new ArrayList<>();
                    for (double[] r : frames) {
                        if (Double.isNaN(r[2])) continue;
                        both++;
                        drawnGaps.add(r[3]);
                        if (r[8] >= 1 && !Double.isNaN(r[9])) offsets.add(Math.abs(r[9] - r[8]));
                        if (r[6] > maxGeo) {
                            maxGeo = r[6];
                            geoAt = r[0];
                        }
                    }
                    double t0 = frames.isEmpty() ? 0 : frames.getFirst()[0];
                    double shift = io.github.profetgit.vanillatimber.client.ClientFalls.anchorShift();
                    SelfTest.record("sync " + f.name + " slam drift", !Double.isNaN(drift) && Math.abs(drift) <= 1.0,
                        String.format(java.util.Locale.ROOT, "displays %.2f ticks after the mesh (slam at mesh t+%.2f), frame-1 anchor moved the clock %.2f",
                            drift, tm - t0, shift));
                    SelfTest.record("sync " + f.name + " geometry", both > 0 && maxGeo <= 0.02, String.format(java.util.Locale.ROOT,
                        "max heading-for gap %.4f blocks at t+%.2f over %d frames", maxGeo, geoAt - t0, both));
                    // the mesh keeps the average client's clock, not this client's tick phase (MeshFall.LAG): the phase puts
                    // this client's displays anywhere in +-0.5 tick of it, fixed for a run at a steady frame rate, and the
                    // arrival estimate (half the frame interval) adds frame jitter on top: 0.6 still catches a whole tick
                    double medOff = percentile(offsets, 0.5);
                    SelfTest.record("sync " + f.name + " clock offset", !offsets.isEmpty() && medOff <= 0.6, String.format(java.util.Locale.ROOT,
                        "|displays - mesh| drawing time median %.2f p95 %.2f ticks over %d frames", medOff, percentile(offsets, 0.95), offsets.size()));
                    SelfTest.record("sync " + f.name + " as drawn", true, String.format(java.util.Locale.ROOT,
                        "display-to-mesh centre gap p50 %.3f p95 %.3f max %.3f blocks (this client's phase vs the mesh clock, see Scenarios.sync)",
                        percentile(drawnGaps, 0.5), percentile(drawnGaps, 0.95), percentile(drawnGaps, 1.0)));
                    dump("sync_" + f.name, frames);
                });
        }
        s.server(server -> io.github.profetgit.vanillatimber.server.Viewers.test = 0);
    }

    /** Squared distance from (x, y, z) to the nearest of the first n centres; its index into {@code hit}[0] if given. */
    static double nearest(double[] c, int n, double x, double y, double z, int[] hit) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double dx = c[i * 3] - x, dy = c[i * 3 + 1] - y, dz = c[i * 3 + 2] - z, d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                if (hit != null) hit[0] = i;
            }
        }
        return best;
    }

    /** Client time column 0 at which column {@code col} first reaches {@code v}, linear between frames; NaN if never. */
    static double cross(List<double[]> frames, int col, double v) {
        double[] prev = null;
        for (double[] r : frames) {
            if (Double.isNaN(r[col])) continue;
            if (r[col] >= v) {
                if (prev == null) return r[0];
                double f = (v - prev[col]) / (r[col] - prev[col]);
                return prev[0] + (r[0] - prev[0]) * f;
            }
            prev = r;
        }
        return Double.NaN;
    }

    static void dump(String name, List<double[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (double[] r : rows) {
            for (int i = 0; i < r.length; i++) sb.append(i == 0 ? "" : "\t").append(String.format(java.util.Locale.ROOT, "%.4f", r[i]));
            sb.append('\n');
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("vanilla_timber.selftest"), name + ".tsv"), sb);
        } catch (java.io.IOException ignored) {
        }
    }

    static int items(MinecraftServer server, int x) {
        int c = 0;
        AABB box = new AABB(x - 8, -64, -12, x + 28, -20, 29);
        for (ItemEntity e : server.overworld().getEntitiesOfClass(ItemEntity.class, box)) c += e.getItem().getCount();
        return c;
    }

    /**
     * Each sampler tree falls on the server: displays for every block on frame 0 with the blocks still standing (only
     * the anchor cell cleared), the blocks gone on frame 1, every display gone at the timeline's end, and every rolled
     * drop in the world. Frames are captured from the side for the flicker check.
     */
    private static void fall(Script s) {
        s.waitTicks(120).server(server -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -16 -16 160 32");
            io.github.profetgit.vanillatimber.server.Viewers.test = io.github.profetgit.vanillatimber.server.Viewers.VANILLA;
        });
        for (Fix f : FIXES) {
            int x = 8 + f.dx;
            List<int[]> frames = Collections.synchronizedList(new ArrayList<>());
            int[] before = {0};
            s.server(server -> {
                Falls.lastFall = null;
                plant(server, f.file, x);
                CommandSourceStack root = server.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().performPrefixedCommand(root, "kill @e[type=item]");
                server.getCommands().performPrefixedCommand(root, "tp @p " + (x - 4.5) + " -60 21.8 facing " + (x + 3) + " -55 10");
            })
                .waitTicks(20)
                .server(server -> before[0] = items(server, x))
                .capture("fall_" + f.name, 240)
                .server(server -> {
                    Fall.probe = fl -> frames.add(new int[] {fl.n, fl.displays(), fl.standing(), fl.tl.end, fl.rolled, fl.dropped,
                        fl.tree.parts().size(), fl.tree.anchor() == null ? 0 : 1});
                    chop(server, x);
                })
                .waitTicks(4)
                .check("fall " + f.name + " displays reach the client", mc -> {
                    long c = 0;
                    for (var e : mc.level.entitiesForRendering()) if (e instanceof Display.BlockDisplay) c++;
                    return c > 0 ? null : "no block displays on the client";
                })
                .until("fall " + f.name + " to finish", 600, mc -> {
                    synchronized (frames) {
                        return !frames.isEmpty() && frames.getLast()[0] >= frames.getLast()[3];
                    }
                })
                .waitTicks(30)
                .check("fall " + f.name + " frame 0", mc -> {
                    int[] a = frames.getFirst();
                    return a[0] == 0 && a[1] == a[6] && a[2] == a[6] - a[7] ? null
                        : "n " + a[0] + ", displays " + a[1] + ", standing " + a[2] + " of " + a[6] + " (anchor " + a[7] + ")";
                })
                .check("fall " + f.name + " frame 1", mc -> {
                    int[] a = frames.get(1);
                    return a[0] == 1 && a[2] == 0 && a[1] == a[6] ? null : "n " + a[0] + ", standing " + a[2] + ", displays " + a[1];
                })
                .check("fall " + f.name + " end", mc -> {
                    int[] a = frames.getLast();
                    return a[1] == 0 && a[0] == a[3] && frames.size() == a[3] + 1 ? null
                        : "last frame " + a[0] + " of " + a[3] + ", " + frames.size() + " frames, displays " + a[1];
                })
                .server(server -> {
                    Fall.probe = null;
                    int[] a = frames.getLast();
                    int got = items(server, x) - before[0];
                    SelfTest.record("fall " + f.name + " drops", a[4] == a[5] && got == a[5] + 1,
                        "rolled " + a[4] + ", dropped " + a[5] + ", in the world " + got + " (+1 chopped log)");
                });
        }
        s.server(server -> io.github.profetgit.vanillatimber.server.Viewers.test = 0);
    }

    private static void smoke(Script s) {
        s.check("mod loaded", mc -> Platform.get().isModLoaded("vanilla_timber") ? null : "vanilla_timber not loaded")
            .check("player alive", mc -> mc.player.isAlive() ? null : "player not alive")
            .capture("smoke", 1)
            .until("smoke frame written", 200, mc -> !SelfTest.capturing())
            .waitTicks(10);
    }
}
