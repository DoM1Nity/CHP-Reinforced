package com.dom1nity.chpreinforced.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Server-side reinforcement data, one SavedData per dimension. */
public final class ServerStore {
    private static final Map<ResourceKey<Level>, ReinforcedData> DATA = new ConcurrentHashMap<>();

    private ServerStore() {
    }

    /** Must be called on the server thread. */
    public static ReinforcedData getOrLoad(ServerLevel level) {
        ReinforcedData data = DATA.get(level.dimension());

        if (data == null) {
            data = level.getDataStorage().computeIfAbsent(ReinforcedData::load, ReinforcedData::new, ReinforcedData.NAME);
            DATA.put(level.dimension(), data);
        }

        return data;
    }

    @Nullable
    public static ReinforcedData peek(ResourceKey<Level> dimension) {
        return DATA.get(dimension);
    }

    public static void unload(ServerLevel level) {
        DATA.remove(level.dimension());
    }

    public static void clear() {
        DATA.clear();
    }
}
