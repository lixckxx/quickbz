package com.lix.quickbz;

import com.lix.quickbz.command.QuickBZCommands;
import com.lix.quickbz.config.QuickBZConfig;
import com.lix.quickbz.util.BazaarActionHandler;
import com.lix.quickbz.util.ItemSuggester;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuickBZMod implements ClientModInitializer {

    public static final String MOD_ID = "quickbz";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[QuickBZ] Initializing...");

        QuickBZConfig.load();
        QuickBZCommands.register();
        ItemSuggester.fetchAsync();

        ClientTickEvents.END_CLIENT_TICK.register((Minecraft client) -> {
            if (client.player == null) return;

            // Open any screen that was scheduled by a command (e.g. /qbz hotkeys).
            // Runs one tick after the command, so the chat screen is already closed.
            if (QuickBZCommands.pendingScreen != null && client.screen == null) {
                client.setScreen(QuickBZCommands.pendingScreen);
                QuickBZCommands.pendingScreen = null;
            }

            BazaarActionHandler.tick(client);
        });

        LOGGER.info("[QuickBZ] Ready!");
    }
}