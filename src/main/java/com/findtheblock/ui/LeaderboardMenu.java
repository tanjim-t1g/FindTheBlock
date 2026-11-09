package com.findtheblock.ui;

import com.findtheblock.stats.PlayerStats;
import com.findtheblock.stats.StatsManager;
import com.findtheblock.util.Chat;
import com.findtheblock.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.List;

public class LeaderboardMenu extends Menu {

    private final StatsManager statsManager;

    public LeaderboardMenu(StatsManager statsManager, Player viewer) {
        super(null, viewer, 54, "<dark_gray>Leaderboard - Top Racers</dark_gray>");
        this.statsManager = statsManager;
    }

    @Override
    public void build() {
        fill(Chat.FILLER_PANE);

        List<PlayerStats> top = statsManager.getTopPlayers(10);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22};

        for (int i = 0; i < top.size() && i < slots.length; i++) {
            PlayerStats p = top.get(i);
            inventory.setItem(slots[i], ItemBuilder.of(Material.PLAYER_HEAD)
                    .skullOwner(Bukkit.getOfflinePlayer(p.getUuid()))
                    .name("<yellow>#" + (i + 1) + " " + p.getName() + "</yellow>")
                    .lore("<gray>Wins: <white>" + p.getWins() + "</white></gray>",
                          "<gray>Podiums: <white>" + (p.getWins() + p.getSecondPlaces() + p.getThirdPlaces()) + "</white></gray>",
                          "<gray>Played: <white>" + p.getGamesPlayed() + "</white></gray>",
                          "<gray>Win Rate: <white>" + String.format("%.1f%%", p.getWinRate()) + "</white></gray>",
                          "<gray>Best Time: <white>" + p.getFormattedBestTime() + "</white></gray>")
                    .build());
        }

        PlayerStats own = statsManager.getStats(player.getUniqueId(), player.getName());
        inventory.setItem(49, ItemBuilder.of(Material.PLAYER_HEAD)
                .skullOwner(player)
                .name("<green>Your Statistics</green>")
                .lore("<gray>Wins: <white>" + own.getWins() + "</white></gray>",
                      "<gray>Played: <white>" + own.getGamesPlayed() + "</white></gray>",
                      "<gray>Win Rate: <white>" + String.format("%.1f%%", own.getWinRate()) + "</white></gray>",
                      "<gray>Best Time: <white>" + own.getFormattedBestTime() + "</white></gray>")
                .build());
    }
}
