package io.github.profetgit.vanillatimber.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.profetgit.vanillatimber.client.LightHold;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client light engine's published layers can be swapped for limited copies, see {@link LightHold}. */
@Mixin(LayerLightSectionStorage.class)
abstract class LayerLightSectionStorageMixin<M extends DataLayerStorageMap<M>> implements LightHold.Storage {
    @Shadow
    @Final
    private LightLayer layer;
    @Shadow
    @Final
    protected LightChunkGetter chunkSource;
    @Shadow
    protected volatile M visibleSectionData;
    @Shadow
    @Final
    protected M updatingSectionData;

    @Inject(method = "swapSectionMap", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/DataLayerStorageMap;disableCache()V", shift = At.Shift.AFTER))
    private void vanilla_timber$hold(CallbackInfo ci, @Local DataLayerStorageMap<?> copy) {
        if (chunkSource instanceof ClientChunkCache) LightHold.apply(layer, copy, false);
    }

    @Inject(method = "swapSectionMap", at = @At("HEAD"))
    private void vanilla_timber$register(CallbackInfo ci) {
        if (chunkSource instanceof ClientChunkCache) LightHold.register(layer, this);
    }

    @Override
    public void vanilla_timber$republish() {
        M copy = visibleSectionData.copy();
        copy.disableCache();
        LightHold.apply(layer, copy, true);
        visibleSectionData = copy;
    }

    @Override
    public DataLayer vanilla_timber$live(long node) {
        return updatingSectionData.getLayer(node);
    }
}
