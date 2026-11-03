package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.platform.Platform;
import io.github.profetgit.vanillatimber.Timber;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.2 {
import net.minecraft.server.permissions.Permissions;
//?}

/** PRD 9: other tree-felling mods. Both would fell the same tree: a warning at server start, and each op hears it once per run. */
public final class Conflicts {
    static final Map<String, String> KNOWN = Map.of("fallingtree", "FallingTree", "treechop", "HT's TreeChop");
    private static List<String> present = List.of();
    private static final Set<UUID> TOLD = new HashSet<>();

    private Conflicts() {}

    static void started() {
        use(KNOWN.keySet().stream().filter(Platform.get()::isModLoaded).sorted().toList());
    }

    static void stopped() {
        present = List.of();
        TOLD.clear();
    }

    public static List<String> present() {
        return present;
    }

    /** The conflicting mod ids found (the self-test passes its own list); logs one warning each and starts a new run. */
    public static List<String> use(List<String> ids) {
        present = ids.stream().filter(KNOWN::containsKey).toList();
        TOLD.clear();
        for (String id : present) Timber.LOG.warn("Timber: {} is also installed and fells trees too. Remove one of them.", KNOWN.get(id));
        return present;
    }

    /** Tells an op about each conflict, once per server run; true when it said something. */
    public static boolean tell(ServerPlayer player) {
//? if >=1.21.2 {
        if (present.isEmpty() || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || !TOLD.add(player.getUUID())) return false;
//?} else {
/*        if (present.isEmpty() || !player.hasPermissions(2) || !TOLD.add(player.getUUID())) return false;
*///?}
        for (String id : present) {
            String name = KNOWN.get(id);
            Say.chat(player, Component.translatableWithFallback("vanilla_timber.msg.conflict",
                "Timber: " + name + " is also installed and fells trees too. Remove one of them.", name).withStyle(ChatFormatting.YELLOW));
        }
        return true;
    }
}
