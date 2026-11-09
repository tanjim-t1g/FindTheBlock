package com.findtheblock.ui;

import com.findtheblock.FindTheBlock;
import com.findtheblock.party.Party;
import com.findtheblock.party.PartyManager;
import com.findtheblock.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.*;

public class PartyFinderMenu extends Menu {

    private final PartyManager partyManager;
    private final Map<Integer, UUID> slotToLeaderMap = new HashMap<>();

    public PartyFinderMenu(FindTheBlock plugin, PartyManager partyManager, Player viewer) {
        super(plugin, viewer, 54, "<dark_gray>Party Finder</dark_gray>");
        this.partyManager = partyManager;
    }

    @Override
    public void build() {
        inventory.clear();
        slotToLeaderMap.clear();

        List<Party> publicParties = partyManager.getPublicParties();
        int slot = 0;

        for (Party party : publicParties) {
            if (slot >= 45) break;

            Player leader = Bukkit.getPlayer(party.getLeaderUuid());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Members: <white>" + party.size() + "/" + party.getMaxSize() + "</white></gray>");
            lore.add("");

            for (UUID memberUuid : party.getMembers()) {
                Player m = Bukkit.getPlayer(memberUuid);
                String name = m != null ? m.getName() : "Offline";
                String role = memberUuid.equals(party.getLeaderUuid()) ? " <yellow>(Leader)</yellow>" : "";
                lore.add("<gray>\u2022 " + name + role + "</gray>");
            }

            lore.add("");
            lore.add("<dark_gray>Click to join</dark_gray>");

            inventory.setItem(slot, ItemBuilder.of(Material.PLAYER_HEAD)
                    .skullOwner(leader)
                    .name("<yellow>" + party.getLeaderName() + "'s Party</yellow>")
                    .lore(lore)
                    .build());

            slotToLeaderMap.put(slot, party.getLeaderUuid());
            slot++;
        }

        if (publicParties.isEmpty()) {
            inventory.setItem(22, ItemBuilder.of(Material.STRUCTURE_VOID)
                    .name("<gray>No Open Parties</gray>")
                    .lore("<dark_gray>Create a party using the buttons below.</dark_gray>")
                    .build());
        }

        inventory.setItem(45, ItemBuilder.of(Material.EMERALD_BLOCK)
                .name("<green>Create Public Party</green>")
                .lore("<gray>Listed in the Party Finder.</gray>")
                .build());

        inventory.setItem(46, ItemBuilder.of(Material.IRON_BLOCK)
                .name("<yellow>Create Private Party</yellow>")
                .lore("<gray>Invite-only party.</gray>")
                .build());

        inventory.setItem(49, ItemBuilder.of(Material.NETHER_STAR)
                .name("<aqua>Refresh</aqua>")
                .build());

        inventory.setItem(53, ItemBuilder.of(Material.BARRIER)
                .name("<red>Close</red>")
                .build());
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        if (slotToLeaderMap.containsKey(slot)) {
            UUID leaderUuid = slotToLeaderMap.get(slot);
            partyManager.joinPublicParty(player, leaderUuid);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            player.closeInventory();
            return;
        }

        if (slot == 45) {
            if (partyManager.createParty(player, true)) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            }
            player.closeInventory();
        } else if (slot == 46) {
            if (partyManager.createParty(player, false)) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
            player.closeInventory();
        } else if (slot == 49) {
            build();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        } else if (slot == 53) {
            player.closeInventory();
        }
    }
}
