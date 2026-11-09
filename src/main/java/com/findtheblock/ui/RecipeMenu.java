package com.findtheblock.ui;

import com.findtheblock.util.Chat;
import com.findtheblock.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

import java.util.List;

public class RecipeMenu extends Menu {

    private final Material target;

    public RecipeMenu(Material target) {
        this(null, target);
    }

    public RecipeMenu(Player player, Material target) {
        super(null, player, 45, "<dark_gray>Recipe - " + Chat.formatMaterial(target) + "</dark_gray>");
        this.target = target;
    }

    @Override
    public void build() {
        fill(Chat.FILLER_PANE_GRAY);

        ItemStack result = new ItemStack(target);
        inventory.setItem(24, result);

        List<Recipe> recipes = Bukkit.getRecipesFor(result);
        if (!recipes.isEmpty()) {
            Recipe r = recipes.get(0);
            if (r instanceof ShapedRecipe shaped) {
                int[][] gridSlots = {
                    {10, 11, 12},
                    {19, 20, 21},
                    {28, 29, 30}
                };
                String[] shape = shaped.getShape();
                for (int row = 0; row < shape.length && row < 3; row++) {
                    char[] rowChars = shape[row].toCharArray();
                    for (int col = 0; col < rowChars.length && col < 3; col++) {
                        RecipeChoice choice = shaped.getChoiceMap().get(rowChars[col]);
                        inventory.setItem(gridSlots[row][col], getDisplayItem(choice));
                    }
                }
                return;
            } else if (r instanceof ShapelessRecipe shapeless) {
                int[] gridSlots = {10, 11, 12, 19, 20, 21, 28, 29, 30};
                int slotIdx = 0;
                for (RecipeChoice choice : shapeless.getChoiceList()) {
                    if (slotIdx < gridSlots.length) {
                        inventory.setItem(gridSlots[slotIdx++], getDisplayItem(choice));
                    }
                }
                return;
            } else if (r instanceof CookingRecipe<?> cooking) {
                inventory.setItem(11, getDisplayItem(cooking.getInputChoice()));
                inventory.setItem(20, new ItemStack(Material.CAMPFIRE));
                inventory.setItem(29, new ItemStack(Material.COAL));
                return;
            }
        }

        inventory.setItem(20, ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name("<yellow>Uncraftable Item</yellow>")
                .lore("<gray>Cannot be crafted directly.</gray>",
                      "<dark_gray>Find via mining, exploration, or looting.</dark_gray>")
                .build());
    }

    @SuppressWarnings("deprecation")
    private ItemStack getDisplayItem(RecipeChoice choice) {
        if (choice == null) {
            return new ItemStack(Material.AIR);
        }
        if (choice instanceof RecipeChoice.MaterialChoice matChoice) {
            List<Material> choices = matChoice.getChoices();
            if (!choices.isEmpty()) return new ItemStack(choices.get(0));
        } else if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            List<ItemStack> choices = exactChoice.getChoices();
            if (!choices.isEmpty()) return choices.get(0).clone();
        }
        return choice.getItemStack();
    }

    public void open(Player player) {
        if (player != null && player.isOnline()) {
            build();
            player.openInventory(inventory);
        }
    }
}
