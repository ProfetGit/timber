package io.github.profetgit.vanillatimber.server;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * The general rule for what hangs from a tree: a block belongs to the tree when it stands now and would not stand
 * once the tree's cells were air. Vanilla's own contract answers it ({@code BlockState.canSurvive}), so apples, vines,
 * cocoa, lanterns, shelf fungus and any mod's fruit are found without a list.
 *
 * <p>The "once the tree is gone" world is a read-only view of the real level in which the tree's cells read as air.
 * It is a dynamic proxy of {@link LevelReader} (no mutators exist on that interface) that dispatches by parameter and
 * return type, never by method name, because Forge 1.20.1 runs obfuscated names. A mod's {@code canSurvive} that
 * cannot run on the view (it casts the level) is reported as unknown, so the caller can still pop it afterwards.
 */
final class Support {
    private final ServerLevel level;
    private final LongOpenHashSet gone;
    private final LevelReader view;
    /** True when the last {@link #dependsOn} could not evaluate the block on the view. */
    boolean unknown;

    Support(ServerLevel level, LongOpenHashSet gone) {
        this.level = level;
        this.gone = gone;
        this.view = (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[] {LevelReader.class}, this::invoke);
    }

    /** The block at {@code pos} stands on the real level and would not on the view. */
    boolean dependsOn(BlockPos pos, BlockState s) {
        unknown = false;
        try {
            if (!s.canSurvive(level, pos)) return false;
        } catch (Throwable t) {
            return false;
        }
        try {
            return !s.canSurvive(view, pos);
        } catch (Throwable t) {
            unknown = true;
            return false;
        }
    }

    private Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        Class<?>[] pt = m.getParameterTypes();
        if (pt.length == 1 && pt[0] == BlockPos.class) {
            Class<?> rt = m.getReturnType();
            if (rt == BlockState.class) {
                BlockPos p = (BlockPos) args[0];
                return gone.contains(p.asLong()) ? Blocks.AIR.defaultBlockState() : level.getBlockState(p);
            }
            if (rt == FluidState.class) {
                BlockPos p = (BlockPos) args[0];
                return gone.contains(p.asLong()) ? Fluids.EMPTY.defaultFluidState() : level.getFluidState(p);
            }
        }
        if (m.getDeclaringClass() == Object.class) {
            return switch (m.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> "Support.view";
            };
        }
        if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
        try {
            return m.invoke(level, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
