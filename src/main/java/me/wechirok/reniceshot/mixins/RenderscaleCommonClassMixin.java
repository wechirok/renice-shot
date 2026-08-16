package me.wechirok.reniceshot.mixins;

import me.wechirok.reniceshot.ReniceShot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.zelo.renderscale.RenderScale")
public class RenderscaleCommonClassMixin {

    @Inject(method = "setShouldScale", at = @At("HEAD"), cancellable = true, require = 0)
    private void disableScalingDuringScreenshot(boolean shouldScale, CallbackInfo ci) {
        if (ReniceShot.isInCapture()) ci.cancel();
    }
}
