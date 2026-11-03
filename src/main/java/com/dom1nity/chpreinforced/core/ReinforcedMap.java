package com.dom1nity.chpreinforced.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.block.Block;

/** Position (BlockPos#asLong) to reinforcement entry, for one dimension. */
public final class ReinforcedMap {
    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
    private final Map<Entry, Entry> interned = new HashMap<>();
    private final Map<UUID, OwnerRef> owners = new HashMap<>();

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    @Nullable
    public Entry get(long pos) {
        return entries.get(pos);
    }

    public Entry put(long pos, Block block, OwnerRef owner) {
        OwnerRef known = owners.get(owner.id());

        if (known == null || !known.name().equals(owner.name())) {
            owners.put(owner.id(), owner);
            known = owner;
        }

        Entry entry = new Entry(block, known);
        Entry shared = interned.putIfAbsent(entry, entry);

        if (shared != null)
            entry = shared;

        entries.put(pos, entry);
        return entry;
    }

    @Nullable
    public Entry remove(long pos) {
        return entries.remove(pos);
    }

    public void clear() {
        entries.clear();
        interned.clear();
        owners.clear();
    }

    /** Read-only view. Do not modify the map while iterating it. */
    public Long2ObjectMap<Entry> view() {
        return entries;
    }

    public long[] positions() {
        return entries.keySet().toLongArray();
    }
}
