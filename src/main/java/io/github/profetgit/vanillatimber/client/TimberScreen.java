package io.github.profetgit.vanillatimber.client;

import com.mojang.serialization.Codec;
import io.github.profetgit.vanillatimber.net.Settings;
import io.github.profetgit.vanillatimber.server.Config;
import io.github.profetgit.vanillatimber.server.Config.Key;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
//? if >=1.21 {
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.OptionsSubScreen;
*///?}

/**
 * Timber's settings (PRD 5.13), vanilla widgets only: the server config in four sections, each with the config file's
 * own comment as hover help. Singleplayer and the title screen edit {@code config/timber.json} (the integrated server
 * re-reads it); on a server, operators change values through {@code /timber config} and everyone else sees them
 * read-only. Loader-neutral: the Fabric entry hands {@link #TimberScreen(Screen)} to Mod Menu; NeoForge's and Forge's
 * mod-list config buttons take the same constructor when the ports add them.
 */
public final class TimberScreen extends OptionsSubScreen {
    /** Where the values live and who may change them. */
    public enum Mode { LOCAL, OPERATOR, READ_ONLY, ABSENT }

    private record Section(String id, String title, Key... keys) {}

    private static final Section[] SECTIONS = {
        new Section("felling", "Felling", Key.CHOPS, Key.CHOP_CURVE, Key.SNEAK, Key.PROTECT),
        new Section("fall", "Fall", Key.ANIMATION, Key.STYLE, Key.LAND_BLOCKS, Key.DROPS, Key.KNOCKBACK, Key.TRUNK_DAMAGE, Key.REPLANT),
        new Section("axe", "Tool", Key.REQUIRE_AXE, Key.DURABILITY),
        new Section("server", "Server", Key.MAX_LOGS, Key.MAX_BLOCKS, Key.RADIUS, Key.FEEDBACK, Key.DISPLAY_CAP, Key.SLICE_MS),
    };

    /** Self-test: the last screen built, its mode, its widgets by key, and the changes it applied. */
    public static volatile TimberScreen last;
    public final java.util.Map<Key, AbstractWidget> widgets = new java.util.EnumMap<>(Key.class);
    public final List<String> applied = new ArrayList<>();
    public final List<String> headers = new ArrayList<>();
    public final java.util.Map<Key, Component> helpText = new java.util.EnumMap<>(Key.class);
    /** Self-test: forces a mode (null: from the connection). */
    public static volatile Mode forced;

    public final Mode mode;
    private final Settings remote;
    /** The tools list as typed (applied when the screen closes), and as it was when it opened. */
    public EditBox toolsBox;
    private String toolsAtOpen = "";

    public TimberScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatableWithFallback("vanilla_timber.config.title", "Timber settings"));
        Minecraft mc = Minecraft.getInstance();
        remote = ChopClient.server();
        Mode m = forced;
        if (m == null) m = modeOf(mc, remote);
        mode = m;
        // from the title screen nothing has read the file this run, or it changed since a world was closed
        if (mode == Mode.LOCAL && mc.getSingleplayerServer() == null) Config.load();
        last = this;
    }

    private static Mode modeOf(Minecraft mc, Settings remote) {
        ClientPacketListener net = mc.getConnection();
        if (net == null || mc.hasSingleplayerServer()) return Mode.LOCAL;
        if (remote == null) return Mode.ABSENT;
        return net.getCommands().getRoot().getChild("timber") != null ? Mode.OPERATOR : Mode.READ_ONLY;
    }

//? if >=1.21 {
    @Override
    protected void addTitle() {
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));
        header.addChild(new StringWidget(subtitle().withStyle(ChatFormatting.GRAY), font));
        layout.setHeaderHeight(46);
    }
//?} else {
/*    private OptionsList list;

    @Override
    protected void init() {
        list = new OptionsList(minecraft, width, height, 32, height - 32, 25);
        addOptions();
        addWidget(list);
        addRenderableWidget(toolsBox);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 + 5, height - 27, 150, 20).build());
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        list.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        g.drawCenteredString(font, subtitle().withStyle(ChatFormatting.GRAY), width / 2, 19, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partial);
    }
*///?}

    private MutableComponent subtitle() {
        return switch (mode) {
            case LOCAL -> Component.translatableWithFallback("vanilla_timber.config.local", "For your worlds and the ones you host");
            case OPERATOR -> Component.translatableWithFallback("vanilla_timber.config.operator", "This server's settings: changes run /timber config");
            case READ_ONLY -> Component.translatableWithFallback("vanilla_timber.config.read_only", "This server's settings: only operators can change them");
            case ABSENT -> Component.translatableWithFallback("vanilla_timber.config.absent", "This server doesn't run Timber");
        };
    }

//? if >=1.21 {
    @Override
//?}
    protected void addOptions() {
        java.util.Map<Key, OptionInstance<?>> made = new java.util.EnumMap<>(Key.class);
        for (Section s : SECTIONS) {
            Component head = Component.translatableWithFallback("vanilla_timber.config.section." + s.id(), s.title());
            header(head);
            headers.add(head.getString());
            OptionInstance<?>[] row = new OptionInstance<?>[s.keys().length];
            for (int i = 0; i < row.length; i++) made.put(s.keys()[i], row[i] = option(s.keys()[i]));
            list.addSmall(row);
            if (s.id().equals("axe")) addTools();
        }
        for (java.util.Map.Entry<Key, OptionInstance<?>> e : made.entrySet()) {
            Key k = e.getKey();
            AbstractWidget w = list.findOption(e.getValue());
            if (w == null) continue;
            widgets.put(k, w);
            if (mode == Mode.READ_ONLY || mode == Mode.ABSENT || mode == Mode.OPERATOR && remote != null && !remote.has(k.json)) w.active = false;
        }
        follow(value(Key.CHOPS) == 1);
    }

    private void header(Component head) {
//? if >=1.21.11 {
        list.addHeader(head);
//?} else if >=1.21 {
/*        list.addSmall(new StringWidget(310, 20, head, font).alignLeft(), null);
*///?}
    }

    /** The {@code tools} list: a text field under the axe switch, applied when the screen closes. */
    private void addTools() {
        java.util.List<String> now = mode == Mode.LOCAL ? Config.tools().entries() : remote != null && remote.tools() != null ? remote.tools()
            : io.github.profetgit.vanillatimber.server.Tools.DEFAULT;
        toolsAtOpen = String.join(", ", now);
        Component caption = Component.translatableWithFallback("vanilla_timber.config.tools", "Fells with");
        toolsBox = new EditBox(font, 0, 0, 150, 20, caption);
        toolsBox.setMaxLength(2048);
        toolsBox.setValue(toolsAtOpen);
//? if >=1.21 {
        toolsBox.moveCursorToStart(false);
//?} else {
/*        toolsBox.moveCursorToStart();
*///?}
//? if >=1.21 {
        toolsBox.setHint(Component.translatableWithFallback("vanilla_timber.config.tools.hint", "Item ids and #tags").withStyle(ChatFormatting.DARK_GRAY));
//?} else {
/*        toolsBox.setHint(Component.translatableWithFallback("vanilla_timber.config.tools.hint_footer", "Fells with: item ids, #tags").withStyle(ChatFormatting.DARK_GRAY));
*///?}
        MutableComponent help = Component.translatableWithFallback("vanilla_timber.config.tools.help",
            "Items that fell trees while Require an axe is on: item ids (minecraft:stick) and #tags (#minecraft:axes), comma separated. Empty: axes. Saved when you leave this screen.");
        if (mode == Mode.READ_ONLY) help.append("\n").append(Component.translatableWithFallback("vanilla_timber.config.op_only",
            "Only operators can change this (/timber config)").withStyle(ChatFormatting.YELLOW));
        toolsBox.setTooltip(Tooltip.create(help));
        boolean editable = mode == Mode.LOCAL || mode == Mode.OPERATOR && remote != null;
        toolsBox.setEditable(editable);
        toolsBox.active = editable;
//? if >=1.21 {
        StringWidget label = new StringWidget(150, 20, caption, font);
        list.addSmall(label, toolsBox);
//?} else {
/*        toolsBox.setX(width / 2 - 155);
        toolsBox.setY(height - 27);
*///?}
    }

    @Override
    public void removed() {
        if (toolsBox != null && !toolsBox.getValue().trim().equals(toolsAtOpen)) applyTools(toolsBox.getValue());
        super.removed();
    }

    /** Self-test hook too: sets the list the way closing the screen does. */
    public void applyTools(String text) {
        java.util.List<String> entries = text.isBlank() ? io.github.profetgit.vanillatimber.server.Tools.DEFAULT : io.github.profetgit.vanillatimber.server.Tools.split(text);
        if (mode == Mode.LOCAL) {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null) server.execute(() -> Config.setTools(entries));
            else Config.setTools(entries);
        } else if (mode == Mode.OPERATOR && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand("timber config tools " + (text.isBlank() ? "default" : String.join(" ", entries)));
        } else return;
        toolsAtOpen = text.trim();
        applied.add("tools=" + String.join(",", entries));
    }

    /** The chop count only matters in chop mode: greyed out while it is off, as vanilla greys dependent options. */
    private void follow(boolean chops) {
        AbstractWidget curve = widgets.get(Key.CHOP_CURVE);
        if (curve != null && (mode == Mode.LOCAL || mode == Mode.OPERATOR)) curve.active = chops;
    }

    private int value(Key k) {
        if (mode == Mode.LOCAL) return Config.get(k);
        return remote == null ? k.def : k.clamp(remote.get(k.json, k.def));
    }

    private void apply(Key k, int v) {
        if (k == Key.CHOPS) follow(v == 1);
        if (mode == Mode.LOCAL) {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null) server.execute(() -> Config.set(k, v));
            else Config.set(k, v);
        } else if (mode == Mode.OPERATOR && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand("timber config " + k.json + " " + v);
        } else return;
        applied.add(k.json + "=" + v);
    }

    private static String captionKey(Key k) {
        return "vanilla_timber.config." + k.json;
    }

    private OptionInstance.TooltipSupplier<Object> help(Key k) {
        MutableComponent text = Component.literal(readable(k)).append("\n").append(Component.literal(k.min == 0 && k.max == 1
            ? "Default: " + (k.def == 1 ? "ON" : "OFF") : choices(k) != null ? "Default: " + choices(k)[k.def - k.min] : k.min + " to " + k.max + ", default " + k.def)
            .withStyle(ChatFormatting.GRAY));
        if (mode == Mode.READ_ONLY) text.append("\n").append(Component.translatableWithFallback("vanilla_timber.config.op_only",
            "Only operators can change this (/timber config)").withStyle(ChatFormatting.YELLOW));
        helpText.put(k, text);
        return OptionInstance.cachedConstantTooltip(text);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> OptionInstance.TooltipSupplier<T> typed(OptionInstance.TooltipSupplier<Object> t) {
        return (OptionInstance.TooltipSupplier) t;
    }

    private OptionInstance<?> option(Key k) {
        int now = value(k);
        String id = captionKey(k);
        String[] choices = choices(k);
        if (choices != null) {
            List<Integer> values = new ArrayList<>();
            for (int v = k.min; v <= k.max; v++) values.add(v);
            return new OptionInstance<>(id, typed(help(k)),
                // a cycle button puts the caption in front itself
                (cap, v) -> Component.translatableWithFallback(id + "." + v, choices[v - k.min]),
                new OptionInstance.Enum<>(values, Codec.INT), now, v -> apply(k, v));
        }
        if (k.min == 0 && k.max == 1) return OptionInstance.createBoolean(id, typed(help(k)), now == 1, b -> apply(k, b ? 1 : 0));
        return new OptionInstance<>(id, typed(help(k)), (cap, v) -> CommonComponents.optionNameValue(cap, Component.literal(shown(k, v))),
//? if >=1.21 {
            new OptionInstance.IntRange(k.min, k.max, false), now, v -> apply(k, v));
//?} else {
/*            new OptionInstance.IntRange(k.min, k.max), now, v -> apply(k, v));
*///?}
    }

    public static String[] choices(Key k) {
        return switch (k) {
            case SNEAK -> new String[] {"Not sneaking", "Sneaking", "Always"};
            case DROPS -> new String[] {"Where it lands", "At the player"};
            case STYLE -> new String[] {"Classic", "Poof"};
            default -> null;
        };
    }

    /**
     * The config file's comment for a player: the file's value keys ("1: "), tag ids and key names in brackets go, the
     * first letter is a capital. A comment that lists choices ("0: ...; 1: ...") keeps them.
     */
    public static String readable(Key k) {
        String t = k.comment.replaceAll("\\s*\\([^)]*[#_][^)]*\\)", "");
        if (k.min == 0 && k.max == 1 && t.startsWith("1: ")) t = t.substring(3);
        else if (t.startsWith("0: ") && choices(k) == null) t = t.substring(3);
        if (k == Key.CHOP_CURVE) t = "How many chops a tree takes in chop mode: more logs take more chops, up to the shown count for a 100-log giant. Smaller is fewer";
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1) + ".";
    }

    public static String shown(Key k, int v) {
        return switch (k) {
            case CHOP_CURVE -> io.github.profetgit.vanillatimber.server.Chops.needed(5, v) + " to " + io.github.profetgit.vanillatimber.server.Chops.needed(100, v);
            case RADIUS -> v + " blocks";
            case SLICE_MS -> v + " ms";
            case LAND_BLOCKS -> v == 0 ? "Off" : v + "%";
            case TRUNK_DAMAGE -> (v % 2 == 0 ? Integer.toString(v / 2) : v / 2 + ".5") + (v == 2 ? " heart" : " hearts");
            default -> Integer.toString(v);
        };
    }
}
