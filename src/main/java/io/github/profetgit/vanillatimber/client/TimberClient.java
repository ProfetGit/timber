package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.net.ChopCells;
import io.github.profetgit.vanillatimber.net.Hello;
import io.github.profetgit.vanillatimber.net.Settings;
import io.github.profetgit.vanillatimber.net.TreeFall;
import io.github.profetgit.vanillatimber.platform.Platform;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client side: says Hello on join, then draws every TreeFall itself (the server hides its displays from us). */
public final class TimberClient {
    public static final Logger LOG = LoggerFactory.getLogger("Profet's Timber");
    /**
     * PRD 5.5 {@code draw}: "server" sends no Hello, so the server shows this player its displays like a vanilla
     * client's. A JVM property until the client config lands (M2).
     */
    public static final boolean DRAW_LOCAL = !"server".equals(System.getProperty("vanilla_timber.draw"));
    private static Platform.Client net;

    private TimberClient() {}

    /** Called once by the loader's client entry. */
    public static void init(Platform.Client client) {
        net = client;
        io.github.profetgit.profetsettings.ProfetSettings.register(io.github.profetgit.vanillatimber.Timber.ID, io.github.profetgit.vanillatimber.client.ui.TimberSettings::create);
        LOG.info("Profet's Timber client loaded");
    }

    public static void joined() {
        ClientFalls.clear();
        ChoppedCells.clear();
        if (DRAW_LOCAL && net.canSendToServer(Hello.TYPE)) net.sendToServer(new Hello(Hello.VERSION));
    }

    public static void left() {
        ClientFalls.clear();
        ChopClient.reset();
        ChoppedCells.clear();
    }

    /** A {@link TreeFall} from the server, on the client thread. */
    public static void received(TreeFall fall) {
        ClientFalls.onPayload(fall);
    }

    /** The server's chop-mode {@link Settings}, on the client thread. */
    public static void received(Settings settings) {
        ChopClient.set(settings);
    }

    /** The server's narrowed logs ({@link ChopCells}), on the client thread. */
    public static void received(ChopCells cells) {
        ChoppedCells.apply(cells);
    }

    public static void levelTick(ClientLevel level) {
        ChoppedCells.tick(level);
        ClientFalls.tick(level);
    }
}
