package com.lix.quickbz.util;

import com.lix.quickbz.QuickBZMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Debug helper for QuickBZ.
 *
 * /qbz debug on   — auto-dumps slots when any BZ screen opens
 * /qbz debug off
 * /qbz debug dump — dumps the currently open screen immediately
 *
 * Paste the ===QUICKBZ DEBUG=== block from latest.log to report issues.
 */
public class QuickBZDebug {

    private static boolean enabled = false;

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) {
        enabled = v;
        QuickBZMod.LOGGER.info("[QuickBZ] Debug mode: {}", v ? "ON" : "OFF");
    }

    public static void dumpCurrentScreen(Minecraft client) {
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            log(client, "No AbstractContainerScreen is open.");
            return;
        }

        String title = screen.getTitle().getString();
        List<Slot> allSlots = screen.getMenu().slots;
        int containerSlots = BazaarScreenHelper.getContainerSlotCount(screen);
        boolean populated = BazaarScreenHelper.isContainerPopulated(screen);

        StringBuilder sb = new StringBuilder();
        sb.append("\n===QUICKBZ DEBUG START===\n");
        sb.append("Screen title:     \"").append(title).append("\"\n");
        sb.append("Total slots:      ").append(allSlots.size()).append("\n");
        sb.append("Container slots:  ").append(containerSlots)
                .append("  (player inventory excluded)\n");
        sb.append("BZ type:          ").append(BazaarScreenHelper.getBazaarScreen(client)).append("\n");
        sb.append("Slots populated:  ").append(populated).append("\n");
        sb.append("Container slots with items:\n");

        for (int i = 0; i < containerSlots; i++) {
            Slot slot = allSlots.get(i);
            if (!slot.hasItem()) continue;
            var stack = slot.getItem();
            String name = stack.getHoverName().getString();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            sb.append(String.format("  Slot %3d: %-45s [%s]%n",
                    slot.index, "\"" + name + "\"", id));
        }
        sb.append("===QUICKBZ DEBUG END===");

        QuickBZMod.LOGGER.info(sb.toString());
        log(client, "Container dump for \"" + title + "\" (" + containerSlots +
                " container slots, populated=" + populated +
                ") → §flatest.log§7. Paste the ===QUICKBZ DEBUG=== block here.");
    }

    private static void log(Minecraft client, String msg) {
        QuickBZMod.LOGGER.info("[QuickBZ Debug] {}", msg);
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("§6[QuickBZ Debug] §7" + msg), false);
        }
    }
}