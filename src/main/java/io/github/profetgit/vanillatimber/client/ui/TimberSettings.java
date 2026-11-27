package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.ModDef;
import io.github.profetgit.profetsettings.ProfetSettings;
import io.github.profetgit.profetsettings.Setting;
import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.client.ChopClient;
import io.github.profetgit.vanillatimber.client.TimberScreen;
import io.github.profetgit.vanillatimber.net.Settings;
import io.github.profetgit.vanillatimber.server.Config;
import io.github.profetgit.vanillatimber.server.Config.Key;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/**
 * Timber's options for the shared Profet screen, bound to the server config {@code config/timber.json} (same keys as
 * {@link TimberScreen}). Built each time the screen opens, so it knows where the values live: singleplayer and the title
 * screen edit the file (the integrated server re-reads it), a server's operator changes values through
 * {@code /timber config}, everyone else on a server sees them and cannot change them. The tools list stays a command
 * ({@code /timber config tools}): the shared screen has no text option.
 */
public final class TimberSettings {
    private TimberSettings() {}

    /** Self-test: forces a mode (null: from the connection). */
    public static volatile TimberScreen.Mode forced;
    /** Self-test: the values sent, as "key=value", and the mode of the last build. */
    public static final java.util.List<String> applied = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static volatile TimberScreen.Mode lastMode;
    /** The values of the screen open now: the previews read these (a server's settings on a server). */
    static volatile Values live;

    private static String t(String key, String fallback) {
        return Component.translatableWithFallback("vanilla_timber." + key, fallback).getString();
    }

    public static ModDef create() {
        Minecraft mc = Minecraft.getInstance();
        Settings remote = ChopClient.server();
        TimberScreen.Mode mode = forced != null ? forced : modeOf(mc, remote);
        lastMode = mode;
        if (mode == TimberScreen.Mode.LOCAL && mc.getSingleplayerServer() == null) Config.load();
        Values v = new Values(mode, remote);
        live = v;

        String tagline = mode == TimberScreen.Mode.OPERATOR ? t("config.operator", "This server's settings: changes run /timber config")
            : t("settings.tagline", "Chop one log and the whole tree comes down");
        String lockNote = switch (mode) {
            case READ_ONLY -> t("config.read_only", "This server's settings: only operators can change them");
            case ABSENT -> t("config.absent", "This server doesn't run Timber");
            default -> null;
        };
        ModDef m = new ModDef(Timber.ID, "Profet's Timber", "Timber", ProfetSettings.modVersion(Timber.ID), tagline, "vanilla_timber:icon.png")
            .issues("ProfetGit/timber")
            .diagnostics(() -> "Settings mode: " + mode + (remote != null ? ", server sent " + remote.names().length + " values" : ""))
            .locked(() -> lockNote);
        // a Cancel puts back only what changed, so a server gets one command per changed value, not one per option
        m.snapshot = () -> {
            Map<Key, Integer> before = new EnumMap<>(Key.class);
            for (Key k : Key.values()) before.put(k, v.get(k));
            return () -> before.forEach((k, old) -> {
                if (v.get(k) != old) v.set(k, old);
            });
        };
        m.group(t("config.section.felling", "Felling"),
            v.bool(Key.CHOPS).preview("chop"),
            v.range(Key.CHOP_CURVE).preview("chop"),
            v.choice(Key.SNEAK).preview("sneak"),
            v.bool(Key.PROTECT).preview("protect"));
        m.group(t("config.section.fall", "Fall"),
            v.bool(Key.ANIMATION).preview("fall"),
            v.choice(Key.STYLE).preview("fall"),
            v.range(Key.LAND_BLOCKS).preview("land"),
            v.choice(Key.DROPS).preview("drops"),
            v.bool(Key.KNOCKBACK).preview("knock"),
            v.range(Key.TRUNK_DAMAGE).preview("knock"),
            v.bool(Key.REPLANT).preview("replant"));
        m.group(t("config.section.axe", "Tool"),
            v.bool(Key.REQUIRE_AXE).preview("tool"),
            v.bool(Key.DURABILITY).preview("durability"));
        m.group(t("config.section.server", "Server"),
            v.range(Key.MAX_LOGS),
            v.range(Key.MAX_BLOCKS),
            v.range(Key.RADIUS),
            v.bool(Key.FEEDBACK).preview("feedback"),
            v.range(Key.DISPLAY_CAP),
            v.range(Key.SLICE_MS));
        Scenes.register(m);
        m.preset(t("preset.default", "Default"));
        m.preset(t("preset.chop", "Chop mode"), Key.CHOPS.json, true);
        m.preset(t("preset.tidy", "Tidy"), Key.REPLANT.json, true, Key.DROPS.json, 1);
        m.preset(t("preset.fallen", "Stays fallen"), Key.LAND_BLOCKS.json, 60);
        m.preset(t("preset.instant", "Instant"), Key.ANIMATION.json, false);
        Scenes.presets(m, new String[] {"default", "chop", "tidy", "fallen", "instant"});
        return m;
    }

    private static TimberScreen.Mode modeOf(Minecraft mc, Settings remote) {
        ClientPacketListener net = mc.getConnection();
        if (net == null || mc.hasSingleplayerServer()) return TimberScreen.Mode.LOCAL;
        if (remote == null) return TimberScreen.Mode.ABSENT;
        return net.getCommands().getRoot().getChild("timber") != null ? TimberScreen.Mode.OPERATOR : TimberScreen.Mode.READ_ONLY;
    }

    /**
     * Where the screen reads and writes. A value just set is shown at once (pending) until the file or the server's
     * next settings packet carries it, so a switch never flicks back while the change travels.
     */
    static final class Values {
        final TimberScreen.Mode mode;
        final Settings remoteAtOpen;
        private final Map<Key, Integer> pending = new EnumMap<>(Key.class);

        Values(TimberScreen.Mode mode, Settings remote) {
            this.mode = mode;
            this.remoteAtOpen = remote;
        }

        private int stored(Key k) {
            if (mode == TimberScreen.Mode.LOCAL) return Config.get(k);
            Settings r = ChopClient.server();
            if (r == null) r = remoteAtOpen;
            return r == null ? k.def : k.clamp(r.get(k.json, k.def));
        }

        synchronized int get(Key k) {
            int now = stored(k);
            Integer p = pending.get(k);
            if (p == null) return now;
            if (p == now) pending.remove(k);
            return p;
        }

        boolean editable(Key k) {
            if (mode == TimberScreen.Mode.LOCAL) return true;
            if (mode != TimberScreen.Mode.OPERATOR) return false;
            Settings r = ChopClient.server();
            return r == null || r.has(k.json);
        }

        synchronized void set(Key k, int value) {
            int val = k.clamp(value);
            if (!editable(k) || val == get(k)) return;
            Minecraft mc = Minecraft.getInstance();
            if (mode == TimberScreen.Mode.LOCAL) {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> Config.set(k, val));
                else Config.set(k, val);
            } else if (mc.getConnection() != null) {
                mc.getConnection().sendCommand("timber config " + k.json + " " + val);
            } else return;
            pending.put(k, val);
            applied.add(k.json + "=" + val);
        }

        private String label(Key k) {
            return t("config." + k.json, k.json);
        }

        private String desc(Key k) {
            String d = t("config." + k.json + ".desc", TimberScreen.readable(k));
            if (k == Key.REQUIRE_AXE) d += " " + t("config.tools.command", "Change the list with /timber config tools.");
            if (!editable(k)) d += " " + t("config.op_only", "Only operators can change this (/timber config)");
            return d;
        }

        Setting bool(Key k) {
            return Setting.bool(k.json, label(k), desc(k), () -> get(k) == 1, b -> set(k, b ? 1 : 0), k.def == 1);
        }

        Setting range(Key k) {
            return Setting.range(k.json, label(k), desc(k), k.min, k.max, () -> get(k), x -> set(k, x), k.def).format(x -> TimberScreen.shown(k, x));
        }

        Setting choice(Key k) {
            String[] names = TimberScreen.choices(k);
            String[] shown = new String[names.length];
            for (int i = 0; i < names.length; i++) shown[i] = t("config." + k.json + "." + (k.min + i), names[i]);
            return Setting.choice(k.json, label(k), desc(k), shown, () -> get(k) - k.min, i -> set(k, i + k.min), k.def - k.min);
        }
    }
}
