package com.lix.quickbz.util;

import com.lix.quickbz.QuickBZMod;
import com.lix.quickbz.config.QuickBZConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BazaarActionHandler {

    private static final int COOLDOWN_TICKS = 20;
    private static final Map<String, Integer> cooldowns = new HashMap<>();
    private static final Set<Integer> heldLastTick = new HashSet<>();

    /**
     * When non-null, we are waiting for the NEXT container screen
     * (containerId != pendingFromId) to open and populate, then execute.
     */
    private static QuickBZConfig.HotkeyEntry pendingEntry  = null;
    /** containerId of the screen we just acted on; wait for something different. */
    private static int                        pendingFromId = -1;
    /** When non-null, we typed into the sign last tick and need to confirm this tick. */
    private static QuickBZConfig.HotkeyEntry  pendingSignConfirm = null;
    /** When non-null, sign was just closed — click Custom Amount on the BUY_INSTANTLY screen to confirm. */
    private static QuickBZConfig.HotkeyEntry  pendingCustomAmountConfirm = null;
    /** When true, close the screen next tick. */
    private static boolean pendingEscapeClose = false;

    public static void tick(Minecraft client) {
        QuickBZConfig cfg = QuickBZConfig.get();

        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));

        // ── Confirm sign one tick after typing ────────────────────────────────
        if (pendingSignConfirm != null) {
            QuickBZMod.LOGGER.info("[QuickBZ] Confirming sign for '{}'.", pendingSignConfirm.bzArgument());
            BazaarScreenAutomator.confirmSign(client);
            pendingCustomAmountConfirm = pendingSignConfirm;
            pendingSignConfirm = null;
            pendingEntry  = null;
            pendingFromId = -1;
            return;
        }

        // ── Close screen one tick after final action ──────────────────────────
        if (pendingEscapeClose) {
            pendingEscapeClose = false;
            QuickBZMod.LOGGER.info("[QuickBZ] Closing BZ screen (closeContainer).");
            client.player.closeContainer();
            return;
        }

        // ── Click Custom Amount to confirm purchase after sign closes ─────────
        if (pendingCustomAmountConfirm != null) {
            BazaarScreenHelper.BazaarScreen curScreen = BazaarScreenHelper.getBazaarScreen(client);
            String curTitle = client.screen == null ? "null" : client.screen.getTitle().getString();
            QuickBZMod.LOGGER.info("[QuickBZ] pendingCustomAmountConfirm tick: bzScreen={} title='{}'",
                    curScreen, curTitle);
            if (curScreen == BazaarScreenHelper.BazaarScreen.BUY_INSTANTLY) {
                AbstractContainerScreen<?> screen = BazaarScreenHelper.getContainerScreen(client);
                boolean populated = screen != null && BazaarScreenHelper.isContainerPopulated(screen);
                QuickBZMod.LOGGER.info("[QuickBZ] BUY_INSTANTLY screen found, populated={}", populated);
                if (populated) {
                    QuickBZMod.LOGGER.info("[QuickBZ] Clicking Custom Amount to confirm purchase for '{}'.",
                            pendingCustomAmountConfirm.bzArgument());
                    BazaarScreenAutomator.clickCustomAmount(client, screen, pendingCustomAmountConfirm);
                    pendingCustomAmountConfirm = null;
                    pendingEscapeClose = true;
                    return;
                }
            }
            // Still waiting — don't fall through to pendingEntry logic
            return;
        }

        // ── Handle pending auto-execute ───────────────────────────────────────
        if (pendingEntry != null) {
            // Sign screen: fill amount and finish — no containerId needed
            boolean isSign = BazaarScreenHelper.isSignScreen(client);
            QuickBZMod.LOGGER.info(
                    "[QuickBZ] Pending tick: screen={} isSign={} amount={} bzScreen={}",
                    client.screen == null ? "null" : client.screen.getClass().getSimpleName(),
                    isSign, pendingEntry.amount,
                    BazaarScreenHelper.getBazaarScreen(client));
            if (isSign && pendingEntry.amount > 0) {
                QuickBZMod.LOGGER.info(
                        "[QuickBZ] Sign screen detected for '{}', typing amount={}.",
                        pendingEntry.bzArgument(), pendingEntry.amount);
                if (BazaarScreenAutomator.typeSignAmount(client, pendingEntry)) {
                    pendingSignConfirm = pendingEntry; // confirm next tick
                    // leave pendingEntry set so the sign confirm clears it
                }
            } else {
                String currentTitle = client.screen == null ? "" : client.screen.getTitle().getString();
                boolean titleMatchesItem =
                        (pendingEntry.displayName != null && !pendingEntry.displayName.isBlank()
                                && currentTitle.contains("➤ " + pendingEntry.displayName))
                                || currentTitle.contains("➤ " + pendingEntry.itemId);
                boolean bzOpen = titleMatchesItem || BazaarScreenHelper.isBazaarScreenOpen(client);

                if (bzOpen) {
                    AbstractContainerScreen<?> screen = BazaarScreenHelper.getContainerScreen(client);
                    String title = client.screen.getTitle().getString();

                    if (screen == null) {
                        QuickBZMod.LOGGER.info(
                                "[QuickBZ] Pending '{}': BZ screen is non-standard type '{}', title='{}', attempting execute.",
                                pendingEntry.bzArgument(),
                                client.screen.getClass().getSimpleName(), title);
                        QuickBZConfig.HotkeyEntry toExecute = pendingEntry;
                        pendingEntry  = null;
                        pendingFromId = -1;
                        int clickedId = BazaarScreenAutomator.execute(client, toExecute);
                        if (BazaarScreenAutomator.shouldCloseAfterAction) {
                            pendingEscapeClose = true;
                        } else if (clickedId != -1) {
                            pendingEntry  = toExecute;
                            pendingFromId = clickedId;
                            QuickBZMod.LOGGER.info("[QuickBZ] Auto-click done (fromId={}), pending next screen.", clickedId);
                        }
                    } else {
                        int currentId = screen.getMenu().containerId;
                        boolean populated = BazaarScreenHelper.isContainerPopulated(screen);

                        QuickBZMod.LOGGER.info(
                                "[QuickBZ] Pending check: entry='{}' pendingFromId={} currentId={} title='{}' populated={}",
                                pendingEntry.bzArgument(), pendingFromId, currentId, title, populated);

                        if (currentId == pendingFromId) {
                            QuickBZMod.LOGGER.info(
                                    "[QuickBZ] Still on same container (id={}), waiting for new screen.",
                                    currentId);
                        } else if (!populated) {
                            QuickBZMod.LOGGER.info(
                                    "[QuickBZ] New container (id={}) not yet populated, waiting.",
                                    currentId);
                        } else {
                            QuickBZMod.LOGGER.info(
                                    "[QuickBZ] New container (id={}) populated — executing pending '{}'.",
                                    currentId, pendingEntry.bzArgument());
                            QuickBZConfig.HotkeyEntry toExecute = pendingEntry;
                            pendingEntry  = null;
                            pendingFromId = -1;
                            int clickedId = BazaarScreenAutomator.execute(client, toExecute);
                            if (BazaarScreenAutomator.shouldCloseAfterAction) {
                                QuickBZMod.LOGGER.info("[QuickBZ] Final action done, scheduling screen close.");
                                pendingEscapeClose = true;
                            } else if (clickedId != -1) {
                                pendingEntry  = toExecute;
                                pendingFromId = clickedId;
                                QuickBZMod.LOGGER.info("[QuickBZ] Auto-click done (fromId={}), pending next screen.", clickedId);
                            }
                        }
                    }
                } else {
                    String screenClass = client.screen == null ? "null" : client.screen.getClass().getSimpleName();
                    String screenTitle = client.screen == null ? "null" : client.screen.getTitle().getString();
                    QuickBZMod.LOGGER.info(
                            "[QuickBZ] Pending '{}': getBazaarScreen=NONE screen={} title='{}'",
                            pendingEntry.bzArgument(), screenClass, screenTitle);
                }
            }
        }

        // ── Hotkey scan ───────────────────────────────────────────────────────
        if (cfg.hotkeyEntries.isEmpty()) return;

        // Don't fire hotkeys while any non-BZ GUI screen is open (e.g. the
        // hotkey manager, config screens, chat). This also prevents the key
        // used to bind a hotkey from immediately triggering that hotkey.
        if (client.screen != null && !BazaarScreenHelper.isBazaarScreenOpen(client)) return;

        for (QuickBZConfig.HotkeyEntry entry : cfg.hotkeyEntries) {
            if (entry.keyCode <= 0 || entry.itemId.isBlank()) continue;

            boolean isDown  = InputConstants.isKeyDown(client.getWindow(), entry.keyCode);
            boolean wasDown = heldLastTick.contains(entry.keyCode);

            if (isDown) heldLastTick.add(entry.keyCode);
            else        heldLastTick.remove(entry.keyCode);

            if (!isDown || wasDown) continue;

            // isTyping() guard kept as a secondary safety net (e.g. signs, anvils)
            if (isTyping(client)) {
                QuickBZMod.LOGGER.debug(
                        "[QuickBZ] Hotkey {} suppressed — player is typing.", entry.keyCode);
                continue;
            }

            String cooldownKey = entry.itemId + entry.actionType.name();
            if (cooldowns.getOrDefault(cooldownKey, 0) > 0) {
                QuickBZMod.LOGGER.debug(
                        "[QuickBZ] Hotkey for '{}' on cooldown ({} ticks left).",
                        entry.bzArgument(), cooldowns.get(cooldownKey));
                continue;
            }

            cooldowns.put(cooldownKey, COOLDOWN_TICKS);

            boolean bzOpen = BazaarScreenHelper.isBazaarScreenOpen(client);
            QuickBZMod.LOGGER.info(
                    "[QuickBZ] Hotkey pressed: item='{}' action={} bzOpen={}",
                    entry.bzArgument(), entry.actionType, bzOpen);

            if (!bzOpen) {
                openBazaar(client, entry);
                pendingEntry  = entry;
                pendingFromId = -1;
                QuickBZMod.LOGGER.info(
                        "[QuickBZ] /bz sent, pending from no-screen (fromId=-1).");
            } else {
                AbstractContainerScreen<?> screen = BazaarScreenHelper.getContainerScreen(client);
                int currentId = screen != null ? screen.getMenu().containerId : -1;
                QuickBZMod.LOGGER.info(
                        "[QuickBZ] BZ already open (id={}), executing then pending next screen.",
                        currentId);
                int manualClickedId = BazaarScreenAutomator.execute(client, entry);
                if (BazaarScreenAutomator.shouldCloseAfterAction) {
                    pendingEscapeClose = true;
                } else {
                    pendingEntry  = entry;
                    pendingFromId = manualClickedId != -1 ? manualClickedId : currentId;
                }
                QuickBZMod.LOGGER.info(
                        "[QuickBZ] Pending next screen after manual click (fromId={}).", currentId);
            }
        }
    }

    /** Returns true if the player is focused on a text input. */
    private static boolean isTyping(Minecraft client) {
        if (client.screen instanceof ChatScreen) return true;
        if (client.screen == null) return false;
        return client.screen.children().stream()
                .anyMatch(w -> w instanceof EditBox eb && eb.isFocused());
    }

    private static void openBazaar(Minecraft client, QuickBZConfig.HotkeyEntry entry) {
        String bzArg = entry.bzArgument();
        QuickBZMod.LOGGER.info("[QuickBZ] Sending command: /bz {}", bzArg);
        client.player.connection.sendCommand("bz " + bzArg);

        if (QuickBZConfig.get().showActionFeedback) {
            client.player.displayClientMessage(
                    Component.literal("§6[QuickBZ] §7Opening BZ for §e" + bzArg + "§7..."), false);
        }
    }
}