package io.github.profetgit.vanillatimber.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Add-on hooks for mods (data packs use the {@code #timber:api/*} function tags). Loader-neutral on purpose. */
public final class TimberEvents {
    private TimberEvents() {}

    /** Listeners run in registration order, on the server thread. */
    public static final class Registry<T> {
        private final List<T> listeners = new CopyOnWriteArrayList<>();

        public void register(T listener) {
            listeners.add(listener);
        }

        public void unregister(T listener) {
            listeners.remove(listener);
        }

        public List<T> listeners() {
            return listeners;
        }
    }

    /**
     * Asked once per chop that passed Timber's own gates (toggle, game mode, sneak, axe), before the tree is scanned.
     * Return null to allow, {@link Component#empty()} to cancel silently (the chopped log still breaks), or any other
     * text to cancel and show it on the action bar (when the server's {@code feedback} setting is on). The first
     * listener that returns non-null decides; they run before the {@code #timber:api/cancel} function tag.
     */
    @FunctionalInterface
    public interface Cancel {
        @Nullable Component cancel(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state);
    }

    public static final Registry<Cancel> CANCEL = new Registry<>();
}
