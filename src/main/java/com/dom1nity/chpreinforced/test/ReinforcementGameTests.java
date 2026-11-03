package com.dom1nity.chpreinforced.test;

import java.util.UUID;

import com.dom1nity.chpreinforced.ChpReinforced;
import com.dom1nity.chpreinforced.core.OwnerRef;
import com.dom1nity.chpreinforced.core.Reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Automated checks run on a dedicated test server by CI (gradle runGameTestServer). They only run when the
 * "chpreinforced" game test namespace is enabled, never in normal play.
 */
@GameTestHolder(ChpReinforced.MODID)
@PrefixGameTestTemplate(false)
public final class ReinforcementGameTests {
    private static final String TEMPLATE = "empty";
    private static final OwnerRef SOMEONE = new OwnerRef(UUID.fromString("00000000-0000-0000-0000-00000000c4a1"), "Someone");

    private ReinforcementGameTests() {
    }

    private static BlockPos reinforceAt(GameTestHelper helper, BlockPos relative, Block block, OwnerRef owner) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);

        helper.setBlock(relative, block);
        helper.assertTrue(Reinforcement.isEligible(level, pos, level.getBlockState(pos)), block + " should be eligible");
        Reinforcement.reinforce(level, pos, block, owner);
        helper.assertTrue(Reinforcement.isProtected(level, pos), block + " should be protected after reinforcing");
        return pos;
    }

    @GameTest(template = TEMPLATE)
    public static void eligibility(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(rel);
        Block[] allowed = {Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.STONE_BRICK_WALL, Blocks.OAK_FENCE, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.OAK_TRAPDOOR, Blocks.LANTERN};
        Block[] refused = {Blocks.TORCH, Blocks.SAND, Blocks.OAK_DOOR, Blocks.CHEST, Blocks.GRASS_BLOCK, Blocks.OAK_LEAVES, Blocks.WHITE_CARPET, Blocks.TNT, Blocks.BEDROCK};

        for (Block block : allowed) {
            helper.setBlock(rel, block);
            helper.assertTrue(Reinforcement.isEligible(level, pos, level.getBlockState(pos)), block + " should be eligible");
        }

        for (Block block : refused) {
            helper.setBlock(rel, block);
            helper.assertFalse(Reinforcement.isEligible(level, pos, level.getBlockState(pos)), block + " should not be eligible");
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void hardnessAndDestroyBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = reinforceAt(helper, new BlockPos(1, 1, 1), Blocks.OAK_PLANKS, SOMEONE);

        helper.assertTrue(level.getBlockState(pos).getDestroySpeed(level, pos) == -1.0F, "reinforced block should report hardness -1");
        helper.assertFalse(level.destroyBlock(pos, true), "Level#destroyBlock should refuse a reinforced block");
        helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(1, 1, 1));

        Reinforcement.unreinforce(level, pos);
        helper.assertTrue(level.getBlockState(pos).getDestroySpeed(level, pos) == 2.0F, "hardness should be back to normal");
        helper.assertTrue(level.destroyBlock(pos, false), "destroyBlock should work after un-reinforcing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ownerCanMineOthersCannot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos pos = reinforceAt(helper, rel, Blocks.OAK_PLANKS, SOMEONE);

        helper.assertTrue(level.getBlockState(pos).getDestroyProgress(player, level, pos) == 0.0F, "a stranger should make no mining progress");

        Reinforcement.reinforce(level, pos, Blocks.OAK_PLANKS, OwnerRef.of(player));
        helper.assertTrue(level.getBlockState(pos).getDestroyProgress(player, level, pos) > 0.0F, "the owner should be able to mine");
        helper.assertFalse(Reinforcement.isBypassing(), "bypass flag must be reset after mining progress is computed");
        helper.assertTrue(level.getBlockState(pos).getDestroySpeed(level, pos) == -1.0F, "hardness should still be -1 for everyone else");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void explosionDoesNotDestroy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos protectedRel = new BlockPos(1, 1, 1);
        BlockPos controlRel = new BlockPos(0, 1, 1);

        reinforceAt(helper, protectedRel, Blocks.WHITE_WOOL, SOMEONE);
        helper.setBlock(controlRel, Blocks.WHITE_WOOL);

        BlockPos center = helper.absolutePos(controlRel);

        level.explode(null, center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D, 2.0F, Level.ExplosionInteraction.TNT);
        helper.assertBlockNotPresent(Blocks.WHITE_WOOL, controlRel);
        helper.assertBlockPresent(Blocks.WHITE_WOOL, protectedRel);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void pistonCannotPush(GameTestHelper helper) {
        BlockPos pistonRel = new BlockPos(0, 1, 1);
        BlockPos targetRel = new BlockPos(1, 1, 1);

        helper.setBlock(pistonRel, Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        reinforceAt(helper, targetRel, Blocks.WHITE_WOOL, SOMEONE);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(6, () -> {
            helper.assertBlockPresent(Blocks.WHITE_WOOL, targetRel);
            helper.assertBlockNotPresent(Blocks.WHITE_WOOL, new BlockPos(2, 1, 1));
            helper.succeed();
        });
    }
}
