package com.findtheblock.listener;

import com.findtheblock.factory.MatchWorldPair;
import com.findtheblock.game.GameManager;
import com.findtheblock.game.TargetEngine;
import com.findtheblock.ui.Menu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final GameManager gameManager;
    private final TargetEngine targetEngine;

    public GameListener(GameManager gameManager, TargetEngine targetEngine) {
        this.gameManager = gameManager;
        this.targetEngine = targetEngine;
    }

    private void checkItem(Player player, ItemStack item) {
        if (player == null || item == null || !gameManager.isGameRunning()) return;
        if (!gameManager.getContestants().contains(player.getUniqueId())) return;

        Material target = gameManager.getCurrentTarget();
        if (target != null && targetEngine.isTarget(item, target)) {
            gameManager.onTargetAcquired(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            checkItem(player, event.getItem().getItemStack());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            checkItem(player, event.getRecipe().getResult());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            // Ignore custom GUI clicks
            if (event.getView().getTopInventory().getHolder() instanceof Menu) {
                return;
            }

            InventoryType type = event.getInventory().getType();
            if (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER) {
                // Check if player took the smelted item from the result slot
                if (event.getRawSlot() == 2 && event.getCurrentItem() != null) {
                    checkItem(player, event.getCurrentItem());
                }
            } else if (event.getCurrentItem() != null) {
                checkItem(player, event.getCurrentItem());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!gameManager.isGameRunning()) return;

        MatchWorldPair match = gameManager.getActiveMatch();
        if (match == null) return;

        World fromWorld = event.getFrom().getWorld();
        if (fromWorld == null) return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            Location from = event.getFrom();
            if (fromWorld.equals(match.getOverworld())) {
                // Send player to match Nether and allow vanilla portal creation/linking
                event.setTo(new Location(match.getNether(), from.getX() / 8.0, from.getY(), from.getZ() / 8.0));
                event.setCanCreatePortal(true);
            } else if (fromWorld.equals(match.getNether())) {
                // Send player back to match Overworld coordinates
                event.setTo(new Location(match.getOverworld(), from.getX() * 8.0, from.getY(), from.getZ() * 8.0));
                event.setCanCreatePortal(true);
            }
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (fromWorld.equals(match.getOverworld())) {
                World endWorld = match.getOrCreateEnd();
                if (endWorld != null) {
                    createEndPlatform(endWorld);
                    event.setTo(new Location(endWorld, 100.5, 49.0, 0.5, 90.0f, 0.0f));
                }
            } else if (match.getEnd() != null && fromWorld.equals(match.getEnd())) {
                org.bukkit.boss.DragonBattle battle = match.getEnd().getEnderDragonBattle();
                if (battle != null && battle.getBossBar() != null) {
                    battle.getBossBar().removePlayer(event.getPlayer());
                }
                event.setTo(match.getSpawnLocation());
            }
        }
    }

    // Generate obsidian landing platform for the End dimension
    private void createEndPlatform(World endWorld) {
        for (int x = 98; x <= 102; x++) {
            for (int z = -2; z <= 2; z++) {
                endWorld.getBlockAt(x, 48, z).setType(Material.OBSIDIAN);
                for (int y = 49; y <= 51; y++) {
                    endWorld.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }
}
