package org.crystalPusher;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.network.EventPacket;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.NullSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.utils.ColorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class CrystalPusherModule extends ToggleableModule {
	NumberSetting<Float> targetRange = new NumberSetting<>("Target Range", 5f, 1f, 10f).incremental(0.25f);
	NumberSetting<Float> placeRange = new NumberSetting<>("Place Range", 5f, 1f, 10f).incremental(0.25f);
	NumberSetting<Integer> rotateDelay = new NumberSetting<>("Rotate Delay", 5, 1, 20).incremental(1);
	NumberSetting<Integer> stuckDetectionTicks = new NumberSetting<>("Stuck Detection Ticks", 5, 1, 20).incremental(1);
	NumberSetting<Integer> explodingDelay = new NumberSetting<>("Exploding Delay", 5, 1, 20).incremental(1);

	NullSetting colors = new NullSetting("Colors");
	ColorSetting crystalColor = new ColorSetting("Crystal Color", new Color(255, 68, 68));
	ColorSetting pistonColor = new ColorSetting("Piston Color", new Color(0, 255, 0));
	ColorSetting torchColor = new ColorSetting("Torch Color", new Color(255, 0, 0));
	ColorSetting targetColor = new ColorSetting("Target Color", new Color(88, 149, 255));

	public CrystalPusherModule() {
		super("Crystal Pusher", "Pushes Crystals into Faces", ModuleCategory.CLIENT);

		this.colors.addSubSettings(
				this.crystalColor,
				this.pistonColor,
				this.torchColor,
				this.targetColor
		);

		this.registerSettings(
				this.targetRange,
				this.placeRange,
				this.rotateDelay,
				this.stuckDetectionTicks,
				this.explodingDelay,
				this.colors
		);
	}

	BlockPos targetPos = null;
	BlockPos pistonPos = null;
	BlockPos crystalPos = null;
	BlockPos torchPos = null;
	Direction pistonDirection;
	int delay = 0;
	int ticksNotExtended = 0;
	int ticksNotExploded = 0;

	@Subscribe
	private void onUpdate(EventUpdate event) {
		if (mc.level == null || mc.player == null) return;

		updateTarget();
		if (targetPos == null) return;

		if (pistonPos == null || crystalPos == null || torchPos == null) {
			updatePositions();
			return;
		}

		/*
		 * First we check if the piston is already placed
		 *  If it is we check if the piston is already extended
		 *  If it is extended we check if the crystal is placed
		 *  If its placed we explode the crystal
		 * If the piston isnt placed we place the piston
		 * If the piston is placed we place the torch
		 */

		List<Entity> entities = mc.level.getEntities(null, new AABB(targetPos.getCenter(), targetPos.getCenter()).expandTowards(0, 2, 0));
		entities.removeIf(entity -> !(entity instanceof EndCrystal));
		if (entities.isEmpty()) {
			ticksNotExploded = 0;
			if (mc.level.getBlockState(pistonPos).getBlock() == Blocks.PISTON || mc.level.getBlockState(pistonPos).getBlock() == Blocks.STICKY_PISTON) {
				Boolean extended = mc.level.getBlockState(pistonPos).getValue(PistonBaseBlock.EXTENDED);
				if (extended) {
					ticksNotExtended = 0;
					if (mc.level.getBlockState(torchPos).getBlock() == Blocks.REDSTONE_TORCH) {
						breakBlock(torchPos);
					} else {
						ChatUtils.print("CRYSTAL PUSHER: Unknwon source powering piston");
					}
					return;
				} else {
					AABB crystalRange = new AABB(crystalPos);
					entities = mc.level.getEntities(null, crystalRange);
					entities.removeIf(entity -> !(entity instanceof EndCrystal));
					if (entities.isEmpty()) {
						if (mc.player.getMainHandItem().getItem() != Items.END_CRYSTAL) {
							getCrystal();
							return;
						}
						BlockHitResult res = new BlockHitResult(crystalPos.getCenter().add(0, -0.5, 0), Direction.UP, crystalPos.below(), false);
						placeBlock(res);
					} else {
						if (mc.level.getBlockState(torchPos).getBlock() == Blocks.REDSTONE_TORCH) {
							ticksNotExtended++;
							if (ticksNotExtended > this.stuckDetectionTicks.getValue()) {
								breakBlock(torchPos);
								ChatUtils.print("CRYSTAL PUSHER: torch stuck");
								ticksNotExtended = 0;
							}
						}
						if (mc.player.getMainHandItem().getItem() != Items.REDSTONE_TORCH) {
							getTorch();
							return;
						}
						BlockHitResult res = new BlockHitResult(torchPos.getCenter().add(0, -0.5, 0), Direction.UP, torchPos.below(), false);
						placeBlock(res);
					}
				}
			} else if (mc.level.getBlockState(pistonPos).getBlock() == Blocks.REDSTONE_TORCH) {
				breakBlock(pistonPos);
			} else {
				if (delay < rotateDelay.getValue()) {
					if (mc.player.getMainHandItem().getItem() != Blocks.PISTON.asItem() && mc.player.getMainHandItem().getItem() != Blocks.STICKY_PISTON.asItem()) {
						getPiston();
						return;
					}
					RusherHackAPI.getRotationManager().updateRotation(pistonDirection.getOpposite().toYRot(), 0);
					delay++;
					return;
				}
				delay = 0;
				BlockHitResult res = RusherHackAPI.interactions().getBlockPlaceHitResult(pistonPos, false, false, placeRange.getValue());
				if (res == null) {
					ChatUtils.print("CRYSTAL PUSHER: Unable to place piston (maybe ghost piston)");
					return;
				}
				placeBlock(res);
			}
		} else {
			if (mc.gameMode == null) return;
			if (ticksNotExploded < this.explodingDelay.getValue()) {
				//ChatUtils.print("CRYSTAL PUSHER: Crystal not exploded");
				ticksNotExploded++;
				return;
			}
			ticksNotExploded = 0;
			mc.gameMode.attack(mc.player, entities.getFirst());
			mc.player.swing(InteractionHand.MAIN_HAND);
		}
	}

	public void getPiston() {
		if (mc.player == null) return;
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getItem(i).getItem() == Blocks.PISTON.asItem() || mc.player.getInventory().getItem(i).getItem() == Blocks.STICKY_PISTON.asItem()) {
				mc.player.getInventory().selected = i;
				return;
			}
		}
	}

	public void getTorch() {
		if (mc.player == null) return;
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getItem(i).getItem() == Items.REDSTONE_TORCH) {
				mc.player.getInventory().selected = i;
				return;
			}
		}
	}

	public void getCrystal() {
		if (mc.player == null) return;
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getItem(i).getItem() == Items.END_CRYSTAL) {
				mc.player.getInventory().selected = i;
				return;
			}
		}
	}

	public void placeBlock(BlockHitResult res) {
		if (mc.gameMode == null || mc.player == null) return;
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, res);
		mc.player.swing(InteractionHand.MAIN_HAND);
	}

	public void breakBlock(BlockPos pos) {
		if (mc.gameMode == null || mc.player == null) return;
		mc.gameMode.startDestroyBlock(pos, Direction.DOWN);
		mc.gameMode.stopDestroyBlock();
		mc.player.swing(InteractionHand.MAIN_HAND);
	}

	/*
	 * PI = Piston
	 * CR = Crystal
	 * PL = Player
	 * IG = Ignore
	 * TO = Torch
	 *
	 * IG PI TO
	 * IG CR IG
	 * IG PL IG
	 * IG IG IG
	 *
	 * TO PI IG
	 * IG CR IG
	 * IG PL IG
	 * IG IG IG
	 *
	 * For this pattern check if theres 2 blocks in any direction + a block air next too the Torch
	 * Also make sure the blocks are in range of placeRange
	 * It should work in the players face and 1 block above ignoring if theres a block in the way on the 1 block above varient
	 * Also should work if theres a free block 2 blocks below the piston and 1 block next or below that free block
	 * Also should work if theres a 3 block strip of blank blocks
	 */
	public void updatePositions() {
		if (targetPos == null || mc.level == null) return;

		/*
		 * We first want too check the head location because this will deal more damage and if not check one block above
		 */
		for (int i = 1; i < 3; i++) {
			/*
			 * The crystal can only be placed in the north, south, east, west direction of the player
			 */
			for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
				pistonPos = null;
				torchPos = null;
				crystalPos = null;
				pistonDirection = null;

				BlockPos crystalCandidate = targetPos.relative(direction).above(i);

				if (!mc.level.getBlockState(crystalCandidate).isAir()) continue;

				BlockPos belowCrystal = crystalCandidate.below();
				if (!mc.level.getBlockState(belowCrystal).is(Blocks.OBSIDIAN) && !mc.level.getBlockState(belowCrystal).is(Blocks.BEDROCK)) continue;
				if (!isWithinPlaceRange(crystalCandidate)) continue;

				{
					List<Entity> entitiesInCrystal = mc.level.getEntities(null, new AABB(crystalCandidate));
					entitiesInCrystal.removeIf(entity -> !(entity instanceof EndCrystal) && !(entity instanceof Player));
					if (!entitiesInCrystal.isEmpty()) continue;
				}
				crystalPos = crystalCandidate;

				BlockPos[] pistonCandidates = new BlockPos[] {
						crystalCandidate.relative(direction.getClockWise()),
						crystalCandidate.relative(direction.getCounterClockWise()),
						crystalCandidate.relative(direction),
						crystalCandidate.relative(direction).relative(direction.getClockWise()),
						crystalCandidate.relative(direction).relative(direction.getCounterClockWise()),
				};

				for (BlockPos pistonCandidate : pistonCandidates) {
					if (!mc.level.getBlockState(pistonCandidate).isAir()) continue;
					if (!isWithinPlaceRange(pistonCandidate)) continue;
					if (mc.level.getBlockState(pistonCandidate.below()).isAir()) continue;
					BlockPos intoPos = pistonCandidate.relative(direction.getOpposite());
					BlockState state = mc.level.getBlockState(intoPos);
					if (!state.isAir() && state.getBlock() != Blocks.LAVA && state.getBlock() != Blocks.WATER) continue;

					pistonPos = pistonCandidate;
					pistonDirection = direction.getOpposite();
					break;
				}
				if (pistonPos == null) continue;

				/*
				 * The torch can be placed a block too the back right or left of piston but not too the front (in the direction of the target)
				 */
				BlockPos[] torchCandidates = new BlockPos[] {
						pistonPos.relative(direction),
						pistonPos.relative(direction.getClockWise()),
						pistonPos.relative(direction.getCounterClockWise()),
				};

				for (BlockPos torchCandidate : torchCandidates) {
					if (!mc.level.getBlockState(torchCandidate).isAir()) continue;
					if (!isWithinPlaceRange(torchCandidate)) continue;
					if (mc.level.getBlockState(torchCandidate.below()).isAir()) continue;
					if (torchCandidate.equals(crystalPos)) continue;
					torchPos = torchCandidate;
					break;
				}
				if (torchPos == null) continue;

				return;
			}
		}

		pistonPos = null;
		torchPos = null;
		crystalPos = null;
	}

	/*
	 * Checks if a given Block Position is in the range of the player
	 */
	private boolean isWithinPlaceRange(BlockPos pos) {
		if (mc.player == null) return false;
		return mc.player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= placeRange.getValue() * placeRange.getValue();
	}

	/*
	 * This updates the target position
	 * Only if the target is not currently set
	 */
	public void updateTarget() {
		if (mc.player == null || mc.level == null) return;
		AABB range = new AABB(mc.player.getX() - this.targetRange.getValue(), mc.player.getY() - this.targetRange.getValue(), mc.player.getZ() - this.targetRange.getValue(), mc.player.getX() + this.targetRange.getValue(), mc.player.getY() + this.targetRange.getValue(), mc.player.getZ() + this.targetRange.getValue());
		List<Entity> entities = mc.level.getEntities(null, range);
		entities.removeIf(entity -> !(entity instanceof Player) || entity == mc.player || mc.player.distanceTo(entity) > this.targetRange.getValue());
		if (targetPos == null) {
			if (entities.isEmpty()) {
				ChatUtils.print("No target in range");
				this.setToggled(false);
				return;
			}
			entities.sort((o1, o2) -> {
				double d1 = mc.player.distanceTo(o1);
				double d2 = mc.player.distanceTo(o2);
				return Double.compare(d1, d2);
			});
			Player target = (Player) entities.getFirst();
			targetPos = target.blockPosition();
		}
	}

	/*
	 * This resets the positions so the module can find new ones at the next start
	 */
	public void resetPositions() {
		targetPos = null;
		pistonPos = null;
		crystalPos = null;
		torchPos = null;
	}

	@Override
	public void onDisable() {
		resetPositions();
	}

	@Subscribe
	public void onRender3D(EventRender3D event) {
		final IRenderer3D renderer = event.getRenderer();

		renderer.begin(event.getMatrixStack());

		if (crystalPos != null) {
			final int crystalColor = ColorUtils.transparency(this.crystalColor.getValueRGB(), 0.5f);
			renderer.drawBox(crystalPos,false, true, crystalColor);
		}

		if (pistonPos != null) {
			final int pistonColor = ColorUtils.transparency(this.pistonColor.getValueRGB(), 0.5f);
			renderer.drawBox(pistonPos,false, true, pistonColor);
		}

		if (torchPos != null) {
			final int torch = ColorUtils.transparency(this.torchColor.getValueRGB(), 0.5f);
			renderer.drawBox(torchPos, false, true, torch);
		}

		if (targetPos != null) {
			final int target = ColorUtils.transparency(this.targetColor.getValueRGB(), 0.5f);
			renderer.drawBox(targetPos, false, true, target);
			renderer.drawBox(targetPos.above(), false, true, target);
		}

		renderer.end();
	}
}