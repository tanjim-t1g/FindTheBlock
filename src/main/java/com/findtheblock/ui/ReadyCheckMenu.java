package com.findtheblock.ui;

import com.findtheblock.FindTheBlock;
import com.findtheblock.config.DifficultyTier;
import com.findtheblock.game.GameManager;
import com.findtheblock.util.Chat;
import com.findtheblock.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReadyCheckMenu {

    private final FindTheBlock plugin;
    private final GameManager gameManager;
    private final Player leader;
    private final DifficultyTier tier;
    private final List<Player> targetPlayers;

    private final Set<UUID> accepted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> declined = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ReadyCheckGui> openMenus = new ConcurrentHashMap<>();

    private int secondsRemaining = 20;
    private BukkitTask timerTask;
    private boolean finished = false;

    public ReadyCheckMenu(FindTheBlock plugin, GameManager gameManager, Player leader,
                          DifficultyTier tier, List<Player> targetPlayers) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.leader = leader;
        this.tier = tier;
        this.targetPlayers = new ArrayList<>(targetPlayers);
    }

    public void start() {
        if (targetPlayers.isEmpty()) return;

        for (Player p : targetPlayers) {
            if (p != null && p.isOnline()) {
                ReadyCheckGui gui = new ReadyCheckGui(this, p, secondsRemaining);
                openMenus.put(p.getUniqueId(), gui);
                gui.open();
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            }
        }

        this.timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            secondsRemaining--;

            if (secondsRemaining <= 0 || (accepted.size() + declined.size() >= targetPlayers.size())) {
                finishReadyCheck();
                return;
            }

            for (ReadyCheckGui gui : openMenus.values()) {
                gui.updateTime(secondsRemaining);
            }
        }, 20L, 20L);
    }

    public void onPlayerDecision(Player player, boolean isAccept) {
        if (isAccept) {
            accepted.add(player.getUniqueId());
            declined.remove(player.getUniqueId());
            Chat.send(player, "<gray>Ready. Waiting for other players...</gray>");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            declined.add(player.getUniqueId());
            accepted.remove(player.getUniqueId());
            Chat.send(player, "<gray>You declined the match.</gray>");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
        player.closeInventory();
    }

    public void onPlayerClose(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    private synchronized void finishReadyCheck() {
        if (finished) return;
        this.finished = true;

        if (timerTask != null) timerTask.cancel();

        for (ReadyCheckGui gui : openMenus.values()) {
            Player p = gui.getPlayer();
            if (p != null && p.isOnline()) {
                p.closeInventory();
            }
        }
        openMenus.clear();

        List<Player> confirmedRacers = new ArrayList<>();
        for (Player p : targetPlayers) {
            if (p != null && p.isOnline()) {
                if (accepted.contains(p.getUniqueId())) {
                    confirmedRacers.add(p);
                } else {
                    Chat.send(p, "<gray>Match invitation timed out.</gray>");
                }
            }
        }

        if (confirmedRacers.size() < 2) {
            Chat.broadcast("<gray>[Race] Cancelled: At least 2 players are needed.</gray>");
            return;
        }

        Chat.broadcast("<gray>[Race] <green>" + confirmedRacers.size() + "</green> players ready. Starting...</gray>");
        gameManager.startMatchWithPlayers(leader, tier, confirmedRacers);
    }

    public static class ReadyCheckGui extends Menu {
        private final ReadyCheckMenu coordinator;
        private int seconds;

        public ReadyCheckGui(ReadyCheckMenu coordinator, Player player, int seconds) {
            super(coordinator.plugin, player, 27, "<dark_gray>Ready Check (" + seconds + "s)</dark_gray>");
            this.coordinator = coordinator;
            this.seconds = seconds;
        }

        @Override
        public void build() {
            inventory.setItem(11, ItemBuilder.of(Material.LIME_CONCRETE)
                    .name("<green>Accept & Join Match</green>")
                    .lore("<gray>Confirm to join race.</gray>")
                    .build());

            updateTime(seconds);

            inventory.setItem(15, ItemBuilder.of(Material.RED_CONCRETE)
                    .name("<red>Decline Match</red>")
                    .lore("<gray>Stay in lobby as spectator.</gray>")
                    .build());
        }

        public void updateTime(int newSeconds) {
            this.seconds = newSeconds;
            inventory.setItem(13, ItemBuilder.of(Material.CLOCK)
                    .name("<yellow>Time Left: " + seconds + "s</yellow>")
                    .lore("<gray>Unconfirmed players will</gray>", "<gray>automatically decline.</gray>")
                    .build());

            if (seconds <= 5 && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.5f);
            }
        }

        @Override
        public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
            if (slot == 11) {
                coordinator.onPlayerDecision(player, true);
            } else if (slot == 15) {
                coordinator.onPlayerDecision(player, false);
            }
        }

        @Override
        public void handleClose(InventoryCloseEvent event) {
            coordinator.onPlayerClose(player);
        }
    }
}
