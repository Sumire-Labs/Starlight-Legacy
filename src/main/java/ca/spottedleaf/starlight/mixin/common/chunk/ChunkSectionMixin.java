package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunkSection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExtendedBlockStorage.class)
public abstract class ChunkSectionMixin implements ExtendedChunkSection {

    @Shadow
    public abstract BlockStateContainer getData();

    @Unique
    protected int starlight$transparentBlockCount;

    @Unique
    private final long[] starlight$knownBlockTransparencies = new long[16 * 16 * 16 * 2 / Long.SIZE];

    @Unique
    private static long starlight$getKnownTransparency(final IBlockState state) {
        final int opacityIfCached = ((ExtendedAbstractBlockState)state).getOpacityIfCached();
        if (opacityIfCached == 0) {
            return ExtendedChunkSection.BLOCK_IS_TRANSPARENT;
        }
        if (opacityIfCached == 15) {
            return ExtendedChunkSection.BLOCK_IS_FULL_OPAQUE;
        }
        return opacityIfCached == -1 ? ExtendedChunkSection.BLOCK_SPECIAL_TRANSPARENCY : ExtendedChunkSection.BLOCK_UNKNOWN_TRANSPARENCY;
    }

    @Unique
    private void starlight$updateTransparencyInfo(final int blockIndex, final long transparency) {
        final int arrayIndex = (blockIndex >>> (6 - 1));
        final int valueShift = (blockIndex & (Long.SIZE / 2 - 1)) << 1;
        long value = this.starlight$knownBlockTransparencies[arrayIndex];
        value &= ~(0b11L << valueShift);
        value |= (transparency << valueShift);
        this.starlight$knownBlockTransparencies[arrayIndex] = value;
    }

    @Override
    public void starlight$initKnownTransparenciesData() {
        this.starlight$transparentBlockCount = 0;
        final BlockStateContainer data = this.getData();
        if (data == null) return;
        for (int y = 0; y <= 15; ++y) {
            for (int z = 0; z <= 15; ++z) {
                for (int x = 0; x <= 15; ++x) {
                    final IBlockState state = data.get(x, y, z);
                    final long transparency = starlight$getKnownTransparency(state);
                    if (transparency == ExtendedChunkSection.BLOCK_IS_TRANSPARENT) {
                        ++this.starlight$transparentBlockCount;
                    }
                    this.starlight$updateTransparencyInfo(y | (x << 4) | (z << 8), transparency);
                }
            }
        }
    }

    /**
     * Initialize transparency data after construction.
     */
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onConstruct(final CallbackInfo ci) {
        // Defer initialization until data is available
    }

    /**
     * Update transparency on block set.
     */
    @Inject(method = "set(IIILnet/minecraft/block/state/IBlockState;)V", at = @At("RETURN"))
    private void onSet(final int x, final int y, final int z, final IBlockState state, final CallbackInfo ci) {
        // Recalculate this block's transparency
        final long newTransparency = starlight$getKnownTransparency(state);
        final int blockIndex = y | (x << 4) | (z << 8);

        final int arrayIndex = (blockIndex >>> (6 - 1));
        final int valueShift = (blockIndex & (Long.SIZE / 2 - 1)) << 1;
        final long oldValue = this.starlight$knownBlockTransparencies[arrayIndex];
        final long oldTransparency = (oldValue >>> valueShift) & 0b11L;

        if (oldTransparency == ExtendedChunkSection.BLOCK_IS_TRANSPARENT) {
            --this.starlight$transparentBlockCount;
        }
        if (newTransparency == ExtendedChunkSection.BLOCK_IS_TRANSPARENT) {
            ++this.starlight$transparentBlockCount;
        }

        this.starlight$updateTransparencyInfo(blockIndex, newTransparency);
    }

    @Override
    public final boolean hasOpaqueBlocks() {
        return this.starlight$transparentBlockCount != 4096;
    }

    @Override
    public final long getKnownTransparency(final int blockIndex) {
        final int arrayIndex = (blockIndex >>> (6 - 1));
        final int valueShift = (blockIndex & (Long.SIZE / 2 - 1)) << 1;
        final long value = this.starlight$knownBlockTransparencies[arrayIndex];
        return (value >>> valueShift) & 0b11L;
    }

    @Override
    public final long getBitsetForColumn(final int columnX, final int columnZ) {
        final int columnIndex = (columnX << 4) | (columnZ << 8);
        final long value = this.starlight$knownBlockTransparencies[columnIndex >>> (6 - 1)];
        final int startIndex = (columnIndex & (Long.SIZE / 2 - 1)) << 1;
        return (value >>> startIndex) & ((1L << (16 * 2)) - 1);
    }
}
