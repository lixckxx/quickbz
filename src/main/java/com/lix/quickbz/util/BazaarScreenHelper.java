package com.lix.quickbz.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public class BazaarScreenHelper {

    public enum BazaarScreen {
        NONE,
        /** "Bazaar ➜ ..." category/browse page - click the item name slot */
        BROWSE_PAGE,
        /** "Category ➜ Item" detail page - has Buy Instantly / Sell Instantly buttons */
        ITEM_DETAIL,
        /** "Item ➜ Instant Buy" - amount selection / confirmation page */
        BUY_INSTANTLY,
        /** "Item ➜ Instant Sell" - amount selection / confirmation page */
        SELL_INSTANTLY,
    }

    private static final int POPULATE_TIMEOUT_TICKS = 40;

    public static boolean isBazaarScreenOpen(Minecraft client) {
        return getBazaarScreen(client) != BazaarScreen.NONE;
    }

    public static BazaarScreen getBazaarScreen(Minecraft client) {
        if (client.screen == null) return BazaarScreen.NONE;
        String title = client.screen.getTitle().getString();

        if (title.contains("Instant Buy"))      return BazaarScreen.BUY_INSTANTLY;
        if (title.contains("Instant Sell"))     return BazaarScreen.SELL_INSTANTLY;
        // Top-level / category browse: title starts with "Bazaar"
        if (title.startsWith("Bazaar"))         return BazaarScreen.BROWSE_PAGE;
        // Item detail page: any other "X ➜ Y" title (category ➜ item)
        if (title.contains("➜"))           return BazaarScreen.ITEM_DETAIL;

        return BazaarScreen.NONE;
    }

    /** Returns true if the current screen is Minecraft's sign editor. */
    public static boolean isSignScreen(Minecraft client) {
        return client.screen instanceof SignEditScreen;
    }

    public static AbstractContainerScreen<?> getContainerScreen(Minecraft client) {
        if (client.screen instanceof AbstractContainerScreen<?> s) return s;
        return null;
    }

    public static int getContainerSlotCount(AbstractContainerScreen<?> screen) {
        int total = screen.getMenu().slots.size();
        return Math.max(0, total - 36);
    }

    public static boolean isContainerPopulated(AbstractContainerScreen<?> screen) {
        int containerSlots = getContainerSlotCount(screen);
        if (containerSlots == 0) return false;
        return screen.getMenu().slots.get(containerSlots - 1).hasItem();
    }

    public static int findSlotByName(AbstractContainerScreen<?> screen, String needle) {
        int containerSlots = getContainerSlotCount(screen);
        List<Slot> slots = screen.getMenu().slots;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = slots.get(i);
            if (!slot.hasItem()) continue;
            String name = slot.getItem().getHoverName().getString();
            if (name.contains(needle)) return slot.index;
        }
        return -1;
    }
}