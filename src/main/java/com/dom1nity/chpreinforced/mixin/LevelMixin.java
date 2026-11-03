package com.dom1nity.chpreinforced.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dom1nity.chpreinforced.core.Reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Level#destroyBlock is what most mobs and mods use to break blocks (it drops the items and then removes the block).
 * Refusing it for reinforced positions, before anything is dropped, stops all of them at once.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void chpreinforced$destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;

        if (!level.isClientSide && Reinforcement.isProtected(level, pos))
            cir.setReturnValue(false);
    }
}
