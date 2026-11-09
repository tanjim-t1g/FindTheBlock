package com.findtheblock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.regex.Pattern;

public final class Chat {

    // Serializers must be initialized first before static items that call toComponent()
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile(".*&[0-9a-fk-orA-FK-OR].*");

    public static final String PREFIX = "<gold>[FTB]</gold> ";
    public static final String RACE_PREFIX = "<gold>[Race]</gold> ";
    public static final String DIVIDER = "<dark_gray>\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500</dark_gray>";
    public static final ItemStack FILLER_PANE = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
    public static final ItemStack FILLER_PANE_GRAY = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

    private Chat() {}

    public static Component toComponent(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        // Check for legacy formatting codes (&a, &c, etc.), otherwise parse via MiniMessage
        if (message.indexOf('&') != -1 && LEGACY_COLOR_PATTERN.matcher(message).matches()) {
            return LEGACY_SERIALIZER.deserialize(message);
        }
        return MINI_MESSAGE.deserialize(message);
    }

    public static String formatMaterial(Material material) {
        if (material == null) return "Unknown";
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1))
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static void send(CommandSender sender, String message) {
        if (sender != null && message != null) {
            sender.sendMessage(toComponent(message));
        }
    }

    public static void broadcast(String message) {
        if (message != null) {
            Bukkit.broadcast(toComponent(message));
        }
    }

    public static void sendActionBar(Player player, String message) {
        if (player != null && message != null) {
            player.sendActionBar(toComponent(message));
        }
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player == null) return;

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L)
        );

        Title titleObj = Title.title(toComponent(title), toComponent(subtitle), times);
        player.showTitle(titleObj);
    }

    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player != null && sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
