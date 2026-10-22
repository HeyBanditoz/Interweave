package mixin;

import java.util.List;
import main.Interweave;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class MixinPlayerManager {
    @Shadow @Final private List<ServerPlayer> players;

    @Shadow @Final private MinecraftServer server;

    @Inject(at = @At("RETURN"), method = "placeNewPlayer")
    public void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        Interweave.setPlayers(players.size(), server.getMaxPlayers());
    }

    @Inject(at = @At("RETURN"), method = "remove")
    public void remove(ServerPlayer player, CallbackInfo ci) {
        Interweave.setPlayers(players.size(), server.getMaxPlayers());
    }

//    @Inject(at = @At("RETURN"), method = "broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Lnet/minecraft/util/registry/RegistryKey;)V")
//    public void broadcast(SignedMessage text, Function f, MessageSender s, RegistryKey rk, CallbackInfo ci) {
////        if (!text.getContent().equals(Text.of(Interweave.getLastMessage())))
////            Interweave.sendMessage(text.getContent());
//        System.out.println("asdhkjfjklahdsfjklbadghjbklagdhbjkladsghkbjasdgjklahsdgadghjklbadsgjkhladsgadgkjagdshljk");
//    }
}
