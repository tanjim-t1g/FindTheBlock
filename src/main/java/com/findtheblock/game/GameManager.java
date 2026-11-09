package com.findtheblock.game;

import com.findtheblock.FindTheBlock;
import com.findtheblock.config.ConfigManager;
import com.findtheblock.config.DifficultyTier;
import com.findtheblock.factory.MatchWorldPair;
import com.findtheblock.factory.WorldFactory;
import com.findtheblock.lobby.LobbyManager;
import com.findtheblock.party.PartyManager;
import com.findtheblock.stats.StatsManager;
import com.findtheblock.ui.BossBarController;
import com.findtheblock.ui.ReadyCheckMenu;
import com.findtheblock.ui.RouletteMenu;
import com.findtheblock.util.Chat;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {

    private final FindTheBlock plugin;
    private final ConfigManager config;
    private final WorldFactory worldFactory;
    private final LobbyManager lobbyManager;
    private final TargetEngine targetEngine;
    private final PartyManager partyManager;
    private final StatsManager statsManager;
    private final SpectatorManager spectatorManager;
    private final ReconnectTracker reconnectTracker;
    private final BossBarController bossBarController;

    private GameState state = GameState.LOBBY;
    private MatchWorldPair activeMatch = null;
    private Material currentTarget = null;
    private DifficultyTier activeTier = DifficultyTier.NORMAL;

    private final Set<UUID> contestants = ConcurrentHashMap.newKeySet();
    private final List<UUID> winners = new ArrayList<>();
    private final Map<UUID, Integer> finishTimes = new HashMap<>();

    private int elapsedSeconds = 0;
    private boolean buffsAwarded = false;
    private boolean adminStarted = false;
    private int emptySeconds = 0;
    private BukkitTask gameTimerTask = null;
    private BukkitTask celebrationTask = null;

    public GameManager(FindTheBlock plugin, ConfigManager config, WorldFactory worldFactory,
                       LobbyManager lobbyManager, TargetEngine targetEngine, PartyManager partyManager,
                       StatsManager statsManager, SpectatorManager spectatorManager,
                       ReconnectTracker reconnectTracker) {
        this.plugin = plugin;
        this.config = config;
        this.worldFactory = worldFactory;
        this.lobbyManager = lobbyManager;
        this.targetEngine = targetEngine;
        this.partyManager = partyManager;
        this.statsManager = statsManager;
        this.spectatorManager = spectatorManager;
        this.reconnectTracker = reconnectTracker;
        this.bossBarController = new BossBarController();
    }

    public void initiatePartyMatch(Player initiator) {
        initiatePartyMatch(initiator, config.getDefaultTier());
    }

    public void initiatePartyMatch(Player initiator, DifficultyTier tier) {
        if (state != GameState.LOBBY) {
            Chat.send(initiator, "<red>A match is already in progress.</red>");
            return;
        }

        if (partyManager.getParty(initiator.getUniqueId()) == null) {
            Chat.send(initiator, "<red>You must be in a party to start.</red>");
            return;
        }

        if (!partyManager.isPartyLeader(initiator.getUniqueId())) {
            Chat.send(initiator, "<red>Only the party leader can start.</red>");
            return;
        }

        List<Player> targetPlayers = new ArrayList<>();
        for (UUID uuid : partyManager.getPartyMembers(initiator.getUniqueId())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) targetPlayers.add(p);
        }

        if (targetPlayers.size() < 2) {
            Chat.send(initiator, "<red>You need at least 2 online party members to start.</red>");
            return;
        }

        DifficultyTier selectedTier = tier != null ? tier : config.getDefaultTier();

        Chat.broadcast("<gold>[FTB]</gold> <yellow>" + initiator.getName() +
                " started a " + selectedTier.name() + " match! Accept in menu (20s).</yellow>");
        new ReadyCheckMenu(plugin, this, initiator, selectedTier, targetPlayers).start();
    }

    public synchronized void startAdminMatchForAll(CommandSender sender, DifficultyTier tier) {
        if (state != GameState.LOBBY) {
            Chat.send(sender, "<red>A match is already in progress.</red>");
            return;
        }

        List<Player> lobbyPlayers = new ArrayList<>();
        World lobbyWorld = lobbyManager.getLobbyWorld();
        if (lobbyWorld != null) {
            for (Player p : lobbyWorld.getPlayers()) {
                if (p != null && p.isOnline()) {
                    lobbyPlayers.add(p);
                }
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null && p.isOnline()) {
                    lobbyPlayers.add(p);
                }
            }
        }

        if (lobbyPlayers.isEmpty()) {
            Chat.send(sender, "<red>No players in the lobby to start a match.</red>");
            return;
        }

        DifficultyTier selectedTier = tier != null ? tier : config.getDefaultTier();
        Chat.broadcast("<gold>[FTB]</gold> <yellow>Admin started a " +
                selectedTier.name() + " match for lobby players.</yellow>");
        startMatchWithPlayers(sender, selectedTier, lobbyPlayers, true);
    }

    public synchronized void startMatchWithPlayers(CommandSender sender, DifficultyTier tier, List<Player> startingPlayers) {
        startMatchWithPlayers(sender, tier, startingPlayers, false);
    }

    public synchronized void startMatchWithPlayers(CommandSender sender, DifficultyTier tier, List<Player> startingPlayers, boolean adminStarted) {
        if (state != GameState.LOBBY) {
            Chat.send(sender, "<red>A match is already running or starting!</red>");
            return;
        }

        this.adminStarted = adminStarted;
        this.emptySeconds = 0;
        this.state = GameState.STARTING;
        this.activeTier = tier != null ? tier : config.getDefaultTier();
        this.currentTarget = targetEngine.pickRandomTarget(activeTier);
        this.contestants.clear();
        this.winners.clear();
        this.finishTimes.clear();
        this.elapsedSeconds = 0;
        this.buffsAwarded = false;
        this.spectatorManager.clear();
        this.reconnectTracker.clear();

        for (Player p : startingPlayers) {
            contestants.add(p.getUniqueId());
            statsManager.recordParticipation(p.getUniqueId(), p.getName());
        }

        this.activeMatch = worldFactory.claimReadyWorldPair();

        RouletteMenu roulette = new RouletteMenu(plugin, config.getTargets(activeTier), currentTarget, () -> {
            launchMatch(startingPlayers);
        });

        roulette.start(startingPlayers);
    }

    private void launchMatch(List<Player> players) {
        Location spawn = activeMatch.getSpawnLocation();
        String targetName = targetEngine.getFriendlyName(currentTarget);

        bossBarController.start(targetName, getTargetWinnerCount());

        for (Player player : players) {
            if (!player.isOnline()) continue;

            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

            giveStarterKit(player);
            player.teleport(spawn);
            bossBarController.addPlayer(player);

            Chat.sendTitle(player,
                    "<gold>" + targetName + "</gold>",
                    "<gray>Find or craft this item</gray>",
                    10, 60, 20);
        }

        this.state = GameState.IN_GAME;

        Chat.broadcast("<gray>[Race] Target: <gold>" + targetName + "</gold> (" + activeTier.getFormattedName() + "<gray>). Good luck!</gray>");

        startMatchTimer();
    }

    private void giveStarterKit(Player player) {
        if (config.isGiveIronTools()) {
            player.getInventory().addItem(
                    new ItemStack(Material.IRON_SWORD),
                    new ItemStack(Material.IRON_PICKAXE),
                    new ItemStack(Material.IRON_AXE),
                    new ItemStack(Material.IRON_SHOVEL)
            );
        }
        if (config.isGiveSilkPickaxe()) {
            ItemStack silkPick = new ItemStack(Material.GOLDEN_PICKAXE);
            silkPick.addEnchantment(Enchantment.SILK_TOUCH, 1);
            player.getInventory().addItem(silkPick);
        }
    }

    private void startMatchTimer() {
        if (gameTimerTask != null) gameTimerTask.cancel();

        this.gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.IN_GAME) {
                    cancel();
                    return;
                }

                elapsedSeconds++;
                int effectiveWinners = getTargetWinnerCount();
                bossBarController.update(targetEngine.getFriendlyName(currentTarget), elapsedSeconds, winners.size(), effectiveWinners);

                if (config.isTwentyMinBuffs() && !buffsAwarded && elapsedSeconds >= 1200) {
                    buffsAwarded = true;
                    award20MinBuffs();
                }

                if (!adminStarted) {
                    long activeCount = contestants.stream()
                            .filter(uuid -> !winners.contains(uuid))
                            .map(Bukkit::getPlayer)
                            .filter(p -> p != null && p.isOnline() && activeMatch != null && activeMatch.containsWorld(p.getWorld()))
                            .count();

                    if (activeCount <= 1) {
                        emptySeconds++;
                        if (emptySeconds == 30) {
                            Chat.broadcast("<gray>[Race] Only 1 player remaining. Match ends in <yellow>30s</yellow>.</gray>");
                        } else if (emptySeconds >= 60) {
                            Chat.broadcast("<gray>[Race] Match ended: Not enough players remaining.</gray>");
                            resetGame();
                            cancel();
                            return;
                        }
                    } else {
                        emptySeconds = 0;
                    }
                }

                if (elapsedSeconds >= config.getTimeoutHours() * 3600) {
                    Chat.broadcast("<gray>[Race] Match reached the time limit.</gray>");
                    resetGame();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void award20MinBuffs() {
        Chat.broadcast("<gray>[Race] 20 minutes reached: <green>Speed II</green> and <green>Regeneration I</green> granted.</gray>");

        for (UUID uuid : contestants) {
            if (winners.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, true, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, true, true));
                Chat.sendTitle(p, "<green>Buffs Granted</green>", "<gray>Speed II & Regeneration I</gray>", 10, 60, 20);
                Chat.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            }
        }
    }

    public synchronized void onTargetAcquired(Player player) {
        if (state != GameState.IN_GAME || winners.contains(player.getUniqueId())) {
            return;
        }

        winners.add(player.getUniqueId());
        int place = winners.size();
        finishTimes.put(player.getUniqueId(), elapsedSeconds);

        statsManager.recordPodiumFinish(player.getUniqueId(), player.getName(), place, elapsedSeconds);

        int m = elapsedSeconds / 60;
        int s = elapsedSeconds % 60;
        String timeStr = String.format("%02d:%02d", m, s);
        String targetName = targetEngine.getFriendlyName(currentTarget);

        String placeTitle = place == 1 ? "<gold><bold>1st Place</bold></gold>" :
                            place == 2 ? "<yellow><bold>2nd Place</bold></yellow>" :
                                         "<red><bold>3rd Place</bold></red>";

        Chat.broadcast("<gray>[Race] <yellow>" + player.getName() +
                "</yellow> found <gold>" + targetName + "</gold> in <white>" + timeStr + "</white>! (" + placeTitle + ")</gray>");

        Chat.sendTitle(player, placeTitle, "<gray>" + targetName + " in " + timeStr + "</gray>", 10, 80, 20);
        spawnVictoryFirework(player.getLocation());

        spectatorManager.makeSpectator(player, player.getLocation());

        int targetWinners = getTargetWinnerCount();
        if (winners.size() >= targetWinners || winners.size() >= contestants.size()) {
            concludeMatch();
        }
    }

    private void concludeMatch() {
        this.state = GameState.CELEBRATING;
        if (gameTimerTask != null) gameTimerTask.cancel();

        Chat.broadcast("<gray>[Race] <gold>Match Over!</gold></gray>");
        for (int i = 0; i < winners.size(); i++) {
            UUID winnerId = winners.get(i);
            Player p = Bukkit.getPlayer(winnerId);
            String name = p != null ? p.getName() : "Unknown";
            int time = finishTimes.getOrDefault(winnerId, elapsedSeconds);
            int m = time / 60;
            int s = time % 60;
            Chat.broadcast("  <gray>#" + (i + 1) + " <white>" + name + "</white> - <white>" + String.format("%02d:%02d", m, s) + "</white></gray>");
        }
        Chat.broadcast("  <gray>Returning to lobby in " + config.getCelebrationSeconds() + "s...</gray>");

        if (plugin.getLeaderboardManager() != null) {
            plugin.getLeaderboardManager().spawnOrUpdateAll();
        }

        if (celebrationTask != null) {
            celebrationTask.cancel();
        }
        celebrationTask = Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, config.getCelebrationSeconds() * 20L);
    }

    public void resetGame() {
        if (celebrationTask != null) {
            celebrationTask.cancel();
            celebrationTask = null;
        }
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }

        this.state = GameState.RESETTING;

        Set<Player> allPlayers = new HashSet<>();
        for (UUID uuid : contestants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) allPlayers.add(p);
        }
        for (UUID uuid : spectatorManager.getSpectators()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) allPlayers.add(p);
        }
        if (activeMatch != null) {
            if (activeMatch.getOverworld() != null) {
                allPlayers.addAll(activeMatch.getOverworld().getPlayers());
            }
            if (activeMatch.getNether() != null) {
                allPlayers.addAll(activeMatch.getNether().getPlayers());
            }
            if (activeMatch.getEnd() != null) {
                allPlayers.addAll(activeMatch.getEnd().getPlayers());
                // Remove dragon boss bar before unloading End dimension
                org.bukkit.boss.DragonBattle battle = activeMatch.getEnd().getEnderDragonBattle();
                if (battle != null && battle.getBossBar() != null) {
                    battle.getBossBar().removeAll();
                }
            }
        }

        bossBarController.hideAll(allPlayers);

        spectatorManager.clear();
        reconnectTracker.clear();
        contestants.clear();
        winners.clear();
        finishTimes.clear();

        for (Player p : allPlayers) {
            lobbyManager.teleportToLobby(p);
        }

        World lobbyWorld = lobbyManager.getLobbyWorld();
        if (lobbyWorld != null) {
            for (Player p : lobbyWorld.getPlayers()) {
                if (p != null && p.isOnline()) {
                    lobbyManager.giveLobbyItems(p);
                }
            }
        }

        if (activeMatch != null) {
            worldFactory.purgeWorldPairAsync(activeMatch, lobbyManager.getLobbyWorld());
            activeMatch = null;
        }

        currentTarget = null;
        elapsedSeconds = 0;
        buffsAwarded = false;
        adminStarted = false;
        emptySeconds = 0;

        this.state = GameState.LOBBY;
        this.worldFactory.refillQueueAsync(null);
    }

    public void shutdown() {
        if (celebrationTask != null) {
            celebrationTask.cancel();
            celebrationTask = null;
        }
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }

        if (bossBarController != null) {
            bossBarController.hideAll(Bukkit.getOnlinePlayers());
        }

        if (activeMatch != null) {
            if (activeMatch.getEnd() != null) {
                org.bukkit.boss.DragonBattle battle = activeMatch.getEnd().getEnderDragonBattle();
                if (battle != null && battle.getBossBar() != null) {
                    battle.getBossBar().removeAll();
                }
            }

            World lobby = lobbyManager.getLobbyWorld();
            Location fallbackSpawn = lobby != null ? lobby.getSpawnLocation()
                    : (!Bukkit.getWorlds().isEmpty() ? Bukkit.getWorlds().get(0).getSpawnLocation() : null);

            if (fallbackSpawn != null) {
                if (activeMatch.getOverworld() != null) {
                    for (Player p : activeMatch.getOverworld().getPlayers()) {
                        p.teleport(fallbackSpawn);
                    }
                }
                if (activeMatch.getNether() != null) {
                    for (Player p : activeMatch.getNether().getPlayers()) {
                        p.teleport(fallbackSpawn);
                    }
                }
                if (activeMatch.getEnd() != null) {
                    for (Player p : activeMatch.getEnd().getPlayers()) {
                        p.teleport(fallbackSpawn);
                    }
                }
            }

            if (activeMatch.getOverworld() != null) Bukkit.unloadWorld(activeMatch.getOverworld(), false);
            if (activeMatch.getNether() != null) Bukkit.unloadWorld(activeMatch.getNether(), false);
            if (activeMatch.getEnd() != null) Bukkit.unloadWorld(activeMatch.getEnd(), false);
            activeMatch = null;
        }

        spectatorManager.clear();
        reconnectTracker.clear();
        contestants.clear();
        winners.clear();
        finishTimes.clear();

        this.currentTarget = null;
        this.elapsedSeconds = 0;
        this.buffsAwarded = false;
        this.adminStarted = false;
        this.emptySeconds = 0;
        this.state = GameState.LOBBY;
    }

    public void forceStop(CommandSender sender) {
        if (state == GameState.LOBBY) {
            Chat.send(sender, "<red>No match running.</red>");
            return;
        }
        Chat.broadcast("<gray>[FTB] Match stopped by admin.</gray>");
        resetGame();
    }

    public boolean leaveMatch(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        boolean wasContestant = contestants.remove(uuid);
        boolean wasWinner = winners.remove(uuid);
        boolean wasSpectator = spectatorManager.isSpectator(uuid);

        if (wasSpectator) {
            spectatorManager.restorePlayer(player);
        }

        if (wasContestant || wasWinner || wasSpectator) {
            bossBarController.removePlayer(player);
            if (activeMatch != null && activeMatch.getEnd() != null) {
                org.bukkit.boss.DragonBattle battle = activeMatch.getEnd().getEnderDragonBattle();
                if (battle != null && battle.getBossBar() != null) {
                    battle.getBossBar().removePlayer(player);
                }
            }
            lobbyManager.teleportToLobby(player);
            Chat.send(player, "<gray>You left the race.</gray>");
            Chat.broadcast("<gray>[Race] <white>" + player.getName() + "</white> left the race.</gray>");
            return true;
        }

        return false;
    }

    public void joinMidGame(Player player) {
        if (state != GameState.IN_GAME || activeMatch == null) {
            Chat.send(player, "<red>No match running.</red>");
            return;
        }
        if (contestants.contains(player.getUniqueId())) {
            Chat.send(player, "<red>You are already in the race.</red>");
            return;
        }
        if (spectatorManager.isSpectator(player.getUniqueId())) {
            Chat.send(player, "<red>You are already spectating.</red>");
            return;
        }
        if (!config.isAllowLateEntry()) {
            Chat.send(player, "<red>Mid-game joining is disabled.</red>");
            return;
        }

        contestants.add(player.getUniqueId());
        statsManager.recordParticipation(player.getUniqueId(), player.getName());

        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        giveStarterKit(player);
        player.teleport(activeMatch.getSpawnLocation());
        bossBarController.addPlayer(player);

        Chat.broadcast("<gray>[Race] <white>" + player.getName() + "</white> joined the race.</gray>");
        Chat.sendTitle(player,
                "<gold>" + targetEngine.getFriendlyName(currentTarget) + "</gold>",
                "<gray>Find or craft this item</gray>",
                10, 60, 20);
    }

    public void spectateMidGame(Player player) {
        if (state != GameState.IN_GAME || activeMatch == null) {
            Chat.send(player, "<red>No match running.</red>");
            return;
        }
        if (!config.isAllowSpectate()) {
            Chat.send(player, "<red>Mid-game spectating is disabled.</red>");
            return;
        }
        if (contestants.contains(player.getUniqueId())) {
            Chat.send(player, "<red>You are already in the race.</red>");
            return;
        }
        if (spectatorManager.isSpectator(player.getUniqueId())) {
            Chat.send(player, "<red>You are already spectating.</red>");
            return;
        }

        spectatorManager.makeSpectator(player, activeMatch.getSpawnLocation());
        bossBarController.addPlayer(player);
    }

    public int getTargetWinnerCount() {
        int total = contestants.size();
        if (total <= 1) return 1;
        if (total <= config.getMaxWinners()) return total - 1;
        return config.getMaxWinners();
    }

    private void spawnVictoryFirework(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.LIME, Color.YELLOW, Color.WHITE)
                .withFade(Color.AQUA)
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    public GameState getState() { return state; }
    public boolean isGameRunning() { return state == GameState.IN_GAME; }
    public Material getCurrentTarget() { return currentTarget; }
    public int getElapsedSeconds() { return elapsedSeconds; }
    public Set<UUID> getContestants() { return contestants; }
    public List<UUID> getWinners() { return winners; }
    public MatchWorldPair getActiveMatch() { return activeMatch; }
    public DifficultyTier getActiveTier() { return activeTier; }
    public SpectatorManager getSpectatorManager() { return spectatorManager; }
    public ReconnectTracker getReconnectTracker() { return reconnectTracker; }

    public static class ReconnectTracker {
        public static class SavedSession {
            private final Location location;
            private final ItemStack[] inventory;
            private final ItemStack[] armor;
            private final double health;
            private final int foodLevel;
            private final float saturation;
            private final long disconnectTime;

            public SavedSession(Player player) {
                this.location = player.getLocation().clone();
                this.inventory = player.getInventory().getContents();
                this.armor = player.getInventory().getArmorContents();
                this.health = player.getHealth();
                this.foodLevel = player.getFoodLevel();
                this.saturation = player.getSaturation();
                this.disconnectTime = System.currentTimeMillis();
            }

            public void restore(Player player) {
                player.teleport(location);
                player.getInventory().setContents(inventory);
                player.getInventory().setArmorContents(armor);
                var maxAttr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                double maxHealth = maxAttr != null ? maxAttr.getValue() : 20.0;
                player.setHealth(Math.min(maxHealth, health));
                player.setFoodLevel(foodLevel);
                player.setSaturation(saturation);
            }

            public boolean isExpired(long graceDurationMs) {
                return System.currentTimeMillis() - disconnectTime > graceDurationMs;
            }
        }

        private final Map<UUID, SavedSession> savedSessions = new ConcurrentHashMap<>();

        public void save(Player player) {
            if (player != null) {
                savedSessions.put(player.getUniqueId(), new SavedSession(player));
            }
        }

        public boolean hasSavedSession(UUID uuid, long graceDurationMs) {
            SavedSession session = savedSessions.get(uuid);
            if (session == null) return false;
            if (session.isExpired(graceDurationMs)) {
                savedSessions.remove(uuid);
                return false;
            }
            return true;
        }

        public boolean restore(Player player, long graceDurationMs) {
            SavedSession session = savedSessions.remove(player.getUniqueId());
            if (session == null || session.isExpired(graceDurationMs)) {
                return false;
            }
            session.restore(player);
            return true;
        }

        public void clear() {
            savedSessions.clear();
        }
    }
}
