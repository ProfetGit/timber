package io.github.profetgit.vanillatimber;

import io.github.profetgit.vanillatimber.net.Hello;
import io.github.profetgit.vanillatimber.server.Falls;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry. */
public final class Timber implements ModInitializer {
    public static final String ID = "vanilla_timber";
    public static final Logger LOG = LoggerFactory.getLogger("Profet's Timber");

    @Override
    public void onInitialize() {
        Hello.register();
        Falls.register();
        LOG.info("Profet's Timber loaded");
    }
}
