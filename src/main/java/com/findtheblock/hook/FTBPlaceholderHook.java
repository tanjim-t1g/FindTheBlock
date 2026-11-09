package com.findtheblock.hook;

import com.findtheblock.FindTheBlock;
import com.findtheblock.game.GameManager;
import com.findtheblock.stats.PlayerStats;
import com.findtheblock.stats.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FTBPlaceholderHook extends PlaceholderExpansion {

    private final GameManager gameManager;
    private final StatsManager statsManager;

    public FTBPlaceholderHook(FindTheBlock plugin, GameManager gameManager, StatsManager statsManager) {
        this.gameManager = gameManager;
        this.statsManager = statsManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "findtheblock";
    }

    @Override
    public @NotNull String getAuthor() {
        return "tanjim-t1g";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String lower = params.toLowerCase();

        return switch (lower) {
            case "state" -> gameManager.getState().name();
            case "state_display" -> gameManager.getState().getDisplay();
            case "target" -> gameManager.getCurrentTarget() != null ? com.findtheblock.util.Chat.formatMaterial(gameManager.getCurrentTarget()) : "None";
            case "target_raw" -> gameManager.getCurrentTarget() != null ? gameManager.getCurrentTarget().name() : "None";
            case "time" -> {
                int elapsed = gameManager.getElapsedSeconds();
                yield String.format("%02d:%02d", elapsed / 60, elapsed % 60);
            }
            case "contestants" -> String.valueOf(gameManager.getContestants().size());
            default -> handleDynamicRequest(player, lower);
        };
    }

    private @Nullable String handleDynamicRequest(OfflinePlayer player, String lower) {
        if (lower.startsWith("top_")) {
            String[] parts = lower.split("_");
            if (parts.length >= 3) {
                try {
                    int rank = Integer.parseInt(parts[1]);
                    String type = parts[2];
                    List<PlayerStats> top = statsManager.getTopPlayers(rank);
                    if (top.size() >= rank) {
                        PlayerStats entry = top.get(rank - 1);
                        return switch (type) {
                            case "name" -> entry.getName();
                            case "wins" -> String.valueOf(entry.getWins());
                            case "time" -> entry.getFormattedBestTime();
                            default -> "-";
                        };
                    }
                } catch (NumberFormatException ignored) {}
            }
            return "-";
        }

        if (player != null) {
            PlayerStats stats = statsManager.getStats(player.getUniqueId());
            if (stats == null) return "0";

            return switch (lower) {
                case "wins" -> String.valueOf(stats.getWins());
                case "played" -> String.valueOf(stats.getGamesPlayed());
                case "win_rate" -> String.format("%.1f%%", stats.getWinRate());
                case "best_time" -> stats.getFormattedBestTime();
                case "podiums" -> String.valueOf(stats.getWins() + stats.getSecondPlaces() + stats.getThirdPlaces());
                default -> null;
            };
        }

        return null;
    }
}
