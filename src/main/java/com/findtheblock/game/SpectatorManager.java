package com.findtheblock.game;

import com.findtheblock.FindTheBlock;
import com.findtheblock.util.Chat;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpectatorManager {

    private final FindTheBlock plugin;
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();

    public SpectatorManager(FindTheBlock plugin) {
        this.plugin = plugin;
    }

    public void makeSpectator(Player player, Location arenaLocation) {
        if (player == null || !player.isOnline()) return;

        spectators.add(player.getUniqueId());

        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();

        // Hide new spectator from active racers
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!spectators.contains(online.getUniqueId())) {
                online.hidePlayer(plugin, player);
            }
        }

        if (arenaLocation != null) {
            player.teleport(arenaLocation);
        }

        Chat.sendTitle(player, "<gray>Spectating</gray>", "<gray>Use <white>/leave</white> to return to lobby</gray>", 10, 40, 10);
    }

    public void restorePlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        spectators.remove(player.getUniqueId());
        player.getInventory().clear();

        // Re-show player to everyone
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }
    }

    public void hideSpectatorsFrom(Player player) {
        if (player == null || !player.isOnline()) return;
        for (UUID uuid : spectators) {
            Player spec = org.bukkit.Bukkit.getPlayer(uuid);
            if (spec != null && spec.isOnline() && !spec.equals(player)) {
                player.hidePlayer(plugin, spec);
            }
        }
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.contains(uuid);
    }

    public Set<UUID> getSpectators() {
        return Collections.unmodifiableSet(spectators);
    }

    public void removeSpectator(UUID uuid) {
        spectators.remove(uuid);
    }

    public void clear() {
        for (UUID uuid : spectators) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                restorePlayer(p);
            }
        }
        spectators.clear();
    }
}
