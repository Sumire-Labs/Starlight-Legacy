package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import ca.spottedleaf.starlight.common.util.WorldUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Chunk.class)
public abstract class ChunkMixin implements ExtendedChunk {

    @Shadow @Final public World world;
    @Shadow @Final public int x;
    @Shadow @Final public int z;
    @Shadow public abstract ExtendedBlockStorage[] getBlockStorageArray();
    @Shadow public abstract int getTopFilledSegment();
    @Shadow private int[] heightMap;
    @Shadow private int heightMapMinimum;
    @Shadow private int[] precipitationHeightMap;
    @Shadow private boolean dirty;

    @Unique
    private volatile SWMRNibbleArray[] blockNibbles;

    @Unique
    private volatile SWMRNibbleArray[] skyNibbles;

    @Unique
    private volatile boolean[] skyEmptinessMap;

    @Unique
    private volatile boolean[] blockEmptinessMap;

    /**
     * Tracks whether this chunk's lighting has been initialized (lightChunk/loadInChunk completed).
     * Before initialization, blockChange() calls are skipped since lightChunk() will compute
     * full lighting anyway. This avoids queuing thousands of redundant light tasks during
     * chunk generation (populate phase runs after onLoad, so those changes ARE processed).
     */
    @Unique
    private volatile boolean starlight$lightInitialized = false;

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return this.blockNibbles;
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        this.blockNibbles = nibbles;
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return this.skyNibbles;
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        this.skyNibbles = nibbles;
    }

    @Override
    public boolean[] getSkyEmptinessMap() {
        return this.skyEmptinessMap;
    }

    @Override
    public void setSkyEmptinessMap(final boolean[] emptinessMap) {
        this.skyEmptinessMap = emptinessMap;
    }

    @Override
    public boolean[] getBlockEmptinessMap() {
        return this.blockEmptinessMap;
    }

    @Override
    public void setBlockEmptinessMap(final boolean[] emptinessMap) {
        this.blockEmptinessMap = emptinessMap;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void onConstruct(World world, int x, int z, CallbackInfo ci) {
        this.blockNibbles = StarLightEngine.getFilledEmptyLight(world);
        this.skyNibbles = StarLightEngine.getFilledEmptyLight(world);
    }

    /**
     * @author Starlight
     * @reason Replace light retrieval with Starlight's SWMR nibble arrays
     */
    @Overwrite
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        final int x = pos.getX() & 15;
        final int y = pos.getY();
        final int z = pos.getZ() & 15;
        final int sectionY = y >> 4;

        if (sectionY < WorldUtil.getMinLightSection(this.world) || sectionY > WorldUtil.getMaxLightSection(this.world)) {
            return type == EnumSkyBlock.SKY ? 15 : 0;
        }

        final int index = sectionY - WorldUtil.getMinLightSection(this.world);

        if (type == EnumSkyBlock.SKY) {
            final SWMRNibbleArray[] nibbles = this.skyNibbles;
            if (nibbles == null || index < 0 || index >= nibbles.length) return 15;
            final SWMRNibbleArray nibble = nibbles[index];
            // NULL or UNINIT nibbles indicate empty/unprocessed sections → full sky light
            if (nibble == null || nibble.isNullNibbleVisible() || nibble.isUninitialisedVisible()) return 15;
            return nibble.getVisible(x, y, z);
        } else {
            final SWMRNibbleArray[] nibbles = this.blockNibbles;
            if (nibbles == null || index < 0 || index >= nibbles.length) return 0;
            final SWMRNibbleArray nibble = nibbles[index];
            if (nibble == null) return 0;
            return nibble.getVisible(x, y, z);
        }
    }

    /**
     * @author Starlight
     * @reason Replace light setting with Starlight's SWMR nibble arrays
     */
    @Overwrite
    public void setLightFor(EnumSkyBlock type, BlockPos pos, int value) {
        final int x = pos.getX() & 15;
        final int y = pos.getY();
        final int z = pos.getZ() & 15;
        final int sectionY = y >> 4;

        if (sectionY < WorldUtil.getMinLightSection(this.world) || sectionY > WorldUtil.getMaxLightSection(this.world)) {
            return;
        }

        final int index = sectionY - WorldUtil.getMinLightSection(this.world);

        if (type == EnumSkyBlock.SKY) {
            final SWMRNibbleArray[] nibbles = this.skyNibbles;
            if (nibbles == null || index < 0 || index >= nibbles.length) return;
            final SWMRNibbleArray nibble = nibbles[index];
            if (nibble != null) {
                nibble.set(x, y, z, value);
            }
        } else {
            final SWMRNibbleArray[] nibbles = this.blockNibbles;
            if (nibbles == null || index < 0 || index >= nibbles.length) return;
            final SWMRNibbleArray nibble = nibbles[index];
            if (nibble != null) {
                nibble.set(x, y, z, value);
            }
        }
    }

    /**
     * @author Starlight
     * @reason Route getLightSubtracted through SWMR nibbles instead of vanilla NibbleArrays.
     *         This ensures server-side gameplay mechanics (mob spawning, etc.) read correct Starlight data.
     */
    @Overwrite
    public int getLightSubtracted(BlockPos pos, int amount) {
        final int x = pos.getX() & 15;
        final int y = pos.getY();
        final int z = pos.getZ() & 15;
        final int sectionY = y >> 4;

        if (sectionY < WorldUtil.getMinSection(this.world) || sectionY > WorldUtil.getMaxSection(this.world)) {
            return this.world.provider.hasSkyLight() ? Math.max(0, EnumSkyBlock.SKY.defaultLightValue - amount) : 0;
        }

        final int index = sectionY - WorldUtil.getMinLightSection(this.world);

        int skyLight = 0;
        if (this.world.provider.hasSkyLight()) {
            final SWMRNibbleArray[] skyNibs = this.skyNibbles;
            if (skyNibs != null && index >= 0 && index < skyNibs.length) {
                final SWMRNibbleArray nibble = skyNibs[index];
                if (nibble == null || nibble.isNullNibbleVisible() || nibble.isUninitialisedVisible()) {
                    skyLight = 15;
                } else {
                    skyLight = nibble.getVisible(x, y, z);
                }
            }
        }
        skyLight -= amount;

        int blockLight = 0;
        final SWMRNibbleArray[] blockNibs = this.blockNibbles;
        if (blockNibs != null && index >= 0 && index < blockNibs.length) {
            final SWMRNibbleArray nibble = blockNibs[index];
            if (nibble != null) {
                blockLight = nibble.getVisible(x, y, z);
            }
        }

        return Math.max(skyLight, blockLight);
    }

    /**
     * @author Starlight
     * @reason Compute the heightmap (required for world generation, mob spawning, weather, etc.)
     *         but skip vanilla's sky light computation - Starlight handles that separately.
     *         On client: also sync vanilla NibbleArrays into SWMR nibbles.
     */
    @Overwrite
    public void generateSkylightMap() {
        // Heightmap computation (from vanilla) - must always run on both server and client
        final int topSegment = this.getTopFilledSegment();
        this.heightMapMinimum = Integer.MAX_VALUE;
        final ExtendedBlockStorage[] sections = this.getBlockStorageArray();

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                this.precipitationHeightMap[z << 4 | x] = -999;

                for (int y = topSegment + 16 - 1; y > 0; --y) {
                    final ExtendedBlockStorage section = sections[y >> 4];
                    if (section == null || section.isEmpty()) {
                        continue;
                    }
                    final IBlockState state = section.getData().get(x, y & 15, z);
                    if (state.getLightOpacity() == 0) {
                        continue;
                    }
                    this.heightMap[z << 4 | x] = y + 1;
                    if (y < this.heightMapMinimum) {
                        this.heightMapMinimum = y;
                    }
                    break;
                }
            }
        }

        this.dirty = true;

        // Client side: sync vanilla NibbleArrays (received from server packet) into SWMR nibbles
        if (this.world != null && this.world.isRemote) {
            this.starlight$syncVanillaToSWMR();
        }
        // Server side: Starlight computes sky light via lightChunk() during onLoad
    }

    /**
     * @author Starlight
     * @reason Disable vanilla relighting - Starlight handles it
     */
    @Overwrite
    private void relightBlock(int x, int y, int z) {
        // no-op - Starlight handles this
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void onSetBlockState(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir) {
        if (cir.getReturnValue() != null && this.world != null && this.starlight$lightInitialized) {
            // Only queue light changes after lighting is initialized.
            // Before onLoad/lightChunk, changes are redundant since full lighting will be computed.
            final StarLightInterface lightEngine = ((StarLightLightingProvider)this.world).getLightEngine();
            if (lightEngine != null) {
                lightEngine.blockChange(pos);
            }
        }
    }

    @Inject(method = "onLoad", at = @At("RETURN"))
    private void onChunkLoad(CallbackInfo ci) {
        if (this.world == null) {
            return;
        }
        final StarLightInterface lightEngine = ((StarLightLightingProvider)this.world).getLightEngine();
        if (lightEngine == null) {
            return;
        }

        if (this.world.isRemote) {
            // Client: light data comes from server via generateSkylightMap sync.
            // Load empty section metadata.
            final Boolean[] emptySections = StarLightEngine.getEmptySectionsForChunk((Chunk)(Object)this);
            lightEngine.loadInChunk(this.x, this.z, emptySections);
            // Sync SWMR → vanilla NibbleArrays for Celeritas compatibility
            lightEngine.syncSWMRToVanilla((Chunk)(Object)this);
            this.starlight$lightInitialized = true;
        } else {
            // Server side: clear any redundant light tasks queued before onLoad
            // (e.g., from chunk primer or early setBlockState calls during generation).
            // lightChunk() will compute full lighting from scratch anyway.
            lightEngine.removeChunkTasks(this.x, this.z);

            final Boolean[] emptySections = StarLightEngine.getEmptySectionsForChunk((Chunk)(Object)this);

            if (this.starlight$isLitByStarlight()) {
                // Chunk was loaded from disk with Starlight data (set by SaveUtil.loadLightHook)
                lightEngine.loadInChunk(this.x, this.z, emptySections);
            } else {
                // Newly generated chunk or pre-Starlight save: compute full lighting
                lightEngine.lightChunk((Chunk)(Object)this, emptySections);
            }

            // C3 fix: queue edge checks instead of running them immediately.
            // By the time propagateChanges() processes this, more neighbors should be loaded.
            lightEngine.queueEdgeCheck(this.x, this.z);
            this.starlight$lightInitialized = true;
        }
    }

    @Unique
    private boolean starlight$isLitByStarlight() {
        // Check if any SWMR nibble has been initialised (not in null state).
        // Nibbles start as null-state from onConstruct. SaveUtil.loadLightHook replaces
        // them with actual data if the chunk was saved with Starlight light version.
        final SWMRNibbleArray[] blockNibs = this.blockNibbles;
        if (blockNibs != null) {
            for (final SWMRNibbleArray nib : blockNibs) {
                if (nib != null && !nib.isNullNibbleVisible()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private void starlight$syncVanillaToSWMR() {
        final ExtendedBlockStorage[] sections = this.getBlockStorageArray();
        final int minLightSection = WorldUtil.getMinLightSection(this.world);
        final boolean hasSkyLight = this.world.provider.hasSkyLight();

        SWMRNibbleArray[] blockNibs = this.blockNibbles;
        SWMRNibbleArray[] skyNibs = this.skyNibbles;

        for (int i = 0; i < sections.length; i++) {
            // sections[i] = chunk section Y=i. Light nibble index = i - minLightSection = i + 1
            final int lightIndex = i - minLightSection;
            if (lightIndex < 0 || lightIndex >= blockNibs.length) continue;

            if (sections[i] != null) {
                final NibbleArray blockLight = sections[i].getBlockLight();
                if (blockLight != null) {
                    blockNibs[lightIndex] = SWMRNibbleArray.fromVanilla(blockLight);
                }

                if (hasSkyLight) {
                    final NibbleArray skyLight = sections[i].getSkyLight();
                    if (skyLight != null) {
                        skyNibs[lightIndex] = SWMRNibbleArray.fromVanilla(skyLight);
                    }
                }
            }
        }
    }
}
