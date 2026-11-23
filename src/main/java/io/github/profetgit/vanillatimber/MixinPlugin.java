package io.github.profetgit.vanillatimber;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Mixins into other mods (package {@code compat}) apply only when their target class exists and has a method of the
 * name and shape listed in {@link #METHODS}: a handler that doesn't match crashes the game even with require = 0, so a
 * mismatch skips the mixin instead (pattern from Snappy Blocks). A shape is {@code name:parameter count:return}, with the
 * trailing parameters checked as listed in {@link #TAILS}: Minecraft's own types are named differently per loader
 * (intermediary, SRG, Mojang), so the full descriptor can't be listed.
 */
public final class MixinPlugin implements IMixinConfigPlugin {
    /** Iris's {@code ShadowRenderer.renderEntities(accessor, dispatcher, [buffers,] poseStack, tickDelta, frustum, x, y, z)}: the buffers are the version before 26.1. */
    private static final Map<String, String> METHODS = Map.of(
//? if >=26.1 {
        "IrisShadowMixin", "renderEntities:8:I"
//?} else {
/*        "IrisShadowMixin", "renderEntities:9:I"
*///?}
    );
    private static final Map<String, String> TAILS = Map.of("IrisShadowMixin", "DDD)");

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
        if (targetClassName.startsWith("net.minecraft.")) return false; // a version's placeholder target
        ClassNode node;
        try {
            node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
        } catch (Throwable absent) {
            return false;
        }
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        String wanted = METHODS.get(name);
        if (wanted == null) return true;
        String[] shape = wanted.split(":");
        String tail = TAILS.get(name);
        for (MethodNode m : node.methods) {
            if (m.name.equals(shape[0]) && org.objectweb.asm.Type.getArgumentTypes(m.desc).length == Integer.parseInt(shape[1])
                && org.objectweb.asm.Type.getReturnType(m.desc).getDescriptor().equals(shape[2])
                && (tail == null || (m.desc.substring(0, m.desc.indexOf(')') + 1)).endsWith(tail))) {
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
