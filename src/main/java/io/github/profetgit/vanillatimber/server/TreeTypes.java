package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.Timber;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The loaded tree types: data types first (sorted by id), the built-in overworld type last. */
public final class TreeTypes {
    public static final TagKey<Block> NEVER = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("timber", "never"));
    private static volatile List<TreeType> data = List.of();

    private TreeTypes() {}

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Timber.ID, "tree_types");

    /** The server data reload listener for {@code data/<ns>/timber/tree_type/*.json}; each loader's entry registers it as {@link #ID}. */
    public static PreparableReloadListener listener() {
        return new Listener();
    }

    /** The type whose trunk this block is, or null: not a tree's trunk (PRD 5.2 logs minus {@code #timber:never}). */
    public static TreeType forTrunk(BlockState s) {
        if (s.is(NEVER)) return null;
        for (TreeType t : data) if (s.is(t.trunk())) return t;
        return s.is(TreeType.OVERWORLD.trunk()) || s.is(TreeScan.LOGS) ? TreeType.OVERWORLD : null;
    }

    private static final class Listener extends SimpleJsonResourceReloadListener<TreeType> {
        Listener() {
            super(TreeType.CODEC, FileToIdConverter.json("timber/tree_type"));
        }

        @Override
        protected void apply(Map<Identifier, TreeType> types, ResourceManager manager, ProfilerFiller profiler) {
            List<Identifier> ids = new ArrayList<>(types.keySet());
            ids.sort(null);
            List<TreeType> out = new ArrayList<>();
            for (Identifier id : ids) out.add(types.get(id));
            data = List.copyOf(out);
            Timber.LOG.info("tree types: {}", ids);
        }
    }
}
