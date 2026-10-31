package com.psu.debrisfinder.mixin;

import com.psu.debrisfinder.Highlights;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void debrisfinder$outline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Highlights.isMarker(entity)) cir.setReturnValue(true);
    }
}
