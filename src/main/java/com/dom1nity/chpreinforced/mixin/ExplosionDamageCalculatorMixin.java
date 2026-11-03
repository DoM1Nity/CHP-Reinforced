package com.dom1nity.chpreinforced.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dom1nity.chpreinforced.core.Reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Reinforced blocks absorb explosions like bedrock, so a reinforced wall also shields what is behind it. (The blocks
 * themselves are additionally removed from every explosion's block list in ExplosionEvent.Detonate.)
 */
@Mixin(ExplosionDamageCalculator.class)
public abstract class ExplosionDamageCalculatorMixin {
    @Inject(method = "getBlockExplosionResistance", at = @At("HEAD"), cancellable = true)
    private void chpreinforced$getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid, CallbackInfoReturnable<Optional<Float>> cir) {
        if (Reinforcement.protection(level, pos, state.getBlock()) != null)
            cir.setReturnValue(Optional.of(Reinforcement.BLAST_RESISTANCE));
    }
}
