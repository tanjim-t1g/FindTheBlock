package com.findtheblock;

import com.findtheblock.command.FTBCommand;
import com.findtheblock.command.PartyCommand;
import com.findtheblock.config.ConfigManager;
import com.findtheblock.factory.WorldFactory;
import com.findtheblock.game.GameManager;
import com.findtheblock.game.GameManager.ReconnectTracker;
import com.findtheblock.game.SpectatorManager;
import com.findtheblock.game.TargetEngine;
import com.findtheblock.hook.FTBPlaceholderHook;
import com.findtheblock.leaderboard.LeaderboardManager;
import com.findtheblock.listener.GameListener;
import com.findtheblock.listener.ChatListener;
import com.findtheblock.listener.ConnectionListener;
import com.findtheblock.listener.MenuListener;
import com.findtheblock.listener.ProtectionListener;
import com.findtheblock.lobby.LobbyManager;
import com.findtheblock.party.PartyManager;
import com.findtheblock.stats.StatsManager;
import com.findtheblock.util.UpdateChecker;
import java.util.List;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class FindTheBlock extends JavaPlugin {

    private ConfigManager configManager;
    private LobbyManager lobbyManager;
    private WorldFactory worldFactory;
    private TargetEngine targetEngine;
    private PartyManager partyManager;
    private StatsManager statsManager;
    private SpectatorManager spectatorManager;
    private ReconnectTracker reconnectTracker;
    private GameManager gameManager;
    private LeaderboardManager leaderboardManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        // Load configurations and initialize core subsystems
        this.configManager = new ConfigManager(this);

        this.lobbyManager = new LobbyManager(this, configManager);
        this.worldFactory = new WorldFactory(this, configManager);
        this.worldFactory.cleanOrphanedMatchWorlds();
        this.targetEngine = new TargetEngine(this, configManager);
        this.partyManager = new PartyManager(this);
        this.statsManager = new StatsManager(this);
        this.leaderboardManager = new LeaderboardManager(this, statsManager);
        this.spectatorManager = new SpectatorManager(this);
        this.reconnectTracker = new ReconnectTracker();

        this.gameManager = new GameManager(this, configManager, worldFactory, lobbyManager,
                targetEngine, partyManager, statsManager, spectatorManager, reconnectTracker);

        this.updateChecker = new UpdateChecker(this);
        this.updateChecker.checkForUpdates();

        // Register event listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new GameListener(gameManager, targetEngine), this);
        pm.registerEvents(new ProtectionListener(this, gameManager, lobbyManager, configManager, partyManager), this);
        pm.registerEvents(new ConnectionListener(gameManager, lobbyManager, configManager, partyManager, updateChecker), this);
        pm.registerEvents(new ChatListener(gameManager, lobbyManager), this);
        pm.registerEvents(new MenuListener(), this);

        registerCommands();

        // Register PlaceholderAPI expansion if present
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FTBPlaceholderHook(this, gameManager, statsManager).register();
        }

        // Metrics and initial match world pre-generation
        new Metrics(this, 35123);
        this.worldFactory.refillQueueAsync(null);
        getLogger().info("FindTheBlock enabled.");
    }

    private void registerCommands() {
        PartyCommand partyExecutor = new PartyCommand(this, partyManager, gameManager, configManager);
        PluginCommand partyCmd = getCommand("party");
        if (partyCmd != null) {
            partyCmd.setExecutor(partyExecutor);
            partyCmd.setTabCompleter(partyExecutor);
        }

        FTBCommand ftbCmd = new FTBCommand(this, gameManager, configManager, partyManager, statsManager, leaderboardManager, partyExecutor);
        for (String name : List.of("findtheblock", "leave", "join", "spectate", "recipe", "menu", "top", "start")) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(ftbCmd);
                cmd.setTabCompleter(ftbCmd);
            }
        }
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (worldFactory != null) {
            worldFactory.cleanOrphanedMatchWorlds();
        }
        if (statsManager != null) {
            statsManager.save();
        }
        getLogger().info("FindTheBlock disabled.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public LobbyManager getLobbyManager() { return lobbyManager; }
    public WorldFactory getWorldFactory() { return worldFactory; }
    public TargetEngine getTargetEngine() { return targetEngine; }
    public PartyManager getPartyManager() { return partyManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public SpectatorManager getSpectatorManager() { return spectatorManager; }
    public ReconnectTracker getReconnectTracker() { return reconnectTracker; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public GameManager getGameManager() { return gameManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}
