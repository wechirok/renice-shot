/*
 * MIT License
 *
 * Copyright (c) 2021 Ramid Khan
 * Copyright (c) 2026 Wechirok
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.wechirok.reniceshot.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import me.wechirok.reniceshot.ReniceShot;
import me.wechirok.reniceshot.capture.CaptureTask;
import me.wechirok.reniceshot.config.Config;
import me.wechirok.reniceshot.event.FramebufferCaptureCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public class ScreenshotMixin {

    @Unique
    private static final ThreadLocal<Path> reniceShot$file = new ThreadLocal<>();

    @WrapMethod(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V")
    private static void capture(File gameDirectory, String fileName, RenderTarget renderTarget, int downscale,
                                Consumer<Component> messageReceiver, Operation<Void> original) {
        if (Config.OVERRIDE_MOD_SCREENSHOTS && !ReniceShot.isVanillaScreenshotKey()
                && renderTarget == Minecraft.getInstance().gameRenderer.mainRenderTarget()) {
            ReniceShot.startCapture(gameDirectory, fileName, downscale, messageReceiver,
                    task -> original.call(gameDirectory, fileName, renderTarget, downscale, task));
        } else {
            original.call(gameDirectory, fileName, renderTarget, downscale, messageReceiver);
        }
    }

    @WrapOperation(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V"))
    private static void onCapture(RenderTarget renderTarget, int downscale, Consumer<NativeImage> receiver,
                                  Operation<Void> original, @Local(argsOnly = true) Consumer<Component> messageReceiver) {
        if (messageReceiver instanceof CaptureTask task) {
            task.onCaptureStarted();
            original.call(renderTarget, downscale, (Consumer<NativeImage>) image -> Util.ioPool().execute(() -> {
                try {
                    FramebufferCaptureCallback.EVENT.invoker().onCapture(image);
                    reniceShot$file.set(task.file());
                    receiver.accept(image);
                } catch (RuntimeException | Error exception) {
                    image.close();
                    ReniceShot.reportFailure(exception, messageReceiver);
                } finally {
                    reniceShot$file.remove();
                }
            }));
        } else {
            original.call(renderTarget, downscale, receiver);
        }
    }

    @WrapMethod(method = "getFile")
    private static File screenshotFile(File directory, Operation<File> original) {
        Path file = reniceShot$file.get();
        return file != null && file.getParent().equals(directory.toPath()) ? file.toFile() : original.call(directory);
    }
}
