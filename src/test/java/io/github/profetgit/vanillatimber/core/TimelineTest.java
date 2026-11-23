package io.github.profetgit.vanillatimber.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The timeline against the 1.3.6 data pack, tick by tick: fixtures/136/*.json are the pack's controller scores at the
 * end of every tick of a real fall (dev/m1/sample136.py). Pillar 1: within 0.5 degrees and 0.02 blocks.
 */
class TimelineTest {
    record Fixture(JsonObject start, List<JsonObject> ticks) {
        double s(String k) {
            return start.get(k).getAsDouble();
        }
    }

    static Fixture load(String tree) throws Exception {
        try (var in = TimelineTest.class.getResourceAsStream("/fixtures/136/" + tree + ".json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<JsonObject> ticks = new ArrayList<>();
            for (var e : o.getAsJsonArray("ticks")) ticks.add(e.getAsJsonObject());
            return new Fixture(o.getAsJsonObject("start"), ticks);
        }
    }

    /** Every sampled tree: the three lab trees and one fixture per tree feature (fixtures/136/f_*.json). */
    static List<String> trees() throws Exception {
        java.nio.file.Path dir = java.nio.file.Path.of(TimelineTest.class.getResource("/fixtures/136").toURI());
        try (var files = java.nio.file.Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json") && !n.equals("curve.json"))
                .map(n -> n.substring(0, n.length() - 5)).sorted().toList();
        }
    }

    static Timeline timeline(Fixture f) {
        FallSpec spec = new FallSpec((int) f.s("h"), f.s("hit") / 100, (int) f.s("dur"), f.s("b") / 100, (int) f.s("km"),
            f.s("fd") / 1000, (int) f.s("ft"), (int) f.s("dr"), (int) f.s("rs"));
        boolean[] rings = new boolean[spec.km() + 1];
        java.util.Arrays.fill(rings, true);
        return new Timeline(spec, rings);
    }

    @ParameterizedTest
    @MethodSource("trees")
    void matches136EveryTick(String tree) throws Exception {
        Fixture f = load(tree);
        Timeline tl = timeline(f);
        double y0 = f.start().getAsJsonArray("pos").get(1).getAsDouble();
        int n = Math.min(f.ticks().size(), tl.end);
        assertTrue(n > 40, tree + ": only " + n + " ticks to compare");
        StringBuilder bad = new StringBuilder();
        double worstP = 0, worstBn = 0;
        for (int i = 0; i < n; i++) {
            JsonObject r = f.ticks().get(i);
            int tick = i + 1;
            worstP = Math.max(worstP, Math.abs(r.get("p").getAsDouble() - tl.p[tick]));
            worstBn = Math.max(worstBn, Math.abs(r.get("bn").getAsDouble() - tl.bn[tick]));
            check(bad, tick, "ph", r.get("ph").getAsDouble(), tl.ph[tick], 0);
            check(bad, tick, "p", r.get("p").getAsDouble(), tl.p[tick], 50);
            check(bad, tick, "bn", r.get("bn").getAsDouble(), tl.bn[tick], 8);
            for (String g : new String[] {"gx", "gy", "gz", "lx", "lz"}) {
                double[] mine = switch (g) {
                    case "gx" -> tl.gx;
                    case "gy" -> tl.gy;
                    case "gz" -> tl.gz;
                    case "lx" -> tl.lx;
                    default -> tl.lz;
                };
                check(bad, tick, g, r.get(g).getAsDouble(), mine[tick], 2);
            }
            check(bad, tick, "y", r.get("y").getAsDouble() / 1000 - y0, tl.dy[tick], 0.02);
        }
        System.out.printf("%s: worst pitch delta %.3f deg, worst bend delta %.3f deg over %d ticks%n", tree, worstP / 100, worstBn / 100, n);
        assertEquals("", bad.toString(), tree + " differs from 1.3.6");
        // the controller is gone after its finish tick: the last sample is the tick before
        assertEquals(f.ticks().size() + 1, tl.end, tree + " finish tick");
    }

    @ParameterizedTest
    @MethodSource("trees")
    void rippleOrderMatches136(String tree) throws Exception {
        Fixture f = load(tree);
        Timeline tl = timeline(f);
        // kr counts rings tagged so far: ring k was tagged on the first tick kr exceeded k
        for (int k = 0; k <= tl.spec.km(); k++) {
            int want = -1;
            for (int i = 0; i < f.ticks().size(); i++) {
                JsonObject r = f.ticks().get(i);
                if (r.get("ph").getAsInt() == 7 && num(r, "kr", 0) > k) {
                    want = i + 1;
                    break;
                }
            }
            if (want < 0) continue;
            assertEquals(want, tl.popTick[k], tree + " ring " + k + " pop tick");
        }
    }

    @ParameterizedTest
    @MethodSource("trees")
    void deriveMatches136(String tree) throws Exception {
        Fixture f = load(tree);
        FallSpec d = FallSpec.derive((int) f.s("h"), f.s("hit") / 100, 0, false, (int) f.s("dr"), (int) f.s("km"), (int) f.s("rs"));
        assertEquals((int) f.s("dur"), d.dur(), "dur");
        assertEquals(f.s("b") / 100, d.amp(), 0.011, "bounce");
    }

    /** A score the pack had not set yet (kr, kf before the ripple) comes back as null. */
    static double num(JsonObject r, String k, double fallback) {
        var e = r.get(k);
        return e == null || e.isJsonNull() ? fallback : e.getAsDouble();
    }

    static void check(StringBuilder bad, int tick, String what, double want, double got, double tol) {
        if (Math.abs(want - got) > tol + 1e-9 && bad.length() < 2000) {
            bad.append(String.format("t%d %s 1.3.6=%.3f 2.0=%.3f; ", tick, what, want, got));
        }
    }

    @org.junit.jupiter.api.Test
    void poofSlamsHoldsAndVanishesOnOneTick() {
        FallSpec spec = FallSpec.sized(8, 90, 0, false, 0, 7, 4);
        boolean[] rings = new boolean[8];
        java.util.Arrays.fill(rings, true);
        Timeline classic = new Timeline(spec, rings);
        Timeline tl = new Timeline(spec, rings, new boolean[8], true);
        int impact = -1, inhale = -1, poof = -1;
        for (Timeline.Event e : tl.events) {
            if (e.kind() == Timeline.Kind.IMPACT) impact = e.tick();
            if (e.kind() == Timeline.Kind.INHALE) inhale = e.tick();
            if (e.kind() == Timeline.Kind.POOF) poof = e.tick();
            assertTrue(e.kind() != Timeline.Kind.RING && e.kind() != Timeline.Kind.LEAVES_GONE, "no ripple, leaves stay: " + e.kind());
        }
        assertTrue(impact > 0 && inhale - impact >= 8 && poof - inhale >= 3, "beats " + impact + " " + inhale + " " + poof);
        assertEquals(poof + 1, tl.end, "one tick after the poof the fall ends");
        assertTrue(tl.leavesGone < 0, "leaves stay on until the poof");
        for (int k = 0; k < 8; k++) assertEquals(-1, tl.popTick[k]);
        assertTrue(poof - impact >= 11 && poof - impact <= 16, "slam to poof " + (poof - impact) + " ticks");
        for (int i = 0; i <= impact; i++) assertEquals(classic.p[i], tl.p[i], 1e-9, "same fall until the slam, tick " + i);
        assertTrue(tl.gy[poof - 1] > 1100 && tl.gx[poof - 1] < 950, "stretch smear just before the poof");
    }
}
