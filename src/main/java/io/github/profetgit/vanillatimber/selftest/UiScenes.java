package io.github.profetgit.vanillatimber.selftest;

import io.github.profetgit.profetsettings.ModDef;
import io.github.profetgit.profetsettings.Preview;
import io.github.profetgit.profetsettings.ProfetSettings;
import io.github.profetgit.profetsettings.ProfetSettingsScreen;
import io.github.profetgit.profetsettings.Setting;
import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.client.ConfigHook;
import io.github.profetgit.vanillatimber.client.TimberScreen;
import io.github.profetgit.vanillatimber.client.ui.TimberSettings;
import io.github.profetgit.vanillatimber.server.Config;
import io.github.profetgit.vanillatimber.server.Config.Key;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.resources.Identifier;

// Scene "ui" (only when named; run at 1920x1080): Timber in the shared Profet settings screen. Every option through the
// real mouse, the file written, presets, Cancel, the operator and read-only modes, every preview at fixed moments, the
// bug report modal (never sent: the endpoint is cleared).
final class UiScenes {
    private static final String KEYS = "chops chop_curve sneak protect animation style land_blocks drops knockback trunk_damage replant "
        + "require_axe durability max_logs max_blocks radius feedback display_cap slice_ms";
    private static final String SWITCHES = "chops protect animation knockback replant require_axe durability feedback";

    private UiScenes() {}

    static void add(Script s) {
        s.run(mc -> {
                reset();
                gui(mc, 2);
            })
            .waitTicks(10)
            .check("ui: fonts, icon and language load", mc -> {
                for (String path : new String[] {"profet_settings:font/tiny5.ttf", "profet_settings:textures/gui/sprites/golem.png", "vanilla_timber:icon.png",
                    "vanilla_timber:textures/gui/preview/glow.png", "vanilla_timber:preview/swing.json"}) {
                    if (mc.getResourceManager().getResource(Identifier.tryParse(path)).isEmpty()) return "missing " + path;
                }
                for (String key : new String[] {"profet_settings.ui.done", "vanilla_timber.config.chops", "vanilla_timber.preview.off.animation", "vanilla_timber.preset.chop"}) {
                    if (net.minecraft.client.resources.language.I18n.get(key).equals(key)) return "untranslated " + key;
                }
                return null;
            });
        //? if fabric {
        s.run(mc -> mc.gui.setScreen(new PauseScreen(true))).waitTicks(15)
            .check("ui: the Game Menu has exactly one golem button", mc -> golems(mc).size() == 1 ? null : "golem buttons: " + golems(mc).size())
            .run(mc -> {
                Button b = golems(mc).get(0);
                click(mc, b.getX() + 10, b.getY() + 10);
            })
            .waitTicks(30)
            .check("ui: the golem button opens the hub", mc -> ProfetSettings.isScreen(mc.gui.screen()) ? null : "screen " + mc.gui.screen());
        //?}
        s.run(mc -> mc.gui.setScreen(ConfigHook.create(null))).waitTicks(30)
            .check("ui: the config button opens the hub on Timber", mc -> hub(mc).debugSelected().id.equals(Timber.ID) ? null : "selected " + hub(mc).debugSelected().id)
            .check("ui: singleplayer edits the config file", mc -> TimberSettings.lastMode == TimberScreen.Mode.LOCAL ? null : "mode " + TimberSettings.lastMode)
            .check("ui: every option is listed with a label and a description", mc -> {
                List<String> keys = new ArrayList<>();
                for (Setting o : hub(mc).debugSelected().all()) {
                    keys.add(o.key);
                    if (o.label.startsWith("vanilla_timber.") || o.desc.isBlank() || o.desc.startsWith("vanilla_timber.")) return "untranslated " + o.key;
                }
                return String.join(" ", keys).equals(KEYS) ? null : "keys " + keys;
            });
        for (int gs : new int[] {2, 3, 4}) {
            s.run(mc -> {
                    gui(mc, gs);
                    mc.gui.setScreen(ConfigHook.create(null));
                })
                .waitTicks(30)
                .run(mc -> SelfTest.record("ui layout gs" + gs, true, hub(mc).debugLayout()));
            capture(s, "ui-gs" + gs + "-a-top");
            s.run(mc -> hub(mc).debugScroll(100000)).waitTicks(30);
            capture(s, "ui-gs" + gs + "-b-end");
        }
        s.run(mc -> {
                gui(mc, 2);
                mc.gui.setScreen(ConfigHook.create(null));
            })
            .waitTicks(30);
        previews(s);
        presetPreviews(s);
        s.run(mc -> Preview.debugTime = -1);
        controls(s);
        cancel(s);
        modes(s);
        bugReport(s);
        s.run(mc -> {
            reset();
            TimberSettings.forced = null;
            mc.gui.setScreen(null);
        });
    }

    // ---- previews ----

    private static void previews(Script s) {
        String only = System.getProperty("vanilla_timber.selftest.pv", "");
        for (String[] p : PREVIEWS) {
            boolean film = p[1].equals("landfilm");
            if ((film || !only.isEmpty()) && !java.util.Arrays.asList(only.split("[,+]")).contains(p[1])) continue;
            String[] t = p[3].split(" ");
            int[] times = new int[t.length];
            for (int i = 0; i < t.length; i++) times[i] = Integer.parseInt(t[i]);
            if (!p[4].isEmpty()) s.run(mc -> set(p[4]));
            preview(s, p[0], film ? "land" : p[1], film ? "-film" : p[2], times);
            if (!p[4].isEmpty()) s.run(mc -> reset());
        }
    }

    private static final String FILM = java.util.stream.IntStream.rangeClosed(0, 118).mapToObj(i -> String.valueOf(1300 + i * 33)).collect(java.util.stream.Collectors.joining(" "));

    // {setting row, preview kind, suffix, ms..., config "key=value,..." for the variant}
    private static final String[][] PREVIEWS = {
        {"animation", "fall", "", "0 400 700 950 1100 1250 1400 1550 1700 1900 2200 2600 3200", ""},
        {"style", "fall", "-poof", "950 1250 1500 1700 1800 1900 2000 2200 2600", "style=1"},
        {"animation", "fall", "-instant", "700 1000 1100 1200 1400 2000", "animation=0"},
        {"land_blocks", "land", "-off", "1700", ""},
        {"land_blocks", "land", "", "1500 1900 2400 3000 3600 4200 4800", "land_blocks=60"},
        {"land_blocks", "land", "-all", "3600 4800", "land_blocks=100"},
        // every 33 ms of the stays-fallen fall, for a frame-by-frame sheet (pv=landfilm)
        {"land_blocks", "landfilm", "", FILM, "land_blocks=60"},
        {"drops", "drops", "", "1600 2000 2600", ""},
        {"drops", "drops", "-player", "1600 2000 2600", "drops=1"},
        {"replant", "replant", "-off", "3600", ""},
        {"replant", "replant", "", "3300 3600 4000", "replant=1"},
        {"knockback", "knock", "", "950 1500 1700 1900 2200 2600", ""},
        {"knockback", "knock", "-dense", "1700 1733 1766 1799 1832 1865 1898 1931 1964 1997 2030 2063 2096 2129 2162 2195", ""},
        {"animation", "fall", "-dense", "1850 1883 1916 1949 1982 2015 2048 2081 2114 2147 2180 2213 2246 2279 2312 2345 2378 2411 2444 2477 2510 2543 2576 2609 2642", ""},
        {"knockback", "knock", "-off", "1700 1900", "knockback=0"},
        {"trunk_damage", "knock", "-kill", "1700 1900 2400 2800", "trunk_damage=20"},
        {"durability", "durability", "", "500 950 1100 1500", ""},
        {"durability", "durability", "-off", "950 1100", "durability=0"},
        {"chops", "chop", "-off", "700 1200", ""},
        {"chops", "chop", "", "500 800 1200 1700 2200 2700 3300 3600 4000 4600", "chops=1"},
        {"chop_curve", "chop", "-few", "2200 3000", "chops=1,chop_curve=1"},
        {"sneak", "sneak", "", "100 800 1100 2000 4300 4600 5000 5600 6500", ""},
        {"sneak", "sneak", "-sneakfells", "800 1100 2000 4600 5600 6500", "sneak=1"},
        {"require_axe", "tool", "", "1100 2000 4600 5600 6500", ""},
        {"require_axe", "tool", "-off", "4600 5600 6500", "require_axe=0"},
        {"protect", "protect", "", "50 350 700 1100 1600 2200 2600 3200 3900 4700 5500", ""},
        {"protect", "protect", "-off", "700 1600 2200 2600 2900 3200 3900 4700 5500", "protect=0"},
        {"feedback", "feedback", "", "1500 1900", ""},
        {"feedback", "feedback", "-off", "1500", "feedback=0"},
    };

    // every Quick setup button shows its own scene, played with that preset's values
    private static final String[][] PRESET_PREVIEWS = {
        {"0", "default", "950 1900 2600"}, {"1", "chop", "1200 2700 4600"}, {"2", "tidy", "1900 2600 4000"},
        {"3", "fallen", "1900 3600 4800"}, {"4", "instant", "700 1100"},
    };

    private static void presetPreviews(Script s) {
        String only = System.getProperty("vanilla_timber.selftest.pv", "");
        if (!only.isEmpty() && !java.util.Arrays.asList(only.split("[,+]")).contains("presets")) return;
        for (String[] p : PRESET_PREVIEWS) {
            int i = Integer.parseInt(p[0]);
            String[] t = p[2].split(" ");
            s.run(mc -> {
                    mc.gui.setScreen(ConfigHook.create(null));
                    Preview.debugTime = Integer.parseInt(t[0]);
                })
                .waitTicks(20)
                .run(mc -> hub(mc).debugScroll(0))
                .waitTicks(6)
                .run(mc -> {
                    int[] pt = hub(mc).debugPresetPoint(i);
                    hub(mc).debugHover(pt[0], pt[1]);
                })
                .waitTicks(12)
                .check("ui: Quick setup " + p[1] + " shows its own preview", mc -> ("preset." + p[1]).equals(hub(mc).debugPreviewKind()) ? null : "preview " + hub(mc).debugPreviewKind())
                .check("ui: Quick setup " + p[1] + " has its plain-words note", mc -> {
                    String n = hub(mc).debugPreviewNote();
                    return n != null && !n.isBlank() && !n.matches(".*\\d.*") ? null : "note " + n;
                });
            for (String tt : t) {
                s.run(mc -> Preview.debugTime = Integer.parseInt(tt)).waitTicks(3);
                capture(s, "ui-pv-preset-" + p[1] + "-" + tt);
            }
        }
    }

    private static void preview(Script s, String key, String kind, String suffix, int... times) {
        s.run(mc -> {
                mc.gui.setScreen(ConfigHook.create(null));
                Preview.debugTime = times[0];
            })
            .waitTicks(20)
            .run(mc -> hub(mc).debugRevealRow(key))
            .waitTicks(4)
            .run(mc -> {
                Setting o = hub(mc).debugSelected().find(key);
                int[] p = hub(mc).debugPoint(key, o.kind == Setting.Kind.BOOL ? -1f : 0.5f);
                hub(mc).debugHover(p[0], p[1]);
            })
            .waitTicks(12)
            .check("ui: " + key + " shows the " + kind + " preview" + suffix, mc -> kind.equals(hub(mc).debugPreviewKind()) ? null : "preview " + hub(mc).debugPreviewKind());
        for (int t : times) {
            s.run(mc -> Preview.debugTime = t).waitTicks(3);
            capture(s, "ui-pv-" + kind + suffix + "-" + t);
        }
    }

    // ---- every control through the real mouse ----

    private static void controls(Script s) {
        s.run(mc -> mc.gui.setScreen(ConfigHook.create(null))).waitTicks(20);
        for (String key : SWITCHES.split(" ")) {
            Key k = Key.of(key);
            int[] was = new int[1];
            s.run(mc -> was[0] = Config.get(k));
            click(s, key, -1f);
            s.check("ui: the " + key + " switch changes the option and the file", mc -> Config.get(k) == 1 - was[0]
                && file().contains("\"" + key + "\":" + (1 - was[0])) ? null : "value " + Config.get(k) + " file " + file());
            click(s, key, -1f);
            s.check("ui: the " + key + " switch changes it back", mc -> Config.get(k) == was[0] ? null : "stuck at " + Config.get(k));
        }
        click(s, "sneak", 1f);
        click(s, "sneak", 1f);
        s.check("ui: the sneaking choice picks Always fell and saves it", mc -> Config.get(Key.SNEAK) == 2 && file().contains("\"sneak\":2") ? null : "sneak " + Config.get(Key.SNEAK));
        click(s, "style", 1f);
        s.check("ui: the style choice picks Poof", mc -> Config.get(Key.STYLE) == 1 && file().contains("\"style\":1") ? null : "style " + Config.get(Key.STYLE));
        click(s, "trunk_damage", 1f);
        s.check("ui: the trunk damage slider reaches 20 and is saved", mc -> Config.get(Key.TRUNK_DAMAGE) == 20 && file().contains("\"trunk_damage\":20") ? null : "damage " + Config.get(Key.TRUNK_DAMAGE));
        click(s, "land_blocks", 0f);
        s.check("ui: the stays-fallen slider reaches 0", mc -> Config.get(Key.LAND_BLOCKS) == 0 ? null : "land " + Config.get(Key.LAND_BLOCKS));
        s.run(mc -> hub(mc).debugRevealRow("max_logs")).waitTicks(4).run(mc -> {
                int[] a = hub(mc).debugPoint("max_logs", 0f), b = hub(mc).debugPoint("max_logs", 1f);
                hub(mc).debugPress(a[0], a[1]);
                hub(mc).debugDrag(b[0] + 30, b[1]);
                hub(mc).debugRelease(b[0] + 30, b[1]);
            })
            .waitTicks(20)
            .check("ui: a slider drag reaches the maximum and is saved", mc -> Config.get(Key.MAX_LOGS) == Key.MAX_LOGS.max
                && file().contains("\"max_logs\":" + Key.MAX_LOGS.max) ? null : "max_logs " + Config.get(Key.MAX_LOGS));
        capture(s, "ui-c-changed-row");
        s.run(mc -> press(mc, hub(mc).debugReset("max_logs"))).waitTicks(10)
            .check("ui: the reset arrow restores max logs", mc -> Config.get(Key.MAX_LOGS) == Key.MAX_LOGS.def ? null : "max_logs " + Config.get(Key.MAX_LOGS))
            .run(mc -> hub(mc).debugScroll(0)).waitTicks(10);
        preset(s, 1, "Chop mode", () -> Config.get(Key.CHOPS) == 1 && Config.get(Key.STYLE) == 0 && Config.get(Key.SNEAK) == 0);
        preset(s, 2, "Tidy", () -> Config.get(Key.CHOPS) == 0 && Config.get(Key.REPLANT) == 1 && Config.get(Key.DROPS) == 1);
        preset(s, 3, "Stays fallen", () -> Config.get(Key.LAND_BLOCKS) == 60 && Config.get(Key.REPLANT) == 0);
        preset(s, 4, "Instant", () -> Config.get(Key.ANIMATION) == 0 && Config.get(Key.LAND_BLOCKS) == 0);
        capture(s, "ui-d-preset-instant");
        preset(s, 0, "Default", () -> {
            for (Key k : Key.values()) if (Config.get(k) != k.def) return false;
            return true;
        });
        s.check("ui: the tools list survives the screen", mc -> file().contains("\"tools\":[") ? null : file())
            .run(mc -> press(mc, hub(mc).debugDone())).waitTicks(10)
            .check("ui: Done closes the screen", mc -> mc.gui.screen() == null ? null : "screen " + mc.gui.screen());
    }

    private static void preset(Script s, int index, String name, java.util.function.BooleanSupplier want) {
        s.run(mc -> hub(mc).debugScroll(0)).waitTicks(6)
            .run(mc -> press(mc, hub(mc).debugPreset(index))).waitTicks(20)
            .check("ui: Quick setup " + name, mc -> want.getAsBoolean() ? null : "config " + file());
    }

    private static void cancel(Script s) {
        String[] opening = new String[1];
        s.run(mc -> {
                set("trunk_damage=6,replant=1");
                opening[0] = values();
                mc.gui.setScreen(ConfigHook.create(null));
            })
            .waitTicks(30);
        for (String key : SWITCHES.split(" ")) click(s, key, -1f);
        click(s, "radius", 1f);
        s.check("ui: options changed before Cancel", mc -> !opening[0].equals(values()) ? null : "nothing changed")
            .run(mc -> press(mc, hub(mc).debugCancel())).waitTicks(15)
            .check("ui: Cancel puts every option back", mc -> opening[0].equals(values()) ? null : "now " + values() + " was " + opening[0])
            .check("ui: Cancel writes the old values to the file", mc -> file().contains("\"trunk_damage\":6") && file().contains("\"replant\":1") ? null : file())
            .check("ui: Cancel closes the screen", mc -> mc.gui.screen() == null ? null : "screen " + mc.gui.screen())
            .run(mc -> reset());
    }

    // a server operator's values go out as /timber config commands; a player who isn't one can't change anything
    private static void modes(Script s) {
        s.run(mc -> {
                TimberSettings.forced = TimberScreen.Mode.READ_ONLY;
                TimberSettings.applied.clear();
                mc.gui.setScreen(ConfigHook.create(null));
            })
            .waitTicks(20);
        click(s, "chops", -1f);
        s.check("ui: read-only: a click changes nothing", mc -> Config.get(Key.CHOPS) == 0 && TimberSettings.applied.isEmpty() ? null : "applied " + TimberSettings.applied)
            .check("ui: read-only: the rows are locked with a note about operators", mc -> {
                String n = hub(mc).debugSelected().lockedNote();
                return n != null && n.toLowerCase().contains("operator") ? null : "note " + n;
            });
        capture(s, "ui-g-read-only");
        // the client's copy of the server values must say replant is off before the operator clicks it
        s.run(mc -> mc.gui.setScreen(null))
            .server(server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "timber config replant 0"))
            .until("ui: the server's settings reach the client", 100, mc -> {
                var r = io.github.profetgit.vanillatimber.client.ChopClient.server();
                return r != null && r.get("replant", -1) == 0;
            })
            .run(mc -> {
                TimberSettings.forced = TimberScreen.Mode.OPERATOR;
                TimberSettings.applied.clear();
                mc.gui.setScreen(ConfigHook.create(null));
            })
            .waitTicks(20);
        click(s, "replant", -1f);
        s.check("ui: operator: a switch sends /timber config", mc -> TimberSettings.applied.contains("replant=1") ? null : "applied " + TimberSettings.applied)
            .until("ui: the command reaches the server", 100, mc -> Config.get(Key.REPLANT) == 1)
            .check("ui: operator: the switch shows the new value", mc -> hub(mc).debugSelected().find("replant").getBool() ? null : "switch still off");
        click(s, "replant", -1f);
        s.until("ui: operator: and back", 100, mc -> Config.get(Key.REPLANT) == 0)
            .run(mc -> {
                TimberSettings.forced = null;
                mc.gui.setScreen(null);
                reset();
            });
    }

    // ---- the bug report (never sent) ----

    private static void bugReport(Script s) {
        List<java.net.URI> opened = new ArrayList<>();
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<java.net.URI>[] prev = new java.util.function.Consumer[1];
        s.run(mc -> {
                gui(mc, 2);
                ProfetSettings.debugResetBugState();
                // never file real issues from tests
                ProfetSettings.reportEndpoint = "";
                prev[0] = ProfetSettings.uriOpener;
                ProfetSettings.uriOpener = opened::add;
                opened.clear();
                mc.gui.setScreen(ConfigHook.create(null));
            })
            .waitTicks(20)
            .check("bug: Report bug button is shown for Timber", mc -> hub(mc).debugBugButton() != null ? null : "no button")
            .run(mc -> press(mc, hub(mc).debugBugButton()))
            .waitTicks(20)
            .check("bug: clicking Report bug opens the modal", mc -> hub(mc).debugBugModalOpen() ? null : "modal did not open");
        capture(s, "ui-bug-modal");
        s.run(mc -> {
                int[] tp = hub(mc).debugBugTitle();
                click(mc, tp[0], tp[1]);
                for (char ch : "Tree floats".toCharArray()) UiInput.type(mc, ch);
                int[] dp = hub(mc).debugBugDesc();
                click(mc, dp[0], dp[1]);
                for (char ch : "A birch stays up.".toCharArray()) UiInput.type(mc, ch);
            })
            .waitTicks(10)
            .check("bug: body has Timber, the version, the settings and the typed text", mc -> {
                String body = hub(mc).debugBugBody();
                if (!body.contains("Timber")) return "missing mod name";
                if (!body.contains(UiInput.versionName())) return "missing MC version";
                if (!body.contains("## Settings")) return "missing settings section";
                if (!body.contains("A birch stays up.")) return "missing typed description";
                String home = System.getProperty("user.home", "");
                return !home.isEmpty() && body.contains(home) ? "home path leaked" : null;
            })
            .run(mc -> press(mc, hub(mc).debugBugSend()))
            .waitTicks(10)
            .check("bug: with no endpoint, Open on GitHub opens a pre-filled Timber issue", mc -> {
                ProfetSettings.uriOpener = prev[0];
                if (opened.size() != 1) return "opened " + opened.size();
                String u = opened.get(0).toString();
                return u.startsWith("https://github.com/ProfetGit/timber/issues/new?") && u.contains("Tree%20floats") ? null : "url " + u;
            })
            .run(mc -> UiInput.escape(hub(mc)))
            .waitTicks(10)
            .check("bug: Escape closes the modal", mc -> !hub(mc).debugBugModalOpen() ? null : "still open")
            .run(mc -> {
                ProfetSettings.debugResetBugState();
                mc.gui.setScreen(null);
            });
    }

    // ---- helpers ----

    private static void set(String kv) {
        for (String p : kv.split(",")) {
            String[] a = p.split("=");
            Config.set(Key.of(a[0]), Integer.parseInt(a[1]));
        }
    }

    private static void reset() {
        for (Key k : Key.values()) if (Config.get(k) != k.def) Config.set(k, k.def);
    }

    private static String values() {
        StringBuilder b = new StringBuilder();
        for (Key k : Key.values()) b.append(k.json).append('=').append(Config.get(k)).append(' ');
        return b.toString();
    }

    private static String file() {
        try {
            return Files.readString(Config.file()).replaceAll("\\s", "");
        } catch (IOException e) {
            return "";
        }
    }

    private static void gui(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        UiInput.resize(mc);
    }

    private static void capture(Script s, String name) {
        s.run(mc -> SelfTest.startCapture(name, 1)).until("ui: frame " + name + " written", 60, mc -> !SelfTest.capturing());
    }

    private static ProfetSettingsScreen hub(Minecraft mc) {
        return (ProfetSettingsScreen) mc.gui.screen();
    }

    private static List<Button> golems(Minecraft mc) {
        List<Button> out = new ArrayList<>();
        for (var w : mc.gui.screen().children()) if (w instanceof Button b && b.getClass().getSimpleName().equals("GolemButton")) out.add(b);
        return out;
    }

    private static void click(Minecraft mc, int guiX, int guiY) {
        UiInput.click(mc, guiX, guiY);
    }

    private static void click(Script s, String key, float frac) {
        s.run(mc -> hub(mc).debugRevealRow(key))
            .waitTicks(4)
            .run(mc -> {
                int[] p = hub(mc).debugPoint(key, frac);
                if (p == null) throw new IllegalStateException("no control for " + key);
                click(mc, p[0], p[1]);
            })
            .waitTicks(15);
    }

    private static void press(Minecraft mc, int[] p) {
        if (p == null) throw new IllegalStateException("nothing to press");
        hub(mc).debugPress(p[0], p[1]);
        hub(mc).debugRelease(p[0], p[1]);
    }

    @SuppressWarnings("unused")
    private static List<Object> values(ProfetSettingsScreen hub) {
        List<Object> out = new ArrayList<>();
        for (ModDef m : hub.debugMods()) if (m.id.equals(Timber.ID)) for (Setting o : m.all()) out.add(o.get.get());
        return out;
    }
}
