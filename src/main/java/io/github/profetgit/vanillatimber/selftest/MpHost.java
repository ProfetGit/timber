package io.github.profetgit.vanillatimber.selftest;

import io.github.profetgit.vanillatimber.client.ClientFalls;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

/**
 * The M1 exit's modded client on a dedicated server (mode {@code mp-host}, case {@code m1} in dev/modtest.json). The
 * case's steps op it and give it haste 127 and a netherite axe; the Guest is a vanilla client with dev/probe. Per
 * sampler tree it plants the fixture with commands, puts the Guest at the side view, cues the probe, chops the log for
 * real ({@code startDestroyBlock}: the server's break path), and logs every frame the mesh is drawn (wall clock in
 * epoch microseconds, the probe's clock) to mesh_&lt;tree&gt;.tsv. A last spruce falls with the Guest far away (only
 * modded viewers: the server's moving tick). dev/m1/exit.py reads both clients' files.
 */
final class MpHost {
    private MpHost() {}

    static final Map<String, String> TREES = Collections.synchronizedMap(new LinkedHashMap<>());

    static long nowMicros() {
        Instant i = Instant.now();
        return i.getEpochSecond() * 1_000_000L + i.getNano() / 1000;
    }

    static void cmd(Minecraft mc, String line) {
        mc.player.connection.sendCommand(line);
    }

    static Script build() {
        Script s = new Script();
        s.until("joined a server", 6000, mc -> mc.player != null && mc.level != null && mc.getSingleplayerServer() == null && mc.gui.screen() == null)
            .until("op, haste, axe and the Guest", 6000, mc -> mc.player.hasEffect(MobEffects.HASTE)
                && mc.player.getMainHandItem().is(Items.NETHERITE_AXE) && mc.getConnection().getPlayerInfo("Guest") != null)
            .waitTicks(60)
            .run(mc -> {
                // the flicker check looks at the tree: no HUD (chat fade, actionbar, hunger jitter, the hand) and no
                // particles (vanilla's, the same as 1.3.6, and fast enough to cross a tile in one frame)
                hideHud(mc);
                mc.options.particles().set(net.minecraft.server.level.ParticleStatus.MINIMAL);
                SelfTest.record("mp ready", true, "op, haste 127, netherite axe, Guest in the tab list");
            });
        for (Scenarios.Fix f : Scenarios.FIXES) tree(s, f, 8 + f.dx(), f.name(), true);
        Scenarios.Fix spruce = Scenarios.FIXES[2];
        s.run(mc -> cmd(mc, "tp Guest -300 -60 8"))
            .waitTicks(100);
        tree(s, spruce, 152, "spruce_solo", false);
        s.run(mc -> cmd(mc, "tellraw Guest \"[probe] quit\""))
            .run(mc -> {
                StringBuilder sb = new StringBuilder("{");
                TREES.forEach((k, v) -> sb.append(sb.length() > 1 ? "," : "").append('"').append(k).append("\":").append(v));
                try {
                    Files.writeString(Path.of(System.getProperty("vanilla_timber.selftest"), "trees.json"), sb.append("}\n"));
                } catch (IOException e) {
                    SelfTest.record("mp trees.json", false, e.toString());
                }
            })
            .waitTicks(40);
        return s;
    }

    static void hideHud(Minecraft mc) {
        try {
            java.lang.reflect.Field f = mc.gui.hud.getClass().getDeclaredField("isHidden");
            f.setAccessible(true);
            f.setBoolean(mc.gui.hud, true);
        } catch (ReflectiveOperationException e) {
            SelfTest.record("mp hud hidden", false, e.toString());
        }
    }

    /** Plants, cues, chops and records one tree at (x, -60, 8). */
    private static void tree(Script s, Scenarios.Fix f, int x, String name, boolean probe) {
        // per drawn frame: wall us, client time, mesh pitch, tau, ns this mod spent on the frame
        List<double[]> frames = Collections.synchronizedList(new ArrayList<>());
        // per captured frame: its index and wall us; and the chop's wall us (exit.py's "at the chop" window)
        List<long[]> captured = Collections.synchronizedList(new ArrayList<>());
        long[] chop = {0};
        s.run(mc -> {
            cmd(mc, "kill @e[type=item]");
            String box = (x - 8) + " %d -12 " + (x + 27) + " %d 28 ";
            cmd(mc, "fill " + box.formatted(-61, -61) + "minecraft:grass_block");
            cmd(mc, "fill " + box.formatted(-60, -46) + "minecraft:air");
            cmd(mc, "fill " + box.formatted(-45, -30) + "minecraft:air");
            try (var in = MpHost.class.getResourceAsStream("/selftest/trees/" + f.file() + ".mcfunction")) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isBlank()) cmd(mc, "execute positioned " + x + " -60 8 run " + line.trim());
                }
            } catch (IOException e) {
                throw new IllegalStateException("fixture " + f.file(), e);
            }
            cmd(mc, "tp Host " + (x - 3.5) + " -60 8.5 -90 -25");
            if (probe) cmd(mc, "tp Guest " + (x - 4.5) + " -60 21.8 facing " + (x + 3) + " -55 10");
        })
            .waitTicks(60)
            .run(mc -> {
                if (probe) cmd(mc, "tellraw Guest \"[probe] start " + name + "\"");
            })
            .waitTicks(20)
            .until("mp " + name + " standing", 200, mc -> mc.level.getBlockState(new BlockPos(x, -60, 8)).is(net.minecraft.tags.BlockTags.LOGS))
            .run(mc -> {
                ClientFalls.last = null;
                ClientFalls.spent = 0;
                SelfTest.frameHook = m -> {
                    int ci = SelfTest.captureIndex();
                    if (ci >= 0) captured.add(new long[] {ci, nowMicros()});
                    double p = ClientFalls.drawnPitch();
                    long ns = ClientFalls.spent;
                    ClientFalls.spent = 0;
                    if (!Double.isNaN(p)) frames.add(new double[] {nowMicros(), ClientFalls.now(), p, ClientFalls.drawnTau(), ns});
                };
            })
            .capture("mp_" + name, 240)
            .run(mc -> {
                mc.player.setYRot(-90);
                chop[0] = nowMicros();
                mc.gameMode.startDestroyBlock(new BlockPos(x, -60, 8), Direction.WEST);
            })
            .until("mp " + name + " payload", 200, mc -> ClientFalls.last != null)
            .until("mp " + name + " fall to end", 900, mc -> ClientFalls.active() == 0)
            .waitTicks(30)
            .run(mc -> {
                SelfTest.frameHook = null;
                if (probe) cmd(mc, "tellraw Guest \"[probe] stop\"");
                var tl = ClientFalls.timeline();
                double hit = tl.spec.hit();
                double max = frames.stream().mapToDouble(r -> r[2]).max().orElse(Double.NaN);
                List<Double> ms = new ArrayList<>();
                for (double[] r : frames) ms.add(r[4] / 1e6);
                SelfTest.record("mp " + name + " drawn to the slam", max >= hit - 0.5,
                    String.format(Locale.ROOT, "max pitch %.2f, hit %.0f, %d frames", max, hit, frames.size()));
                SelfTest.record("mp " + name + " frame ms", true, String.format(Locale.ROOT, "p50 %.3f p95 %.3f max %.3f over %d frames, %d quads, bake %.2f ms",
                    Scenarios.percentile(ms, 0.5), Scenarios.percentile(ms, 0.95), Scenarios.percentile(ms, 1.0), ms.size(),
                    ClientFalls.quads(), ClientFalls.bakeNanos / 1e6));
                TREES.put(name, String.format(Locale.ROOT, "{\"hit\":%.1f,\"end\":%d,\"quads\":%d,\"bake_ms\":%.3f,\"anchor_shift\":%.2f,\"x\":%d,\"chop_us\":%d}",
                    hit, tl.end, ClientFalls.quads(), ClientFalls.bakeNanos / 1e6, ClientFalls.anchorShift(), x, chop[0]));
                StringBuilder sb = new StringBuilder("wall_us\tnow\tpitch\ttau\tns\n");
                for (double[] r : frames) {
                    sb.append(String.format(Locale.ROOT, "%d\t%.4f\t%.4f\t%.4f\t%d%n", (long) r[0], r[1], r[2], r[3], (long) r[4]));
                }
                StringBuilder cf = new StringBuilder("frame\twall_us\n");
                for (long[] r : captured) cf.append(r[0]).append('\t').append(r[1]).append('\n');
                try {
                    Files.writeString(Path.of(System.getProperty("vanilla_timber.selftest"), "frames_" + name + ".tsv"), cf);
                    Files.writeString(Path.of(System.getProperty("vanilla_timber.selftest"), "mesh_" + name + ".tsv"), sb);
                } catch (IOException e) {
                    SelfTest.record("mp " + name + " tsv", false, e.toString());
                }
            });
    }
}
