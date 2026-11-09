package com.findtheblock.ui;

import com.findtheblock.FindTheBlock;
import com.findtheblock.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public abstract class Menu implements InventoryHolder {

    protected final FindTheBlock plugin;
    protected final Player player;
    protected final Inventory inventory;

    public Menu(FindTheBlock plugin, Player player, int size, String title) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, size, Chat.toComponent(title));
    }

    public abstract void build();

 
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {}

    public void handleClose(InventoryCloseEvent event) {}

    public void open() {
        build();
        player.openInventory(inventory);
    }

    protected void fill(ItemStack filler) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    protected void fillBorders(ItemStack filler) {
        int size = inventory.getSize();
        int rows = size / 9;
        for (int col = 0; col < 9; col++) {
            inventory.setItem(col, filler);
            inventory.setItem(size - 9 + col, filler);
        }
        for (int row = 0; row < rows; row++) {
            inventory.setItem(row * 9, filler);
            inventory.setItem(row * 9 + 8, filler);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }
}
