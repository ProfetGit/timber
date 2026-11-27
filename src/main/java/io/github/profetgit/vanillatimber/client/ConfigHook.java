package io.github.profetgit.vanillatimber.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * The settings screen for a mod-list config button: loader entries ask here, so they need no version branches. Every
 * version and loader opens the shared Profet screen (tools/ProfetSettings) with Timber selected; {@link TimberScreen}
 * is kept for its parity checks, unused by players.
 */
public final class ConfigHook {
    public static final boolean AVAILABLE = true;

    public static Screen create(Screen parent) {
        return io.github.profetgit.profetsettings.ProfetSettings.screen(parent, io.github.profetgit.vanillatimber.Timber.ID);
    }

    private ConfigHook() {}
}
