package com.findtheblock.util;

import com.findtheblock.FindTheBlock;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class UpdateChecker {

    private static final String DOWNLOAD_URL = "https://modrinth.com/findtheblock";
    private static final String GITHUB_URL = "https://github.com/tanjim-t1g/FindTheBlock/releases/latest";
    private static final String API_URL = "https://api.github.com/repos/tanjim-t1g/FindTheBlock/releases/latest";

    private final FindTheBlock plugin;
    private boolean updateAvailable = false;
    private String latestVersion = "";
    private String minMinecraft = "1.21.0";

    public UpdateChecker(FindTheBlock plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        if (!plugin.getConfigManager().isUpdateCheckerEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = URI.create(API_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "FindTheBlock-UpdateChecker");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) {
                    return;
                }

                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                    latestVersion = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
                    if (latestVersion.startsWith("v") || latestVersion.startsWith("V")) {
                        latestVersion = latestVersion.substring(1);
                    }

                    String body = json.has("body") ? json.get("body").getAsString() : "";
                    minMinecraft = parseMinMinecraft(body);

                    if (!isServerCompatible(minMinecraft)) {
                        return;
                    }

                    String currentVersion = plugin.getPluginMeta().getVersion();
                    if (compareVersions(latestVersion, currentVersion) > 0) {
                        updateAvailable = true;
                        printConsoleBanner(currentVersion, latestVersion);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
            }
        });
    }

    private boolean isServerCompatible(String minMC) {
        try {
            return compareVersions(Bukkit.getMinecraftVersion(), minMC) >= 0;
        } catch (Exception e) {
            return true;
        }
    }

    private String parseMinMinecraft(String body) {
        if (body == null) return "1.21.0";
        for (String line : body.split("\n")) {
            if (line.toLowerCase().contains("min-minecraft:")) {
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    return parts[1].trim();
                }
            }
        }
        return "1.21.0";
    }

    private int compareVersions(String v1, String v2) {
        try {
            String[] p1 = v1.split("-")[0].split("\\.");
            String[] p2 = v2.split("-")[0].split("\\.");
            int len = Math.max(p1.length, p2.length);
            for (int i = 0; i < len; i++) {
                int n1 = i < p1.length ? Integer.parseInt(p1[i].replaceAll("[^0-9]", "")) : 0;
                int n2 = i < p2.length ? Integer.parseInt(p2[i].replaceAll("[^0-9]", "")) : 0;
                if (n1 != n2) {
                    return Integer.compare(n1, n2);
                }
            }
        } catch (Exception ignored) {}
        return v1.compareTo(v2);
    }

    private void printConsoleBanner(String current, String latest) {
        plugin.getLogger().info("==================================================");
        plugin.getLogger().info("  FindTheBlock v" + latest + " is available!");
        plugin.getLogger().info("  You are currently running v" + current);
        plugin.getLogger().info("");
        plugin.getLogger().info("  Modrinth: " + DOWNLOAD_URL);
        plugin.getLogger().info("  GitHub:   " + GITHUB_URL);
        plugin.getLogger().info("==================================================");
    }

    public void sendOpNotification(Player player) {
        if (!updateAvailable) return;

        player.sendMessage(Component.text("------------------------------", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("FindTheBlock ", NamedTextColor.GOLD)
                .append(Component.text("v" + latestVersion, NamedTextColor.GREEN))
                .append(Component.text(" is available!", NamedTextColor.YELLOW)));

        Component links = Component.text("[Modrinth]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.openUrl(DOWNLOAD_URL))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("[GitHub]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(GITHUB_URL)));

        player.sendMessage(links);
        player.sendMessage(Component.text("------------------------------", NamedTextColor.DARK_GRAY));
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }
}
