package io.github.profetgit.vanillatimber.net;

import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.core.FallSpec;
import io.github.profetgit.vanillatimber.server.Tree;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server to a modded client: a tree is falling (PRD 5.5). Everything the client needs to draw the same fall the
 * displays show: the pivot and yaw bin (the client rebuilds each block's rest corner with {@code Layout}, from the same
 * integers as the server), the spec (the client builds the same {@code Timeline}), the ring mask, the blocks as a
 * palette plus cells relative to {@code origin}, and {@code e}, the server frames already played when it was sent
 * (0 at the chop; a player who walks in mid-fall gets the frame it walked in at).
 *
 * @param cells 4 ints per block: dx, dy, dz from {@code origin}, palette index << 1 | leaf
 */
public record TreeFall(int id, BlockPos origin, int px, int py, int pz, int bin, FallSpec spec, boolean[] logRings, int e,
                       List<BlockState> palette, int[] cells) implements CustomPacketPayload {
    public static final Type<TreeFall> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Timber.ID, "tree_fall"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TreeFall> CODEC = StreamCodec.of(TreeFall::write, TreeFall::read);

    @Override
    public Type<TreeFall> type() {
        return TYPE;
    }

    public int blocks() {
        return cells.length / 4;
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    /** The payload of one fall, sent at server frame {@code e}. Air parts (a block that changed under the scan) are left out. */
    public static TreeFall of(int id, Tree tree, int e) {
        Map<BlockState, Integer> index = new IdentityHashMap<>();
        List<BlockState> palette = new ArrayList<>();
        List<Tree.Part> parts = tree.parts();
        int[] cells = new int[parts.size() * 4];
        int n = 0;
        BlockPos o = tree.origin();
        for (Tree.Part p : parts) {
            if (p.state().isAir()) continue;
            Integer i = index.get(p.state());
            if (i == null) {
                i = palette.size();
                index.put(p.state(), i);
                palette.add(p.state());
            }
            cells[n++] = p.pos().getX() - o.getX();
            cells[n++] = p.pos().getY() - o.getY();
            cells[n++] = p.pos().getZ() - o.getZ();
            cells[n++] = i << 1 | (p.leaf() ? 1 : 0);
        }
        return new TreeFall(id, o, tree.px(), tree.py(), tree.pz(), tree.bin(), tree.spec(), tree.logRings(), e, palette,
            n == cells.length ? cells : java.util.Arrays.copyOf(cells, n));
    }

    public static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, TYPE);
    }

    public void send(ServerPlayer player) {
        ServerPlayNetworking.send(player, this);
    }

    private static void write(RegistryFriendlyByteBuf buf, TreeFall f) {
        buf.writeVarInt(f.id);
        buf.writeBlockPos(f.origin);
        buf.writeInt(f.px);
        buf.writeInt(f.py);
        buf.writeInt(f.pz);
        buf.writeByte(f.bin);
        FallSpec s = f.spec;
        buf.writeVarInt(s.h());
        buf.writeDouble(s.hit());
        buf.writeVarInt(s.dur());
        buf.writeDouble(s.amp());
        buf.writeVarInt(s.km());
        buf.writeDouble(s.fd());
        buf.writeVarInt(s.ft());
        buf.writeVarInt(s.dr());
        buf.writeVarInt(s.rs());
        buf.writeVarInt(f.logRings.length);
        for (int i = 0; i < f.logRings.length; i += 8) {
            int b = 0;
            for (int j = 0; j < 8 && i + j < f.logRings.length; j++) if (f.logRings[i + j]) b |= 1 << j;
            buf.writeByte(b);
        }
        buf.writeVarInt(f.e);
        buf.writeVarInt(f.palette.size());
        for (BlockState st : f.palette) buf.writeVarInt(Block.getId(st));
        buf.writeVarInt(f.blocks());
        for (int i = 0; i < f.cells.length; i += 4) {
            zig(buf, f.cells[i]);
            zig(buf, f.cells[i + 1]);
            zig(buf, f.cells[i + 2]);
            buf.writeVarInt(f.cells[i + 3]);
        }
    }

    private static TreeFall read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        BlockPos origin = buf.readBlockPos();
        int px = buf.readInt(), py = buf.readInt(), pz = buf.readInt(), bin = buf.readUnsignedByte();
        int h = buf.readVarInt();
        double hit = buf.readDouble();
        int dur = buf.readVarInt();
        double amp = buf.readDouble();
        int km = buf.readVarInt();
        double fd = buf.readDouble();
        int ft = buf.readVarInt(), dr = buf.readVarInt(), rs = buf.readVarInt();
        FallSpec spec = new FallSpec(h, hit, dur, amp, km, fd, ft, dr, rs);
        boolean[] rings = new boolean[buf.readVarInt()];
        for (int i = 0; i < rings.length; i += 8) {
            int b = buf.readUnsignedByte();
            for (int j = 0; j < 8 && i + j < rings.length; j++) rings[i + j] = (b >> j & 1) != 0;
        }
        int e = buf.readVarInt();
        int np = buf.readVarInt();
        List<BlockState> palette = new ArrayList<>(np);
        for (int i = 0; i < np; i++) palette.add(Block.stateById(buf.readVarInt()));
        int nb = buf.readVarInt();
        int[] cells = new int[nb * 4];
        for (int i = 0; i < cells.length; i += 4) {
            cells[i] = unzig(buf);
            cells[i + 1] = unzig(buf);
            cells[i + 2] = unzig(buf);
            cells[i + 3] = buf.readVarInt();
        }
        return new TreeFall(id, origin, px, py, pz, bin, spec, rings, e, palette, cells);
    }

    private static void zig(RegistryFriendlyByteBuf buf, int v) {
        buf.writeVarInt(v << 1 ^ v >> 31);
    }

    private static int unzig(RegistryFriendlyByteBuf buf) {
        int v = buf.readVarInt();
        return v >>> 1 ^ -(v & 1);
    }
}
