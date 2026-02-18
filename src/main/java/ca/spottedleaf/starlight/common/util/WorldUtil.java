package ca.spottedleaf.starlight.common.util;

import net.minecraft.world.World;

public final class WorldUtil {

    // 1.12.2: fixed height Y=0..255, 16 sections (0..15)
    // light sections include one above and one below: -1..16 (18 total)

    public static int getMinSection(final World world) {
        return 0;
    }

    public static int getMaxSection(final World world) {
        return 15;
    }

    public static int getMinLightSection(final World world) {
        return -1;
    }

    public static int getMaxLightSection(final World world) {
        return 16;
    }

    public static int getTotalSections(final World world) {
        return 16;
    }

    public static int getTotalLightSections(final World world) {
        return 18;
    }

    public static int getMinBlockY(final World world) {
        return 0;
    }

    public static int getMaxBlockY(final World world) {
        return 255;
    }

    private WorldUtil() {
        throw new RuntimeException();
    }
}
