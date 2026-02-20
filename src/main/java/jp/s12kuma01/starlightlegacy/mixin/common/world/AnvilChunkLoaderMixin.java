package jp.s12kuma01.starlightlegacy.mixin.common.world;

import jp.s12kuma01.starlightlegacy.common.chunk.ExtendedChunk;
import jp.s12kuma01.starlightlegacy.common.light.SWMRNibbleArray;
import jp.s12kuma01.starlightlegacy.common.light.StarLightEngine;
import jp.s12kuma01.starlightlegacy.common.light.StarLightInterface;
import jp.s12kuma01.starlightlegacy.common.util.WorldUtil;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedWorld;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilChunkLoader.class)
public abstract class AnvilChunkLoaderMixin {

    @Unique
    private static final Logger STARLIGHT_LOGGER = LogManager.getLogger("StarlightLegacy");

    @Unique
    private static final int STARLIGHT_LIGHT_VERSION = 5;

    @Unique
    private static final String BLOCKLIGHT_STATE_TAG = "starlightlegacy.blocklight_state";
    @Unique
    private static final String SKYLIGHT_STATE_TAG = "starlightlegacy.skylight_state";
    @Unique
    private static final String STARLIGHT_VERSION_TAG = "starlightlegacy.light_version";

    /**
     * Force-process all pending light updates before saving.
     */
    @Inject(method = "saveChunk", at = @At("HEAD"))
    private void onSaveChunk(final World world, final Chunk chunk, final CallbackInfo ci) {
        final StarLightInterface lightEngine = ((ExtendedWorld)world).getLightEngine();
        if (lightEngine != null) {
            lightEngine.propagateChanges();
        }
    }

    /**
     * Inject starlight data on save.
     */
    @Inject(method = "writeChunkToNBT", at = @At("RETURN"))
    private void onWriteChunkToNBT(final Chunk chunk, final World world, final NBTTagCompound compound, final CallbackInfo ci) {
        try {
            starlight$saveLightData(chunk, world, compound);
        } catch (final Exception ex) {
            STARLIGHT_LOGGER.warn("Failed to inject starlight data into save for chunk (" + chunk.x + ", " + chunk.z + "), light will be recalculated on next load", ex);
        }
    }

    /**
     * Load starlight data on chunk read.
     */
    @Inject(method = "readChunkFromNBT", at = @At("RETURN"))
    private void onReadChunkFromNBT(final World world, final NBTTagCompound compound, final CallbackInfoReturnable<Chunk> cir) {
        try {
            final Chunk chunk = cir.getReturnValue();
            if (chunk != null) {
                starlight$loadLightData(chunk, world, compound);
            }
        } catch (final Exception ex) {
            STARLIGHT_LOGGER.warn("Failed to load starlight data for chunk, light will be recalculated", ex);
        }
    }

    @Unique
    private static void starlight$saveLightData(final Chunk chunk, final World world, final NBTTagCompound compound) {
        final int minSection = WorldUtil.getMinLightSection(world);
        final int maxSection = WorldUtil.getMaxLightSection(world);

        final SWMRNibbleArray[] blockNibbles = ((ExtendedChunk)chunk).getBlockNibbles();
        final SWMRNibbleArray[] skyNibbles = ((ExtendedChunk)chunk).getSkyNibbles();

        if (blockNibbles == null || skyNibbles == null) {
            return;
        }

        final boolean lit = ((ExtendedChunk)chunk).isStarlightLit();

        // Read existing "Level" tag from compound
        final NBTTagCompound level = compound.getCompoundTag("Level");

        // Mark vanilla light as not populated to force re-light if vanilla loads this chunk
        if (lit) {
            level.setBoolean("LightPopulated", false);
        }

        final NBTTagList sectionsTag = level.getTagList("Sections", 10);

        // Build index of existing section tags
        final NBTTagCompound[] sections = new NBTTagCompound[maxSection - minSection + 1];
        for (int i = 0; i < sectionsTag.tagCount(); ++i) {
            final NBTTagCompound sectionTag = sectionsTag.getCompoundTagAt(i);
            final int y = sectionTag.getByte("Y");

            // Strip vanilla light data
            sectionTag.removeTag("BlockLight");
            sectionTag.removeTag("SkyLight");

            if (y >= minSection && y <= maxSection) {
                sections[y - minSection] = sectionTag;
            }
        }

        if (lit) {
            final boolean hasSkyLight = !world.provider.isNether();

            for (int i = minSection; i <= maxSection; ++i) {
                final SWMRNibbleArray.SaveState blockNibble = blockNibbles[i - minSection].getSaveState();
                final SWMRNibbleArray.SaveState skyNibble = hasSkyLight ? skyNibbles[i - minSection].getSaveState() : null;

                if (blockNibble != null || skyNibble != null) {
                    NBTTagCompound section = sections[i - minSection];
                    if (section == null) {
                        section = new NBTTagCompound();
                        section.setByte("Y", (byte)i);
                        sections[i - minSection] = section;
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

        // Rewrite section list
        final NBTTagList newSections = new NBTTagList();
        for (final NBTTagCompound section : sections) {
            if (section != null) {
                newSections.appendTag(section);
            }
        }
        level.setTag("Sections", newSections);
        if (lit) {
            level.setInteger(STARLIGHT_VERSION_TAG, STARLIGHT_LIGHT_VERSION);
        }
    }

    @Unique
    private static void starlight$loadLightData(final Chunk chunk, final World world, final NBTTagCompound compound) {
        final int minSection = WorldUtil.getMinLightSection(world);
        final int maxSection = WorldUtil.getMaxLightSection(world);

        final SWMRNibbleArray[] blockNibbles = StarLightEngine.getFilledEmptyLight(world);
        final SWMRNibbleArray[] skyNibbles = StarLightEngine.getFilledEmptyLight(world);

        final NBTTagCompound level = compound.getCompoundTag("Level");
        final boolean lit = level.hasKey(STARLIGHT_VERSION_TAG) && level.getInteger(STARLIGHT_VERSION_TAG) == STARLIGHT_LIGHT_VERSION;
        final boolean hasSkyLight = !world.provider.isNether();

        if (lit) {
            final NBTTagList sections = level.getTagList("Sections", 10);

            for (int i = 0; i < sections.tagCount(); ++i) {
                final NBTTagCompound sectionData = sections.getCompoundTagAt(i);
                final int y = sectionData.getByte("Y");

                if (y < minSection || y > maxSection) {
                    continue;
                }

                if (sectionData.hasKey("BlockLight", 7)) {
                    blockNibbles[y - minSection] = new SWMRNibbleArray(sectionData.getByteArray("BlockLight").clone(),
                            sectionData.getInteger(BLOCKLIGHT_STATE_TAG));
                } else if (sectionData.hasKey(BLOCKLIGHT_STATE_TAG)) {
                    blockNibbles[y - minSection] = new SWMRNibbleArray(null, sectionData.getInteger(BLOCKLIGHT_STATE_TAG));
                }

                if (hasSkyLight) {
                    if (sectionData.hasKey("SkyLight", 7)) {
                        skyNibbles[y - minSection] = new SWMRNibbleArray(sectionData.getByteArray("SkyLight").clone(),
                                sectionData.getInteger(SKYLIGHT_STATE_TAG));
                    } else if (sectionData.hasKey(SKYLIGHT_STATE_TAG)) {
                        skyNibbles[y - minSection] = new SWMRNibbleArray(null, sectionData.getInteger(SKYLIGHT_STATE_TAG));
                    }
                }
            }
        }

        ((ExtendedChunk)chunk).setBlockNibbles(blockNibbles);
        ((ExtendedChunk)chunk).setSkyNibbles(skyNibbles);
        ((ExtendedChunk)chunk).setStarlightLit(lit);
    }
}
