package net.malisis.doors.block.doors;

import static net.malisis.doors.block.doors.DoorHandler.*;

import java.util.Random;

import net.malisis.core.renderer.IBaseRendering;
import net.malisis.doors.MalisisDoors;
import net.malisis.doors.entity.DoorTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.IconFlipped;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class Door extends BlockDoor implements ITileEntityProvider, IBaseRendering
{
	protected IIcon[] iconTop;
	protected IIcon[] iconBottom;
	protected IIcon iconSide;
	protected String soundPath;

	public static final int openingTime = 6;

	private int renderType = -1;

	public Door(Material material)
	{
		super(material);
		float f = 0.5F;
		float f1 = 1.0F;
		setBlockBounds(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f1, 0.5F + f);
		disableStats();
		this.isBlockContainer = true;
		// wood
		if (material == Material.wood)
		{
			setHardness(3.0F);
			setStepSound(soundTypeWood);
			setBlockName("doorWood");
			disableStats();
			setBlockTextureName("door_wood");
		}
		else
		{
			setHardness(5.0F);
			setStepSound(soundTypeMetal);
			setBlockName("doorIron");
			setBlockTextureName("door_iron");
		}
	}

	// #region Icons
	@SideOnly(Side.CLIENT)
	@Override
	public IIcon getIcon(int side, int metadata)
	{
		// return icons[(metadata & flagTopBlock) >> 3];
		if (side == 1 || side == 0)
			return iconSide;

		int dir = metadata & 3;
		boolean opened = (metadata & flagOpened) != 0;
		boolean topBlock = (metadata & flagTopBlock) != 0;
		boolean reversed = (metadata & flagReversed) != 0;

		switch (side)
		{
			case 0:
			case 1:
			case 4:
			case 5:
				return iconSide;
			case 2:
				return topBlock ? iconTop[reversed ? 0 : 1] : iconBottom[reversed ? 0 : 1];
			case 3:
				return topBlock ? iconTop[reversed ? 0 : 1] : iconBottom[reversed ? 0 : 1];
			default:
				break;
		}

		if (((dir == DIR_NORTH || dir == DIR_SOUTH) && !opened) || ((dir == DIR_WEST || dir == DIR_EAST) && opened))
		{
			// {DOWN, UP, NORTH, SOUTH, WEST, EAST}
			if (side == 4 || side == 5)
				return iconSide;
		}
		else if (side == 2 || side == 3)
			return iconSide;

		if (opened)
		{
			if (dir == DIR_WEST && side == 2)
				reversed = true;
			else if (dir == DIR_NORTH && side != 4)
				reversed = true;
			else if (dir == DIR_EAST && side == 3)
				reversed = true;
			else if (dir == DIR_SOUTH && side == 4)
				reversed = true;
		}
		else
		{
			if (dir == DIR_WEST && side == 5)
				reversed = true;
			else if (dir == DIR_NORTH && side == 3)
				reversed = true;
			else if (dir == DIR_EAST && side == 4)
				reversed = true;
			else if (dir == DIR_SOUTH && side == 2)
				reversed = true;

			if ((metadata & flagReversed) != 0)
				reversed = !reversed;
		}

		return topBlock ? iconTop[reversed ? 1 : 0] : iconBottom[reversed ? 1 : 0];
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void registerBlockIcons(IIconRegister register)
	{
		iconTop = new IIcon[2];
		iconBottom = new IIcon[2];
		iconSide = register.registerIcon(MalisisDoors.modid + ":" + getTextureName() + "_side");
		iconTop[0] = register.registerIcon(getTextureName() + "_upper");
		iconBottom[0] = register.registerIcon(getTextureName() + "_lower");
		iconTop[1] = new IconFlipped(iconTop[0], true, false);
		iconBottom[1] = new IconFlipped(iconBottom[0], true, false);

	}

	// #end

	// #region Events
	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer p, int par6, float par7, float par8, float par9)
	{
		if (blockMaterial == Material.iron)
			return false;

		boolean opened = (getFullMetadata(world, x, y, z) & flagOpened) != 0;
		setDoorState(world, x, y, z, opened ? stateClosing : stateOpening);
		openDoubleDoor(world, x, y, z, opened ? stateClosing : stateOpening);

		return true;
	}

	/**
	 * Call from villagers AI opening doors
	 */
	@Override
	public void func_150014_a(World world, int x, int y, int z, boolean opening)
	{
		boolean opened = (getFullMetadata(world, x, y, z) & flagOpened) != 0;
		if (opening && opened)
			return;

		setDoorState(world, x, y, z, opened ? stateClosing : stateOpening);
		openDoubleDoor(world, x, y, z, opened ? stateClosing : stateOpening);
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block block)
	{
		int metadata = world.getBlockMetadata(x, y, z);

		if ((metadata & flagTopBlock) == 0)
		{
			boolean flag = false;

			if (world.getBlock(x, y + 1, z) != this)
			{
				world.setBlockToAir(x, y, z);
				flag = true;
			}

			if (!World.doesBlockHaveSolidTopSurface(world, x, y - 1, z))
			{
				world.setBlockToAir(x, y, z);
				flag = true;

				if (world.getBlock(x, y + 1, z) == this)
					world.setBlockToAir(x, y + 1, z);
			}

			if (flag)
			{
				if (!world.isRemote)
					dropBlockAsItem(world, x, y, z, metadata, 0);
			}
			else
			{
				boolean flag1 = world.isBlockIndirectlyGettingPowered(x, y, z) || world.isBlockIndirectlyGettingPowered(x, y + 1, z);

				if ((flag1 || block.canProvidePower()) && block != this)
					onPoweredBlockChange(world, x, y, z, flag1);
			}
		}
		else
		{
			if (world.getBlock(x, y - 1, z) != this)
				world.setBlockToAir(x, y, z);

			if (block != this)
				onNeighborBlockChange(world, x, y - 1, z, block);
		}
	}

	// #end Events

	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z)
	{
		DoorHandler.setBlockBoundsBasedOnState(world, x, y, z, false);
	}

	// @Override

	@SideOnly(Side.CLIENT)
	@Override
	public Item getItem(World par1World, int par2, int par3, int par4)
	{
		return blockMaterial == Material.iron ? Items.iron_door : Items.wooden_door;
	}

	@Override
	public void onBlockHarvested(World world, int x, int y, int z, int metadata, EntityPlayer p)
	{
		if (p.capabilities.isCreativeMode && (metadata & flagTopBlock) != 0 && world.getBlock(x, y - 1, z) == this)
		{
			world.setBlockToAir(x, y - 1, z);
		}
	}

	/**
	 * Called when the block receives a BlockEvent - see World.addBlockEvent. By default, passes it on to the tile entity at this location.
	 * Args: world, x, y, z, blockID, EventID, event parameter
	 */
	@Override
	public boolean onBlockEventReceived(World world, int x, int y, int z, int blockID, int eventID)
	{
		super.onBlockEventReceived(world, x, y, z, blockID, eventID);
		TileEntity tileentity = world.getTileEntity(x, y, z);
		return tileentity != null ? tileentity.receiveClientEvent(blockID, eventID) : false;
	}

	@Override
	public Item getItemDropped(int metadata, Random par2Random, int par3)
	{
		return (metadata & flagTopBlock) != 0 ? null : (blockMaterial == Material.iron ? Items.iron_door : Items.wooden_door);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z)
	{
		if (DoorHandler.setBlockBoundsBasedOnState(world, x, y, z, true))
			return getAABB(world, x, y, z);
		else
			return null;
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z)
	{
		if (DoorHandler.setBlockBoundsBasedOnState(world, x, y, z, false))
			return getAABB(world, x, y, z);
		else
			return null;
	}

	@Override
	public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 par5Vec3, Vec3 par6Vec3)
	{
		DoorHandler.setBlockBoundsBasedOnState(world, x, y, z, false);
		return super.collisionRayTrace(world, x, y, z, par5Vec3, par6Vec3);
	}

	public AxisAlignedBB getAABB(World world, int x, int y, int z)
	{
		return AxisAlignedBB.getBoundingBox(x + this.minX, y + this.minY, z + this.minZ, x + this.maxX, y + this.maxY, z + this.maxZ);
	}

	@Override
	public void setRenderId(int id)
	{
		renderType = id;
	}

	@Override
	public int getRenderType()
	{
		return renderType;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int metadata)
	{
		return new DoorTileEntity();
	}
}