package io.github.profetgit.vanillatimber.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.profetgit.vanillatimber.Timber;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The server config {@code config/timber.json} (PRD 5.8): the 1.3.x keys with 1.3.6's defaults and clamps, plus
 * {@code hide_leaves}, {@code display_cap} and {@code slice_ms}. Re-read when the file's modification time changes,
 * every value clamped, a commented file written on the first run. Switches are 0 or 1 like the 1.3.x scores.
 */
public final class Config {
    public enum Key {
        MAX_LOGS("max_logs", 256, 16, 1024, "Most logs in one tree; a bigger tree is left alone (only the chopped log breaks)"),
        MAX_BLOCKS("max_blocks", 900, 100, 1200, "Most leaves (and attached blocks) one tree takes with it"),
        RADIUS("radius", 12, 4, 32, "How far, in blocks sideways, a tree may reach from the cut"),
        REQUIRE_AXE("require_axe", 1, 0, 1, "1: only fell trees when chopping with an axe (#minecraft:axes or #c:tools/axe)"),
        SNEAK("sneak", 0, 0, 2, "0: sneaking takes a single log; 1: only fell while sneaking; 2: always fell"),
        DURABILITY("durability", 1, 0, 1, "1: each log costs axe durability (Unbreaking applies); a tree that would break the axe is not felled"),
        ANIMATION("animation", 1, 0, 1, "0: the tree vanishes at once and drops at the stump"),
        DROPS("drops", 0, 0, 1, "0: each log hops out where it lands, leaf drops burst from the crown; 1: everything drops at the player"),
        PROTECT("protect", 1, 0, 1, "1: logs placed by players are never felled"),
        FEEDBACK("feedback", 1, 0, 1, "1: action bar messages (logs felled, and why a tree was not felled)"),
        WELCOME("welcome", 0, 0, 1, "1: explain Timber to each player once, on their first join"),
        HIDE_LEAVES("hide_leaves", 0, 0, 1, "1: leaves shut in on all sides get no display (cheaper, but the crown looks hollow when it falls)"),
        DISPLAY_CAP("display_cap", 1200, 100, 4000, "Most displays one fall may spawn"),
        SLICE_MS("slice_ms", 2, 1, 20, "Server milliseconds a fall may spend per tick when work is time-sliced");

        public final String json;
        public final int def, min, max;
        final String comment;

        Key(String json, int def, int min, int max, String comment) {
            this.json = json;
            this.def = def;
            this.min = min;
            this.max = max;
            this.comment = comment;
        }

        public int clamp(int v) {
            return Math.clamp(v, min, max);
        }

        public static Key of(String name) {
            for (Key k : values()) if (k.json.equals(name)) return k;
            return null;
        }
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("timber.json");
    private static volatile int[] values = defaults();
    private static JsonObject extra = new JsonObject();
    private static long stamp = -1;
    /** The file failed to parse at the last read: the values in use are the last good ones. */
    static boolean broken;
    private static int poll;

    private Config() {}

    private static int[] defaults() {
        int[] v = new int[Key.values().length];
        for (Key k : Key.values()) v[k.ordinal()] = k.def;
        return v;
    }

    public static int get(Key key) {
        return values[key.ordinal()];
    }

    public static boolean on(Key key) {
        return get(key) == 1;
    }

    public static int maxLogs() {
        return get(Key.MAX_LOGS);
    }

    public static int maxBlocks() {
        return get(Key.MAX_BLOCKS);
    }

    public static int radius() {
        return get(Key.RADIUS);
    }

    /** Reads the file (defaults for anything missing or unreadable), clamps, and rewrites it when it differs. */
    public static void load() {
        JsonObject raw = new JsonObject();
        try {
            if (Files.exists(FILE)) {
                JsonElement e = JsonParser.parseString(Files.readString(FILE));
                if (!e.isJsonObject()) throw new IllegalStateException("not a JSON object");
                raw = e.getAsJsonObject();
            }
        } catch (IOException | RuntimeException e) {
            // a typo while editing: keep the values in use and the operator's file, try again on the next save
            Timber.LOG.warn("Timber: config unreadable, keeping the current values ({})", e.toString());
            try {
                stamp = Files.getLastModifiedTime(FILE).toMillis();
            } catch (IOException ignored) {
            }
            broken = true;
            return;
        }
        broken = false;
        int[] v = defaults();
        for (Key k : Key.values()) {
            JsonElement e = raw.get(k.json);
            try {
                if (e != null && e.isJsonPrimitive()) v[k.ordinal()] = k.clamp(e.getAsInt());
            } catch (RuntimeException ex) {
                Timber.LOG.warn("Timber: config {} is not a number, using {}", k.json, k.def);
            }
        }
        JsonObject rest = new JsonObject();
        for (String name : raw.keySet()) if (Key.of(name) == null) rest.add(name, raw.get(name));
        values = v;
        extra = rest;
        write();
    }

    /** Sets one key (clamped) and saves it; returns the stored value. */
    public static int set(Key key, int value) {
        int[] v = values.clone();
        v[key.ordinal()] = key.clamp(value);
        values = v;
        write();
        return v[key.ordinal()];
    }

    public static void reset() {
        values = defaults();
        write();
    }

    private static void write() {
        StringBuilder b = new StringBuilder("{\n");
        Key[] keys = Key.values();
        for (int i = 0; i < keys.length; i++) {
            Key k = keys[i];
            b.append("  // ").append(k.comment).append(" (").append(k.min).append("..").append(k.max).append(", default ").append(k.def).append(")\n");
            b.append("  \"").append(k.json).append("\": ").append(values[i]);
            b.append(i < keys.length - 1 || !extra.keySet().isEmpty() ? ",\n" : "\n");
        }
        int n = 0, total = extra.keySet().size();
        for (String name : extra.keySet()) {
            b.append("  \"").append(name).append("\": ").append(extra.get(name));
            b.append(++n < total ? ",\n" : "\n");
        }
        b.append("}\n");
        String text = b.toString();
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE) || !Files.readString(FILE).equals(text)) Files.writeString(FILE, text);
            stamp = Files.getLastModifiedTime(FILE).toMillis();
        } catch (IOException e) {
            Timber.LOG.warn("Timber: could not write the config ({})", e.toString());
        }
    }

    /** Every 40 server ticks: reload when the file's modification time changed. */
    static void pollReload() {
        if (++poll < 40) return;
        poll = 0;
        try {
            if (Files.exists(FILE) && Files.getLastModifiedTime(FILE).toMillis() != stamp) load();
        } catch (IOException ignored) {
        }
    }

    /** {@code key = value} lines for {@code /timber config}. */
    public static String describe(Key key) {
        return key.json + " = " + get(key) + " (" + key.min + ".." + key.max + ", default " + key.def + ")";
    }
}
