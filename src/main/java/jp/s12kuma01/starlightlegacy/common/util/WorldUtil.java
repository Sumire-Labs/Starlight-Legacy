package jp.s12kuma01.starlightlegacy.common.util;

import net.minecraft.world.World;

public final class WorldUtil {

    // 1.12.2 has fixed world height: 0-255 (16 sections, indices 0-15)

    public static int getMaxSection(final World world) {
        return 15;
    }

    public static int getMinSection(final World world) {
        return 0;
    }

    public static int getMaxLightSection(final World world) {
        return getMaxSection(world) + 1;
    }

    public static int getMinLightSection(final World world) {
        return getMinSection(world) - 1;
    }

    public static int getTotalSections(final World world) {
        return getMaxSection(world) - getMinSection(world) + 1;
    }

    public static int getTotalLightSections(final World world) {
        return getMaxLightSection(world) - getMinLightSection(world) + 1;
    }

    public static int getMinBlockY(final World world) {
        return getMinSection(world) << 4;
    }

    public static int getMaxBlockY(final World world) {
        return (getMaxSection(world) << 4) | 15;
    }

    private WorldUtil() {
        throw new RuntimeException();
    }
}
