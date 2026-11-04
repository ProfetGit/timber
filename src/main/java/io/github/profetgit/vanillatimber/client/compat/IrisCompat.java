package io.github.profetgit.vanillatimber.client.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.profetgit.vanillatimber.Timber;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hands a shader pack the block id of each mesh block, the way Iris does for chunk meshes: with a pack on, buffers carry
 * mc_Entity, so materials (leaves, bark, emissive blocks) treat the fall as the blocks it replaces. Reflective, so
 * Timber has no Iris dependency (the mesh shadow hook is {@code mixin.compat.IrisShadowMixin}). Pattern from SeamlessDisplays.
 */
public final class IrisCompat {
    private static MethodHandle begin, end, packInUse, shadows, idsGetter;
    private static Class<?> sensitive;
    private static boolean tried;
    private static Object2IntMap<BlockState> ids;

    private IrisCompat() {
    }

    /** Once per drawn pass: is a shader pack running, and its block ids. */
    @SuppressWarnings("unchecked")
    public static void frame() {
        if (!tried) {
            tried = true;
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                try {
                    MethodHandles.Lookup l = MethodHandles.publicLookup();
                    sensitive = Class.forName("net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder");
                    begin = l.findVirtual(sensitive, "beginBlock", MethodType.methodType(void.class, int.class, byte.class, byte.class, int.class, int.class, int.class))
                        .asType(MethodType.methodType(void.class, Object.class, int.class, byte.class, byte.class, int.class, int.class, int.class));
                    end = l.findVirtual(sensitive, "endBlock", MethodType.methodType(void.class)).asType(MethodType.methodType(void.class, Object.class));
                    Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                    packInUse = l.findStatic(iris, "isPackInUseQuick", MethodType.methodType(boolean.class));
                    shadows = l.findStatic(Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState"), "areShadowsCurrentlyBeingRendered", MethodType.methodType(boolean.class));
                    Class<?> settings = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
                    Object instance = l.findStaticGetter(settings, "INSTANCE", settings).invoke();
                    idsGetter = l.findVirtual(settings, "getBlockStateIds", MethodType.methodType(Object2IntMap.class)).bindTo(instance);
                } catch (Throwable t) {
                    Timber.LOG.warn("Iris block ids unavailable ({}); the mesh keeps an unknown block id under shader packs", t.toString());
                    begin = null;
                }
            }
        }
        ids = null;
        if (begin == null) return;
        try {
            if ((boolean) packInUse.invokeExact()) ids = (Object2IntMap<BlockState>) (Object) idsGetter.invoke();
        } catch (Throwable t) {
            begin = null;
        }
    }

    /** A shader pack is running (as of the last {@link #frame}). */
    public static boolean active() {
        return ids != null;
    }

    public static boolean inShadowPass() {
        if (ids == null) return false;
        try {
            return (boolean) shadows.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    public static Object2IntMap<BlockState> ids() {
        return ids;
    }

    public static boolean applies(VertexConsumer buffer) {
        return ids != null && sensitive.isInstance(buffer);
    }

    public static void begin(VertexConsumer buffer, int id, int emission, int lx, int ly, int lz) {
        try {
            begin.invokeExact((Object) buffer, id, (byte) 0, (byte) emission, lx, ly, lz);
        } catch (Throwable t) {
            ids = null;
        }
    }

    public static void end(VertexConsumer buffer) {
        try {
            end.invokeExact((Object) buffer);
        } catch (Throwable t) {
            ids = null;
        }
    }
}
