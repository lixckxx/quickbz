package com.lix.quickbz.config;

import com.lix.quickbz.util.ItemSuggester;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for managing QuickBZ hotkey entries.
 * Open via /qbz config or ModMenu.
 */
public class HotkeyManagerScreen extends Screen {

    private final Screen parent;
    private final List<HotkeyRow> rows = new ArrayList<>();

    private static final int ROW_H    = 22;
    private static final int ROW_PAD  = 4;
    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 38;

    // Column widths set in init()
    private int rowX, rowWidth;
    private int colItem, colAction, colKey, colAmount, colRemove;

    // Key capture
    private HotkeyRow capturingRow = null;

    public HotkeyManagerScreen(Screen parent) {
        super(Component.literal("§6QuickBZ §f- Hotkey Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rowX     = 12;
        rowWidth = width - 24;
        colItem   = (int)(rowWidth * 0.35);
        colAction = 54;
        colKey    = 94;
        colAmount = 62;
        colRemove = rowWidth - colItem - colAction - colKey - colAmount - 9;

        rebuildWidgets();
    }

    /** Clears all widgets and rebuilds from the current rows list. */
    protected void rebuildWidgets() {
        clearWidgets();
        rows.clear();

        for (QuickBZConfig.HotkeyEntry entry : QuickBZConfig.get().hotkeyEntries) {
            rows.add(new HotkeyRow(entry));
        }
        layoutRows();

        // "+ Add Hotkey" button
        addRenderableWidget(Button.builder(Component.literal("+ Add Hotkey"), btn -> {
            QuickBZConfig.HotkeyEntry e = new QuickBZConfig.HotkeyEntry();
            QuickBZConfig.get().hotkeyEntries.add(e);
            rows.add(new HotkeyRow(e));
            layoutRows();
        }).bounds(rowX, height - FOOTER_H + 9, 114, 20).build());

        // "Done" button
        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(width - rowX - 114, height - FOOTER_H + 9, 114, 20).build());
    }

    /** Positions each row's widgets and registers them. */
    private void layoutRows() {
        for (HotkeyRow row : rows) {
            int y = HEADER_H + ROW_PAD + rows.indexOf(row) * (ROW_H + ROW_PAD);
            row.layout(rowX, y, colItem, colAction, colKey, colAmount, colRemove);
            addRenderableWidget(row.itemField);
            addRenderableWidget(row.actionBtn);
            addRenderableWidget(row.keyBtn);
            addRenderableWidget(row.amountField);
            addRenderableWidget(row.removeBtn);
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        // 1. Let the superclass handle background and standard widget rendering
        super.render(gfx, mouseX, mouseY, delta);

        // 2. Title
        gfx.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        // 3. Column headers
        int hdrY = HEADER_H - 13;
        gfx.drawString(font, "§7Item ID", rowX + 2, hdrY, 0xAAAAAA);
        gfx.drawString(font, "§7Action",  rowX + colItem + 4, hdrY, 0xAAAAAA);
        gfx.drawString(font, "§7Hotkey",  rowX + colItem + colAction + 4, hdrY, 0xAAAAAA);
        gfx.drawString(font, "§7Amt",     rowX + colItem + colAction + colKey + 4, hdrY, 0xAAAAAA);

        // 4. Row backgrounds
        for (int i = 0; i < rows.size(); i++) {
            int y = HEADER_H + ROW_PAD + i * (ROW_H + ROW_PAD);
            gfx.fill(rowX - 2, y - 2, rowX + rowWidth + 2, y + ROW_H + 2, 0x22FFFFFF);
        }

        // 5. Key capture hint (rendered last so it appears on top)
        if (capturingRow != null) {
            String hint = "Press a key (Esc = unbind)";
            int hw = font.width(hint) + 12;
            int hx = (width - hw) / 2;
            int hy = height - FOOTER_H - 26;
            gfx.fill(hx - 2, hy - 2, hx + hw + 2, hy + 13, 0xDD000000);
            gfx.drawString(font, "§e" + hint, hx + 6, hy + 2, 0xFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int keyCode = e.key();

        if (capturingRow != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturingRow.entry.keyCode = -1;
            } else {
                capturingRow.entry.keyCode = keyCode;
            }
            capturingRow.updateKeyLabel();
            capturingRow = null;
            return true;
        }

        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        for (HotkeyRow row : rows) row.flush();
        QuickBZConfig.save();
        minecraft.setScreen(parent);
    }

    class HotkeyRow {
        final QuickBZConfig.HotkeyEntry entry;

        EditBox itemField;
        Button  actionBtn;
        Button  keyBtn;
        EditBox amountField;
        Button  removeBtn;

        HotkeyRow(QuickBZConfig.HotkeyEntry entry) {
            this.entry = entry;
        }

        void layout(int x, int y, int wItem, int wAction, int wKey, int wAmount, int wRemove) {
            final int gap = 3;
            int cx = x;

            // Item ID field
            itemField = new EditBox(font, cx, y, wItem - gap, ROW_H, Component.literal("Item ID"));
            itemField.setMaxLength(64);
            itemField.setValue(entry.itemId);
            itemField.setResponder(s -> {
                entry.itemId = s.trim().toUpperCase();
                String dn = ItemSuggester.getDisplayName(entry.itemId);
                if (dn != null) entry.displayName = dn;
                String status = ItemSuggester.isFetched()
                        ? (dn != null ? "§a" + dn : "§cUnknown ID")
                        : "§7Loading item list...";
                itemField.setTooltip(Tooltip.create(Component.literal(status)));
            });
            String initDn = ItemSuggester.getDisplayName(entry.itemId);
            String initStatus = !entry.itemId.isBlank()
                    ? (initDn != null ? "§a" + initDn : "§7(Loading...)")
                    : "§7Enter a SkyBlock item ID";
            itemField.setTooltip(Tooltip.create(Component.literal(initStatus)));
            cx += wItem;

            // Buy / Sell toggle, also shows/hides the amount field
            actionBtn = Button.builder(Component.literal(entry.actionType.toString()), btn -> {
                entry.actionType = (entry.actionType == QuickBZConfig.ActionType.INSTABUY)
                        ? QuickBZConfig.ActionType.INSTASELL
                        : QuickBZConfig.ActionType.INSTABUY;
                btn.setMessage(Component.literal(entry.actionType.toString()));
                // Show amount field only for BUY
                amountField.setVisible(entry.actionType == QuickBZConfig.ActionType.INSTABUY);
            }).bounds(cx, y, wAction - gap, ROW_H).build();
            cx += wAction;

            // Hotkey button
            keyBtn = Button.builder(keyLabel(), btn -> {
                capturingRow = this;
                btn.setMessage(Component.literal("..."));
            }).bounds(cx, y, wKey - gap, ROW_H).build();
            cx += wKey;

            // Amount field - hidden for SELL but still occupies space
            // so the remove button never shifts.
            amountField = new EditBox(font, cx, y, wAmount - gap, ROW_H, Component.literal("0"));
            amountField.setMaxLength(6);
            amountField.setValue(entry.amount > 0 ? String.valueOf(entry.amount) : "");
            amountField.setResponder(s -> {
                try { entry.amount = s.isBlank() ? 0 : Integer.parseInt(s.trim()); }
                catch (NumberFormatException ignored) {}
            });
            amountField.setTooltip(Tooltip.create(Component.literal("Amount (0 = use default)")));
            // Apply initial visibility
            amountField.setVisible(entry.actionType == QuickBZConfig.ActionType.INSTABUY);
            cx += wAmount;

            // Remove button
            removeBtn = Button.builder(Component.literal("§c✕"), btn -> {
                QuickBZConfig.get().hotkeyEntries.remove(entry);
                rebuildWidgets();
            }).bounds(cx, y, wRemove, ROW_H).build();
        }

        Component keyLabel() {
            if (entry.keyCode <= 0) return Component.literal("Unbound");
            try {
                return Component.literal(
                        InputConstants.Type.KEYSYM.getOrCreate(entry.keyCode).getDisplayName().getString()
                );
            } catch (Exception e) {
                return Component.literal("?");
            }
        }

        void updateKeyLabel() {
            if (keyBtn != null) keyBtn.setMessage(keyLabel());
        }

        void flush() {
            if (itemField != null) {
                entry.itemId = itemField.getValue().trim().toUpperCase();
                String dn = ItemSuggester.getDisplayName(entry.itemId);
                if (dn != null) entry.displayName = dn;
            }
            // Only persist amount if the field was visible (i.e. BUY action)
            if (amountField != null && amountField.isVisible()) {
                try { entry.amount = Integer.parseInt(amountField.getValue().trim()); }
                catch (NumberFormatException ignored) { entry.amount = 0; }
            } else {
                entry.amount = 0;
            }
        }
    }
}