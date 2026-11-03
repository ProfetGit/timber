package io.github.profetgit.vanillatimber;

import io.github.profetgit.vanillatimber.platform.Platform;
import io.github.profetgit.vanillatimber.server.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry: each loader's entry ({@code platform/<loader>/}) calls {@link #init} first, then wires its events. */
public final class Timber {
    public static final String ID = "vanilla_timber";
    public static final Logger LOG = LoggerFactory.getLogger("Profet's Timber");
    private static Platform platform;

    private Timber() {}

    public static Platform platform() {
        return platform;
    }

    public static void init(Platform p) {
        platform = p;
        Config.load();
        LOG.info("Profet's Timber loaded ({})", p.loader());
    }
}
