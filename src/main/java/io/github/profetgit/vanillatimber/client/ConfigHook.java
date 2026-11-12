package io.github.profetgit.vanillatimber.client;

import net.minecraft.client.gui.screens.Screen;

/** The settings screen for a mod-list config button: loader entries ask here, so they need no version branches. */
public final class ConfigHook {
//? if >=26.2 {
    public static final boolean AVAILABLE = true;

    public static Screen create(Screen parent) {
        return new TimberScreen(parent);
    }
//?} else {
/*    public static final boolean AVAILABLE = false;

    public static Screen create(Screen parent) {
        return parent;
    }
*///?}

    private ConfigHook() {}
}
