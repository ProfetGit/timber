package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.platform.Platform;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.server.Config.Key;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /timber config <key> [value] | reload | menu} (op level 2, PRD 5.8). The menu is 1.3.6's clickable chat settings
 * menu; its buttons run {@code /timber menu <key> <value>}, which sets the key and shows the menu again.
 */
public final class TimberCommand {
    private static final SuggestionProvider<CommandSourceStack> KEYS = (c, b) -> {
        List<String> names = new ArrayList<>();
        for (Key k : Key.values()) names.add(k.json);
        return SharedSuggestionProvider.suggest(names, b);
    };

    private TimberCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        RequiredArgumentBuilder<CommandSourceStack, String> configKey = Commands.argument("key", StringArgumentType.word()).suggests(KEYS)
            .executes(c -> show(c.getSource(), StringArgumentType.getString(c, "key")))
            .then(Commands.argument("value", IntegerArgumentType.integer()).executes(c ->
                set(c.getSource(), StringArgumentType.getString(c, "key"), IntegerArgumentType.getInteger(c, "value"))));
        RequiredArgumentBuilder<CommandSourceStack, String> menuKey = Commands.argument("key", StringArgumentType.word()).suggests(KEYS)
            .then(Commands.argument("value", IntegerArgumentType.integer()).executes(c -> {
                int r = set(c.getSource(), StringArgumentType.getString(c, "key"), IntegerArgumentType.getInteger(c, "value"));
                if (r > 0) menu(c.getSource());
                return r;
            }));
//? if >=1.21.6 {
        d.register(Commands.literal("timber").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
//?} else {
/*        d.register(Commands.literal("timber").requires(s -> s.hasPermission(2))
*///?}
            .then(Commands.literal("config").executes(c -> list(c.getSource()))
                .then(Commands.literal(Config.TOOLS).executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(Config.describeTools()), false);
                    return 1;
                }).then(Commands.argument("items", StringArgumentType.greedyString()).executes(c ->
                    setTools(c.getSource(), StringArgumentType.getString(c, "items")))))
                .then(configKey))
            .then(Commands.literal("reload").executes(c -> {
                Config.load();
                c.getSource().sendSuccess(() -> Component.literal("Timber config reloaded"), true);
                return 1;
            }))
            .then(Commands.literal("menu").executes(c -> menu(c.getSource()))
                .then(Commands.literal("reset").executes(c -> {
                    Config.reset();
                    return menu(c.getSource());
                }))
                .then(menuKey)));
    }

    private static int list(CommandSourceStack src) {
        for (Key k : Key.values()) src.sendSuccess(() -> Component.literal(Config.describe(k)), false);
        src.sendSuccess(() -> Component.literal(Config.describeTools()), false);
        return Key.values().length + 1;
    }

    /** {@code /timber config tools <ids and #tags>}: "default" restores the axes. */
    private static int setTools(CommandSourceStack src, String text) {
        List<String> entries = text.trim().equalsIgnoreCase("default") ? Tools.DEFAULT : Tools.split(text);
        if (Tools.clean(entries) == null) {
            src.sendFailure(Component.literal("No item id or #tag in '" + text + "'. Example: /timber config tools #minecraft:axes minecraft:stick"));
            return 0;
        }
        Tools t = Config.setTools(entries);
        src.sendSuccess(() -> Component.literal("tools set to " + t.text()), true);
        return 1;
    }

    private static int show(CommandSourceStack src, String name) {
        Key k = Key.of(name);
        if (k == null) return unknown(src, name);
        src.sendSuccess(() -> Component.literal(Config.describe(k)), false);
        return 1;
    }

    private static int set(CommandSourceStack src, String name, int value) {
        Key k = Key.of(name);
        if (k == null) return unknown(src, name);
        int stored = Config.set(k, value);
        src.sendSuccess(() -> Component.literal(k.json + " set to " + stored + (stored != value ? " (clamped from " + value + ")" : "")), true);
        return 1;
    }

    private static int unknown(CommandSourceStack src, String name) {
        src.sendFailure(Component.literal("Unknown Timber setting '" + name + "'. See /timber config"));
        return 0;
    }

    // ---- the menu (1.3.6 settings.mcfunction) ----

    private static void line(CommandSourceStack src, Component text) {
        ServerPlayer p = src.getPlayer();
        if (p != null) Say.chat(p, text);
        else src.sendSuccess(() -> text, false);
    }

    private static String version() {
        String v = Platform.get().modVersion(Timber.ID);
        return v != null ? v : "2.0";
    }

    static int menu(CommandSourceStack src) {
        line(src, Component.literal("\n🪓 Timber settings ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal("v" + version()).withStyle(ChatFormatting.DARK_GRAY)));
        List<Component> requires = Api.requires(src.getServer());
        if (!requires.isEmpty()) {
            MutableComponent row = Component.literal(" ");
            for (Component c : requires) row.append(c);
            line(src, row);
        }
        numRow(src, Key.MAX_LOGS, "Max logs per tree", "Bigger trees are left alone (only the one log breaks)", 256, 512, 1024, 2048);
        numRow(src, Key.RADIUS, "Search radius", "How far (in blocks, sideways) from the cut a tree may reach", 8, 12, 16, 24);
        choiceRow(src, Key.SNEAK, "Trees fall when", new String[] {"Not sneaking", "Sneaking", "Always"},
            new String[] {"Sneak to break just one log", "Only a chop while sneaking fells the tree", "Every chop fells the tree, sneaking or not"});
        choiceRow(src, Key.DROPS, "Drops", new String[] {"Where it lands", "At player"},
            new String[] {"Each log hops out where it lands, leaf drops burst from the crown", "Drops appear at your feet as the tree breaks apart"});
        boolRow(src, Key.REQUIRE_AXE, "Require an axe", "Only fell trees with an item from the tools list (axes by default; /timber config tools)");
        boolRow(src, Key.DURABILITY, "Use axe durability", "Each log costs durability (Unbreaking applies); a worn axe still fells the tree and breaks");
        boolRow(src, Key.CHOPS, "Chop mode", "Bigger trees take more chops (HT's TreeChop style); each chop costs one durability and cracks the log");
        boolRow(src, Key.ANIMATION, "Falling animation", "Off: the tree vanishes at once and drops at the stump");
        choiceRow(src, Key.STYLE, "Fall style", new String[] {"Classic", "Poof"},
            new String[] {"The logs pop ring by ring after the slam", "After the slam the tree waits, swells and vanishes in one cloud, its items left behind"});
        numRow(src, Key.LAND_BLOCKS, "Tree stays fallen %", "Share of the trunk (and the leaves beside it) that stays on the ground as blocks for you to chop", 0, 25, 50, 100);
        boolRow(src, Key.PROTECT, "Protect placed logs", "Logs placed by players are never felled");
        boolRow(src, Key.FEEDBACK, "Refusal message", "Say on the action bar why a tree was not felled");
        boolRow(src, Key.REPLANT, "Auto-replant", "A free sapling is planted at the stump when the tree has fallen");
        boolRow(src, Key.KNOCKBACK, "Trunk knockback", "The falling trunk knocks back mobs in its way (never players)");
        numRow(src, Key.TRUNK_DAMAGE, "Trunk damage", "Half hearts the trunk deals to hostile mobs it hits (0 = push only)", 0, 2, 4, 8);
        line(src, Component.literal(" ").append(Players.button("[Reset to defaults]", ChatFormatting.YELLOW, "Restore every setting to its default", "/timber menu reset")));
        return 1;
    }

    private static String setCmd(Key key, int v) {
        return "/timber menu " + key.json + " " + v;
    }

    private static MutableComponent label(String text, String hint) {
        return Component.literal(" " + text + ": ").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withHoverEvent(new HoverEvent.ShowText(Component.literal(hint))));
    }

    private static MutableComponent choice(Key key, int v, String text, String hint) {
        if (Config.get(key) == v) {
            return Component.literal("[" + text + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hint))));
        }
        return Component.literal("[" + text + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hint))).withClickEvent(new ClickEvent.RunCommand(setCmd(key, v))));
    }

    private static void numRow(CommandSourceStack src, Key key, String text, String hint, int... options) {
        MutableComponent row = label(text, hint).append(Component.literal(Integer.toString(Config.get(key))).withStyle(ChatFormatting.WHITE)).append("  ");
        for (int i = 0; i < options.length; i++) {
            if (i > 0) row.append(" ");
            row.append(choice(key, options[i], Integer.toString(options[i]), "Set to " + options[i]));
        }
        line(src, row);
    }

    private static void choiceRow(CommandSourceStack src, Key key, String text, String[] labels, String[] hints) {
        MutableComponent row = Component.literal(" " + text + ": ").withStyle(ChatFormatting.GRAY);
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) row.append(" ");
            row.append(choice(key, i, labels[i], hints[i]));
        }
        line(src, row);
    }

    private static void boolRow(CommandSourceStack src, Key key, String text, String hint) {
        boolean on = Config.on(key);
        line(src, label(text, hint).append(Component.literal(on ? "[ON]" : "[OFF]").withStyle(Style.EMPTY.withColor(on ? ChatFormatting.GREEN : ChatFormatting.RED)
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(on ? "Click to turn off" : "Click to turn on")))
            .withClickEvent(new ClickEvent.RunCommand(setCmd(key, on ? 0 : 1))))));
    }
}
