package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.core.Cut;
import io.github.profetgit.vanillatimber.net.ChopCells;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chop mode's cut logs on the client (PRD 5.13): cell position to the box its log still fills ({@link Cut}), as the
 * server last said. Read by the chunk meshing threads ({@link ChopModels}), written on the client thread, which marks the
 * changed cells for a re-mesh.
 */
public final class ChoppedCells {
    private static final Map<Long, Integer> BOXES = new ConcurrentHashMap<>();
    private static volatile boolean any;
    private static ClientLevel level;

    private ChoppedCells() {}

    /** The box the log at {@code pos} still fills: {@link Cut#WHOLE} when it is whole. */
    public static int box(BlockPos pos) {
        if (!any) return Cut.WHOLE;
        Integer b = BOXES.get(pos.asLong());
        return b == null ? Cut.WHOLE : b;
    }

    public static boolean any() {
        return any;
    }

    public static void apply(ChopCells msg) {
        Set<Long> changed = new HashSet<>();
        if (msg.reset()) {
            Set<Long> keep = new HashSet<>();
            for (int i = 0; i < msg.cells().length; i++) if (msg.boxes()[i] != Cut.WHOLE) keep.add(msg.cells()[i]);
            for (Long old : new HashSet<>(BOXES.keySet())) {
                if (!keep.contains(old)) {
                    BOXES.remove(old);
                    changed.add(old);
                }
            }
        }
        for (int i = 0; i < msg.cells().length; i++) {
            long c = msg.cells()[i];
            int b = msg.boxes()[i];
            Integer was = b == Cut.WHOLE ? BOXES.remove(c) : BOXES.put(c, b);
            if (b == Cut.WHOLE ? was != null : was == null || was != b) changed.add(c);
        }
        any = !BOXES.isEmpty();
        dirty(changed);
    }

    public static void clear() {
        Set<Long> old = new HashSet<>(BOXES.keySet());
        BOXES.clear();
        any = false;
        dirty(old);
    }

    /** A new level (dimension change, new world): nothing carries over. */
    public static void tick(ClientLevel now) {
        if (now == level) return;
        level = now;
        BOXES.clear();
        any = false;
    }

    /** Self-test: the boxes as the client holds them. */
    public static Map<Long, Integer> snapshot() {
        return new HashMap<>(BOXES);
    }

    private static void dirty(Set<Long> cells) {
        ClientLevel lvl = Minecraft.getInstance().level;
        if (lvl == null || cells.isEmpty()) return;
        for (long c : cells) {
            BlockPos pos = BlockPos.of(c);
            if (!lvl.hasChunkAt(pos)) continue;
            BlockState s = lvl.getBlockState(pos);
            lvl.sendBlockUpdated(pos, s, s, 0);
        }
    }
}
