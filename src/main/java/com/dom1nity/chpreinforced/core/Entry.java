package com.dom1nity.chpreinforced.core;

import net.minecraft.world.level.block.Block;

/**
 * One reinforced position. The block is remembered so that a stale entry (the block was replaced by some other
 * means) never protects a different block.
 */
public record Entry(Block block, OwnerRef owner) {
}
