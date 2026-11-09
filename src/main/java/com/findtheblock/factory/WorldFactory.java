package com.findtheblock.factory;

import com.findtheblock.FindTheBlock;
import com.findtheblock.config.ConfigManager;
import com.findtheblock.util.LocationUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class WorldFactory {

    private final FindTheBlock plugin;
    private final ConfigManager config;
    private final Queue<MatchWorldPair> readyQueue = new ConcurrentLinkedQueue<>();
    private boolean isGenerating = false;
    private ChunkPacer activePacer = null;

    public WorldFactory(FindTheBlock plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public synchronized void refillQueueAsync(Consumer<MatchWorldPair> onComplete) {
        if (!plugin.isEnabled() || readyQueue.size() >= config.getBufferSize() || isGenerating) {
            return;
        }

        this.isGenerating = true;
        String matchId = UUID.randomUUID().toString().substring(0, 8);
        long seed = ThreadLocalRandom.current().nextLong();

        plugin.getLogger().info("Pre-generating standby world pair 'ftb_" + matchId + "'...");

        WorldCreator overworldCreator = new WorldCreator("ftb_" + matchId);
        overworldCreator.environment(World.Environment.NORMAL);
        overworldCreator.seed(seed);
        World overworld = overworldCreator.createWorld();

        WorldCreator netherCreator = new WorldCreator("ftb_" + matchId + "_nether");
        netherCreator.environment(World.Environment.NETHER);
        netherCreator.seed(seed);
        World nether = netherCreator.createWorld();

        if (overworld == null || nether == null) {
            plugin.getLogger().severe("Failed to instantiate world pair for match " + matchId);
            this.isGenerating = false;
            return;
        }

        double borderSize = config.getMatchBorderSize();
        overworld.getWorldBorder().setSize(borderSize);
        nether.getWorldBorder().setSize(borderSize);

        overworld.setAutoSave(false);
        nether.setAutoSave(false);

        MatchWorldPair pair = new MatchWorldPair(matchId, overworld, nether, seed);

        int chunkRadius = config.getChunkRadius();
        int chunksPerSec = config.getChunksPerSecond();
        Location initialSpawn = overworld.getSpawnLocation();

        ChunkPacer pacer = new ChunkPacer(plugin, overworld,
                initialSpawn.getBlockX() >> 4, initialSpawn.getBlockZ() >> 4,
                chunkRadius, chunksPerSec, finishedPacer -> {

            this.activePacer = null;
            Location safeSpawn = LocationUtil.findSafeSurfaceSpawn(overworld, initialSpawn);
            overworld.setSpawnLocation(safeSpawn);
            pair.setSpawnLocation(safeSpawn);
            pair.setChunksPreloaded(finishedPacer.getTotalChunks());
            pair.setReady(true);

            readyQueue.offer(pair);
            isGenerating = false;

            plugin.getLogger().info("Standby world pair 'ftb_" + matchId + "' is ready (" +
                    finishedPacer.getTotalChunks() + " chunks preloaded).");

            if (onComplete != null) {
                onComplete.accept(pair);
            }
        });

        this.activePacer = pacer;
        pacer.start();
    }

    public MatchWorldPair claimReadyWorldPair() {
        MatchWorldPair pair = readyQueue.poll();
        if (pair == null) {
            plugin.getLogger().info("Generating fallback match world pair on demand...");
            pair = createFallbackPair();
        }
        return pair;
    }

    private MatchWorldPair createFallbackPair() {
        String matchId = UUID.randomUUID().toString().substring(0, 8);
        long seed = ThreadLocalRandom.current().nextLong();

        World overworld = new WorldCreator("ftb_" + matchId).environment(World.Environment.NORMAL).seed(seed).createWorld();
        World nether = new WorldCreator("ftb_" + matchId + "_nether").environment(World.Environment.NETHER).seed(seed).createWorld();

        MatchWorldPair pair = new MatchWorldPair(matchId, overworld, nether, seed);
        Location spawn = LocationUtil.findSafeSurfaceSpawn(overworld, overworld.getSpawnLocation());
        pair.setSpawnLocation(spawn);
        pair.setReady(true);
        return pair;
    }

    public void purgeWorldPairAsync(MatchWorldPair pair, World lobbyWorld) {
        if (pair == null) return;

        Location lobbySpawn = lobbyWorld.getSpawnLocation();

        if (pair.getOverworld() != null) {
            for (Player player : pair.getOverworld().getPlayers()) {
                player.teleport(lobbySpawn);
            }
        }
        if (pair.getNether() != null) {
            for (Player player : pair.getNether().getPlayers()) {
                player.teleport(lobbySpawn);
            }
        }
        if (pair.getEnd() != null) {
            // Clear dragon battle boss bar before players leave and world unloads
            org.bukkit.boss.DragonBattle battle = pair.getEnd().getEnderDragonBattle();
            if (battle != null && battle.getBossBar() != null) {
                battle.getBossBar().removeAll();
            }
            for (Player player : pair.getEnd().getPlayers()) {
                player.teleport(lobbySpawn);
            }
        }

        File overworldFolder = pair.getOverworld() != null ? pair.getOverworld().getWorldFolder() : null;
        File netherFolder = pair.getNether() != null ? pair.getNether().getWorldFolder() : null;
        File endFolder = pair.getEnd() != null ? pair.getEnd().getWorldFolder() : null;

        if (pair.getOverworld() != null) Bukkit.unloadWorld(pair.getOverworld(), false);
        if (pair.getNether() != null) Bukkit.unloadWorld(pair.getNether(), false);
        if (pair.getEnd() != null) Bukkit.unloadWorld(pair.getEnd(), false);

        if (!plugin.isEnabled()) {
            deleteWorldFolder(overworldFolder);
            deleteWorldFolder(netherFolder);
            deleteWorldFolder(endFolder);
            return;
        }

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            deleteWorldFolder(overworldFolder);
            deleteWorldFolder(netherFolder);
            deleteWorldFolder(endFolder);
        }, 60L);
    }

    // Retry deletion with GC invocations to release open Windows file locks on session.lock/region files
    private void deleteWorldFolder(File folder) {
        if (folder == null || !folder.exists()) return;

        for (int attempt = 1; attempt <= 8; attempt++) {
            if (attempt > 1) {
                System.gc();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try {
                Files.walkFileTree(folder.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.deleteIfExists(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
                return;
            } catch (IOException e) {
                if (attempt == 8) {
                    plugin.getLogger().warning("Could not delete world directory " + folder.getName() + ": " + e.getMessage());
                    markDeleteOnExit(folder);
                    return;
                }
            }
        }
    }

    private void markDeleteOnExit(File folder) {
        if (folder == null || !folder.exists()) return;
        try {
            Files.walk(folder.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(path -> path.toFile())
                    .forEach(file -> {
                        if (file != null) {
                            file.deleteOnExit();
                        }
                    });
        } catch (IOException ignored) {}
    }

    public void cleanOrphanedMatchWorlds() {
        if (activePacer != null) {
            activePacer.cancel();
            activePacer = null;
        }
        readyQueue.clear();

        File container = Bukkit.getWorldContainer();
        if (container == null || !container.exists() || !container.isDirectory()) return;

        File[] matches = container.listFiles((dir, name) -> name.startsWith("ftb_"));
        if (matches == null || matches.length == 0) return;

        for (File folder : matches) {
            World loaded = Bukkit.getWorld(folder.getName());
            if (loaded != null) {
                Bukkit.unloadWorld(loaded, false);
            }
            deleteWorldFolder(folder);
        }
    }

    public boolean hasReadyWorld() { return !readyQueue.isEmpty(); }
    public int getQueueSize() { return readyQueue.size(); }
    public boolean isGenerating() { return isGenerating; }
}
