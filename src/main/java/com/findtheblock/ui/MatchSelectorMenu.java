package com.findtheblock.ui;

import com.findtheblock.FindTheBlock;
import com.findtheblock.game.GameManager;
import com.findtheblock.game.GameState;
import com.findtheblock.party.PartyManager;
import com.findtheblock.util.Chat;
import com.findtheblock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MatchSelectorMenu extends Menu {

    private final GameManager gameManager;
    private final PartyManager partyManager;

    public MatchSelectorMenu(FindTheBlock plugin, GameManager gameManager, PartyManager partyManager, Player viewer) {
        super(plugin, viewer, 36, "<dark_gray>Match Selector</dark_gray>");
        this.gameManager = gameManager;
        this.partyManager = partyManager;
    }

    @Override
    public void build() {
        inventory.clear();

        if (gameManager.isGameRunning()) {
            Material target = gameManager.getCurrentTarget();
            int elapsed = gameManager.getElapsedSeconds();
            String timeStr = String.format("%02d:%02d", elapsed / 60, elapsed % 60);
            String friendlyTarget = target != null ? Chat.formatMaterial(target) : "Target";

            inventory.setItem(13, ItemBuilder.of(target != null && target.isItem() ? target : Material.BEACON)
                    .name("<yellow>Active Match: <white>" + friendlyTarget + "</white></yellow>")
                    .lore("<gray>Time: <white>" + timeStr + "</white></gray>",
                          "<gray>Racers Left: <white>" + gameManager.getContestants().size() + "</white></gray>",
                          "",
                          "<gray>Left-Click: <green>Join Match</green></gray>",
                          "<gray>Right-Click: <aqua>Spectate</aqua></gray>")
                    .build());
        } else {
            inventory.setItem(13, ItemBuilder.of(Material.STRUCTURE_VOID)
                    .name("<gray>No Active Match</gray>")
                    .lore("<dark_gray>Start a match or join a party below.</dark_gray>")
                    .build());
        }

        boolean isAdmin = player.hasPermission("findtheblock.admin");
        boolean inParty = partyManager.getParty(player.getUniqueId()) != null;

        if (isAdmin) {
            inventory.setItem(28, ItemBuilder.of(Material.PLAYER_HEAD)
                    .name("<yellow>" + (inParty ? "Manage Party" : "Party Finder") + "</yellow>")
                    .lore("<gray>Find or manage parties.</gray>",
                          "",
                          "<dark_gray>Click to open</dark_gray>")
                    .build());

            inventory.setItem(30, ItemBuilder.of(Material.EMERALD)
                    .name("<green>Start Party Match</green>")
                    .lore("<gray>Start match for your party.</gray>",
                          "<dark_gray>Min. 2 online members.</dark_gray>",
                          "",
                          "<dark_gray>Click to choose mode</dark_gray>")
                    .build());

            inventory.setItem(32, ItemBuilder.of(Material.NETHER_STAR)
                    .name("<gold>Start Match (All Lobby)</gold>")
                    .lore("<gray>Start for everyone in lobby.</gray>",
                          "",
                          "<dark_gray>Click to choose mode</dark_gray>")
                    .build());

            boolean running = gameManager.isGameRunning();
            ItemBuilder stopBuilder = ItemBuilder.of(running ? Material.REDSTONE_BLOCK : Material.BARRIER)
                    .name(running ? "<red>Stop Match</red>" : "<gray>No Active Match</gray>");
            if (running) {
                stopBuilder.lore("<gray>Stop current match and return to lobby.</gray>",
                                 "",
                                 "<dark_gray>Click to stop</dark_gray>");
            } else {
                stopBuilder.lore("<dark_gray>No match is running.</dark_gray>");
            }
            inventory.setItem(34, stopBuilder.build());
        } else {
            inventory.setItem(29, ItemBuilder.of(Material.PLAYER_HEAD)
                    .name("<yellow>" + (inParty ? "Manage Party" : "Party Finder") + "</yellow>")
                    .lore("<gray>Find or manage parties.</gray>",
                          "",
                          "<dark_gray>Click to open</dark_gray>")
                    .build());

            inventory.setItem(33, ItemBuilder.of(Material.EMERALD)
                    .name("<green>Start Party Match</green>")
                    .lore("<gray>Start match for your party.</gray>",
                          "<dark_gray>Min. 2 online members.</dark_gray>",
                          "",
                          "<dark_gray>Click to start</dark_gray>")
                    .build());
        }
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        boolean isAdmin = player.hasPermission("findtheblock.admin");

        if (slot == 13) {
            if (gameManager.isGameRunning()) {
                player.closeInventory();
                if (clickType == ClickType.RIGHT) {
                    gameManager.spectateMidGame(player);
                } else {
                    gameManager.joinMidGame(player);
                }
            }
        } else if ((slot == 28 && isAdmin) || (slot == 29 && !isAdmin)) {
            player.closeInventory();
            if (partyManager.getParty(player.getUniqueId()) != null) {
                new PartyMenu(plugin, partyManager, gameManager, player).open();
            } else {
                new PartyFinderMenu(plugin, partyManager, player).open();
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        } else if ((slot == 30 && isAdmin) || (slot == 33 && !isAdmin)) {
            player.closeInventory();
            new DifficultySelectMenu(plugin, gameManager, partyManager, player, DifficultySelectMenu.StartTarget.PARTY).open();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        } else if (slot == 32 && isAdmin) {
            player.closeInventory();
            new DifficultySelectMenu(plugin, gameManager, partyManager, player, DifficultySelectMenu.StartTarget.ALL_LOBBY).open();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        } else if (slot == 34 && isAdmin) {
            player.closeInventory();
            if (gameManager.getState() == GameState.LOBBY) {
                Chat.send(player, "<red>There is no active match running.</red>");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
            } else {
                gameManager.forceStop(player);
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.0f);
            }
        }
    }
}
