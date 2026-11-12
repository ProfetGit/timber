package io.github.profetgit.vanillatimber.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The private mining state chop mode reads (PRD 5.13): which block the player is mining and since when. */
@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeAccess {
    @Accessor("gameTicks")
    int timber$gameTicks();

    @Accessor("destroyProgressStart")
    int timber$destroyProgressStart();

    @Accessor("isDestroyingBlock")
    boolean timber$isDestroyingBlock();

    @Accessor("destroyPos")
    BlockPos timber$destroyPos();

    @Accessor("hasDelayedDestroy")
    boolean timber$hasDelayedDestroy();

    @Accessor("delayedDestroyPos")
    BlockPos timber$delayedDestroyPos();

    @Accessor("delayedTickStart")
    int timber$delayedTickStart();
}
