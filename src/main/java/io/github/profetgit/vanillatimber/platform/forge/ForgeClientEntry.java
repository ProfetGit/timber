package io.github.profetgit.vanillatimber.platform.forge;

//? forge {
/*import io.github.profetgit.vanillatimber.client.TimberClient;
import io.github.profetgit.vanillatimber.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;

// Forge client events into TimberClient; ForgeEntry calls it on the client only
final class ForgeClientEntry implements Platform.Client {
    private ForgeClientEntry() {
    }

    static void register() {
        TimberClient.init(new ForgeClientEntry());
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(e -> TimberClient.joined());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> TimberClient.left());
        TickEvent.LevelTickEvent.Post.BUS.addListener(e -> {
            if (e.level() instanceof ClientLevel level) TimberClient.levelTick(level);
        });
    }

    // the channel covers both payloads
    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        return c != null && ForgeEntry.channel.isRemotePresent(c.getConnection());
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ForgeEntry.channel.send(payload, PacketDistributor.SERVER.noArg());
    }
}
*///?}
