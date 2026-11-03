package com.dom1nity.chpreinforced.net;

import com.dom1nity.chpreinforced.ChpReinforced;
import com.dom1nity.chpreinforced.core.Entry;
import com.dom1nity.chpreinforced.core.ServerStore;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(ChpReinforced.MODID, "main"), () -> PROTOCOL, NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    private Network() {
    }

    public static void register() {
        CHANNEL.messageBuilder(SyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPacket::encode)
                .decoder(SyncPacket::decode)
                .consumerMainThread(SyncPacket::handle)
                .add();
    }

    /**
     * The mod is optional on the other side of the connection: a player without it can still join (they just get no
     * outlines and the server alone enforces the protection), so only send to players that have the channel.
     */
    private static boolean hasMod(ServerPlayer player) {
        return player.connection != null && CHANNEL.isRemotePresent(player.connection.connection);
    }

    /** Sends the whole data set of the player's current dimension. */
    public static void sendFull(ServerPlayer player) {
        if (!hasMod(player))
            return;

        ServerLevel level = player.serverLevel();

        for (SyncPacket packet : SyncPacket.full(level.dimension(), ServerStore.getOrLoad(level).map)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    private static void sendToDimension(ServerLevel level, SyncPacket packet) {
        for (ServerPlayer player : level.players()) {
            if (hasMod(player))
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void sendAdded(ServerLevel level, long pos, Entry entry) {
        sendToDimension(level, SyncPacket.added(level.dimension(), pos, entry));
    }

    public static void sendRemoved(ServerLevel level, long... positions) {
        if (positions.length > 0)
            sendToDimension(level, SyncPacket.removed(level.dimension(), positions));
    }
}
