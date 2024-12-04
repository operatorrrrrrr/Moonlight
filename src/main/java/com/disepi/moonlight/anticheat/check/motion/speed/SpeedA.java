package com.disepi.moonlight.anticheat.check.motion.speed;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import com.disepi.moonlight.anticheat.check.Check;
import com.disepi.moonlight.anticheat.player.PlayerData;

public class SpeedA extends Check {
    // Constructor
    public SpeedA() {
        super("SpeedA", "Movement speed above vanilla limits", 3);
    }

    // This is a very simple check, it checks how far the player has travelled from the last point they were at.
    // The max speed number is obtained by logging distance values and using the highest one that was possible
    // to achieve in vanilla.

    public void check(PlayerAuthInputPacket e, PlayerData d, Player p) {
        reward(d, 0.1f); // Violation reward
        if (d.currentSpeed > 0.6475837678752038 * d.speedMultiplier) // Check if distance is more than vanilla allowed move speed
        {
            fail(p, "speed=" + d.currentSpeed + ", vl=" + (int) getViolationScale(d)); // We have failed the check if we reach this
            violate(p, d, 1, true); // Violate
        }
    }

}
