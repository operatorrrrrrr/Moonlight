package com.disepi.moonlight.events;

import cn.nukkit.Player;
import cn.nukkit.block.*;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemElytra;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.*;
import cn.nukkit.potion.Effect;
import com.disepi.moonlight.anticheat.Moonlight;
import com.disepi.moonlight.anticheat.check.Check;
import com.disepi.moonlight.anticheat.player.PlayerData;
import com.disepi.moonlight.utils.MotionUtils;
import com.disepi.moonlight.utils.Util;
import com.disepi.moonlight.utils.WorldUtils;

public class onPlayerSendPacket implements Listener {

    public static int DEFAULT_LENIENCE = 20;

    public void _punish(Player player) {
        Moonlight.getData(player).punish(player, "");
    }

    @EventHandler
    public void onPlayerPacketSend(DataPacketReceiveEvent event) {
        Player player = event.getPlayer(); // Get the player instance from the packet
        PlayerData data = Moonlight.getData(event.getPlayer());
        if (data == null) {
            return;
        }

        // InteractPacket receive
        if ((event.getPacket() instanceof InteractPacket)) {
            InteractPacket packet = (InteractPacket) event.getPacket();
            if (packet.target == player.getId()) {

                // You can't look at yourself
                if (packet.action == InteractPacket.ACTION_MOUSEOVER)
                    _punish(player);
            } else {
                // This won't ever happen in vanilla
                if (packet.action == InteractPacket.ACTION_OPEN_INVENTORY)
                    _punish(player);
            }
            return;
        }

        // AnimatePacket receive
        if ((event.getPacket() instanceof AnimatePacket)) {
            AnimatePacket packet = (AnimatePacket) event.getPacket();
            if (packet.action != AnimatePacket.Action.SWING_ARM) return;
            data.lastSwingTimeBefore = data.lastSwingTime;
            data.lastSwingTime = System.currentTimeMillis(); // Set swing time
            return;
        }

        // MobEquipmentPacket receive
        if ((event.getPacket() instanceof MobEquipmentPacket)) {
            for (Check check : Moonlight.checks) { // Loop through all of Moonlight's checks
                check.check((MobEquipmentPacket) event.getPacket(), data, player); // Call the check function that wants an "MobEquipmentPacket"
            }
            data.lastSwitchTime = System.currentTimeMillis();
            return;
        }

        // InventoryTransactionPacket receive
        if ((event.getPacket() instanceof InventoryTransactionPacket)) {
            for (Check check : Moonlight.checks) { // Loop through all of Moonlight's checks
                check.check((InventoryTransactionPacket) event.getPacket(), data, player); // Call the check function that wants an "InventoryTransactionPacket"
            }
            return;
        }

        // operator: The client is never going to send this packet when the server is in server auth movement mode
        if ((event.getPacket() instanceof MovePlayerPacket)) {
            _punish(player);
        }

        if ((event.getPacket() instanceof PlayerAuthInputPacket))
        {
            // Get and store packet info
            PlayerAuthInputPacket packet = (PlayerAuthInputPacket) event.getPacket();
            float x = packet.getPosition().x;
            float y = packet.getPosition().y;
            float z = packet.getPosition().z;

            // Set data
            data.moveTicks++;

            // Teleport/respawn check
            if (data.isTeleporting) {
                data.lastX = (float) data.teleportPos.x;
                data.lastY = (float) data.teleportPos.y + 1.62f;
                data.lastZ = (float) data.teleportPos.z;
                if (Util.distance(x, y, z, (float) data.teleportPos.x, (float) data.teleportPos.y, (float) data.teleportPos.z) > 1.7) {
                    event.setCancelled(true);
                    player.teleport(new Vector3(data.teleportPos.x, data.teleportPos.y, data.teleportPos.z));
                    return;
                } else
                    data.isTeleporting = false;
            }

            // Elytra
            Item chestplateItem = player.getInventory().getArmorItem(1);
            if (chestplateItem instanceof ItemElytra) data.elytraWornLenience = DEFAULT_LENIENCE;
            else data.elytraWornLenience--;
            boolean isWearingElytra = data.elytraWornLenience > 0;

            // Jump boost
            Effect jumpBoostEffect = player.getEffect(8); // Get jump boost effect
            if (jumpBoostEffect != null) {
                data.lastJumpAmplifier = jumpBoostEffect.getAmplifier();
                data.jumpPotionLenientTicks = DEFAULT_LENIENCE;
            } else
                data.jumpPotionLenientTicks--;

            // Speed
            Effect speedEffect = player.getEffect(1); // Get jump boost effect
            if (speedEffect != null) {
                data.lastSpeedAmplifier = speedEffect.getAmplifier();
                data.speedPotionLenientTicks = DEFAULT_LENIENCE;
            } else
                data.speedPotionLenientTicks--;

            // Levitation
            Effect levitationEffect = player.getEffect(24); // Get levitation effect
            if (levitationEffect != null)
                data.levitationPotionLenientTicks = DEFAULT_LENIENCE;
            else
                data.levitationPotionLenientTicks--;

            // Speed calculations
            data.currentSpeed = Util.distance(x, 0, z, data.lastX, 0, data.lastZ); // Get the current horizontal distance from the last position
            if (player.isSprinting()) data.sprintingTicks = DEFAULT_LENIENCE;
            else data.sprintingTicks--; // Sprint tick stuff
            data.speedMultiplier = MotionUtils.getSpeedMultiplier(data); // Get speed multiplier from speed potions
            if (!data.isPlayerConsideredSprinting())
                data.speedMultiplier *= 0.75f; // Check if the player is actually sprinting
            if (player.isSneaking()) data.speedMultiplier *= 0.75f; // Check if the player is sneaking
            data.jumpTicks--; // Decrease jump ticks
            data.lerpTicks--; // Decrease lerp ticks

            // View vector calculation
            double cYaw = (packet.getYaw() + 90.0) * MotionUtils.DEG;
            double cPitch = packet.getPitch() * -MotionUtils.DEG;
            data.viewVector = new Vector3(Math.cos(cYaw), Math.sin(cPitch), Math.sin(cYaw));

            Block block = WorldUtils.getNearestSolidBlock(x, y, z, player.level, 1.0f); // Retrieve nearest solid block
            Block blockAboveNearestBlock = WorldUtils.getBlock(player.level, (int) block.x, (int) block.y + 1, (int) block.z);
//            data.onGround = !(block instanceof BlockAir) && block.isSolid(); // Set on ground if block is not air (solid)
//
//            if (!Util.isRoughlyEqual(packet.y % 0.015625f, 0.010627747f, 0.00001f) || !packet.onGround)
//                data.onGround = false;
//
//            packet.onGround = data.onGround; // Our information is more accurate - we do NOT trust the client with the onGround value located inside the packet.

            var currentPos = event.getPlayer().getPosition();

            Block blockBelow = WorldUtils.getBlock(player.level, (int) currentPos.x, (int) currentPos.y - 1, (int) currentPos.z);

            var groundState = blockBelow instanceof BlockAir;

            // You're always (technically) be on the ground if you're inside of a block or water
            if (
                    event.getPlayer().isInsideOfSolid() ||
                    event.getPlayer().isInsideOfWater()
            ) groundState = true;

            data.onGround = groundState;
            //Util.log(String.format("onGround? %s", data.onGround));

            // Collision
            float expand = 1.25f;
            if (WorldUtils.isConsideredSolid(player.level, x + expand, y - 1.62f, z) || WorldUtils.isConsideredSolid(player.level, x - expand, y - 1.62f, z) || WorldUtils.isConsideredSolid(player.level, x, y - 1.62f, z + expand) || WorldUtils.isConsideredSolid(player.level, x, y - 1.62f, z - expand) || WorldUtils.isConsideredSolid(player.level, x + expand, y - 1.62f, z + expand) || WorldUtils.isConsideredSolid(player.level, x - expand, y - 1.62f, z + expand) || WorldUtils.isConsideredSolid(player.level, x + expand, y - 1.62f, z - expand))
                data.collidedHorizontallyTicks = DEFAULT_LENIENCE / 3;
            else data.collidedHorizontallyTicks--;

            // Stair check - we also have to check for the above block because sometimes it
            if (block instanceof BlockStairs || blockAboveNearestBlock instanceof BlockStairs)
                data.staircaseLenientTicks = DEFAULT_LENIENCE;
            else data.staircaseLenientTicks--;

            // Gravity changing blocks
            if (block instanceof BlockLadder || block instanceof BlockWater || block instanceof BlockWaterStill || block instanceof BlockLava || block instanceof BlockLavaStill || block instanceof BlockVine || block instanceof BlockCobweb || block instanceof BlockSlime || block instanceof BlockHayBale || block instanceof BlockBed)
                data.gravityLenientTicks = DEFAULT_LENIENCE;
            else data.gravityLenientTicks--;

            // Friction changing blocks
            if (block instanceof BlockIce || block instanceof BlockIcePacked || block instanceof BlockWater || block instanceof BlockWaterStill || block instanceof BlockLava || block instanceof BlockLavaStill || block instanceof BlockSlime || block instanceof BlockHayBale || block instanceof BlockBed)
                data.frictionLenientTicks = (int) (DEFAULT_LENIENCE * 1.5f);
            else data.frictionLenientTicks--;

            // Check for a block above us
            if (!(WorldUtils.getNearestSolidBlock(x, y + 2.53, z, player.level, 1.5f) instanceof BlockAir))
                data.blockAboveLenientTicks = DEFAULT_LENIENCE;
            else data.blockAboveLenientTicks--;


            // Adjust speed to environment
            if (data.lerpTicks > 0) // Damage ticks
            {
                data.currentSpeed /= 1.0 + (data.lastLerpStrength * 1.5f);
                data.startFallPos = null;
                data.offGroundTicks = 0;
                data.fallingTicks = 0;
            }

            if (data.frictionLenientTicks > 0) data.currentSpeed /= 2.0;
            if (data.blockAboveLenientTicks > 0) data.currentSpeed /= 2.4;
            if (data.staircaseLenientTicks > 0) data.currentSpeed /= 2.4;
            if (isWearingElytra) data.currentSpeed /= 4.0;

            data.resetMove = false;

            // Cycles through and runs Moonlight's checks.
            if (player.gamemode != 1 && player.isAlive()) { // TODO: Implement this better and create creative mode specific checks or adjust checks to fit creative mode's movements. Creative mode has movement mechanics such as flying which can false flag a lot of checks.
                for (Check check : Moonlight.checks) { // Loop through all checks in Moonlight's list
                    check.check(packet, data, player); // Call the check function that wants a PlayerAuthInput
                }
            }

            if (data.resetMove) // If we have to reset move
            {
                event.setCancelled(true);
                return;
            }

            if (data.onGround) // set onground state
                data.lastGroundPos = new Vector3(x, y - (1.62 - 0.000001), z);

            // Calculate off/on ground ticks
            if (data.onGround) { // If we are onGround
                // Clear all info that is not relevant anymore
                data.offGroundTicks = 0;
                data.startFallPos = null;
                data.predictedFallAmount = 0;
                data.fallingTicks = 0;

                // Increment the onGround tick value.
                data.onGroundTicks++;
            } else {
                data.onGroundTicks = 0; // Clear onGround ticks because we are now off ground.
                data.offGroundTicks++; // Increment the offGround tick value.

                // Determine the position of when the player actually starts falling.
                // We have 3 methods of this:
                // =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
                // 1.) Check if the current vertical position is less than the last movement's vertical position
                // =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
                // 2.) Check if offGround ticks has reached 8 - this number comes from the amount of offGround
                // ticks it takes to start falling after a vanilla jump.
                // =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
                // 3.) Check if the fall distance from last vertical position is abnormal. This is more of a
                // check to detect fly cheats faster instead of falling

                if (data.isLevitationActive() || isWearingElytra)
                    data.offGroundTicks = 0; // Fix levitation/elytra false flags

                float differenceValue = Math.abs(data.lastY - y);
                if (data.startFallPos == null && (y < data.lastY || data.offGroundTicks >= (data.jumpPotionLenientTicks > 0 ? 9 + data.lastJumpAmplifier : 7))) // Check if the start fall position is already defined, if not, we then use the mentioned methods
                {
                    data.startFallPos = new Vector3(x, y, z); // Set the start fall position value
                    if (data.jumpPotionLenientTicks > 0) data.offGroundTicks = 7; // Jump boost fix
                } else
                    data.fallingTicks++; // We have already started falling - increment falling ticks.
            }

            // Set/get data for when the next packet is received.
            data.lastSpeed = data.currentSpeed;
            data.lastX = x;
            data.lastY = y;
            data.lastZ = z;
            data.lastPitch = packet.getPitch();
            data.lastYaw = packet.getYaw();
        }
    }
}
