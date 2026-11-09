package com.findtheblock.ui;

import com.findtheblock.FindTheBlock;
import com.findtheblock.util.Chat;
import com.findtheblock.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RouletteMenu extends Menu {

    private final List<Material> pool;
    private final Material finalTarget;
    private final Runnable onComplete;

    public RouletteMenu(FindTheBlock plugin, List<Material> pool, Material finalTarget, Runnable onComplete) {
        super(plugin, null, 27, "<dark_gray>Selecting Target...</dark_gray>");
        this.pool = pool;
        this.finalTarget = finalTarget;
        this.onComplete = onComplete;
        build();
    }

    @Override
    public void build() {
        for (int i = 0; i < 9; i++) inventory.setItem(i, Chat.FILLER_PANE);
        for (int i = 18; i < 27; i++) inventory.setItem(i, Chat.FILLER_PANE);

        inventory.setItem(4, ItemBuilder.of(Material.REDSTONE_TORCH)
                .name("<yellow>? Target Objective ?</yellow>")
                .build());
    }

    public void start(List<Player> players) {
        for (Player p : players) {
            p.openInventory(inventory);
        }

        int rollSeconds = plugin.getConfigManager() != null ? plugin.getConfigManager().getRollSeconds() : 3;
        int previewSeconds = plugin.getConfigManager() != null ? plugin.getConfigManager().getPreviewSeconds() : 4;
        final int maxSteps = Math.max(10, rollSeconds * 10);
        final long previewDelayTicks = Math.max(20L, (long) previewSeconds * 20L);

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;

                if (step < maxSteps) {
                    Material roll = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                    inventory.setItem(13, new ItemStack(roll));

                    for (Player p : players) {
                        if (p.isOnline()) {
                            Chat.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.5f);
                        }
                    }
                } else {
                    inventory.setItem(13, new ItemStack(finalTarget));
                    for (Player p : players) {
                        if (p.isOnline()) {
                            Chat.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                        }
                    }

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (Player p : players) {
                            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof RouletteMenu) {
                                p.closeInventory();
                            }
                        }
                        if (onComplete != null) onComplete.run();
                    }, previewDelayTicks);

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }
}
