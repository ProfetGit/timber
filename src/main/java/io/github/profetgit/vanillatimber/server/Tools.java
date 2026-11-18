package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.Timber;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The items that fell trees while {@code require_axe} is 1 (config key {@code tools}): item ids and {@code #tags}. Shared by
 * the server's gates and a modded client's chop prediction (which gets the list in {@code Settings}).
 */
public final class Tools {
    public static final List<String> DEFAULT = List.of("#minecraft:axes", "#c:tools/axe");

    private final List<String> entries;
    private final List<TagKey<Item>> tags = new ArrayList<>();
    private final Set<Identifier> items = new HashSet<>();
    private boolean checked;

    private Tools(List<String> entries) {
        this.entries = List.copyOf(entries);
        for (String e : entries) {
            if (e.startsWith("#")) tags.add(TagKey.create(Registries.ITEM, Identifier.tryParse(e.substring(1))));
            else items.add(Identifier.tryParse(e));
        }
    }

    public static final Tools DEFAULTS = new Tools(DEFAULT);

    /** The valid entries of {@code raw} (trimmed, {@code minecraft:} added where no namespace is given), or null when none is valid. */
    public static List<String> clean(Iterable<String> raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            boolean tag = t.startsWith("#");
            String id = tag ? t.substring(1) : t;
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) {
                Timber.LOG.warn("Timber: config tools entry '{}' is not an item id or #tag, skipped", t);
                continue;
            }
            String norm = (tag ? "#" : "") + parsed;
            if (!out.contains(norm)) out.add(norm);
        }
        return out.isEmpty() ? null : out;
    }

    /** Splits a typed list ("a, #b c") into entries. */
    public static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String s : text.split("[,\\s]+")) if (!s.isBlank()) out.add(s);
        return out;
    }

    public static Tools of(List<String> entries) {
        List<String> c = entries == null ? null : clean(entries);
        return c == null || c.equals(DEFAULT) ? DEFAULTS : new Tools(c);
    }

    public List<String> entries() {
        return entries;
    }

    public String text() {
        return String.join(", ", entries);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (TagKey<Item> t : tags) if (stack.is(t)) return true;
        if (items.isEmpty()) return false;
        if (!checked) {
            checked = true;
            for (Identifier id : items) if (!BuiltInRegistries.ITEM.containsKey(id)) Timber.LOG.warn("Timber: config tools entry '{}' is no item in this game", id);
        }
        return items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
