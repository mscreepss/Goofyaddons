package com.goofy.goofyaddons;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class GoofyAddonsClient implements ClientModInitializer {
    Book book = new Book("ENCHANTMENT_ULTIMATE_WISE", 1, 5, "Ultimate Wise");
    Task task;

    @Override
    public void onInitializeClient() {
        GoofyConfig.load();
        ChatHook.register();
        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FailsafeManager.INSTANCE.onTick();
            FeatureManager.INSTANCE.onTick();



            boolean keyDown = InputConstants.isKeyDown(minecraft.getWindow(), GoofyConfig.INSTANCE.startKey);
            boolean keyDown1 = InputConstants.isKeyDown(minecraft.getWindow(), GoofyConfig.INSTANCE.stopKey);

            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_BACKSLASH)) GoofyConfig.save();

            if (keyDown && client.screen == null) FeatureManager.INSTANCE.start("BazaarFlipper");
            if (keyDown1 && client.screen == null) FeatureManager.INSTANCE.stop();

            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_B)) {
                task = new Task(book);
            }

            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_L)) {
                int num = task.assignBook(book, 1, 0, 5);
                System.out.println(num);
                System.out.println(task.getBookPool().size());
            }

        });
    }
}