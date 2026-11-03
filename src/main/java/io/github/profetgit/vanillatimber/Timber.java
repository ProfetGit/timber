package io.github.profetgit.vanillatimber;

import io.github.profetgit.vanillatimber.net.Hello;
import io.github.profetgit.vanillatimber.server.Config;
import io.github.profetgit.vanillatimber.server.Falls;
import io.github.profetgit.vanillatimber.server.Players;
import io.github.profetgit.vanillatimber.server.TimberCommand;
import io.github.profetgit.vanillatimber.server.TreeTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry. */
public final class Timber implements ModInitializer {
    public static final String ID = "vanilla_timber";
    public static final Logger LOG = LoggerFactory.getLogger("Profet's Timber");

    @Override
    public void onInitialize() {
        Config.load();
        Hello.register();
        Falls.register();
        TreeTypes.register();
        if (io.github.profetgit.vanillatimber.selftest.TestBlocks.wanted()) io.github.profetgit.vanillatimber.selftest.TestBlocks.register();
        Players.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> TimberCommand.register(dispatcher));
        LOG.info("Profet's Timber loaded");
    }
}
