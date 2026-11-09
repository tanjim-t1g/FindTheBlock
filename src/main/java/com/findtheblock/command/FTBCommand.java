package com.findtheblock.command;

import com.findtheblock.FindTheBlock;
import com.findtheblock.config.ConfigManager;
import com.findtheblock.config.DifficultyTier;
import com.findtheblock.game.GameManager;
import com.findtheblock.leaderboard.LeaderboardManager;
import com.findtheblock.party.PartyManager;
import com.findtheblock.stats.StatsManager;
import com.findtheblock.ui.LeaderboardMenu;
import com.findtheblock.ui.MatchSelectorMenu;
import com.findtheblock.ui.RecipeMenu;
import com.findtheblock.util.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FTBCommand implements CommandExecutor, TabCompleter {

    private final FindTheBlock plugin;
    private final GameManager gameManager;
    private final ConfigManager config;
    private final PartyManager partyManager;
    private final StatsManager statsManager;
    private final LeaderboardManager leaderboardManager;
    private final PartyCommand partyCommand;

    public FTBCommand(FindTheBlock plugin, GameManager gameManager, ConfigManager config,
                      PartyManager partyManager, StatsManager statsManager,
                      LeaderboardManager leaderboardManager, PartyCommand partyCommand) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.config = config;
        this.partyManager = partyManager;
        this.statsManager = statsManager;
        this.leaderboardManager = leaderboardManager;
        this.partyCommand = partyCommand;
    }

    private String[] resolveArgs(Command cmd, String[] args) {
        String cmdName = cmd.getName().toLowerCase(Locale.ROOT);
        if (!cmdName.equals("findtheblock") && !cmdName.equals("ftb")) {
            String[] effective = new String[args.length + 1];
            effective[0] = cmdName;
            System.arraycopy(args, 0, effective, 1, args.length);
            return effective;
        }
        return args;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String[] effectiveArgs = resolveArgs(cmd, args);

        if (effectiveArgs.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = effectiveArgs[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                boolean wantsAll = effectiveArgs.length > 1 && effectiveArgs[1].equalsIgnoreCase("all");
                if (wantsAll) {
                    if (!sender.hasPermission("findtheblock.admin")) {
                        Chat.send(sender, "<red>You must be an administrator to start a match for all players.</red>");
                        return true;
                    }
                    DifficultyTier tier = effectiveArgs.length > 2 ? DifficultyTier.fromString(effectiveArgs[2]) : config.getDefaultTier();
                    gameManager.startAdminMatchForAll(sender, tier);
                } else if (sender.hasPermission("findtheblock.admin") && !(sender instanceof Player p && partyManager.isPartyLeader(p.getUniqueId()))) {
                    DifficultyTier tier = effectiveArgs.length > 1 ? DifficultyTier.fromString(effectiveArgs[1]) : config.getDefaultTier();
                    gameManager.startAdminMatchForAll(sender, tier);
                } else if (sender instanceof Player p) {
                    gameManager.initiatePartyMatch(p);
                } else {
                    DifficultyTier tier = effectiveArgs.length > 1 ? DifficultyTier.fromString(effectiveArgs[1]) : config.getDefaultTier();
                    gameManager.startAdminMatchForAll(sender, tier);
                }
            }
            case "startall" -> {
                if (!sender.hasPermission("findtheblock.admin")) {
                    Chat.send(sender, "<red>You must be an administrator to start a match for all players.</red>");
                    return true;
                }
                DifficultyTier tier = effectiveArgs.length > 1 ? DifficultyTier.fromString(effectiveArgs[1]) : config.getDefaultTier();
                gameManager.startAdminMatchForAll(sender, tier);
            }
            case "stop", "stopall" -> {
                if (!sender.hasPermission("findtheblock.admin")) {
                    Chat.send(sender, "<red>You must be an administrator to stop matches.</red>");
                    return true;
                }
                gameManager.forceStop(sender);
            }
            case "menu", "selector" -> {
                if (sender instanceof Player p) {
                    new MatchSelectorMenu(plugin, gameManager, partyManager, p).open();
                } else {
                    Chat.send(sender, "<red>Only players can execute this command.</red>");
                }
            }
            case "join" -> {
                if (sender instanceof Player p) {
                    gameManager.joinMidGame(p);
                } else {
                    Chat.send(sender, "<red>Only players can execute this command.</red>");
                }
            }
            case "spectate" -> {
                if (sender instanceof Player p) {
                    gameManager.spectateMidGame(p);
                } else {
                    Chat.send(sender, "<red>Only players can execute this command.</red>");
                }
            }
            case "leave" -> {
                if (sender instanceof Player p) {
                    if (gameManager.isGameRunning() && gameManager.leaveMatch(p)) {
                        return true;
                    }
                    if (partyManager.getParty(p.getUniqueId()) != null) {
                        partyManager.leaveParty(p);
                        return true;
                    }
                    Chat.send(p, "<red>You are not in an active match or party.</red>");
                } else {
                    Chat.send(sender, "<red>Only players can execute this command.</red>");
                }
            }
            case "recipe", "recipy", "guide" -> {
                if (sender instanceof Player p) {
                    if (gameManager.isGameRunning() && gameManager.getCurrentTarget() != null) {
                        new RecipeMenu(gameManager.getCurrentTarget()).open(p);
                    } else {
                        Chat.send(p, "<red>There is no active match or target to view.</red>");
                    }
                }
            }
            case "leaderboard" -> handleLeaderboardCommand(sender, effectiveArgs);
            case "top" -> {
                if (sender instanceof Player p) {
                    new LeaderboardMenu(statsManager, p).open();
                } else {
                    Chat.send(sender, "<yellow>Top Racers:</yellow>");
                    statsManager.getTopPlayers(5).forEach(s ->
                            Chat.send(sender, "  <white>" + s.getName() + "</white> <gray>-</gray> <green>" + s.getWins() + " wins</green>"));
                }
            }
            case "party" -> partyCommand.execute(sender, Arrays.copyOfRange(effectiveArgs, 1, effectiveArgs.length));
            case "reload" -> {
                if (!sender.hasPermission("findtheblock.admin")) {
                    Chat.send(sender, "<red>I'm sorry, but you do not have permission to perform this command.</red>");
                    return true;
                }
                config.load();
                if (leaderboardManager != null) {
                    leaderboardManager.spawnOrUpdateAll();
                }
                Chat.send(sender, "<green>Configuration and leaderboards reloaded.</green>");
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleLeaderboardCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "<red>Only players can execute this command.</red>");
            return;
        }

        if (args.length < 2) {
            new LeaderboardMenu(statsManager, player).open();
            return;
        }

        if (!player.hasPermission("findtheblock.admin")) {
            Chat.send(player, "<red>I'm sorry, but you do not have permission to perform this command.</red>");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("spawn") || action.equals("create")) {
            String type = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "wins";
            if (leaderboardManager.spawnLeaderboard(player.getLocation().clone().add(0, 1.2, 0), type)) {
                Chat.send(player, "<green>Leaderboard spawned (" + type + ").</green>");
            } else {
                Chat.send(player, "<red>Could not spawn leaderboard at this location.</red>");
            }
        } else if (action.equals("remove") || action.equals("delete")) {
            if (leaderboardManager.removeNearestLeaderboard(player.getLocation())) {
                Chat.send(player, "<green>Nearest leaderboard removed.</green>");
            } else {
                Chat.send(player, "<red>No leaderboard found within 10 blocks.</red>");
            }
        } else {
            Chat.send(player, "<yellow>Usage:</yellow> <gray>/ftb leaderboard <spawn|remove> <wins|time></gray>");
        }
    }

    private void sendHelp(CommandSender sender) {
        Chat.send(sender, "<yellow>FindTheBlock Commands:</yellow>");
        Chat.send(sender, "  <yellow>/ftb start</yellow> <gray>- Start match or ready check</gray>");
        Chat.send(sender, "  <yellow>/ftb menu</yellow> <gray>- Open match menu</gray>");
        Chat.send(sender, "  <yellow>/ftb party</yellow> <gray>- Party menu & finder</gray>");
        Chat.send(sender, "  <yellow>/ftb join</yellow> <gray>- Join active race</gray>");
        Chat.send(sender, "  <yellow>/ftb spectate</yellow> <gray>- Spectate race</gray>");
        Chat.send(sender, "  <yellow>/leave</yellow> <gray>- Leave race or party</gray>");
        Chat.send(sender, "  <yellow>/ftb recipe</yellow> <gray>- View target recipe</gray>");
        Chat.send(sender, "  <yellow>/ftb top</yellow> <gray>- View leaderboards</gray>");
        if (sender.hasPermission("findtheblock.admin")) {
            Chat.send(sender, "  <red>/ftb startall [tier]</red> <gray>- Start match for all</gray>");
            Chat.send(sender, "  <red>/ftb stop</red> <gray>- Stop active match</gray>");
            Chat.send(sender, "  <red>/ftb reload</red> <gray>- Reload configuration</gray>");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String[] effectiveArgs = resolveArgs(cmd, args);

        if (effectiveArgs.length == 1) {
            List<String> sub = new ArrayList<>(List.of("start", "menu", "join", "spectate", "leave", "recipe", "top", "party", "leaderboard"));
            if (sender.hasPermission("findtheblock.admin")) {
                sub.add("startall");
                sub.add("stop");
                sub.add("reload");
            }
            return sub.stream().filter(s -> s.startsWith(effectiveArgs[0].toLowerCase(Locale.ROOT))).toList();
        }

        if (effectiveArgs.length >= 2 && effectiveArgs[0].equalsIgnoreCase("party")) {
            return partyCommand.complete(sender, Arrays.copyOfRange(effectiveArgs, 1, effectiveArgs.length));
        }

        if (effectiveArgs.length == 2 && (effectiveArgs[0].equalsIgnoreCase("start") || effectiveArgs[0].equalsIgnoreCase("startall"))) {
            List<String> options = new ArrayList<>(List.of("easy", "normal", "hard"));
            if (sender.hasPermission("findtheblock.admin") && effectiveArgs[0].equalsIgnoreCase("start")) {
                options.add("all");
            }
            return options;
        }

        if (effectiveArgs.length == 2 && effectiveArgs[0].equalsIgnoreCase("leaderboard") && sender.hasPermission("findtheblock.admin")) {
            return List.of("spawn", "remove");
        }

        if (effectiveArgs.length == 3 && effectiveArgs[0].equalsIgnoreCase("leaderboard") && effectiveArgs[1].equalsIgnoreCase("spawn")) {
            return List.of("wins", "time");
        }

        return Collections.emptyList();
    }
}
