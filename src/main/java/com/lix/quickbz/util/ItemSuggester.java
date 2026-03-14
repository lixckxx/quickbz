package com.lix.quickbz.util;

import com.google.gson.*;
import com.lix.quickbz.QuickBZMod;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ItemSuggester {

    private static final Map<String, String> ID_TO_NAME = new ConcurrentHashMap<>();
    private static final List<String> SORTED_IDS = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean fetched  = false;
    private static volatile boolean fetching = false;

    private static final String HYPIXEL_ITEMS_URL =
            "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final Gson GSON = new Gson();

    public static void fetchAsync() {
        if (fetched || fetching) return;
        fetching = true;
        Thread t = new Thread(ItemSuggester::doFetch, "QuickBZ-ItemFetch");
        t.setDaemon(true);
        t.start();
    }

    private static void doFetch() {
        try {
            QuickBZMod.LOGGER.info("[QuickBZ] Fetching SkyBlock items from Hypixel API...");
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create(HYPIXEL_ITEMS_URL).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "QuickBZ-Mod/1.0");
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(12_000);

            if (conn.getResponseCode() == 200) {
                try (var reader = new InputStreamReader(conn.getInputStream())) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    JsonArray items = root.getAsJsonArray("items");
                    if (items != null) {
                        for (JsonElement el : items) {
                            JsonObject obj = el.getAsJsonObject();
                            String id   = obj.has("id")   ? obj.get("id").getAsString()   : null;
                            String name = obj.has("name") ? obj.get("name").getAsString() : null;
                            if (id != null && !id.isBlank()) {
                                String upper = id.toUpperCase(Locale.ROOT);
                                ID_TO_NAME.put(upper, name != null ? name : upper);
                                SORTED_IDS.add(upper);
                            }
                        }
                        Collections.sort(SORTED_IDS);
                        fetched = true;
                        QuickBZMod.LOGGER.info("[QuickBZ] Loaded {} items.", SORTED_IDS.size());
                        return;
                    }
                }
            }
        } catch (Exception e) {
            QuickBZMod.LOGGER.warn("[QuickBZ] Hypixel API fetch failed: {}", e.getMessage());
        }

        fetching = false;
    }

    public static String getDisplayName(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        return ID_TO_NAME.get(itemId.toUpperCase(Locale.ROOT));
    }

    public static List<SuggestionResult> suggest(String query, int limit) {
        String q = query.toUpperCase(Locale.ROOT);
        List<SuggestionResult> prefix   = new ArrayList<>();
        List<SuggestionResult> contains = new ArrayList<>();
        for (String id : SORTED_IDS) {
            if (prefix.size() + contains.size() >= limit * 3) break;
            String name = ID_TO_NAME.getOrDefault(id, id).toUpperCase(Locale.ROOT);
            if (id.startsWith(q))
                prefix.add(new SuggestionResult(id, ID_TO_NAME.getOrDefault(id, id)));
            else if (id.contains(q) || name.contains(q))
                contains.add(new SuggestionResult(id, ID_TO_NAME.getOrDefault(id, id)));
        }
        List<SuggestionResult> result = new ArrayList<>(prefix);
        result.addAll(contains);
        return result.subList(0, Math.min(limit, result.size()));
    }

    public static boolean isFetched() { return fetched; }

    // Public record — accessors are r.id() and r.displayName(), NOT r.id / r.displayName
    public record SuggestionResult(String id, String displayName) {}
}
