package com.findtheblock.ui;

import com.findtheblock.FindTheBlock;
import com.findtheblock.config.DifficultyTier;
import com.findtheblock.game.GameManager;
import com.findtheblock.party.PartyManager;
import com.findtheblock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public class DifficultySelectMenu extends Menu {

    public enum StartTarget {
        PARTY,
        ALL_LOBBY
    }

    private final GameManager gameManager;
    private final PartyManager partyManager;
    private final StartTarget startTarget;

    public DifficultySelectMenu(FindTheBlock plugin, GameManager gameManager, PartyManager partyManager, Player viewer) {
        this(plugin, gameManager, partyManager, viewer, StartTarget.PARTY);
    }

    public DifficultySelectMenu(FindTheBlock plugin, GameManager gameManager, PartyManager partyManager, Player viewer, StartTarget startTarget) {
        super(plugin, viewer, 27, startTarget == StartTarget.ALL_LOBBY
                ? "<dark_gray>Select Mode (All Lobby)</dark_gray>"
                : "<dark_gray>Select Difficulty</dark_gray>");
        this.gameManager = gameManager;
        this.partyManager = partyManager;
        this.startTarget = startTarget;
    }

    @Override
    public void build() {
        inventory.clear();

        String actionHint = startTarget == StartTarget.ALL_LOBBY
                ? "<dark_gray>Click to start for lobby</dark_gray>"
                : "<dark_gray>Click to select & start</dark_gray>";

        inventory.setItem(11, ItemBuilder.of(Material.LIME_CONCRETE)
                .name("<green>Easy</green>")
                .lore("<gray>Surface and basic farming items.</gray>",
                      "<gray>Fast, casual races.</gray>",
                      "",
                      actionHint)
                .build());

        inventory.setItem(13, ItemBuilder.of(Material.YELLOW_CONCRETE)
                .name("<yellow>Normal</yellow>")
                .lore("<gray>Cave mining and Nether exploration.</gray>",
                      "<gray>Standard scavenger race.</gray>",
                      "",
                      actionHint)
                .build());

        inventory.setItem(15, ItemBuilder.of(Material.RED_CONCRETE)
                .name("<red>Hard</red>")
                .lore("<gray>Deep caves, fortresses, challenge blocks.</gray>",
                      "<gray>Advanced survival search.</gray>",
                      "",
                      actionHint)
                .build());

        inventory.setItem(22, ItemBuilder.of(Material.ARROW)
                .name("<gray>Back</gray>")
                .build());
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        DifficultyTier selectedTier = switch (slot) {
            case 11 -> DifficultyTier.EASY;
            case 13 -> DifficultyTier.NORMAL;
            case 15 -> DifficultyTier.HARD;
            default -> null;
        };

        if (slot == 22) {
            player.closeInventory();
            new MatchSelectorMenu(plugin, gameManager, partyManager, player).open();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
            return;
        }

        if (selectedTier != null) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

            if (startTarget == StartTarget.ALL_LOBBY) {
                gameManager.startAdminMatchForAll(player, selectedTier);
            } else {
                gameManager.initiatePartyMatch(player, selectedTier);
            }
        }
    }
}
