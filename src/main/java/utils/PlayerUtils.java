package utils;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class PlayerUtils {
    /**
     * Dings a player. You got mail!
     *
     * @param serverPlayer The ServerPlayer to ding.
     */
    public static void dingPlayer(ServerPlayer serverPlayer) {
        serverPlayer.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_CHIME, SoundSource.MASTER, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), 1.0F, 1.0F, serverPlayer.getRandom().nextLong()));
    }
}
