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
 * checks that the title screen is reached; "full" creates a world and runs {@link Scenarios}; "mp-host" is the M1 exit's
 * modded client on a dedicated server ({@link MpHost}). Results go to
 * results.json, captured frames to frames/&lt;name&gt;/, then the game quits.
 */
public final class SelfTest {
    private static final String OUT = System.getProperty("vanilla_timber.selftest");
    private static final String MODE = System.getProperty("vanilla_timber.selftest.mode", "full");
    private static final List<String> RESULTS = new ArrayList<>();
    // daemon: a writer still busy at shutdown never keeps the JVM alive (the client shutdown watchdog fires at 15 s)
    private static final ExecutorService WRITER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "timber-selftest-writer");
        t.setDaemon(true);
        return t;
    });

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
        if (steps == null && MODE.equals("mp-host")) {
            if (!mc.isGameLoadFinished()) return;
            ModTestHook.audit();
            steps = MpHost.build().steps;
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
//? if >=1.21.5 {
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> WRITER.execute(() -> {
            try (img) {
                Files.createDirectories(out.getParent());
                img.writeToFile(out);
            } catch (IOException e) {
                TimberClient.LOG.warn("[selftest] frame write failed: {}", e.toString());
            }
        }));
//?} else {
/*        NativeImage img = Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget());
        WRITER.execute(() -> {
            try (img) {
                Files.createDirectories(out.getParent());
                img.writeToFile(out);
            } catch (IOException e) {
                TimberClient.LOG.warn("[selftest] frame write failed: {}", e.toString());
            }
        });
*///?}
    }

    /** Index of the frame the next capture writes, or -1 when not capturing (scenes stamp their frames with it). */
    static int captureIndex() {
        return captureLeft > 0 ? captureFrame : -1;
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
            // every captured frame is on disk before the game quits; hundreds of PNGs take a while on a busy machine
            if (!WRITER.awaitTermination(5, TimeUnit.MINUTES)) TimberClient.LOG.warn("[selftest] frame writes still running at exit");
        } catch (InterruptedException ignored) {
        }
        String json = String.format("{\"loader\":\"%s\",\"mc\":\"%s\",\"results\":[%s]}%n",
            io.github.profetgit.vanillatimber.platform.Platform.get().loader(), net.minecraft.SharedConstants.getCurrentVersion().name(), String.join(",", RESULTS));
        try {
            Files.createDirectories(Path.of(OUT));
            Files.writeString(Path.of(OUT, "results.json"), json);
        } catch (IOException e) {
            TimberClient.LOG.error("[selftest] could not write results", e);
        }
        mc.stop();
    }
}
