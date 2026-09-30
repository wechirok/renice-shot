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

package me.wechirok.reniceshot;

import com.mojang.blaze3d.platform.InputConstants;
import me.wechirok.reniceshot.capture.CaptureTask;
import me.wechirok.reniceshot.config.Config;
import me.wechirok.reniceshot.config.FileFormat;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

public class ReniceShot {

    private static final Logger LOGGER = LogManager.getLogger(ReniceShot.class);

    private static final InputConstants.Key DEFAULT_KEY = InputConstants.getKey("key.keyboard.f9");

    public static final KeyMapping SCREENSHOT_BINDING = new KeyMapping(
            "key.renice-shot.screenshot",
            DEFAULT_KEY.getType(),
            DEFAULT_KEY.getValue(),
            KeyMapping.Category.MISC);

    private static final Queue<CaptureTask> pendingCaptures = new ArrayDeque<>();
    private static final ThreadLocal<Boolean> vanillaScreenshotKey = new ThreadLocal<>();
    private static CaptureTask task;

    public static void withVanillaScreenshotKey(Runnable screenshot) {
        vanillaScreenshotKey.set(true);
        try {
            screenshot.run();
        } finally {
            vanillaScreenshotKey.remove();
        }
    }

    public static boolean isVanillaScreenshotKey() {
        return Boolean.TRUE.equals(vanillaScreenshotKey.get());
    }

    public static void showMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            minecraft.gui.hud.getChat().addClientSystemMessage(message);
            minecraft.getNarrator().saySystemQueued(message);
        });
    }

    public static void initialize() {
        KeyMappingHelper.registerKeyMapping(SCREENSHOT_BINDING);
    }

    public static void startCapture() {
        startCapture(Minecraft.getInstance().gameDirectory, null, 1, ReniceShot::showMessage, null);
    }

    public static void startCapture(File gameDirectory, String fileName, int downscale,
                                    Consumer<Component> messageReceiver, Consumer<CaptureTask> capture) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            boolean saveFile = Config.SAVE_FILE;
            FileFormat fileFormat = fileName == null ? Config.CAPTURE_FILE_FORMAT : FileFormat.PNG;

            try {
                if (downscale < 1 || Config.CAPTURE_WIDTH % downscale != 0 || Config.CAPTURE_HEIGHT % downscale != 0) {
                    throw new IllegalArgumentException("Capture size must be divisible by the downscale factor");
                }
                Path file = getScreenshotFile(minecraft, gameDirectory.toPath(), fileName, saveFile, fileFormat);
                pendingCaptures.add(new CaptureTask(minecraft, file, saveFile, fileFormat, downscale,
                        fileName == null && saveFile, messageReceiver,
                        saveFile && fileFormat == FileFormat.PNG ? capture : null));
                beginNextCapture();
            } catch (IOException | RuntimeException exception) {
                reportFailure(exception, messageReceiver);
            }
        });
    }

    private static void beginNextCapture() {
        while (task == null && !pendingCaptures.isEmpty()) {
            task = pendingCaptures.remove();
            try {
                task.begin();
                refresh();
            } catch (RuntimeException exception) {
                task.discardReservedFile(exception);
                reportFailure(exception, task.messageReceiver());
                task.restoreState();
                task = null;
                refresh();
            }
        }
    }

    public static void onRenderPreOrPost() {
        CaptureTask currentTask = task;
        if (currentTask == null) {
            return;
        }

        final boolean finished;
        try {
            finished = currentTask.onRenderTick();
        } catch (RuntimeException exception) {
            currentTask.discardReservedFile(exception);
            reportFailure(exception, currentTask.messageReceiver());
            finishCapture(currentTask);
            return;
        } catch (Error exception) {
            currentTask.discardReservedFile(exception);
            finishCapture(currentTask);
            throw exception;
        }

        if (finished) {
            finishCapture(currentTask);
        }
    }

    private static void finishCapture(CaptureTask completedTask) {
        try {
            completedTask.restoreState();
        } finally {
            if (task == completedTask) {
                task = null;
            }
            refresh();
            beginNextCapture();
        }
    }

    private static void refresh() {
        Minecraft.getInstance().resizeGui();
    }

    public static void reportFailure(Throwable exception) {
        reportFailure(exception, ReniceShot::showMessage);
    }

    public static void reportFailure(Throwable exception, Consumer<Component> messageReceiver) {
        LOGGER.error("Screenshot capture failed", exception);

        Minecraft minecraft = Minecraft.getInstance();
        String reason = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        minecraft.execute(() -> {
            messageReceiver.accept(Component.translatable("screenshot.failure", reason));
        });
    }

    private static Path getScreenshotFile(Minecraft client, Path gameDirectory, String fileName,
                                          boolean saveFile, FileFormat fileFormat) throws IOException {
        Path dir = gameDirectory.resolve("screenshots");
        Files.createDirectories(dir);
        if (fileName != null) {
            return dir.resolve(fileName);
        }

        String world = null;

        if (client.getSingleplayerServer() != null) {
            world = client.getSingleplayerServer().getWorldData().getLevelName();
        } else if (client.getCurrentServer() != null) {
            world = client.getCurrentServer().name;
        }

        String prefix = Config.CUSTOM_FILE_NAME
                .replace("%time%", Util.getFilenameFormattedDateTime())
                .replace("%world%", world != null ? world : "no_world");

        for (int index = 1; ; index++) {
            String suffix = index == 1 ? "" : "_" + index;
            Path file = dir.resolve(prefix + suffix + fileFormat.extension());

            if (!saveFile) {
                if (!Files.exists(file)) {
                    return file;
                }
                continue;
            }

            try {
                return Files.createFile(file);
            } catch (FileAlreadyExistsException ignored) {
                continue;
            }
        }
    }

    public static boolean isInCapture() {
        return task != null;
    }
}
