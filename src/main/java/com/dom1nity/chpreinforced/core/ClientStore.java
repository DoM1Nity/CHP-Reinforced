package com.dom1nity.chpreinforced.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.dom1nity.chpreinforced.net.SyncPacket;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Client-side copy of the reinforcement data, filled by {@link SyncPacket}. Contains no client-only classes, so it is
 * safe to load on a dedicated server (where it simply stays empty).
 */
public final class ClientStore {
    private static final Map<ResourceKey<Level>, ReinforcedMap> MAPS = new ConcurrentHashMap<>();

    private ClientStore() {
    }

    @Nullable
    public static ReinforcedMap get(ResourceKey<Level> dimension) {
        return MAPS.get(dimension);
    }

    /** Must be called on the client main thread. */
    public static void apply(SyncPacket packet) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, packet.dimension());
        ReinforcedMap map = MAPS.computeIfAbsent(dimension, k -> new ReinforcedMap());

        if (packet.reset())
            map.clear();

        packet.forEachRemoval(map::remove);
        packet.forEachAddition(map::put);
    }

    public static void clear() {
        MAPS.clear();
    }
}
