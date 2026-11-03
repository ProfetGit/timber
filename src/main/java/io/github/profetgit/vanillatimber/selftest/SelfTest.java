package io.github.profetgit.vanillatimber.selftest;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.profetgit.vanillatimber.client.TimberClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * In-game test runner, inert unless -Dvanilla_timber.selftest=&lt;out dir&gt; is set (ModTest sets it). Mode "boot" only
 * checks that the title screen is reached; "full" creates a world and runs {@link Scenarios}. Results go to
 * results.json, captured frames to frames/&lt;name&gt;/, then the game quits.
 */
public final class SelfTest {
    private static final String OUT = System.getProperty("vanilla_timber.selftest");
    private static final String MODE = System.getProperty("vanilla_timber.selftest.mode", "full");
    private static final List<String> RESULTS = new ArrayList<>();
    private static final ExecutorService WRITER = Executors.newFixedThreadPool(2);

    private static boolean finished;
    private static int titleTicks, ticks;
    private static List<Script.Step> steps;
    private static int index;
    private static String captureName;
    private static int captureLeft, captureFrame;

    private SelfTest() {}

    public static boolean active() {
        return OUT != null;
    }

    public static void onClientTick(Minecraft mc) {
        if (finished) return;
        if (++ticks % 400 == 0) {
            TimberClient.LOG.info("[selftest] step {}/{} screen={}", index, steps == null ? 0 : steps.size(),
                mc.gui.screen() == null ? null : mc.gui.screen().getClass().getSimpleName());
        }
        if (steps == null) {
            if (!mc.isGameLoadFinished() || !(mc.gui.screen() instanceof TitleScreen) || ++titleTicks < 20) return;
            ModTestHook.audit();
            record("boot", true, "title screen reached");
            if (MODE.equals("boot")) {
                finish(mc);
                return;
            }
            steps = Scenarios.build().steps;
        }
        try {
            while (index < steps.size() && steps.get(index).tick(mc)) index++;
        } catch (Throwable t) {
            TimberClient.LOG.error("[selftest] step {} failed", index, t);
            record("step " + index, false, t.toString());
            index = steps.size();
        }
        if (index >= steps.size()) finish(mc);
    }

    /** Called once per rendered frame, after the frame (scenes that sample what was drawn), or null. */
    static volatile java.util.function.Consumer<Minecraft> frameHook;

    public static void onFrame(Minecraft mc) {
        var hook = frameHook;
        if (hook != null) hook.accept(mc);
        if (captureLeft <= 0) return;
        Path out = Path.of(OUT, "frames", captureName, String.format("f%03d.png", captureFrame++));
        captureLeft--;
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> WRITER.execute(() -> {
            try (img) {
                Files.createDirectories(out.getParent());
                img.writeToFile(out);
            } catch (IOException e) {
                TimberClient.LOG.warn("[selftest] frame write failed: {}", e.toString());
            }
        }));
    }

    static boolean capturing() {
        return captureLeft > 0;
    }

    static void startCapture(String name, int frames) {
        captureName = name;
        captureLeft = frames;
        captureFrame = 0;
    }

    public static void record(String name, boolean pass, String detail) {
        RESULTS.add(String.format("{\"name\":\"%s\",\"pass\":%s,\"detail\":\"%s\"}",
            name, pass, detail.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")));
        TimberClient.LOG.info("[selftest] {} {} {}", pass ? "PASS" : "FAIL", name, detail);
    }

    private static void finish(Minecraft mc) {
        finished = true;
        WRITER.shutdown();
        try {
            WRITER.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        String json = String.format("{\"loader\":\"%s\",\"mc\":\"%s\",\"results\":[%s]}%n",
            "fabric", net.minecraft.SharedConstants.getCurrentVersion().name(), String.join(",", RESULTS));
        try {
            Files.createDirectories(Path.of(OUT));
            Files.writeString(Path.of(OUT, "results.json"), json);
        } catch (IOException e) {
            TimberClient.LOG.error("[selftest] could not write results", e);
        }
        mc.stop();
    }
}
