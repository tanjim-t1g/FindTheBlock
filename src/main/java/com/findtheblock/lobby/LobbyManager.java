package com.findtheblock.lobby;

import com.findtheblock.FindTheBlock;
import com.findtheblock.config.ConfigManager;
import com.findtheblock.util.ItemBuilder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LobbyManager {

    private final FindTheBlock plugin;
    private final ConfigManager config;
    private World lobbyWorld;
    private Location spawnLocation;

    public LobbyManager(FindTheBlock plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        initializeLobbyWorld();
    }

    public void initializeLobbyWorld() {
        String worldName = config.getLobbyWorldName();
        this.lobbyWorld = Bukkit.getWorld(worldName);

        if (this.lobbyWorld == null) {
            plugin.getLogger().info("Generating dedicated void lobby world '" + worldName + "'...");
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.NORMAL);
            creator.generator(new VoidChunkGenerator());
            this.lobbyWorld = creator.createWorld();
        }

        if (this.lobbyWorld == null) {
            plugin.getLogger().severe("Failed to initialize lobby world! Falling back to default world.");
            this.lobbyWorld = Bukkit.getWorlds().get(0);
        }

        if (config.isDaylightFixed()) {
            lobbyWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            lobbyWorld.setTime(6000L);
        }
        if (config.isDisableWeather()) {
            lobbyWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            lobbyWorld.setStorm(false);
            lobbyWorld.setThundering(false);
        }
        if (config.isDisableMobSpawning()) {
            lobbyWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        }

        lobbyWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        lobbyWorld.setGameRule(GameRule.KEEP_INVENTORY, true);

        if (config.isAutoGeneratePlatform()) {
            buildBedrockPlatform();
            this.spawnLocation = new Location(lobbyWorld, 0.5, config.getPlatformY() + 1.0, 0.5, 0.0f, 0.0f);
            lobbyWorld.setSpawnLocation(spawnLocation);
        } else {
            Location customSpawn = config.getCustomSpawnLocation(lobbyWorld);
            this.spawnLocation = customSpawn != null ? customSpawn : lobbyWorld.getSpawnLocation();
            lobbyWorld.setSpawnLocation(spawnLocation);
        }
    }

    public void buildBedrockPlatform() {
        int halfSize = config.getPlatformSize() / 2;
        int y = config.getPlatformY();
        Material mat = config.getPlatformMaterial();

        Block center = lobbyWorld.getBlockAt(0, y, 0);
        if (center.getType() == mat) {
            return;
        }

        plugin.getLogger().info("Building " + config.getPlatformSize() + "x" + config.getPlatformSize() + " " + mat.name() + " platform at Y=" + y + " in lobby world...");
        for (int x = -halfSize; x < halfSize; x++) {
            for (int z = -halfSize; z < halfSize; z++) {
                Block block = lobbyWorld.getBlockAt(x, y, z);
                if (block.getType() != mat) {
                    block.setType(mat, false);
                }
            }
        }
        plugin.getLogger().info("Lobby bedrock platform complete!");
    }

    public void teleportToLobby(Player player) {
        if (player == null || !player.isOnline()) return;

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFlySpeed(0.1f);
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setSilent(false);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.getInventory().clear();
        giveLobbyItems(player);


        for (java.util.Iterator<org.bukkit.boss.KeyedBossBar> it = Bukkit.getBossBars(); it.hasNext(); ) {
            org.bukkit.boss.KeyedBossBar bar = it.next();
            bar.removePlayer(player);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }

        player.teleport(spawnLocation);
    }

    public void giveLobbyItems(Player player) {
        if (player == null || !player.isOnline()) return;
        ItemStack compass = ItemBuilder.of(Material.COMPASS)
                .name("<yellow>Match Selector</yellow> <gray>(Right-Click)</gray>")
                .lore("<gray>Browse matches and parties.</gray>")
                .build();
        player.getInventory().setItem(4, compass);
    }

    public World getLobbyWorld() {
        return lobbyWorld;
    }

    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation : lobbyWorld.getSpawnLocation();
    }

    private static class VoidChunkGenerator extends org.bukkit.generator.ChunkGenerator {
        @Override
        public boolean canSpawn(@org.jetbrains.annotations.NotNull World world, int x, int z) {
            return true;
        }

        @Override
        public Location getFixedSpawnLocation(@org.jetbrains.annotations.NotNull World world, @org.jetbrains.annotations.NotNull java.util.Random random) {
            return new Location(world, 0.5, 101.0, 0.5);
        }
    }
}
