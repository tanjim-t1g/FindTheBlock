package com.findtheblock.listener;

import com.findtheblock.FindTheBlock;
import com.findtheblock.config.ConfigManager;
import com.findtheblock.game.GameManager;
import com.findtheblock.lobby.LobbyManager;
import com.findtheblock.party.PartyManager;
import com.findtheblock.ui.MatchSelectorMenu;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public class ProtectionListener implements Listener {

    private final FindTheBlock plugin;
    private final GameManager gameManager;
    private final LobbyManager lobbyManager;
    private final ConfigManager config;
    private final PartyManager partyManager;

    public ProtectionListener(FindTheBlock plugin, GameManager gameManager, LobbyManager lobbyManager,
                              ConfigManager config, PartyManager partyManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.lobbyManager = lobbyManager;
        this.config = config;
        this.partyManager = partyManager;
    }

    private boolean isLobby(World world) {
        if (world == null) return false;
        World lobbyWorld = lobbyManager.getLobbyWorld();
        if (lobbyWorld != null) {
            return world.equals(lobbyWorld);
        }
        return world.getName().equalsIgnoreCase(config.getLobbyWorldName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLobby(event.getBlock().getWorld()) && config.isProtectBlocks()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isLobby(event.getBlock().getWorld()) && config.isProtectBlocks()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (isLobby(player.getWorld())) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && config.isDisableFallDamage()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        if (isLobby(victim.getWorld()) && config.isDisablePvp()) {
            
            if (event.getDamager() instanceof Player || (event.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isLobby(player.getWorld()) && config.isDisableHunger()) {
                event.setCancelled(true);
                player.setFoodLevel(20);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isLobby(player.getWorld()) && config.isDisableItemDrops()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (isLobby(event.getEntity().getWorld())) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isLobby(player.getWorld())) {
            if (config.isVoidTeleportEnabled()) {
                double damageY = player.getWorld().getMinHeight() - 64.0;
                double thresholdY = damageY + config.getVoidTeleportPadding();
                if (player.getLocation().getY() < thresholdY) {
                    player.setFallDistance(0.0f);
                    player.teleport(player.getWorld().getSpawnLocation());
                }
            }
        }
    }

    @EventHandler
    public void onLobbyInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isLobby(player.getWorld())) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.COMPASS) {
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    new MatchSelectorMenu(plugin, gameManager, partyManager, player).open();
                }
            }
        }
    }

    @EventHandler
    public void onLobbyInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isLobby(player.getWorld())) {
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
                event.setCancelled(true);
            }
        }
    }
}
