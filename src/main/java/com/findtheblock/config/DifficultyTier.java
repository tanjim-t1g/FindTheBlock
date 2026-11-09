package com.findtheblock.config;

public enum DifficultyTier {
    EASY("<green>Easy</green>", "<gray>Surface and basic farming/crafting items.</gray>"),
    NORMAL("<yellow>Normal</yellow>", "<gray>Cave mining and Nether exploration items.</gray>"),
    HARD("<red>Hard</red>", "<gray>Rare fortress and deep underground challenge items.</gray>");

    private final String formattedName;
    private final String description;

    DifficultyTier(String formattedName, String description) {
        this.formattedName = formattedName;
        this.description = description;
    }

    public String getFormattedName() {
        return formattedName;
    }

    public String getDescription() {
        return description;
    }

    public static DifficultyTier fromString(String input) {
        if (input == null) return NORMAL;
        String s = input.trim().toUpperCase();
        if (s.equals("MEDIUM")) return NORMAL;
        try {
            return DifficultyTier.valueOf(s);
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
