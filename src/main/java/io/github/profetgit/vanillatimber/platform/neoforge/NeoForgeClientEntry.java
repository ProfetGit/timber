package io.github.profetgit.vanillatimber.platform.neoforge;

//? neoforge {
/*import io.github.profetgit.vanillatimber.Timber;
import io.github.profetgit.vanillatimber.client.TimberClient;
import io.github.profetgit.vanillatimber.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// NeoForge client events into TimberClient
@Mod(value = Timber.ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntry implements Platform.Client {
    public NeoForgeClientEntry() {
        TimberClient.init(this);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> TimberClient.joined());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> TimberClient.left());
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ClientLevel level) TimberClient.levelTick(level);
        });
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        return c != null && c.hasChannel(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
*///?}
