package ca.spottedleaf.starlight.common.util;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Compatibility layer for the Dynamic Lights mod.
 * Uses reflection to avoid hard dependency on Dynamic Lights at compile time.
 */
public final class LightUtil {

    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean dynamicLightsLoaded;
    private static Method dynamicLightsGetLightValue;

    public static void init() {
        try {
            if (Loader.isModLoaded("dynamiclights")) {
                final Class<?> dlClass = Class.forName("atomicstryker.dynamiclights.client.DynamicLights");
                dynamicLightsGetLightValue = dlClass.getMethod("getLightValue",
                        Block.class, IBlockState.class, IBlockAccess.class, BlockPos.class);
                dynamicLightsLoaded = true;
                LOGGER.info("Starlight: Dynamic Lights detected, enabling compatibility.");
            }
        } catch (final Exception e) {
            LOGGER.warn("Starlight: Failed to initialize Dynamic Lights compatibility.", e);
            dynamicLightsLoaded = false;
        }
    }

    public static boolean isDynamicLightsLoaded() {
        return dynamicLightsLoaded;
    }

    public static int getLightValueForState(final IBlockState blockState, final IBlockAccess blockAccess, final BlockPos blockPos) {
        if (dynamicLightsLoaded && dynamicLightsGetLightValue != null) {
            try {
                return (Integer) dynamicLightsGetLightValue.invoke(null,
                        blockState.getBlock(), blockState, blockAccess, blockPos);
            } catch (final Exception e) {
                // Fall through to vanilla
            }
        }
        return blockState.getLightValue(blockAccess, blockPos);
    }

    private LightUtil() {}
}
