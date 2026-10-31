package com.psu.debrisfinder.mixin;

import com.psu.debrisfinder.SeedStore;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void debrisfinder$watchForSeed(Text message, CallbackInfo ci) {
        SeedStore.onChat(message.getString());
    }
}
