package io.github.profetgit.vanillatimber.selftest;

import net.fabricmc.loader.api.FabricLoader;
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
        return s;
    }

    private static void smoke(Script s) {
        s.check("mod loaded", mc -> FabricLoader.getInstance().isModLoaded("vanilla_timber") ? null : "vanilla_timber not loaded")
            .check("player alive", mc -> mc.player.isAlive() ? null : "player not alive")
            .capture("smoke", 1)
            .until("smoke frame written", 200, mc -> !SelfTest.capturing())
            .waitTicks(10);
    }
}
