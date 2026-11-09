package com.findtheblock.ui;

import com.findtheblock.FindTheBlock;
import com.findtheblock.game.GameManager;
import com.findtheblock.game.GameState;
import com.findtheblock.party.Party;
import com.findtheblock.party.PartyManager;
import com.findtheblock.util.Chat;
import com.findtheblock.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.UUID;

public class PartyMenu extends Menu {

    private final PartyManager partyManager;
    private final GameManager gameManager;

    public PartyMenu(FindTheBlock plugin, PartyManager partyManager, GameManager gameManager, Player viewer) {
        super(plugin, viewer, 36, "<dark_gray>My Party</dark_gray>");
        this.partyManager = partyManager;
        this.gameManager = gameManager;
    }

    @Override
    public void build() {
        inventory.clear();
        Party party = partyManager.getParty(player.getUniqueId());

        if (party == null) {
            Chat.send(player, "<gray>You are not in a party. Opening Party Finder...</gray>");
            Bukkit.getScheduler().runTask(plugin, () -> new PartyFinderMenu(plugin, partyManager, player).open());
            return;
        }

        boolean isLeader = party.getLeaderUuid().equals(player.getUniqueId());

        int slot = 10;
        for (UUID memberUuid : party.getMembers()) {
            if (slot > 16) break;

            Player m = Bukkit.getPlayer(memberUuid);
            String name = m != null ? m.getName() : "Offline";
            boolean isThisLeader = memberUuid.equals(party.getLeaderUuid());

            inventory.setItem(slot++, ItemBuilder.of(Material.PLAYER_HEAD)
                    .skullOwner(m)
                    .name("<yellow>" + name + (isThisLeader ? " <gray>(Leader)</gray>" : "") + "</yellow>")
                    .build());
        }

        boolean isAdmin = player.hasPermission("findtheblock.admin");

        if (isLeader) {
            inventory.setItem(28, ItemBuilder.of(Material.EMERALD_BLOCK)
                    .name("<green>Start Ready Check</green>")
                    .lore("<gray>Requires at least 2 members.</gray>",
                          "<dark_gray>Opens 20s confirmation.</dark_gray>")
                    .build());

            String status = party.isPublic() ? "<green>Public</green>" : "<gray>Private</gray>";
            inventory.setItem(31, ItemBuilder.of(party.isPublic() ? Material.OAK_DOOR : Material.IRON_DOOR)
                    .name("<yellow>Visibility: " + status + "</yellow>")
                    .lore("<dark_gray>Click to toggle</dark_gray>")
                    .build());

            inventory.setItem(34, ItemBuilder.of(Material.TNT)
                    .name("<red>Disband Party</red>")
                    .build());
        } else {
            inventory.setItem(31, ItemBuilder.of(Material.BARRIER)
                    .name("<red>Leave Party</red>")
                    .build());
        }

        if (isAdmin) {
            inventory.setItem(29, ItemBuilder.of(Material.NETHER_STAR)
                    .name("<gold>Start Match (All Lobby)</gold>")
                    .lore("<gray>Start match for everyone in lobby.</gray>",
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
                stopBuilder.lore("<dark_gray>No match currently running.</dark_gray>");
            }
            inventory.setItem(33, stopBuilder.build());
        }
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        Party party = partyManager.getParty(player.getUniqueId());
        if (party == null) {
            player.closeInventory();
            return;
        }

        boolean isLeader = party.getLeaderUuid().equals(player.getUniqueId());
        boolean isAdmin = player.hasPermission("findtheblock.admin");

        if (isLeader && slot == 28) {
            player.closeInventory();
            new DifficultySelectMenu(plugin, gameManager, partyManager, player, DifficultySelectMenu.StartTarget.PARTY).open();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        } else if (isAdmin && slot == 29) {
            player.closeInventory();
            new DifficultySelectMenu(plugin, gameManager, partyManager, player, DifficultySelectMenu.StartTarget.ALL_LOBBY).open();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        } else if (isLeader && slot == 31) {
            partyManager.setPublic(player, !party.isPublic());
            build();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } else if (!isLeader && slot == 31) {
            partyManager.leaveParty(player);
            player.closeInventory();
        } else if (isAdmin && slot == 33) {
            player.closeInventory();
            if (gameManager.getState() == GameState.LOBBY) {
                Chat.send(player, "<red>There is no active match running.</red>");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
            } else {
                gameManager.forceStop(player);
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.0f);
            }
        } else if (isLeader && slot == 34) {
            partyManager.disbandParty(player.getUniqueId());
            player.closeInventory();
        }
    }
}
