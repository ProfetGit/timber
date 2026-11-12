package io.github.profetgit.vanillatimber.net;

import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.core.Cut;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to a modded client (PRD 5.13): the logs chop mode has cut into, as packed cell positions with the box each one
 * still fills ({@link Cut}, sixteenths per axis). {@link Cut#WHOLE} means the cell is whole again. {@code reset} drops
 * every cell the client knew first, so the message is the full set of the level.
 */
public record ChopCells(boolean reset, long[] cells, int[] boxes) implements CustomPacketPayload {
    public static final Type<ChopCells> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Timber.ID, "chop_cells"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChopCells> CODEC = StreamCodec.of(
        (buf, m) -> {
            buf.writeBoolean(m.reset());
            buf.writeVarInt(m.cells().length);
            for (int i = 0; i < m.cells().length; i++) {
                buf.writeLong(m.cells()[i]);
                buf.writeVarInt(m.boxes()[i]);
            }
        },
        buf -> {
            boolean reset = buf.readBoolean();
            int n = Math.min(buf.readVarInt(), 65536);
            long[] cells = new long[n];
            int[] boxes = new int[n];
            for (int i = 0; i < n; i++) {
                cells[i] = buf.readLong();
                boxes[i] = buf.readVarInt();
            }
            return new ChopCells(reset, cells, boxes);
        });

    @Override
    public Type<ChopCells> type() {
        return TYPE;
    }
}
