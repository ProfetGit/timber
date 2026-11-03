package io.github.profetgit.vanillatimber.selftest;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only hook of the shared ModTest harness (template: ModTest/java/ModTestHook.java, copied into each mod's test
 * package by `ModTest/sync_hook.py`; don't edit the copies). Inert unless ModTest's system properties are set.
 */
public final class ModTestHook {
    private static boolean audited;

    private ModTestHook() {
    }

    /**
     * -Dmodtest.audit=1: force-load every mixin target that hasn't loaded yet, so a mixin that can't apply shows up now
     * (ModTest turns the log into the mixin_audit check) instead of at the class's first use, or never on a path no
     * scene reaches. Call once the game has loaded (title screen or world), on the render thread.
     */
    public static void audit() {
        if (audited || !"1".equals(System.getProperty("modtest.audit"))) return;
        audited = true;
        System.out.println("[ModTest] mixin audit start");
        try {
            Class<?> env = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment");
            Object current = env.getMethod("getCurrentEnvironment").invoke(null);
            env.getMethod("audit").invoke(current);
            System.out.println("[ModTest] mixin audit done");
        } catch (ReflectiveOperationException | LinkageError e) {
            System.out.println("[ModTest] mixin audit unavailable: " + e);
        }
    }

    /** Extra setup commands for the test world: -Dmodtest.tickrate=N runs the game at N ticks per second. */
    public static List<String> commands() {
        List<String> out = new ArrayList<>();
        String rate = System.getProperty("modtest.tickrate", "");
        if (!rate.isEmpty()) out.add("tick rate " + rate);
        return out;
    }
}
