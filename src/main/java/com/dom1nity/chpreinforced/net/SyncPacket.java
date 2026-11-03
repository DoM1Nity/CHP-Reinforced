package com.dom1nity.chpreinforced.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import com.dom1nity.chpreinforced.core.ClientStore;
import com.dom1nity.chpreinforced.core.Entry;
import com.dom1nity.chpreinforced.core.OwnerRef;
import com.dom1nity.chpreinforced.core.ReinforcedMap;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Server to client: full or partial reinforcement data for one dimension. Blocks are sent as registry names through a
 * small palette, so the packet does not depend on numeric registry ids.
 */
public final class SyncPacket {
    private static final int MAX_ENTRIES_PER_PACKET = 8192;

    private final ResourceLocation dimension;
    private final boolean reset;
    private final List<ResourceLocation> blockPalette;
    private final List<OwnerRef> ownerPalette;
    private final long[] positions;
    private final int[] blockIds;
    private final int[] ownerIds;
    private final long[] removals;

    private SyncPacket(ResourceLocation dimension, boolean reset, List<ResourceLocation> blockPalette, List<OwnerRef> ownerPalette, long[] positions, int[] blockIds, int[] ownerIds, long[] removals) {
        this.dimension = dimension;
        this.reset = reset;
        this.blockPalette = blockPalette;
        this.ownerPalette = ownerPalette;
        this.positions = positions;
        this.blockIds = blockIds;
        this.ownerIds = ownerIds;
        this.removals = removals;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public boolean reset() {
        return reset;
    }

    /** Builds the packets that replace the client's whole data set for a dimension. Never returns an empty list. */
    public static List<SyncPacket> full(ResourceKey<Level> dimension, ReinforcedMap map) {
        List<SyncPacket> packets = new ArrayList<>();
        Builder builder = new Builder(dimension.location(), true);

        for (Long2ObjectMap.Entry<Entry> mapEntry : map.view().long2ObjectEntrySet()) {
            builder.add(mapEntry.getLongKey(), mapEntry.getValue());

            if (builder.size() >= MAX_ENTRIES_PER_PACKET) {
                packets.add(builder.build());
                builder = new Builder(dimension.location(), false);
            }
        }

        if (packets.isEmpty() || builder.size() > 0)
            packets.add(builder.build());

        return packets;
    }

    public static SyncPacket added(ResourceKey<Level> dimension, long pos, Entry entry) {
        Builder builder = new Builder(dimension.location(), false);

        builder.add(pos, entry);
        return builder.build();
    }

    public static SyncPacket removed(ResourceKey<Level> dimension, long... positions) {
        return new SyncPacket(dimension.location(), false, List.of(), List.of(), new long[0], new int[0], new int[0], positions.clone());
    }

    public void forEachRemoval(LongConsumer consumer) {
        for (long pos : removals) {
            consumer.accept(pos);
        }
    }

    public void forEachAddition(AdditionConsumer consumer) {
        Block[] blocks = new Block[blockPalette.size()];

        for (int i = 0; i < blocks.length; i++) {
            ResourceLocation id = blockPalette.get(i);

            blocks[i] = ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
        }

        for (int i = 0; i < positions.length; i++) {
            int b = blockIds[i];
            int o = ownerIds[i];

            if (b < 0 || b >= blocks.length || blocks[b] == null || o < 0 || o >= ownerPalette.size())
                continue;

            consumer.accept(positions[i], blocks[b], ownerPalette.get(o));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBoolean(reset);
        buf.writeVarInt(blockPalette.size());

        for (ResourceLocation id : blockPalette) {
            buf.writeResourceLocation(id);
        }

        buf.writeVarInt(ownerPalette.size());

        for (OwnerRef owner : ownerPalette) {
            buf.writeUUID(owner.id());
            buf.writeUtf(owner.name(), 64);
        }

        buf.writeVarInt(positions.length);

        for (int i = 0; i < positions.length; i++) {
            buf.writeLong(positions[i]);
            buf.writeVarInt(blockIds[i]);
            buf.writeVarInt(ownerIds[i]);
        }

        buf.writeVarInt(removals.length);

        for (long pos : removals) {
            buf.writeLong(pos);
        }
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        boolean reset = buf.readBoolean();
        int blockCount = buf.readVarInt();
        List<ResourceLocation> blockPalette = new ArrayList<>(blockCount);

        for (int i = 0; i < blockCount; i++) {
            blockPalette.add(buf.readResourceLocation());
        }

        int ownerCount = buf.readVarInt();
        List<OwnerRef> ownerPalette = new ArrayList<>(ownerCount);

        for (int i = 0; i < ownerCount; i++) {
            ownerPalette.add(new OwnerRef(buf.readUUID(), buf.readUtf(64)));
        }

        int count = buf.readVarInt();
        long[] positions = new long[count];
        int[] blockIds = new int[count];
        int[] ownerIds = new int[count];

        for (int i = 0; i < count; i++) {
            positions[i] = buf.readLong();
            blockIds[i] = buf.readVarInt();
            ownerIds[i] = buf.readVarInt();
        }

        long[] removals = new long[buf.readVarInt()];

        for (int i = 0; i < removals.length; i++) {
            removals[i] = buf.readLong();
        }

        return new SyncPacket(dimension, reset, blockPalette, ownerPalette, positions, blockIds, ownerIds, removals);
    }

    public static void handle(SyncPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientStore.apply(packet);
    }

    @FunctionalInterface
    public interface AdditionConsumer {
        void accept(long pos, Block block, OwnerRef owner);
    }

    private static final class Builder {
        private final ResourceLocation dimension;
        private final boolean reset;
        private final Map<Block, Integer> blockIndex = new HashMap<>();
        private final Map<OwnerRef, Integer> ownerIndex = new HashMap<>();
        private final List<ResourceLocation> blockPalette = new ArrayList<>();
        private final List<OwnerRef> ownerPalette = new ArrayList<>();
        private final List<long[]> rows = new ArrayList<>();

        private Builder(ResourceLocation dimension, boolean reset) {
            this.dimension = dimension;
            this.reset = reset;
        }

        private int size() {
            return rows.size();
        }

        private void add(long pos, Entry entry) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(entry.block());

            if (id == null)
                return;

            Integer b = blockIndex.get(entry.block());

            if (b == null) {
                b = blockPalette.size();
                blockIndex.put(entry.block(), b);
                blockPalette.add(id);
            }

            Integer o = ownerIndex.get(entry.owner());

            if (o == null) {
                o = ownerPalette.size();
                ownerIndex.put(entry.owner(), o);
                ownerPalette.add(entry.owner());
            }

            rows.add(new long[] {pos, b, o});
        }

        private SyncPacket build() {
            int n = rows.size();
            long[] positions = new long[n];
            int[] blockIds = new int[n];
            int[] ownerIds = new int[n];

            for (int i = 0; i < n; i++) {
                long[] row = rows.get(i);

                positions[i] = row[0];
                blockIds[i] = (int) row[1];
                ownerIds[i] = (int) row[2];
            }

            return new SyncPacket(dimension, reset, blockPalette, ownerPalette, positions, blockIds, ownerIds, new long[0]);
        }
    }
}
