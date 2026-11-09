package net.amyrose.storyamycore.block;

import net.amyrose.storyamycore.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class FlashlightBlock extends Block {
    public static final VoxelShape SHAPE = Block.createCuboidShape(6, 0, 5.5, 11, 10, 10);

    public FlashlightBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /* Breaking the block hands the flashlight item to the player (dropped at the block if the inventory is full). Server only:
       the client's inventory is overwritten by the server's, so adding there would just flicker. */
    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        giveFlashlight(world, pos, player);
        super.onBreak(world, pos, state, player);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        giveFlashlight(world, pos, player);
        world.removeBlock(pos, false);
        return ActionResult.success(world.isClient);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        world.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
    }

    private static void giveFlashlight(World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient) {
            return;
        }
        ItemStack stack = new ItemStack(ModItems.FLASHLIGHT);
        if (!player.getInventory().insertStack(stack)) {
            world.spawnEntity(new ItemEntity(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack));
        }
    }
}
