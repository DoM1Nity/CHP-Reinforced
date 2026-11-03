package com.dom1nity.chpreinforced.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dom1nity.chpreinforced.ChpReinforced;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

/** Saved per dimension as data/chpreinforced.dat. */
public final class ReinforcedData extends SavedData {
    public static final String NAME = ChpReinforced.MODID;
    private static final int VERSION = 1;

    public final ReinforcedMap map = new ReinforcedMap();

    public ReinforcedData() {
    }

    public static ReinforcedData load(CompoundTag tag) {
        ReinforcedData data = new ReinforcedData();
        ListTag paletteTag = tag.getList("palette", Tag.TAG_STRING);
        ListTag ownersTag = tag.getList("owners", Tag.TAG_COMPOUND);
        long[] positions = tag.getLongArray("positions");
        int[] blocks = tag.getIntArray("blocks");
        int[] owners = tag.getIntArray("ownerIds");
        Block[] palette = new Block[paletteTag.size()];
        OwnerRef[] ownerRefs = new OwnerRef[ownersTag.size()];
        int skipped = 0;

        for (int i = 0; i < palette.length; i++) {
            ResourceLocation id = ResourceLocation.tryParse(paletteTag.getString(i));

            palette[i] = id != null && ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
        }

        for (int i = 0; i < ownerRefs.length; i++) {
            CompoundTag ownerTag = ownersTag.getCompound(i);

            if (ownerTag.hasUUID("id"))
                ownerRefs[i] = new OwnerRef(ownerTag.getUUID("id"), ownerTag.getString("name"));
        }

        int count = Math.min(positions.length, Math.min(blocks.length, owners.length));

        for (int i = 0; i < count; i++) {
            int b = blocks[i];
            int o = owners[i];
            Block block = b >= 0 && b < palette.length ? palette[b] : null;
            OwnerRef owner = o >= 0 && o < ownerRefs.length ? ownerRefs[o] : null;

            if (block == null || owner == null) {
                skipped++;
                continue;
            }

            data.map.put(positions[i], block, owner);
        }

        if (skipped > 0)
            ChpReinforced.LOGGER.warn("Skipped {} reinforced positions whose block or owner no longer exists", skipped);

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        Map<Block, Integer> blockIndex = new HashMap<>();
        Map<OwnerRef, Integer> ownerIndex = new HashMap<>();
        List<OwnerRef> ownerList = new ArrayList<>();
        ListTag palette = new ListTag();
        int size = map.size();
        long[] positions = new long[size];
        int[] blocks = new int[size];
        int[] owners = new int[size];
        int n = 0;

        for (Long2ObjectMap.Entry<Entry> mapEntry : map.view().long2ObjectEntrySet()) {
            Entry entry = mapEntry.getValue();
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(entry.block());

            if (id == null)
                continue;

            Integer b = blockIndex.get(entry.block());

            if (b == null) {
                b = palette.size();
                blockIndex.put(entry.block(), b);
                palette.add(StringTag.valueOf(id.toString()));
            }

            Integer o = ownerIndex.get(entry.owner());

            if (o == null) {
                o = ownerList.size();
                ownerIndex.put(entry.owner(), o);
                ownerList.add(entry.owner());
            }

            positions[n] = mapEntry.getLongKey();
            blocks[n] = b;
            owners[n] = o;
            n++;
        }

        ListTag ownersTag = new ListTag();

        for (OwnerRef owner : ownerList) {
            CompoundTag ownerTag = new CompoundTag();

            ownerTag.putUUID("id", owner.id());
            ownerTag.putString("name", owner.name());
            ownersTag.add(ownerTag);
        }

        tag.putInt("version", VERSION);
        tag.put("palette", palette);
        tag.put("owners", ownersTag);
        tag.putLongArray("positions", Arrays.copyOf(positions, n));
        tag.putIntArray("blocks", Arrays.copyOf(blocks, n));
        tag.putIntArray("ownerIds", Arrays.copyOf(owners, n));
        return tag;
    }
}
