package com.findtheblock.listener;

import com.findtheblock.config.ConfigManager;
import com.findtheblock.game.GameManager;
import com.findtheblock.game.GameState;
import com.findtheblock.lobby.LobbyManager;
import com.findtheblock.party.PartyManager;
import com.findtheblock.util.Chat;
import com.findtheblock.util.UpdateChecker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListener implements Listener {

    private final GameManager gameManager;
    private final LobbyManager lobbyManager;
    private final ConfigManager config;
    private final PartyManager partyManager;
    private final UpdateChecker updateChecker;

    public ConnectionListener(GameManager gameManager, LobbyManager lobbyManager,
                              ConfigManager config, PartyManager partyManager, UpdateChecker updateChecker) {
        this.gameManager = gameManager;
        this.lobbyManager = lobbyManager;
        this.config = config;
        this.partyManager = partyManager;
        this.updateChecker = updateChecker;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long graceMs = config.getReconnectGraceSeconds() * 1000L;

        gameManager.getSpectatorManager().hideSpectatorsFrom(player);

        if (gameManager.getState() == GameState.IN_GAME) {
            if (gameManager.getReconnectTracker().hasSavedSession(player.getUniqueId(), graceMs)) {
                gameManager.getReconnectTracker().restore(player, graceMs);
                Chat.sendTitle(player, "<green>Welcome Back</green>", "<gray>Reconnected to match</gray>", 10, 40, 10);
                Chat.broadcast("<gray>[Race] <white>" + player.getName() + "</white> reconnected.</gray>");
                return;
            }

            gameManager.getContestants().remove(player.getUniqueId());

            lobbyManager.teleportToLobby(player);
            String targetName = gameManager.getCurrentTarget() != null ? Chat.formatMaterial(gameManager.getCurrentTarget()) : "None";
            Chat.send(player, "<yellow>Match in progress! Target: <gold>" + targetName + "</gold></yellow>");
            Chat.send(player, "  <green><click:run_command:/ftb join>[Join Race]</click></green>  <gray>|</gray>  <aqua><click:run_command:/ftb spectate>[Spectate]</click></aqua>");
        } else {
            lobbyManager.teleportToLobby(player);
        }

        if ((player.isOp() || player.hasPermission("findtheblock.admin")) && updateChecker.isUpdateAvailable() && config.isUpdateNotifyOps()) {
            updateChecker.sendOpNotification(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (gameManager.getSpectatorManager().isSpectator(player.getUniqueId())) {
            gameManager.getSpectatorManager().restorePlayer(player);
        }

        if (gameManager.getState() == GameState.IN_GAME && gameManager.getContestants().contains(player.getUniqueId())) {
            gameManager.getReconnectTracker().save(player);
        }

        // Clean up party membership or pass leadership if leader leaves
        partyManager.handlePlayerQuit(player);
    }
}
