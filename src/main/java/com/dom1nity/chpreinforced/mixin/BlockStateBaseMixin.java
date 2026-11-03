package com.dom1nity.chpreinforced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dom1nity.chpreinforced.core.Entry;
import com.dom1nity.chpreinforced.core.Reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Reinforced positions report a hardness of -1 (unbreakable), like SecurityCraft's reinforced blocks, so mobs and
 * tools that check hardness leave them alone. The owner still mines them at normal speed.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Shadow
    public abstract Block getBlock();

    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void chpreinforced$getDestroySpeed(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (Reinforcement.protection(level, pos, getBlock()) != null && !Reinforcement.isBypassing())
            cir.setReturnValue(-1.0F);
    }

    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void chpreinforced$getDestroyProgressHead(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        Entry entry = Reinforcement.protection(level, pos, getBlock());

        if (entry == null)
            return;

        if (Reinforcement.isAllowed(player, entry))
            Reinforcement.setBypass(true);
        else
            cir.setReturnValue(0.0F);
    }

    @Inject(method = "getDestroyProgress", at = @At("RETURN"))
    private void chpreinforced$getDestroyProgressReturn(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (Reinforcement.isBypassing())
            Reinforcement.setBypass(false);
    }
}
