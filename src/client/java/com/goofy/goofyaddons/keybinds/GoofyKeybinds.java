package com.goofy.goofyaddons.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class GoofyKeybinds {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("goofyaddons", "category")
    );

    public static KeyMapping startKey;
    public static KeyMapping stopKey;
    public static KeyMapping reloadConfigKey;

    public static void register() {
        startKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.start",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));

        stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.stop",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CATEGORY
        ));

        // NOT: GLFW'nin tuş sabitleri (GLFW_KEY_*) fiziksel/konum tabanlıdır ve her zaman
        // ABD (QWERTY) klavye düzenindeki o karakterin bulunduğu FİZİKSEL tuş konumunu temsil eder;
        // aktif klavye düzeninden bağımsızdır. Türkçe Q klavyede "/" karakteri Shift+7 ile yazılır,
        // GLFW_KEY_SLASH ise ABD düzeninde "/" olan fiziksel tuş konumunu (TR'de "." tuşunun olduğu yer)
        // temsil eder. Minecraft'ın tuş atama menüsünde bu tuş yine de "/" olarak görünecektir.
        // Eğer gerçekten TR klavyenizde basarken "/" yazdığınız fiziksel tuşu (Shift+7) istiyorsanız
        // GLFW.GLFW_KEY_7 kullanmanız gerekir; ona göre haber verin, değiştiririm.
        reloadConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.reload_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_SLASH,
                CATEGORY
        ));
    }
}
