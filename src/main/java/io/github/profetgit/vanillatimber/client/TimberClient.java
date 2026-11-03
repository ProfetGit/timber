package io.github.profetgit.vanillatimber.client;

import io.github.profetgit.vanillatimber.net.Hello;
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
        LOG.info("Profet's Timber client loaded");
    }

    public static void joined() {
        ClientFalls.clear();
        if (DRAW_LOCAL && net.canSendToServer(Hello.TYPE)) net.sendToServer(new Hello(Hello.VERSION));
    }

    public static void left() {
        ClientFalls.clear();
    }

    /** A {@link TreeFall} from the server, on the client thread. */
    public static void received(TreeFall fall) {
        ClientFalls.onPayload(fall);
    }

    public static void levelTick(ClientLevel level) {
        ClientFalls.tick(level);
    }
}
