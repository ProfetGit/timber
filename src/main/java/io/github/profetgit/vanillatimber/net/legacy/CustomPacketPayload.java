package io.github.profetgit.vanillatimber.net.legacy;

//? if <1.20.5 {
/*import net.minecraft.resources.ResourceLocation;

// stands in for 1.20.5's CustomPacketPayload before it existed (dev/renames.json points the imports here): a payload is its channel id
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
*///?}
