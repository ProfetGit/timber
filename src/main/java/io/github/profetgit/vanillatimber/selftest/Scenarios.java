package io.github.profetgit.vanillatimber.selftest;

import io.github.profetgit.vanillatimber.core.FallSpec;
import io.github.profetgit.vanillatimber.server.Falls;
import io.github.profetgit.vanillatimber.server.Tree;
import java.nio.charset.StandardCharsets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
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
        s.run(mc -> mc.createWorldOpenFlows().createFreshLevel("vanilla_timbertest-" + System.currentTimeMillis(),
                new LevelSettings("vanilla_timbertest", GameType.SURVIVAL,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false), WorldPresets::createTestWorldDimensions, mc.gui.screen()))
            .until("world to load", 2400, mc -> mc.player != null && mc.level != null && mc.gui.screen() == null)
            .waitTicks(40)
            .check("world loaded", mc -> mc.player.isAlive() ? null : "player not alive");
        String only = System.getProperty("vanilla_timber.selftest.scene", "all");
        if (only.equals("all") || only.contains("smoke")) smoke(s);
        if (only.equals("all") || only.contains("scan")) scan(s);
        return s;
    }

    /** One 1.3.6 sampler fixture: tree file, x offset from the sampler's spot, and the start it recorded. */
    record Fix(String name, String file, int dx, int hit, int dur, int h, double amp, int km, double fd, int ft, int dr,
               int rs, int px, int py, int pz) {}

    // dev/m1/sample136.py: tree at (8, -60, 8), chopped there by a player at yaw -90 (falls at 303.75)
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
                if (!line.isBlank()) server.getCommands().performPrefixedCommand(src, line.trim());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("fixture " + file, e);
        }
    }

    /** Chops the log at (x, -60, 8) as the sampler's player: survival, netherite axe, yaw -90. */
    static void chop(MinecraftServer server, int x) {
        ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_AXE));
        p.setYRot(-90);
        p.setXRot(20);
        p.gameMode.destroyBlock(new BlockPos(x, -60, 8));
    }

    private static void scan(Script s) {
        s.server(server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
            "forceload add -16 -16 160 32"));
        for (Fix f : FIXES) {
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
                        && p.km() == f.km && p.fd() == f.fd && p.ft() == f.ft && p.dr() == f.dr && p.rs() == f.rs;
                    return ok ? null : p + " vs 1.3.6 h=" + f.h + " hit=" + f.hit + " dur=" + f.dur + " amp=" + f.amp + " km=" + f.km
                        + " fd=" + f.fd + " ft=" + f.ft + " dr=" + f.dr + " rs=" + f.rs;
                })
                .check("scan " + f.name + " ring mask", mc -> {
                    Tree t = Falls.last;
                    if (t == null) return "no tree";
                    for (boolean b : t.logRings()) if (!b) return "ring without a log: " + java.util.Arrays.toString(t.logRings());
                    return t.logRings().length == f.km + 1 ? null : "mask length " + t.logRings().length;
                });
        }
    }

    private static void smoke(Script s) {
        s.check("mod loaded", mc -> FabricLoader.getInstance().isModLoaded("vanilla_timber") ? null : "vanilla_timber not loaded")
            .check("player alive", mc -> mc.player.isAlive() ? null : "player not alive")
            .capture("smoke", 1)
            .until("smoke frame written", 200, mc -> !SelfTest.capturing())
            .waitTicks(10);
    }
}
