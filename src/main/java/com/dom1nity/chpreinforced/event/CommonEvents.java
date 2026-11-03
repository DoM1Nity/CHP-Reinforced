package com.dom1nity.chpreinforced.event;

import com.dom1nity.chpreinforced.ChpReinforced;
import com.dom1nity.chpreinforced.compat.ScCompat;
import com.dom1nity.chpreinforced.core.Entry;
import com.dom1nity.chpreinforced.core.OwnerRef;
import com.dom1nity.chpreinforced.core.ReinforcedMap;
import com.dom1nity.chpreinforced.core.Reinforcement;
import com.dom1nity.chpreinforced.core.ServerStore;
import com.dom1nity.chpreinforced.net.Network;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = ChpReinforced.MODID)
public final class CommonEvents {
    private static final int SWEEP_INTERVAL_TICKS = 20 * 60 * 5;
    private static int tickCounter;

    private CommonEvents() {
    }

    // ---------------------------------------------------------------- data lifecycle and sync

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level)
            ServerStore.getOrLoad(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level)
            ServerStore.unload(level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerStore.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            Network.sendFull(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            Network.sendFull(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            Network.sendFull(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tickCounter < SWEEP_INTERVAL_TICKS)
            return;

        tickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Reinforcement.sweep(level);
            }
        }
    }

    // ---------------------------------------------------------------- protection

    /** Non-owners cannot break reinforced blocks. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;

        Entry entry = Reinforcement.protection(level, event.getPos(), event.getState().getBlock());

        if (entry != null && !Reinforcement.isAllowed(event.getPlayer(), entry)) {
            event.setCanceled(true);
            notifyNotOwner(event.getPlayer(), entry);
        }
    }

    /** When the owner breaks a reinforced block (and nothing else cancelled it), forget the reinforcement. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakAllowed(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && Reinforcement.protection(level, event.getPos(), event.getState().getBlock()) != null)
            Reinforcement.unreinforce(level, event.getPos());
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();

        if (level.isClientSide)
            return;

        ReinforcedMap map = Reinforcement.mapFor(level);

        if (map != null && !map.isEmpty())
            event.getAffectedBlocks().removeIf(pos -> Reinforcement.isProtected(level, pos));
    }

    /** Withers, the ender dragon and modded mobs that use Forge's hook. */
    @SubscribeEvent
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (Reinforcement.protection(event.getEntity().level(), event.getPos(), event.getState().getBlock()) != null)
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide)
            return;

        ReinforcedMap map = Reinforcement.mapFor(level);

        if (map == null || map.isEmpty())
            return;

        PistonStructureResolver resolver = event.getStructureHelper();

        if (resolver == null || !resolver.resolve())
            return;

        for (BlockPos pos : resolver.getToPush()) {
            if (Reinforcement.isProtected(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }

        for (BlockPos pos : resolver.getToDestroy()) {
            if (Reinforcement.isProtected(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Stripping, scraping, path-making and similar tool actions. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || !(event.getLevel() instanceof ServerLevel level))
            return;

        Entry entry = Reinforcement.protection(level, event.getPos(), event.getState().getBlock());

        if (entry == null)
            return;

        if (!Reinforcement.isAllowed(event.getPlayer(), entry)) {
            event.setCanceled(true);
            return;
        }

        BlockState result = event.getFinalState();

        // The owner changed the block (e.g. stripped a log): keep it reinforced.
        if (result != null && result.getBlock() != entry.block())
            Reinforcement.reinforce(level, event.getPos(), result.getBlock(), entry.owner());
    }

    // ---------------------------------------------------------------- the reinforcer

    /**
     * Runs after SecurityCraft's own handler. If SecurityCraft converted the block it cancelled the event and this is
     * not called; for blocks SecurityCraft cannot convert, this mod takes over.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack stack = player.getMainHandItem();
        boolean start = event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START;
        BlockState state = level.getBlockState(pos);

        if (!ScCompat.isReinforcer(stack)) {
            if (start && !level.isClientSide) {
                Entry entry = Reinforcement.protection(level, pos, state.getBlock());

                if (entry != null && !Reinforcement.isAllowed(player, entry))
                    notifyNotOwner(player, entry);
            }

            return;
        }

        if (state.isAir() || ScCompat.isSecurityCraftBlock(state.getBlock()) || ScCompat.handlesBlock(state.getBlock()))
            return;

        Entry existing = Reinforcement.protection(level, pos, state.getBlock());

        if (ScCompat.isReinforcingMode(stack)) {
            if (existing != null) {
                event.setCanceled(true);

                if (start && !level.isClientSide)
                    actionBar(player, Component.translatable("chpreinforced.message.already_reinforced", existing.owner().name()).withStyle(ChatFormatting.YELLOW));

                return;
            }

            if (!Reinforcement.isEligible(level, pos, state)) {
                if (start && !level.isClientSide)
                    actionBar(player, Component.translatable("chpreinforced.message.cannot_reinforce").withStyle(ChatFormatting.RED));

                return;
            }

            event.setCanceled(true);

            if (level instanceof ServerLevel serverLevel && player.mayBuild() && level.mayInteract(player, pos)) {
                Reinforcement.reinforce(serverLevel, pos, state.getBlock(), OwnerRef.of(player));
                damageReinforcer(stack, player);
                level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35F, 1.6F);
                actionBar(player, Component.translatable("chpreinforced.message.reinforced").withStyle(ChatFormatting.GREEN));
            }
        }
        else {
            if (existing == null)
                return;

            event.setCanceled(true);

            if (level instanceof ServerLevel serverLevel) {
                if (Reinforcement.isAllowed(player, existing)) {
                    Reinforcement.unreinforce(serverLevel, pos);
                    damageReinforcer(stack, player);
                    level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35F, 1.4F);
                    actionBar(player, Component.translatable("chpreinforced.message.unreinforced").withStyle(ChatFormatting.GOLD));
                }
                else if (start)
                    notifyNotOwner(player, existing);
            }
        }
    }

    private static void damageReinforcer(ItemStack stack, Player player) {
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    }

    private static void notifyNotOwner(Player player, Entry entry) {
        if (player != null)
            actionBar(player, Component.translatable("chpreinforced.message.not_owner", entry.owner().name()).withStyle(ChatFormatting.RED));
    }

    private static void actionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}
