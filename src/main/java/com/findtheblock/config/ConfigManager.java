package com.findtheblock.config;

import com.findtheblock.FindTheBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final FindTheBlock plugin;
    private final Map<DifficultyTier, List<Material>> targetPools = new EnumMap<>(DifficultyTier.class);

    private String lobbyWorldName;
    private boolean autoGeneratePlatform;
    private int platformSize;
    private int platformY;
    private Material platformMaterial;
    private boolean voidTeleportEnabled = true;
    private double voidTeleportPadding = 2.0;

    private boolean protectBlocks;
    private boolean disableHunger;
    private boolean disablePvp;
    private boolean disableFallDamage;
    private boolean disableMobSpawning;
    private boolean disableItemDrops;
    private boolean disableWeather;
    private boolean daylightFixed;

    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;

    private int bufferSize;
    private int chunkRadius;
    private int chunksPerSecond;
    private double matchBorderSize;

    private int maxWinners;
    private int celebrationSeconds;
    private boolean twentyMinBuffs;
    private int timeoutHours;
    private boolean giveIronTools;
    private boolean giveSilkPickaxe;

    private boolean allowLateEntry;
    private boolean allowSpectate;
    private int reconnectGraceSeconds;

    private int rollSeconds;
    private int previewSeconds;
    private int leaderInactivityMinutes;
    private DifficultyTier defaultTier;
    private boolean updateCheckerEnabled;
    private boolean updateNotifyOps;

    public ConfigManager(FindTheBlock plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.lobbyWorldName = config.getString("lobby.world-name", "lobby");
        this.autoGeneratePlatform = config.getBoolean("lobby.platform.auto-generate", true);
        this.platformSize = config.getInt("lobby.platform.size", 100);
        this.platformY = config.getInt("lobby.platform.y-level", 100);
        String matStr = config.getString("lobby.platform.material", "BEDROCK");
        this.voidTeleportEnabled = config.getBoolean("lobby.void-teleport", true);
        this.voidTeleportPadding = config.getDouble("lobby.void-teleport-blocks-above-damage", 2.0);
        Material pMat = Material.matchMaterial(matStr);
        this.platformMaterial = (pMat != null && pMat.isBlock()) ? pMat : Material.BEDROCK;

        this.protectBlocks = config.getBoolean("lobby.protect-blocks", true);
        this.disableHunger = config.getBoolean("lobby.disable-hunger", true);
        this.disablePvp = config.getBoolean("lobby.disable-pvp", true);
        this.disableFallDamage = config.getBoolean("lobby.disable-fall-damage", true);
        this.disableMobSpawning = config.getBoolean("lobby.disable-mob-spawning", true);
        this.disableItemDrops = config.getBoolean("lobby.disable-item-drops", true);
        this.disableWeather = config.getBoolean("lobby.disable-weather", true);
        this.daylightFixed = config.getBoolean("lobby.daylight-fixed", true);

        this.spawnX = config.getDouble("lobby.spawn.x", 0.5);
        this.spawnY = config.getDouble("lobby.spawn.y", 101.0);
        this.spawnZ = config.getDouble("lobby.spawn.z", 0.5);
        this.spawnYaw = (float) config.getDouble("lobby.spawn.yaw", 0.0);
        this.spawnPitch = (float) config.getDouble("lobby.spawn.pitch", 0.0);

        this.bufferSize = Math.max(1, config.getInt("world-factory.buffer-size", 1));
        this.chunkRadius = Math.max(2, config.getInt("world-factory.chunk-radius", 6));
        this.chunksPerSecond = Math.max(5, config.getInt("world-factory.chunks-per-second", 30));
        this.matchBorderSize = config.getDouble("world-factory.match-border-size", 59999968.0);

        this.maxWinners = Math.max(1, config.getInt("match.max-winners", 3));
        this.celebrationSeconds = Math.max(3, config.getInt("match.celebration-seconds", 10));
        this.twentyMinBuffs = config.getBoolean("match.twenty-minute-buffs", true);
        this.timeoutHours = config.getInt("match.timeout-hours", 3);
        this.giveIronTools = config.getBoolean("match.starter-kit.iron-tools", true);
        this.giveSilkPickaxe = config.getBoolean("match.starter-kit.silk-touch-pickaxe", true);

        this.allowLateEntry = config.getBoolean("match.mid-game.allow-late-entry", true);
        this.allowSpectate = config.getBoolean("match.mid-game.allow-spectate", true);
        this.reconnectGraceSeconds = config.getInt("match.mid-game.reconnect-grace-seconds", 60);

        this.rollSeconds = Math.max(1, config.getInt("match.roulette.roll-seconds", 3));
        this.previewSeconds = Math.max(1, config.getInt("match.roulette.preview-seconds", 4));
        this.leaderInactivityMinutes = Math.max(1, config.getInt("match.party.leader-inactivity-minutes", 10));
        this.defaultTier = DifficultyTier.fromString(config.getString("targets.default-tier", "NORMAL"));
        this.updateCheckerEnabled = config.getBoolean("update-checker.enabled", true);
        this.updateNotifyOps = config.getBoolean("update-checker.notify-ops", true);

        targetPools.clear();
        for (DifficultyTier tier : DifficultyTier.values()) {
            List<String> rawList = config.getStringList("targets." + tier.name());
            List<Material> materials = new ArrayList<>();
            for (String itemStr : rawList) {
                Material mat = Material.matchMaterial(itemStr.trim());
                if (mat != null && mat.isItem()) {
                    materials.add(mat);
                } else {
                    plugin.getLogger().warning("Invalid target material in " + tier.name() + ": " + itemStr);
                }
            }
            if (materials.isEmpty()) {
                materials.add(Material.CAMPFIRE);
                materials.add(Material.BOOKSHELF);
            }
            targetPools.put(tier, materials);
        }
    }

    public List<Material> getTargets(DifficultyTier tier) {
        return targetPools.getOrDefault(tier, targetPools.get(DifficultyTier.NORMAL));
    }

    public Location getCustomSpawnLocation(World world) {
        if (world == null) return null;
        return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    public String getLobbyWorldName() { return lobbyWorldName; }
    public boolean isAutoGeneratePlatform() { return autoGeneratePlatform; }
    public int getPlatformSize() { return platformSize; }
    public int getPlatformY() { return platformY; }
    public Material getPlatformMaterial() { return platformMaterial; }
    public boolean isVoidTeleportEnabled() { return voidTeleportEnabled; }
    public double getVoidTeleportPadding() { return voidTeleportPadding; }

    public boolean isProtectBlocks() { return protectBlocks; }
    public boolean isDisableHunger() { return disableHunger; }
    public boolean isDisablePvp() { return disablePvp; }
    public boolean isDisableFallDamage() { return disableFallDamage; }
    public boolean isDisableMobSpawning() { return disableMobSpawning; }
    public boolean isDisableItemDrops() { return disableItemDrops; }
    public boolean isDisableWeather() { return disableWeather; }
    public boolean isDaylightFixed() { return daylightFixed; }

    public int getBufferSize() { return bufferSize; }
    public int getChunkRadius() { return chunkRadius; }
    public int getChunksPerSecond() { return chunksPerSecond; }
    public double getMatchBorderSize() { return matchBorderSize; }

    public int getMaxWinners() { return maxWinners; }
    public int getCelebrationSeconds() { return celebrationSeconds; }
    public boolean isTwentyMinBuffs() { return twentyMinBuffs; }
    public int getTimeoutHours() { return timeoutHours; }
    public boolean isGiveIronTools() { return giveIronTools; }
    public boolean isGiveSilkPickaxe() { return giveSilkPickaxe; }

    public boolean isAllowLateEntry() { return allowLateEntry; }
    public boolean isAllowSpectate() { return allowSpectate; }
    public int getReconnectGraceSeconds() { return reconnectGraceSeconds; }

    public int getRollSeconds() { return rollSeconds; }
    public int getPreviewSeconds() { return previewSeconds; }
    public int getLeaderInactivityMinutes() { return leaderInactivityMinutes; }
    public DifficultyTier getDefaultTier() { return defaultTier; }
    public boolean isUpdateCheckerEnabled() { return updateCheckerEnabled; }
    public boolean isUpdateNotifyOps() { return updateNotifyOps; }
}
