package io.github.profetgit.vanillatimber.client.ui;

import io.github.profetgit.profetsettings.ModDef;
import io.github.profetgit.vanillatimber.server.Config.Key;

/** Timber's preview scenes, one per kind named by {@link TimberSettings}. */
final class Scenes {
    private Scenes() {}

    // one scene per Quick setup, playing with the preset's own values: kind "preset.<id>"
    static void presets(ModDef m, String[] ids) {
        for (int i = 0; i < m.presets.size() && i < ids.length; i++) {
            ModDef.Preset p = m.presets.get(i);
            java.util.Map<Key, Integer> v = new java.util.EnumMap<>(Key.class);
            for (Key k : Key.values()) v.put(k, k.def);
            p.values().forEach((name, value) -> {
                Key k = Key.of(name);
                io.github.profetgit.profetsettings.Setting s = m.find(name);
                if (k == null || s == null) return;
                int n = value instanceof Boolean b ? (b ? 1 : 0) : (Integer) value;
                v.put(k, s.kind == io.github.profetgit.profetsettings.Setting.Kind.CHOICE ? n + k.min : n);
            });
            String id = ids[i];
            m.presetPreview(p.name(), "preset." + id, net.minecraft.network.chat.Component.translatableWithFallback("vanilla_timber.preset." + id + ".note", "").getString());
            m.scene("preset." + id, d -> switch (id) {
                case "chop" -> new ChopScene().pin(v);
                case "tidy" -> new FallScene(FallScene.Topic.REPLANT).pin(v);
                case "fallen" -> new FallScene(FallScene.Topic.LAND).pin(v);
                default -> new FallScene(FallScene.Topic.FALL).pin(v);
            });
        }
    }

    static void register(ModDef m) {
        m.scene("fall", d -> new FallScene(FallScene.Topic.FALL))
            .scene("drops", d -> new FallScene(FallScene.Topic.DROPS))
            .scene("land", d -> new FallScene(FallScene.Topic.LAND))
            .scene("replant", d -> new FallScene(FallScene.Topic.REPLANT))
            .scene("knock", d -> new KnockScene())
            .scene("durability", d -> new DurabilityScene())
            .scene("chop", d -> new ChopScene())
            .scene("sneak", d -> new RoundScene(RoundScene.Topic.SNEAK))
            .scene("tool", d -> new RoundScene(RoundScene.Topic.TOOL))
            .scene("protect", d -> new RoundScene(RoundScene.Topic.PROTECT))
            .scene("feedback", d -> new RoundScene(RoundScene.Topic.FEEDBACK));
    }
}
