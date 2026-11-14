package dev.pixelclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PixelClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Pixel Client");
    public static final PixelConfig CONFIG = new PixelConfig();
    public static final ScannerEngine ENGINE = new ScannerEngine(CONFIG);

    private static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("pixelclient", "client")
        );
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.pixelclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                if (client.player != null) client.setScreen(new PixelScreen());
            }
            ENGINE.tick(client);
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("pixelclient", "radar"),
            (graphics, tickCounter) -> PixelHud.draw(graphics, Minecraft.getInstance()));
        LOG.info("Pixel Client ready. No external networking or token access.");
    }
}
