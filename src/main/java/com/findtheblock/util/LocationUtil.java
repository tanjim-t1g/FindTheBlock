package com.findtheblock.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class LocationUtil {

    private LocationUtil() {}

    public static Location findSafeSurfaceSpawn(World world, Location origin) {
        if (world == null) return origin;

        int startX = origin != null ? origin.getBlockX() : 0;
        int startZ = origin != null ? origin.getBlockZ() : 0;

        for (int r = 0; r <= 32; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    int x = startX + dx;
                    int z = startZ + dz;

                    int highestY = world.getHighestBlockYAt(x, z);
                    if (highestY <= world.getMinHeight() + 5) continue;

                    Block ground = world.getBlockAt(x, highestY, z);
                    Block feet = world.getBlockAt(x, highestY + 1, z);
                    Block head = world.getBlockAt(x, highestY + 2, z);

                    if (isSafeGround(ground.getType()) && feet.isEmpty() && head.isEmpty()) {
                        return ground.getLocation().add(0.5, 1.0, 0.5);
                    }
                }
            }
        }

        int fallbackY = world.getHighestBlockYAt(startX, startZ);
        return new Location(world, startX + 0.5, Math.max(64, fallbackY + 1), startZ + 0.5);
    }

    private static boolean isSafeGround(Material mat) {
        if (!mat.isSolid()) return false;
        return mat != Material.LAVA &&
               mat != Material.WATER &&
               mat != Material.FIRE &&
               mat != Material.SOUL_FIRE &&
               mat != Material.MAGMA_BLOCK &&
               mat != Material.CACTUS &&
               mat != Material.SWEET_BERRY_BUSH;
    }
}
