package io.github.profetgit.vanillatimber.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Mixins into other mods (package {@code compat}) apply only when their target class exists and has a method with
 * exactly the name and descriptor listed in {@link #METHODS}: a handler that doesn't match crashes the game even with
 * require = 0, so a mismatch skips the mixin instead (pattern from Snappy Blocks).
 */
public final class MixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> METHODS = Map.of(
        "IrisShadowMixin", "renderEntities(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/renderer/culling/Frustum;DDD)I");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.contains(".compat.")) return true;
        ClassNode node;
        try {
            node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
        } catch (Throwable absent) {
            return false;
        }
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        String wanted = METHODS.get(name);
        if (wanted == null) return true;
        for (MethodNode m : node.methods) {
            if ((m.name + m.desc).equals(wanted)) {
                org.slf4j.LoggerFactory.getLogger("Timber").info("Timber: {} applies", name);
                return true;
            }
        }
        org.slf4j.LoggerFactory.getLogger("Timber").warn("Timber: {} skipped, {} has no {}", name, targetClassName, wanted);
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
