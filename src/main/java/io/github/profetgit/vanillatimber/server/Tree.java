package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.core.FallSpec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A scanned tree, ready to fall: its blocks with their rest corners in the tree frame (x1000, as 1.3.6 kept them),
 * the pivot (x1000; y is the spawn height, hang lift included), the yaw bin and the timeline's spec.
 *
 * @param anchor the tree block in the pivot's cell (removed at the chop: displays are lit by their entity's cell), or null
 * @param hw     half the trunk width toward the fall, x1000 (the lying trunk's centre line is hw behind the pivot)
 * @param cw     crown half-width across the fall, x1000 (1.3.6 {@code #cw}: 600..4000), for the leaf bursts
 * @param log    the chopped log; {@code leaf} the last leaf the walk took (the particles' block), or null
 * @param roots  this tree's own trunk cells standing on ground, at or above the cut (where auto-replant plants)
 */
public record Tree(BlockPos origin, List<Part> parts, int px, int py, int pz, int bin, FallSpec spec, boolean[] logRings,
                   BlockPos anchor, int gap, boolean hang, int logs, int leaves, int hw, int cw, BlockState log, BlockState leaf,
                   List<BlockPos> roots) {
    /** One block: world cell, state, leaf or log, rest corner (tx, wy, tz) x1000 and ring; {@code hidden}: a leaf shut in on all sides, no display (hide_leaves). */
    public record Part(BlockPos pos, BlockState state, boolean leaf, int tx, int wy, int tz, int k, boolean hidden) {}

    public double yaw() {
        return bin * 11.25;
    }
}
