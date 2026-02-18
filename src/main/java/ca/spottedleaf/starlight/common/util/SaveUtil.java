package ca.spottedleaf.starlight.common.util;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SaveUtil {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final int STARLIGHT_LIGHT_VERSION = 9;

    public static int getLightVersion() {
        return STARLIGHT_LIGHT_VERSION;
    }

    private static final String BLOCKLIGHT_STATE_TAG = "starlight.blocklight_state";
    private static final String SKYLIGHT_STATE_TAG = "starlight.skylight_state";
    private static final String STARLIGHT_VERSION_TAG = "starlight.light_version";

    public static void saveLightHook(final World world, final Chunk chunk, final NBTTagCompound nbt) {
        try {
            saveLightHookReal(world, chunk, nbt);
        } catch (final Throwable ex) {
            if (ex instanceof ThreadDeath) {
                throw (ThreadDeath)ex;
            }
            LOGGER.warn("Failed to inject light data into save data for chunk " + chunk.x + "," + chunk.z + ", chunk light will be recalculated on its next load", ex);
        }
    }

    private static void saveLightHookReal(final World world, final Chunk chunk, final NBTTagCompound tag) {
        if (tag == null) {
            return;
        }

        final int minSection = WorldUtil.getMinLightSection(world);
        final int maxSection = WorldUtil.getMaxLightSection(world);

        SWMRNibbleArray[] blockNibbles = ((ExtendedChunk)chunk).getBlockNibbles();
        SWMRNibbleArray[] skyNibbles = ((ExtendedChunk)chunk).getSkyNibbles();

        // Check if this chunk has been lit by Starlight by inspecting nibble state.
        // Any non-null nibble indicates Starlight has computed light for this chunk.
        boolean lit = false;
        for (final SWMRNibbleArray nib : blockNibbles) {
            if (nib != null && !nib.isNullNibbleVisible()) {
                lit = true;
                break;
            }
        }
        if (!lit) {
            for (final SWMRNibbleArray nib : skyNibbles) {
                if (nib != null && !nib.isNullNibbleVisible()) {
                    lit = true;
                    break;
                }
            }
        }

        // Get or create the Level compound
        NBTTagCompound level = tag.getCompoundTag("Level");

        // Store starlight version
        if (lit) {
            level.setInteger(STARLIGHT_VERSION_TAG, STARLIGHT_LIGHT_VERSION);
        }

        // Save light data into section tags
        NBTTagList sections = level.getTagList("Sections", 10);

        // Build a map of existing section tags by Y
        NBTTagCompound[] sectionsByY = new NBTTagCompound[maxSection - minSection + 1];
        for (int i = 0; i < sections.tagCount(); ++i) {
            NBTTagCompound sectionTag = sections.getCompoundTagAt(i);
            int y = sectionTag.getByte("Y");
            if (y >= minSection && y <= maxSection) {
                sectionsByY[y - minSection] = sectionTag;
            }
        }

        if (lit) {
            for (int i = minSection; i <= maxSection; ++i) {
                SWMRNibbleArray.SaveState blockNibble = blockNibbles[i - minSection].getSaveState();
                SWMRNibbleArray.SaveState skyNibble = skyNibbles[i - minSection].getSaveState();
                if (blockNibble != null || skyNibble != null) {
                    NBTTagCompound section = sectionsByY[i - minSection];
                    if (section == null) {
                        section = new NBTTagCompound();
                        section.setByte("Y", (byte)i);
                        sections.appendTag(section);
                        sectionsByY[i - minSection] = section;
                    }

                    if (blockNibble != null) {
                        if (blockNibble.data != null) {
                            section.setByteArray("BlockLight", blockNibble.data);
                        }
                        section.setInteger(BLOCKLIGHT_STATE_TAG, blockNibble.state);
                    }

                    if (skyNibble != null) {
                        if (skyNibble.data != null) {
                            section.setByteArray("SkyLight", skyNibble.data);
                        }
                        section.setInteger(SKYLIGHT_STATE_TAG, skyNibble.state);
                    }
                }
            }
        }
    }

    public static void loadLightHook(final World world, final NBTTagCompound tag, final Chunk into) {
        try {
            loadLightHookReal(world, tag, into);
        } catch (final Throwable ex) {
            if (ex instanceof ThreadDeath) {
                throw (ThreadDeath)ex;
            }
            LOGGER.warn("Failed to load light for chunk " + into.x + "," + into.z + ", light will be recalculated", ex);
        }
    }

    private static void loadLightHookReal(final World world, final NBTTagCompound tag, final Chunk into) {
        if (into == null) {
            return;
        }
        final int minSection = WorldUtil.getMinLightSection(world);
        final int maxSection = WorldUtil.getMaxLightSection(world);

        SWMRNibbleArray[] blockNibbles = StarLightEngine.getFilledEmptyLight(world);
        SWMRNibbleArray[] skyNibbles = StarLightEngine.getFilledEmptyLight(world);

        NBTTagCompound level = tag.getCompoundTag("Level");

        boolean lit = level.hasKey(STARLIGHT_VERSION_TAG) && level.getInteger(STARLIGHT_VERSION_TAG) == STARLIGHT_LIGHT_VERSION;
        boolean canReadSky = world.provider.hasSkyLight();

        if (lit) {
            NBTTagList sections = level.getTagList("Sections", 10);

            for (int i = 0; i < sections.tagCount(); ++i) {
                NBTTagCompound sectionData = sections.getCompoundTagAt(i);
                int y = sectionData.getByte("Y");

                if (y < minSection || y > maxSection) {
                    continue;
                }

                if (sectionData.hasKey("BlockLight", 7)) {
                    blockNibbles[y - minSection] = new SWMRNibbleArray(sectionData.getByteArray("BlockLight").clone(), sectionData.getInteger(BLOCKLIGHT_STATE_TAG));
                } else if (sectionData.hasKey(BLOCKLIGHT_STATE_TAG)) {
                    blockNibbles[y - minSection] = new SWMRNibbleArray(null, sectionData.getInteger(BLOCKLIGHT_STATE_TAG));
                }

                if (canReadSky) {
                    if (sectionData.hasKey("SkyLight", 7)) {
                        skyNibbles[y - minSection] = new SWMRNibbleArray(sectionData.getByteArray("SkyLight").clone(), sectionData.getInteger(SKYLIGHT_STATE_TAG));
                    } else if (sectionData.hasKey(SKYLIGHT_STATE_TAG)) {
                        skyNibbles[y - minSection] = new SWMRNibbleArray(null, sectionData.getInteger(SKYLIGHT_STATE_TAG));
                    }
                }
            }
        }

        ((ExtendedChunk)into).setBlockNibbles(blockNibbles);
        ((ExtendedChunk)into).setSkyNibbles(skyNibbles);
    }

    private SaveUtil() {}
}
