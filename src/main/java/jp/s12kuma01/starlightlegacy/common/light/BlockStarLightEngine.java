package jp.s12kuma01.starlightlegacy.common.light;

import jp.s12kuma01.starlightlegacy.common.blockstate.ExtendedAbstractBlockState;
import jp.s12kuma01.starlightlegacy.common.chunk.ExtendedChunk;
import jp.s12kuma01.starlightlegacy.common.chunk.ExtendedChunkSection;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import java.util.Set;

public final class BlockStarLightEngine extends StarLightEngine {

    public BlockStarLightEngine(final World world) {
        super(false, world);
    }

    @Override
    protected boolean[] getEmptinessMap(final Chunk chunk) {
        return ((ExtendedChunk)chunk).getBlockEmptinessMap();
    }

    @Override
    protected void setEmptinessMap(final Chunk chunk, final boolean[] to) {
        ((ExtendedChunk)chunk).setBlockEmptinessMap(to);
    }

    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(final Chunk chunk) {
        return ((ExtendedChunk)chunk).getBlockNibbles();
    }

    @Override
    protected void setNibbles(final Chunk chunk, final SWMRNibbleArray[] to) {
        ((ExtendedChunk)chunk).setBlockNibbles(to);
    }

    @Override
    protected boolean canUseChunk(final Chunk chunk) {
        // In 1.12.2 there is no ChunkStatus; use our own lit flag
        return ((ExtendedChunk)chunk).isStarlightLit();
    }

    @Override
    protected void setNibbleNull(final int chunkX, final int chunkY, final int chunkZ) {
        final SWMRNibbleArray nibble = this.getNibbleFromCache(chunkX, chunkY, chunkZ);
        if (nibble != null) {
            // de-initialisation is not as straightforward as with sky data, since deinit of block light is typically
            // because a block was removed - which can decrease light. with sky data, block breaking can only result
            // in increases, and thus the existing sky block check will actually correctly propagate light through
            // a null section. so in order to propagate decreases correctly, we can do a couple of things: not remove
            // the data section, or do edge checks on ALL axis (x, y, z). however I do not want edge checks running
            // for clients at all, as they are expensive. so we don't remove the section, but to maintain the appearence
            // of vanilla data management we "hide" them.
            nibble.setHidden();
        }
    }

    @Override
    protected void initNibble(final int chunkX, final int chunkY, final int chunkZ, final boolean extrude, final boolean initRemovedNibbles) {
        if (chunkY < this.minLightSection || chunkY > this.maxLightSection || this.getChunkInCache(chunkX, chunkZ) == null) {
            return;
        }

        final SWMRNibbleArray nibble = this.getNibbleFromCache(chunkX, chunkY, chunkZ);
        if (nibble == null) {
            if (!initRemovedNibbles) {
                throw new IllegalStateException();
            } else {
                this.setNibbleInCache(chunkX, chunkY, chunkZ, new SWMRNibbleArray());
            }
        } else {
            nibble.setNonNull();
        }
    }

    @Override
    protected final void checkBlock(final LightChunkGetter lightAccess, final int worldX, final int worldY, final int worldZ) {
        // blocks can change opacity
        // blocks can change emitted light
        // blocks can change direction of propagation

        final int encodeOffset = this.coordinateOffset;
        final int emittedMask = this.emittedLightMask;

        final VariableBlockLightHandler customBlockHandler = ((ExtendedWorld)lightAccess.getWorld()).getCustomLightHandler();
        final int currentLevel = this.getLightLevel(worldX, worldY, worldZ);
        final IBlockState blockState = this.getBlockState(worldX, worldY, worldZ);
        final int emittedLevel = (customBlockHandler != null ? this.getCustomLightLevel(customBlockHandler, worldX, worldY, worldZ, blockState.getLightValue()) : blockState.getLightValue()) & emittedMask;

        this.setLightLevel(worldX, worldY, worldZ, emittedLevel);
        // this accounts for change in emitted light that would cause an increase
        if (emittedLevel != 0) {
            this.appendToIncreaseQueue(
                    ((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                            | (emittedLevel & 0xFL) << (6 + 6 + 16)
                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                            | (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0)
            );
        }
        // this also accounts for a change in emitted light that would cause a decrease
        // this also accounts for the change of direction of propagation (i.e old block was full transparent, new block is full opaque or vice versa)
        // as it checks all neighbours (even if current level is 0)
        this.appendToDecreaseQueue(
                ((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                        | (currentLevel & 0xFL) << (6 + 6 + 16)
                        | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                        // always keep sided transparent false here, new block might be conditionally transparent which would
                        // prevent us from decreasing sources in the directions where the new block is opaque
                        // if it turns out we were wrong to de-propagate the source, the re-propagate logic WILL always
                        // catch that and fix it.
        );
        // re-propagating neighbours (done by the decrease queue) will also account for opacity changes in this block
    }

    protected final BlockPos.MutableBlockPos recalcCenterPos = new BlockPos.MutableBlockPos();
    protected final BlockPos.MutableBlockPos recalcNeighbourPos = new BlockPos.MutableBlockPos();

    @Override
    protected int calculateLightValue(final LightChunkGetter lightAccess, final int worldX, final int worldY, final int worldZ,
                                      final int expect, final VariableBlockLightHandler customBlockLight) {
        final IBlockState centerState = this.getBlockState(worldX, worldY, worldZ);
        int level = centerState.getLightValue() & 0xF;
        if (customBlockLight != null) {
            level = this.getCustomLightLevel(customBlockLight, worldX, worldY, worldZ, level);
        }

        if (level >= (15 - 1) || level > expect) {
            return level;
        }

        final int sectionOffset = this.chunkSectionIndexOffset;
        final IBlockState conditionallyOpaqueState;
        int opacity = ((ExtendedAbstractBlockState)centerState).getOpacityIfCached();

        if (opacity == -1) {
            this.recalcCenterPos.setPos(worldX, worldY, worldZ);
            opacity = centerState.getBlock().getLightOpacity(centerState, lightAccess.getWorld(), this.recalcCenterPos);
            if (((ExtendedAbstractBlockState)centerState).isConditionallyFullOpaque()) {
                conditionallyOpaqueState = centerState;
            } else {
                conditionallyOpaqueState = null;
            }
        } else if (opacity >= 15) {
            return level;
        } else {
            conditionallyOpaqueState = null;
        }
        opacity = Math.max(1, opacity);

        for (final AxisDirection direction : AXIS_DIRECTIONS) {
            final int offX = worldX + direction.x;
            final int offY = worldY + direction.y;
            final int offZ = worldZ + direction.z;

            final int sectionIndex = (offX >> 4) + 5 * (offZ >> 4) + (5 * 5) * (offY >> 4) + sectionOffset;

            final int neighbourLevel = this.getLightLevel(sectionIndex, (offX & 15) | ((offZ & 15) << 4) | ((offY & 15) << 8));

            if ((neighbourLevel - 1) <= level) {
                // don't need to test transparency, we know it wont affect the result.
                continue;
            }

            final long neighbourOpacity = this.getKnownTransparency(sectionIndex, (offY & 15) | ((offX & 15) << 4) | ((offZ & 15) << 8));

            if (neighbourOpacity == ExtendedChunkSection.BLOCK_SPECIAL_TRANSPARENCY) {
                // here the block can be conditionally opaque (i.e light cannot propagate from it), so we need to test that
                // we don't read the blockstate because most of the time this is false, so using the faster
                // known transparency lookup results in a net win
                final IBlockState neighbourState = this.getBlockState(offX, offY, offZ);
                this.recalcNeighbourPos.setPos(offX, offY, offZ);
                // In 1.12.2, doesSideBlockRendering replaces VoxelShape face occlusion checks.
                // Check if the neighbour blocks rendering on the face towards us
                final boolean neighbourBlocks = neighbourState.doesSideBlockRendering(lightAccess.getWorld(), this.recalcNeighbourPos, direction.opposite.nms);
                final boolean thisBlocks = conditionallyOpaqueState != null && conditionallyOpaqueState.doesSideBlockRendering(lightAccess.getWorld(), this.recalcCenterPos, direction.nms);
                if (neighbourBlocks || thisBlocks) {
                    // not allowed to propagate
                    continue;
                }
            }

            // passed transparency,

            final int calculated = neighbourLevel - opacity;
            level = Math.max(calculated, level);
            if (level > expect) {
                return level;
            }
        }

        return level;
    }

    @Override
    protected void propagateBlockChanges(final LightChunkGetter lightAccess, final Chunk atChunk, final Set<BlockPos> positions) {
        for (final BlockPos pos : positions) {
            this.checkBlock(lightAccess, pos.getX(), pos.getY(), pos.getZ());
        }

        this.performLightDecrease(lightAccess);
    }

    @Override
    public void lightChunk(final LightChunkGetter lightAccess, final Chunk chunk, final boolean needsEdgeChecks) {
        // setup sources - inlined from getSources to avoid ArrayList/BlockPos allocation
        final int emittedMask = this.emittedLightMask;
        final VariableBlockLightHandler customBlockHandler = ((ExtendedWorld)lightAccess.getWorld()).getCustomLightHandler();

        final int offX = chunk.x << 4;
        final int offZ = chunk.z << 4;

        final ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (int sectionY = this.minSection; sectionY <= this.maxSection; ++sectionY) {
            final ExtendedBlockStorage section = sections[sectionY - this.minSection];
            if (section == null || section.isEmpty()) {
                // no sources in empty sections
                continue;
            }
            final int sectionOffY = sectionY << 4;

            for (int index = 0; index < (16 * 16 * 16); ++index) {
                // index = x | (z << 4) | (y << 8)
                final int localX = index & 15;
                final int localZ = (index >>> 4) & 15;
                final int localY = index >>> 8;

                final IBlockState state = section.getData().get(localX, localY, localZ);
                if (state.getLightValue() <= 0) {
                    continue;
                }

                final int worldX = offX | localX;
                final int worldY = sectionOffY | localY;
                final int worldZ = offZ | localZ;

                final int emittedLight = (customBlockHandler != null ? this.getCustomLightLevel(customBlockHandler, worldX, worldY, worldZ, state.getLightValue()) : state.getLightValue()) & emittedMask;

                if (emittedLight <= this.getLightLevel(worldX, worldY, worldZ)) {
                    // some other source is brighter
                    continue;
                }

                this.appendToIncreaseQueue(
                        ((worldX + (worldZ << 6) + (worldY << (6 + 6)) + this.coordinateOffset) & ((1L << (6 + 6 + 16)) - 1))
                                | (emittedLight & 0xFL) << (6 + 6 + 16)
                                | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                | (((ExtendedAbstractBlockState)state).isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0)
                );

                // propagation wont set this for us
                this.setLightLevel(worldX, worldY, worldZ, emittedLight);
            }
        }

        // handle custom light handler sources
        if (customBlockHandler != null) {
            for (final BlockPos pos : customBlockHandler.getCustomLightPositions(chunk.x, chunk.z)) {
                final IBlockState blockState = this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
                if (blockState == null) {
                    continue;
                }
                final int emittedLight = this.getCustomLightLevel(customBlockHandler, pos.getX(), pos.getY(), pos.getZ(), blockState.getLightValue()) & emittedMask;

                if (emittedLight <= this.getLightLevel(pos.getX(), pos.getY(), pos.getZ())) {
                    continue;
                }

                this.appendToIncreaseQueue(
                        ((pos.getX() + (pos.getZ() << 6) + (pos.getY() << (6 + 6)) + this.coordinateOffset) & ((1L << (6 + 6 + 16)) - 1))
                                | (emittedLight & 0xFL) << (6 + 6 + 16)
                                | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                | (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0)
                );

                this.setLightLevel(pos.getX(), pos.getY(), pos.getZ(), emittedLight);
            }
        }

        if (needsEdgeChecks) {
            // not required to propagate here, but this will reduce the hit of the edge checks
            this.performLightIncrease(lightAccess);

            // verify neighbour edges
            this.checkChunkEdges(lightAccess, chunk, this.minLightSection, this.maxLightSection);
        } else {
            this.propagateNeighbourLevels(lightAccess, chunk, this.minLightSection, this.maxLightSection);

            this.performLightIncrease(lightAccess);
        }
    }
}
