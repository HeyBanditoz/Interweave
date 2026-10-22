package mixin;

import main.Interweave;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {
    @Inject(at = @At("RETURN"), method = "sendSystemMessage")
    public void handleMessage(Component text, CallbackInfo ci) {
        if (text.getContents() instanceof TranslatableContents)
            Interweave.sendMessage((TranslatableContents) text.getContents());
    }

    @Inject(at = @At("RETURN"), method = "logChatMessage")
    public void handleChatMessage(Component message, ChatType.Bound params, String prefix, CallbackInfo ci) {
        // /me arrives as chat too, so tell it apart by its chat type
        if (params.chatType().is(ChatType.EMOTE_COMMAND))
            Interweave.sendMessage(new TranslatableContents("chat.type.emote",null ,new Object[]{params,message,prefix}));
        else if (message.getContents() instanceof ComponentContents)
            Interweave.sendMessage(new TranslatableContents("chat.type.text",null ,new Object[]{params,message.getContents(),prefix}));
    }
}