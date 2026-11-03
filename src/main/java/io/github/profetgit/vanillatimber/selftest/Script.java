package io.github.profetgit.vanillatimber.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/** A list of client-tick steps; each returns true when it is done. */
final class Script {
    interface Step {
        boolean tick(Minecraft mc);
    }

    final List<Step> steps = new ArrayList<>();

    Script run(Consumer<Minecraft> action) {
        steps.add(mc -> {
            action.accept(mc);
            return true;
        });
        return this;
    }

    Script server(Consumer<MinecraftServer> action) {
        CompletableFuture<?>[] f = new CompletableFuture<?>[1];
        steps.add(mc -> {
            if (f[0] == null) {
                MinecraftServer s = mc.getSingleplayerServer();
                f[0] = s.submit(() -> action.accept(s));
            }
            if (!f[0].isDone()) return false;
            f[0].join();
            return true;
        });
        return this;
    }

    Script waitTicks(int n) {
        int[] left = {n};
        steps.add(mc -> --left[0] <= 0);
        return this;
    }

    /** Runs {@code action} once a tick for {@code n} ticks. */
    Script each(int n, Consumer<Minecraft> action) {
        int[] left = {n};
        steps.add(mc -> {
            action.accept(mc);
            return --left[0] <= 0;
        });
        return this;
    }

    Script until(String what, int timeout, Predicate<Minecraft> cond) {
        int[] t = {0};
        steps.add(mc -> {
            if (cond.test(mc)) return true;
            if (++t[0] > timeout) throw new IllegalStateException("timed out waiting for " + what);
            return false;
        });
        return this;
    }

    /** {@code check} returns null on success, else what went wrong. */
    Script check(String name, Function<Minecraft, String> check) {
        steps.add(mc -> {
            String fail = check.apply(mc);
            SelfTest.record(name, fail == null, fail == null ? "ok" : fail);
            return true;
        });
        return this;
    }

    Script capture(String name, int frames) {
        steps.add(mc -> {
            SelfTest.startCapture(name, frames);
            return true;
        });
        return this;
    }
}
