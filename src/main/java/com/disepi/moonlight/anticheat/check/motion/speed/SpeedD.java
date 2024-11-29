package com.disepi.moonlight.anticheat.check.motion.speed;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import com.disepi.moonlight.anticheat.check.Check;
import com.disepi.moonlight.anticheat.player.PlayerData;
import com.disepi.moonlight.utils.MotionUtils;

public class SpeedD extends Check {
    // Constructor
    public SpeedD() {
        super("SpeedD", "Invalid vertical jump movement", 8);
    }

    private void doFailCheck(Player p, PlayerData d, float value, float expected) {
        fail(p, "height=" + value + ", expected=" + expected + ", offGroundTicks=" + d.offGroundTicks);
        lagback(p, d);
        violate(p, d, 1, true);
    }

    public void check(PlayerAuthInputPacket e, PlayerData d, Player p) {
        reward(d, 0.25f); // Violate

        // fixes a teleport loop
        if (p.ticksLived < (20 * 5)) return;

        boolean hasJumpBoost = d.isJumpBoostActive();

        // Catches teleports
        float value = e.getPosition().y - d.lastY;
        float expectedTeleportValue = hasJumpBoost ? 1.0f + d.getExtraJumpValue() : 1.0f;
        if (value >= expectedTeleportValue) doFailCheck(p, d, value, expectedTeleportValue);

        if (!d.onGround && value > 0.0f && d.offGroundTicks < 6) {
            for (float expected : MotionUtils.GRAVITY_JUMP_VALUES) {
                if (expected == value) return;
            }
            doFailCheck(p, d, value, 0);
        }
    }

}
