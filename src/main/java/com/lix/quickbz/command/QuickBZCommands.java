package com.lix.quickbz.command;

import com.lix.quickbz.config.HotkeyManagerScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.lix.quickbz.config.QuickBZConfig;
import com.lix.quickbz.util.ItemSuggester;
import com.lix.quickbz.util.QuickBZDebug;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class QuickBZCommands {

    /** When non-null, open this screen on the next tick (after chat closes). */
    public static net.minecraft.client.gui.screens.Screen pendingScreen = null;

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = buildRoot();
            dispatcher.register(root);
            dispatcher.register(literal("qbz").redirect(dispatcher.getRoot().getChild("quickbz")));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> buildRoot() {

        SuggestionProvider<FabricClientCommandSource> itemSuggestions = (ctx, builder) -> {
            String remaining = builder.getRemaining().toUpperCase(Locale.ROOT);
            for (var r : ItemSuggester.suggest(remaining, 20)) {
                builder.suggest(r.id(), Component.literal(r.displayName()));
            }
            return builder.buildFuture();
        };

        return literal("quickbz")


                .then(literal("config").executes(ctx -> {
                    pendingScreen = new HotkeyManagerScreen(null);
                    return 1;
                }))

                // /qbz add <itemId> <buy|sell> [key] [amount]
                .then(literal("add")
                        .then(argument("itemId", StringArgumentType.word())
                                .suggests(itemSuggestions)
                                .then(argument("action", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                List.of("buy", "sell"), b))
                                        .executes(ctx -> addEntry(ctx,
                                                StringArgumentType.getString(ctx, "itemId"),
                                                StringArgumentType.getString(ctx, "action"),
                                                "none", 0))
                                        .then(argument("key", StringArgumentType.word())
                                                .then(argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> addEntry(ctx,
                                                                StringArgumentType.getString(ctx, "itemId"),
                                                                StringArgumentType.getString(ctx, "action"),
                                                                StringArgumentType.getString(ctx, "key"),
                                                                IntegerArgumentType.getInteger(ctx, "amount"))))
                                                .executes(ctx -> addEntry(ctx,
                                                        StringArgumentType.getString(ctx, "itemId"),
                                                        StringArgumentType.getString(ctx, "action"),
                                                        StringArgumentType.getString(ctx, "key"),
                                                        0))))))

                // /qbz remove <index>
                .then(literal("remove")
                        .then(argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int idx = IntegerArgumentType.getInteger(ctx, "index") - 1;
                                    var entries = QuickBZConfig.get().hotkeyEntries;
                                    if (idx < 0 || idx >= entries.size()) {
                                        ctx.getSource().sendFeedback(Component.literal("§cIndex out of range."));
                                        return 0;
                                    }
                                    var removed = entries.remove(idx);
                                    QuickBZConfig.save();
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "§6[QuickBZ] §7Removed §e" + removed.label()));
                                    return 1;
                                })))

                // /qbz list
                .then(literal("list").executes(ctx -> {
                    var entries = QuickBZConfig.get().hotkeyEntries;
                    if (entries.isEmpty()) {
                        ctx.getSource().sendFeedback(Component.literal("§6[QuickBZ] §7No hotkeys configured."));
                        return 1;
                    }
                    ctx.getSource().sendFeedback(Component.literal("§6[QuickBZ] §7Hotkeys:"));
                    for (int i = 0; i < entries.size(); i++) {
                        var e = entries.get(i);
                        String key = e.keyCode > 0
                                ? InputConstants.Type.KEYSYM.getOrCreate(e.keyCode).getDisplayName().getString()
                                : "unbound";
                        ctx.getSource().sendFeedback(Component.literal(
                                "§7 " + (i + 1) + ". §e" + e.label() +
                                        " §7key=§f" + key +
                                        (e.amount > 0 ? " §7amt=§f" + e.amount : "") +
                                        " §7→ §f/bz " + e.bzArgument()));
                    }
                    return 1;
                }))

                // /qbz debug <on|off|dump>
                .then(literal("debug")
                        .then(literal("on").executes(ctx -> {
                            QuickBZDebug.setEnabled(true);
                            ctx.getSource().sendFeedback(Component.literal(
                                    "§6[QuickBZ] §aDebug ON — open any BZ screen to auto-dump slots."));
                            return 1;
                        }))
                        .then(literal("off").executes(ctx -> {
                            QuickBZDebug.setEnabled(false);
                            ctx.getSource().sendFeedback(Component.literal("§6[QuickBZ] §7Debug OFF."));
                            return 1;
                        }))
                        .then(literal("dump").executes(ctx -> {
                            QuickBZDebug.dumpCurrentScreen(Minecraft.getInstance());
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "§6[QuickBZ] §7Debug: " + (QuickBZDebug.isEnabled() ? "§aON" : "§cOFF")));
                            return 1;
                        }))

                // /qbz (no args) — help
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal(
                            "§6[QuickBZ] §fCommands:\n" +
                                    "§e/qbz config §7— open config\n" +
                                    "§e/qbz add <id> <buy|sell> [key] [amount]\n" +
                                    "§e/qbz remove <#>\n" +
                                    "§e/qbz list\n" +
                                    "§e/qbz debug <on|off|dump>"));
                    return 1;
                });
    }

    private static int addEntry(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
            String rawItemId, String action, String keyName, int amount) {

        String itemId = rawItemId.toUpperCase(Locale.ROOT);
        QuickBZConfig.ActionType actionType = action.equalsIgnoreCase("buy")
                ? QuickBZConfig.ActionType.INSTABUY
                : QuickBZConfig.ActionType.INSTASELL;

        int keyCode = -1;
        if (!keyName.equalsIgnoreCase("none")) {
            try {
                keyCode = InputConstants.getKey("key.keyboard." + keyName.toLowerCase(Locale.ROOT)).getValue();
            } catch (Exception e) {
                ctx.getSource().sendFeedback(Component.literal(
                        "§eWarning: Unknown key '§f" + keyName + "§e'. Entry added unbound."));
            }
        }

        String displayName = ItemSuggester.getDisplayName(itemId);
        var entry = new QuickBZConfig.HotkeyEntry(
                itemId, displayName != null ? displayName : "", keyCode, actionType, amount);

        QuickBZConfig.get().hotkeyEntries.add(entry);
        QuickBZConfig.save();

        String nameStr = displayName != null
                ? "§a" + displayName + " §7(§e" + itemId + "§7)"
                : "§e" + itemId;
        ctx.getSource().sendFeedback(Component.literal(
                "§6[QuickBZ] §7Added: " + nameStr + " → §f" + actionType.name() +
                        (keyCode > 0 ? " §7key=§f" + keyName : " §7(unbound)") +
                        (amount > 0  ? " §7amt=§f" + amount : "") +
                        "\n§7Opens BZ as: §f/bz " + entry.bzArgument()));
        return 1;
    }
}