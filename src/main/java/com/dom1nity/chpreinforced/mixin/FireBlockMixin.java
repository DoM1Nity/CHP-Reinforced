package com.dom1nity.chpreinforced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dom1nity.chpreinforced.core.Reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;

/** Fire never burns away reinforced blocks. tryCatchFire is Forge's name for vanilla's checkBurnOut. */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "tryCatchFire", at = @At("HEAD"), cancellable = true, remap = false)
    private void chpreinforced$tryCatchFire(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci) {
        if (!level.isClientSide && Reinforcement.isProtected(level, pos))
            ci.cancel();
    }
}
