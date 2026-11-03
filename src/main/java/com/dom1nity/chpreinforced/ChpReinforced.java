package com.dom1nity.chpreinforced;

import com.dom1nity.chpreinforced.net.Network;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * CHP Reinforced Blocks.
 *
 * Lets SecurityCraft's Universal Block Reinforcer protect blocks that SecurityCraft itself cannot convert
 * (blocks from other mods). Instead of swapping the block for a reinforced copy, the protection is stored per
 * position in a per-dimension SavedData. The block stays exactly the same block, so visuals, connections and
 * behaviour are unchanged, and removing the mod never deletes anything.
 */
@Mod(ChpReinforced.MODID)
public final class ChpReinforced {
    public static final String MODID = "chpreinforced";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChpReinforced() {
        Network.register();
    }
}
