package io.github.profetgit.vanillatimber.client;

import net.minecraft.client.gui.screens.Screen;

/** The settings screen for a mod-list config button: loader entries ask here, so they need no version branches. */
public final class ConfigHook {
    public static final boolean AVAILABLE = true;

    public static Screen create(Screen parent) {
        return new TimberScreen(parent);
    }

    private ConfigHook() {}
}
