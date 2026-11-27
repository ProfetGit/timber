package io.github.profetgit.vanillatimber.platform.fabric;

//? if fabric {
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.profetgit.vanillatimber.client.ConfigHook;

/** Mod Menu's settings button (only loaded when Mod Menu is installed; compiled against it, never shipped with it). */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigHook::create;
    }
}
//?}
