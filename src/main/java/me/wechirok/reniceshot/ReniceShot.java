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
import me.wechirok.reniceshot.event.ScreenshotSaveCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReniceShot {

    private static final Logger LOGGER = LogManager.getLogger(ReniceShot.class);

    public static final KeyMapping SCREENSHOT_BINDING = new KeyMapping(
            "key.renice-shot.screenshot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KeyMapping.Category.MISC);

    private static CaptureTask task;

    private static void printFileLink(Path path) {
        Minecraft minecraft = Minecraft.getInstance();

        Component fileText = Component.literal(path.toFile().getName())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(path)));
        minecraft.execute(() -> {
            Component message = Component.translatable("screenshot.success", fileText);

            minecraft.gui.hud.getChat().addClientSystemMessage(message);
            minecraft.getNarrator().saySystemQueued(message);
        });
    }

    public static void initialize() {
        KeyMappingHelper.registerKeyMapping(SCREENSHOT_BINDING);
        ScreenshotSaveCallback.EVENT.register(ReniceShot::printFileLink);
    }

    public static void startCapture() {
        Minecraft minecraft = Minecraft.getInstance();

        if (task == null) {
            boolean saveFile = Config.SAVE_FILE;
            FileFormat fileFormat = Config.CAPTURE_FILE_FORMAT;

            try {
                task = new CaptureTask(minecraft, getScreenshotFile(minecraft, saveFile, fileFormat), saveFile, fileFormat);
                task.setResolution(Config.CAPTURE_WIDTH, Config.CAPTURE_HEIGHT);
                refresh();
            } catch (IOException exception) {
                reportFailure(exception);
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
        } catch (RuntimeException | Error exception) {
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
        }
    }

    private static void refresh() {
        Minecraft.getInstance().resizeGui();
    }

    public static void reportFailure(Throwable exception) {
        LOGGER.error("Screenshot capture failed", exception);

        Minecraft minecraft = Minecraft.getInstance();
        String reason = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        minecraft.execute(() -> {
            Component message = Component.translatable("screenshot.failure", reason);
            minecraft.gui.hud.getChat().addClientSystemMessage(message);
            minecraft.getNarrator().saySystemQueued(message);
        });
    }

    private static Path getScreenshotFile(Minecraft client, boolean saveFile, FileFormat fileFormat) throws IOException {
        Path dir = client.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(dir);

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
