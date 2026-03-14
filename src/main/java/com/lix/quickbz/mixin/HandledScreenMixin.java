package com.lix.quickbz.mixin;

import com.lix.quickbz.util.BazaarScreenHelper;
import com.lix.quickbz.util.QuickBZDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class HandledScreenMixin<T extends AbstractContainerMenu> {

    /** The containerId we last dumped, so we re-dump whenever a new container opens. */
    private int quickbz$lastDumpedContainerId = -1;

    @Inject(method = "init", at = @At("TAIL"))
    private void quickbz$onInit(CallbackInfo ci) {
        // Reset on every init so a freshly opened screen always gets evaluated
        quickbz$lastDumpedContainerId = -1;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void quickbz$onRender(GuiGraphics gfx, int mouseX, int mouseY, float delta,
                                  CallbackInfo ci) {
        if (!QuickBZDebug.isEnabled()) return;

        Minecraft client = Minecraft.getInstance();
        if (BazaarScreenHelper.getBazaarScreen(client) == BazaarScreenHelper.BazaarScreen.NONE) return;

        // Must be an AbstractContainerScreen to access slots
        AbstractContainerScreen<?> screen = BazaarScreenHelper.getContainerScreen(client);
        if (screen == null) return;

        int currentContainerId = screen.getMenu().containerId;

        // Already dumped this exact container instance
        if (currentContainerId == quickbz$lastDumpedContainerId) return;

        // Wait until the last slot is filled
        if (!BazaarScreenHelper.isContainerPopulated(screen)) return;

        quickbz$lastDumpedContainerId = currentContainerId;
        QuickBZDebug.dumpCurrentScreen(client);
    }
}