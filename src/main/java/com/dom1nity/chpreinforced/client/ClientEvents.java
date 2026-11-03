package com.dom1nity.chpreinforced.client;

import com.dom1nity.chpreinforced.ChpReinforced;
import com.dom1nity.chpreinforced.compat.ScCompat;
import com.dom1nity.chpreinforced.core.ClientStore;
import com.dom1nity.chpreinforced.core.Entry;
import com.dom1nity.chpreinforced.core.ReinforcedMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChpReinforced.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final double OUTLINE_RANGE = 24.0D;
    private static final int MAX_OUTLINES = 4096;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStore.clear();
    }

    /** While a reinforcer is held, outline nearby reinforced blocks: cyan for yours, red for other players'. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null)
            return;

        if (!ScCompat.isReinforcer(player.getMainHandItem()) && !ScCompat.isReinforcer(player.getOffhandItem()))
            return;

        ReinforcedMap map = ClientStore.get(level.dimension());

        if (map == null || map.isEmpty())
            return;

        Vec3 camera = event.getCamera().getPosition();
        double rangeSqr = OUTLINE_RANGE * OUTLINE_RANGE;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int drawn = 0;

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (Long2ObjectMap.Entry<Entry> mapEntry : map.view().long2ObjectEntrySet()) {
            long key = mapEntry.getLongKey();
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            double dx = x + 0.5D - camera.x;
            double dy = y + 0.5D - camera.y;
            double dz = z + 0.5D - camera.z;

            if (dx * dx + dy * dy + dz * dz > rangeSqr)
                continue;

            pos.set(x, y, z);

            BlockState state = level.getBlockState(pos);
            Entry entry = mapEntry.getValue();

            if (state.getBlock() != entry.block())
                continue;

            VoxelShape shape = state.getShape(level, pos);
            AABB box = (shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds()).move(x, y, z).inflate(0.004D);
            boolean mine = player.getUUID().equals(entry.owner().id());

            if (mine)
                LevelRenderer.renderLineBox(poseStack, lines, box, 0.25F, 0.85F, 1.0F, 1.0F);
            else
                LevelRenderer.renderLineBox(poseStack, lines, box, 1.0F, 0.3F, 0.3F, 1.0F);

            if (++drawn >= MAX_OUTLINES)
                break;
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
