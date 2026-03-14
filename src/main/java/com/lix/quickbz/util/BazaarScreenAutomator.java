package com.lix.quickbz.util;

import com.lix.quickbz.QuickBZMod;
import com.lix.quickbz.config.QuickBZConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.world.inventory.ClickType;
import org.lwjgl.glfw.GLFW;

public class BazaarScreenAutomator {

    /** Set to true when the final action was performed and the caller should close the screen. */
    public static boolean shouldCloseAfterAction = false;

    /**
     * Executes the appropriate BZ action for the current screen.
     * Returns the containerId acted on, or -1 if nothing was done.
     */
    public static int execute(Minecraft client, QuickBZConfig.HotkeyEntry entry) {
        shouldCloseAfterAction = false;
        BazaarScreenHelper.BazaarScreen bzScreen = BazaarScreenHelper.getBazaarScreen(client);

        // Sign screen is handled separately — caller checks isSignScreen first
        if (bzScreen == BazaarScreenHelper.BazaarScreen.NONE) {
            QuickBZMod.LOGGER.warn(
                    "[QuickBZ] execute() called but no BZ screen open (screen={} title='{}').",
                    client.screen == null ? "null" : client.screen.getClass().getSimpleName(),
                    client.screen == null ? "" : client.screen.getTitle().getString());
            return -1;
        }

        AbstractContainerScreen<?> screen = BazaarScreenHelper.getContainerScreen(client);
        if (screen == null) {
            QuickBZMod.LOGGER.warn(
                    "[QuickBZ] BZ screen '{}' is not an AbstractContainerScreen, cannot click slots.",
                    client.screen.getClass().getSimpleName());
            return -1;
        }

        int containerId = screen.getMenu().containerId;
        QuickBZMod.LOGGER.info(
                "[QuickBZ] execute(): screen='{}' bzType={} containerId={}",
                screen.getTitle().getString(), bzScreen, containerId);

        switch (bzScreen) {
            case BROWSE_PAGE  -> handleBrowsePage(client, screen, entry);
            case ITEM_DETAIL  -> handleItemDetail(client, screen, entry);
            case BUY_INSTANTLY  -> handleBuyInstantly(client, screen, entry);
            case SELL_INSTANTLY -> handleSellInstantly(client, screen, entry);
            default -> {
                QuickBZMod.LOGGER.warn("[QuickBZ] No handler for screen type {}.", bzScreen);
                return -1;
            }
        }
        return containerId;
    }

    /**
     * Fills the sign editor with the entry's amount and submits.
     * Call this when isSignScreen() is true and there is a pending entry with amount > 0.
     */
    /**
     * Step 1: type the amount into the sign. Call this tick N.
     * Returns true so the caller knows to call confirmSign() on tick N+1.
     */
    public static boolean typeSignAmount(Minecraft client, QuickBZConfig.HotkeyEntry entry) {
        if (!(client.screen instanceof SignEditScreen signScreen)) {
            QuickBZMod.LOGGER.warn("[QuickBZ] typeSignAmount() called but screen is not SignEditScreen (it's {}).",
                    client.screen == null ? "null" : client.screen.getClass().getName());
            return false;
        }
        String amount = String.valueOf(entry.amount);
        QuickBZMod.LOGGER.info("[QuickBZ] Typing sign amount '{}'.", amount);
        for (char c : amount.toCharArray()) {
            signScreen.charTyped(new net.minecraft.client.input.CharacterEvent(c, 0));
        }
        return true;
    }

    /**
     * Step 2: press Escape to confirm. Call this on tick N+1 after typeSignAmount.
     */
    public static void confirmSign(Minecraft client) {
        if (!(client.screen instanceof SignEditScreen signScreen)) {
            QuickBZMod.LOGGER.warn("[QuickBZ] confirmSign() called but screen is not SignEditScreen.");
            return;
        }
        QuickBZMod.LOGGER.info("[QuickBZ] Confirming sign (Escape).");
        signScreen.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
    }

    // ── Screen handlers ───────────────────────────────────────────────────────

    /** Category browse page — find and click the item's slot. */
    private static void handleBrowsePage(Minecraft client,
                                         AbstractContainerScreen<?> screen,
                                         QuickBZConfig.HotkeyEntry entry) {
        QuickBZMod.LOGGER.info(
                "[QuickBZ] handleBrowsePage(): looking for '{}' or '{}'",
                entry.displayName, entry.itemId);

        for (String candidate : new String[]{
                entry.displayName != null ? entry.displayName.trim() : "",
                entry.itemId }) {
            if (candidate.isBlank()) continue;
            int slotId = BazaarScreenHelper.findSlotByName(screen, candidate);
            QuickBZMod.LOGGER.info("[QuickBZ]   '{}' → slotId={}", candidate, slotId);
            if (slotId != -1) {
                clickSlot(client, screen, slotId, 0);
                return;
            }
        }

        QuickBZMod.LOGGER.warn("[QuickBZ] Item '{}' not found on browse page.", entry.bzArgument());
        dumpSlots(screen);
    }

    /** Item detail page — click Buy Instantly or Sell Instantly (always left-click). */
    private static void handleItemDetail(Minecraft client,
                                         AbstractContainerScreen<?> screen,
                                         QuickBZConfig.HotkeyEntry entry) {
        String target = entry.actionType == QuickBZConfig.ActionType.INSTABUY
                ? "Buy Instantly" : "Sell Instantly";
        int slotId = BazaarScreenHelper.findSlotByName(screen, target);
        QuickBZMod.LOGGER.info("[QuickBZ] handleItemDetail(): '{}' → slotId={}", target, slotId);

        if (slotId == -1) {
            QuickBZMod.LOGGER.warn("[QuickBZ] '{}' button not found.", target);
            dumpSlots(screen);
            return;
        }

        clickSlot(client, screen, slotId, 0);
        if (entry.actionType == QuickBZConfig.ActionType.INSTASELL) {
            shouldCloseAfterAction = true;
        }
    }

    /** "Item ➜ Instant Buy" page — click Custom Amount to open sign, or Confirm if no amount. */
    private static void handleBuyInstantly(Minecraft client,
                                           AbstractContainerScreen<?> screen,
                                           QuickBZConfig.HotkeyEntry entry) {
        QuickBZMod.LOGGER.info("[QuickBZ] handleBuyInstantly(): amount={}", entry.amount);

        if (entry.amount > 0) {
            clickCustomAmount(client, screen, entry);
        } else {
            int confirmSlot = BazaarScreenHelper.findSlotByName(screen, "Confirm");
            QuickBZMod.LOGGER.info("[QuickBZ]   'Confirm' → slotId={}", confirmSlot);
            if (confirmSlot != -1) {
                clickSlot(client, screen, confirmSlot, 0);
            }
        }
    }

    /**
     * Clicks "Custom Amount" on the Instant Buy page.
     * Called both on first entry (to open sign) and after sign close (to confirm).
     */
    public static void clickCustomAmount(Minecraft client,
                                         AbstractContainerScreen<?> screen,
                                         QuickBZConfig.HotkeyEntry entry) {
        int slotId = BazaarScreenHelper.findSlotByName(screen, "Custom Amount");
        QuickBZMod.LOGGER.info("[QuickBZ] clickCustomAmount(): slotId={}", slotId);
        if (slotId != -1) {
            clickSlot(client, screen, slotId, 0);
        } else {
            QuickBZMod.LOGGER.warn("[QuickBZ] 'Custom Amount' slot not found.");
            dumpSlots(screen);
        }
    }

    /** "Item ➜ Instant Sell" — nothing to do, sell completes after clicking the hopper. */
    private static void handleSellInstantly(Minecraft client,
                                            AbstractContainerScreen<?> screen,
                                            QuickBZConfig.HotkeyEntry entry) {
        QuickBZMod.LOGGER.info("[QuickBZ] handleSellInstantly(): sell complete for '{}'.", entry.bzArgument());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void clickSlot(Minecraft client, AbstractContainerScreen<?> screen,
                                  int slotId, int button) {
        QuickBZMod.LOGGER.info(
                "[QuickBZ] handleInventoryMouseClick(containerId={}, slotId={}, button={}, PICKUP)",
                screen.getMenu().containerId, slotId, button);
        client.gameMode.handleInventoryMouseClick(
                screen.getMenu().containerId, slotId, button, ClickType.PICKUP, client.player);
    }

    private static void dumpSlots(AbstractContainerScreen<?> screen) {
        int containerSlots = BazaarScreenHelper.getContainerSlotCount(screen);
        for (int i = 0; i < containerSlots; i++) {
            var slot = screen.getMenu().slots.get(i);
            if (slot.hasItem()) {
                QuickBZMod.LOGGER.warn("  Slot {:3d}: '{}'",
                        slot.index, slot.getItem().getHoverName().getString());
            }
        }
    }

}