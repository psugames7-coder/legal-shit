package com.psu.debrisfinder.mixin;

import com.psu.debrisfinder.Highlights;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void debrisfinder$glow(CallbackInfoReturnable<Boolean> cir) {
        if (Highlights.isMarker((Entity) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void debrisfinder$color(CallbackInfoReturnable<Integer> cir) {
        if (Highlights.isMarker((Entity) (Object) this)) cir.setReturnValue(Highlights.COLOR);
    }
}
