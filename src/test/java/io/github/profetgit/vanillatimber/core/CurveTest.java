package io.github.profetgit.vanillatimber.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every per-tick fall table equals the one 1.3.6 shipped (fixtures/136/curve.json, made with packs/Timber/dev/gen.py). */
class CurveTest {
    @Test
    void tablesEqualGenPy() throws Exception {
        JsonObject o;
        try (var in = CurveTest.class.getResourceAsStream("/fixtures/136/curve.json")) {
            o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet()) {
            int d = Integer.parseInt(e.getKey());
            var want = e.getValue().getAsJsonArray();
            for (int t = 0; t <= d; t++) assertEquals(want.get(t).getAsInt(), Curve.at(d, t), "d" + d + "[" + t + "]");
        }
    }
}
