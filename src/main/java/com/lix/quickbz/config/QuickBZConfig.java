package com.lix.quickbz.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lix.quickbz.QuickBZMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuickBZConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("quickbz.json");

    private static QuickBZConfig INSTANCE = new QuickBZConfig();

    public List<HotkeyEntry> hotkeyEntries = new ArrayList<>();
    public boolean showActionFeedback = true;

    public static QuickBZConfig get() { return INSTANCE; }

    public static void load() {
        File file = CONFIG_PATH.toFile();
        if (file.exists()) {
            try (Reader r = new FileReader(file)) {
                QuickBZConfig loaded = GSON.fromJson(r, QuickBZConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    if (INSTANCE.hotkeyEntries == null) INSTANCE.hotkeyEntries = new ArrayList<>();
                }
            } catch (Exception e) {
                QuickBZMod.LOGGER.error("[QuickBZ] Failed to load config", e);
            }
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            QuickBZMod.LOGGER.error("[QuickBZ] Failed to save config", e);
        }
    }

    public enum ActionType {
        INSTABUY, INSTASELL;

        @Override
        public String toString() {
            return this == INSTABUY ? "Buy" : "Sell";
        }
    }

    public static class HotkeyEntry {
        public String itemId      = "";
        public String displayName = "";
        /** GLFW key code; -1 = unbound */
        public int    keyCode     = -1;
        public ActionType actionType = ActionType.INSTASELL;
        /** 0 = use defaultAmount / BZ default */
        public int    amount      = 0;

        public HotkeyEntry() {}

        public HotkeyEntry(String itemId, String displayName, int keyCode,
                           ActionType actionType, int amount) {
            this.itemId      = itemId;
            this.displayName = displayName;
            this.keyCode     = keyCode;
            this.actionType  = actionType;
            this.amount      = amount;
        }

        /** Argument passed to /bz, uses display name, if it doesnt exist we assume the user input the displayname */
        public String bzArgument() {
            return (displayName != null && !displayName.isBlank()) ? displayName : itemId;
        }

        public String label() {
            String name = (displayName != null && !displayName.isBlank()) ? displayName : itemId;
            return name + " [" + actionType + "]";
        }
    }
}