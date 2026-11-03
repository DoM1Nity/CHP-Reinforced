package com.dom1nity.chpreinforced.core;

import java.util.UUID;

import net.minecraft.world.entity.player.Player;

/** The player who reinforced a block. */
public record OwnerRef(UUID id, String name) {
    public static OwnerRef of(Player player) {
        return new OwnerRef(player.getUUID(), player.getGameProfile().getName());
    }
}
