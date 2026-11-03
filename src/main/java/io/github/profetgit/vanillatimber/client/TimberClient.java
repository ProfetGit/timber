package io.github.profetgit.vanillatimber.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entry. */
public final class TimberClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Profet's Timber");

    @Override
    public void onInitializeClient() {
        LOG.info("Profet's Timber client loaded");
    }
}
