package com.dom1nity.chpreinforced.core;

import javax.annotation.Nullable;

import com.dom1nity.chpreinforced.compat.ScCompat;
import com.dom1nity.chpreinforced.net.Network;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.IPlantable;

/** Central API: lookups used by the mixins and events, plus the server-side operations. */
public final class Reinforcement {
    /** Blast resistance reported for reinforced blocks (same as bedrock). */
    public static final float BLAST_RESISTANCE = 3_600_000.0F;
    private static final ThreadLocal<boolean[]> BYPASS = ThreadLocal.withInitial(() -> new boolean[1]);

    private Reinforcement() {
    }

    @Nullable
    public static ReinforcedMap mapFor(Level level) {
        if (level.isClientSide)
            return ClientStore.get(level.dimension());

        ReinforcedData data = ServerStore.peek(level.dimension());

        return data == null ? null : data.map;
    }

    /**
     * Returns the entry protecting this position, or null. Cheap when nothing is reinforced in the dimension, because
     * this is called from hot paths such as BlockState#getDestroySpeed.
     */
    @Nullable
    public static Entry protection(@Nullable BlockGetter getter, @Nullable BlockPos pos, Block block) {
        if (pos == null || !(getter instanceof Level level))
            return null;

        ReinforcedMap map = mapFor(level);

        if (map == null || map.isEmpty())
            return null;

        Entry entry = map.get(pos.asLong());

        return entry != null && entry.block() == block ? entry : null;
    }

    /** Like {@link #protection(BlockGetter, BlockPos, Block)}, but reads the block from the level only when needed. */
    @Nullable
    public static Entry protection(@Nullable Level level, @Nullable BlockPos pos) {
        if (pos == null || level == null)
            return null;

        ReinforcedMap map = mapFor(level);

        if (map == null || map.isEmpty())
            return null;

        Entry entry = map.get(pos.asLong());

        return entry != null && level.getBlockState(pos).getBlock() == entry.block() ? entry : null;
    }

    public static boolean isProtected(@Nullable Level level, @Nullable BlockPos pos) {
        return protection(level, pos) != null;
    }

    /** Owner, creative players and (if SecurityCraft's team ownership is enabled) the owner's team may break it. */
    public static boolean isAllowed(@Nullable Player player, Entry entry) {
        if (player == null)
            return false;

        if (player.getAbilities().instabuild)
            return true;

        if (player.getUUID().equals(entry.owner().id()))
            return true;

        return ScCompat.isTreatedAsOwner(entry.owner(), player);
    }

    public static boolean isBypassing() {
        return BYPASS.get()[0];
    }

    public static void setBypass(boolean bypass) {
        boolean[] flag = BYPASS.get();

        flag[0] = bypass;
    }

    /**
     * Which blocks this mod lets the reinforcer protect. Building and decoration blocks are allowed; things that would
     * break game mechanics or duplicate items when they cannot be removed are not: block entities (chests, furniture
     * with storage), plants, falling blocks, two-block-tall blocks (doors, beds), anything that ticks randomly
     * (grass, copper, leaves) and anything flowing water could wash away (torches, carpets).
     */
    public static boolean isEligible(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (state.isAir())
            return false;

        if (ScCompat.isSecurityCraftBlock(block) || ScCompat.handlesBlock(block))
            return false;

        if (block instanceof EntityBlock || state.hasBlockEntity())
            return false;

        if (block instanceof LiquidBlock || block instanceof BaseFireBlock)
            return false;

        if (block instanceof BushBlock || block instanceof IPlantable || block instanceof BonemealableBlock || block instanceof GrowingPlantBlock
                || block instanceof VineBlock || block instanceof LeavesBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock
                || block instanceof ChorusPlantBlock || block instanceof ChorusFlowerBlock)
            return false;

        if (block instanceof Fallable)
            return false;

        if (block instanceof DoorBlock || block instanceof BedBlock || block instanceof DoublePlantBlock || block instanceof TntBlock
                || block instanceof InfestedBlock || block instanceof ScaffoldingBlock || block instanceof PistonBaseBlock
                || block instanceof PistonHeadBlock || block instanceof MovingPistonBlock || block instanceof NetherPortalBlock)
            return false;

        for (Property<?> property : state.getProperties()) {
            Class<?> valueClass = property.getValueClass();

            if (valueClass == DoubleBlockHalf.class || valueClass == BedPart.class)
                return false;
        }

        if (state.getDestroySpeed(level, pos) < 0.0F)
            return false;

        if (state.is(BlockTags.ENDERMAN_HOLDABLE) || state.canBeReplaced())
            return false;

        boolean holdsFluid = block instanceof LiquidBlockContainer;

        for (BlockState possible : block.getStateDefinition().getPossibleStates()) {
            if (possible.isRandomlyTicking())
                return false;

            // Fluids replace (and drop) every block that does not block motion, which would duplicate a block that
            // cannot be removed. Waterloggable blocks are fine: water fills them instead of washing them away.
            if (!holdsFluid && !possible.blocksMotion())
                return false;
        }

        return true;
    }

    public static Entry reinforce(ServerLevel level, BlockPos pos, Block block, OwnerRef owner) {
        ReinforcedData data = ServerStore.getOrLoad(level);
        long key = pos.asLong();
        Entry entry = data.map.put(key, block, owner);

        data.setDirty();
        Network.sendAdded(level, key, entry);
        return entry;
    }

    public static boolean unreinforce(ServerLevel level, BlockPos pos) {
        ReinforcedData data = ServerStore.getOrLoad(level);
        long key = pos.asLong();

        if (data.map.remove(key) == null)
            return false;

        data.setDirty();
        Network.sendRemoved(level, key);
        return true;
    }

    /** Removes entries whose block was replaced by something else (only checks loaded chunks). */
    public static int sweep(ServerLevel level) {
        ReinforcedData data = ServerStore.peek(level.dimension());

        if (data == null || data.map.isEmpty())
            return 0;

        LongArrayList stale = new LongArrayList();

        for (long key : data.map.positions()) {
            BlockPos pos = BlockPos.of(key);
            Entry entry = data.map.get(key);

            if (entry == null || !level.isLoaded(pos))
                continue;

            if (level.getBlockState(pos).getBlock() != entry.block())
                stale.add(key);
        }

        if (stale.isEmpty())
            return 0;

        for (int i = 0; i < stale.size(); i++) {
            data.map.remove(stale.getLong(i));
        }

        data.setDirty();
        Network.sendRemoved(level, stale.toLongArray());
        return stale.size();
    }
}
