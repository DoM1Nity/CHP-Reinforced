package com.dom1nity.chpreinforced.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import com.dom1nity.chpreinforced.ChpReinforced;
import com.dom1nity.chpreinforced.core.OwnerRef;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Everything this mod needs from SecurityCraft, accessed by registry name and reflection so that SecurityCraft is not a
 * compile-time dependency and the mod still loads (doing nothing useful) without it.
 */
public final class ScCompat {
    public static final String SC_MODID = "securitycraft";
    private static final ResourceLocation REINFORCER_LVL_1 = new ResourceLocation(SC_MODID, "universal_block_reinforcer_lvl1");
    private static final ResourceLocation REINFORCER_LVL_2 = new ResourceLocation(SC_MODID, "universal_block_reinforcer_lvl2");
    private static final ResourceLocation REINFORCER_LVL_3 = new ResourceLocation(SC_MODID, "universal_block_reinforcer_lvl3");

    private static volatile boolean initialized;
    private static Map<?, ?> vanillaToSecurityCraft;
    private static Class<?> reinforcedBlockInterface;
    private static Constructor<?> ownerFromNameAndUuid;
    private static Constructor<?> ownerFromPlayer;
    private static Method isTreatedTheSameAs;

    private ScCompat() {
    }

    private static void init() {
        if (initialized)
            return;

        synchronized (ScCompat.class) {
            if (initialized)
                return;

            try {
                Class<?> reinforced = Class.forName("net.geforcemods.securitycraft.api.IReinforcedBlock");

                reinforcedBlockInterface = reinforced;
                vanillaToSecurityCraft = (Map<?, ?>) reinforced.getField("VANILLA_TO_SECURITYCRAFT").get(null);
            }
            catch (Throwable t) {
                ChpReinforced.LOGGER.info("SecurityCraft reinforcement API not found ({}), only this mod's own reinforcement will work", t.toString());
            }

            try {
                Class<?> owner = Class.forName("net.geforcemods.securitycraft.api.Owner");

                ownerFromNameAndUuid = owner.getConstructor(String.class, String.class);
                ownerFromPlayer = owner.getConstructor(Player.class);
                isTreatedTheSameAs = owner.getMethod("isTreatedTheSameAs", owner);
            }
            catch (Throwable t) {
                ChpReinforced.LOGGER.info("SecurityCraft Owner API not found ({}), team ownership will not be checked", t.toString());
            }

            initialized = true;
        }
    }

    /** True if SecurityCraft itself can reinforce this block (it has its own reinforced version). */
    public static boolean handlesBlock(Block block) {
        init();

        Map<?, ?> map = vanillaToSecurityCraft;

        return map != null && map.containsKey(block);
    }

    /** True for SecurityCraft's own reinforced blocks. */
    public static boolean isSecurityCraftBlock(Block block) {
        init();

        if (reinforcedBlockInterface != null && reinforcedBlockInterface.isInstance(block))
            return true;

        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);

        return id != null && SC_MODID.equals(id.getNamespace());
    }

    public static boolean isReinforcer(ItemStack stack) {
        if (stack.isEmpty())
            return false;

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());

        return REINFORCER_LVL_1.equals(id) || REINFORCER_LVL_2.equals(id) || REINFORCER_LVL_3.equals(id);
    }

    /** Mirrors UniversalBlockReinforcerItem#isReinforcing: level 1 always reinforces, levels 2 and 3 can be switched. */
    public static boolean isReinforcingMode(ItemStack stack) {
        if (REINFORCER_LVL_1.equals(ForgeRegistries.ITEMS.getKey(stack.getItem())))
            return true;

        CompoundTag tag = stack.getTag();

        return tag == null || !tag.getBoolean("is_unreinforcing");
    }

    /** Uses SecurityCraft's own owner comparison, which also honours its team ownership setting. */
    public static boolean isTreatedAsOwner(OwnerRef owner, Player player) {
        init();

        if (ownerFromNameAndUuid == null || ownerFromPlayer == null || isTreatedTheSameAs == null)
            return false;

        try {
            Object blockOwner = ownerFromNameAndUuid.newInstance(owner.name(), owner.id().toString());
            Object playerOwner = ownerFromPlayer.newInstance(player);

            return Boolean.TRUE.equals(isTreatedTheSameAs.invoke(blockOwner, playerOwner));
        }
        catch (Throwable t) {
            return false;
        }
    }
}
