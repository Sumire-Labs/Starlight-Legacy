package jp.s12kuma01.starlightlegacy.mixin.common.chunk;

import jp.s12kuma01.starlightlegacy.common.chunk.ExtendedChunk;
import jp.s12kuma01.starlightlegacy.common.light.SWMRNibbleArray;
import jp.s12kuma01.starlightlegacy.common.light.StarLightEngine;
import jp.s12kuma01.starlightlegacy.common.light.StarLightInterface;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chunk.class)
public abstract class ChunkMixin implements ExtendedChunk {

    @Shadow
    @Final
    private World world;

    @Unique
    private volatile SWMRNibbleArray[] starlight$blockNibbles;

    @Unique
    private volatile SWMRNibbleArray[] starlight$skyNibbles;

    @Unique
    private volatile boolean[] starlight$skyEmptinessMap;

    @Unique
    private volatile boolean[] starlight$blockEmptinessMap;

    @Unique
    private volatile boolean starlight$isLit;

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return this.starlight$blockNibbles;
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        this.starlight$blockNibbles = nibbles;
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return this.starlight$skyNibbles;
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        this.starlight$skyNibbles = nibbles;
    }

    @Override
    public boolean[] getSkyEmptinessMap() {
        return this.starlight$skyEmptinessMap;
    }

    @Override
    public void setSkyEmptinessMap(final boolean[] emptinessMap) {
        this.starlight$skyEmptinessMap = emptinessMap;
    }

    @Override
    public boolean[] getBlockEmptinessMap() {
        return this.starlight$blockEmptinessMap;
    }

    @Override
    public void setBlockEmptinessMap(final boolean[] emptinessMap) {
        this.starlight$blockEmptinessMap = emptinessMap;
    }

    @Override
    public boolean isStarlightLit() {
        return this.starlight$isLit;
    }

    @Override
    public void setStarlightLit(final boolean lit) {
        this.starlight$isLit = lit;
    }

    /**
     * Initialize SWMR nibble arrays on chunk construction.
     */
    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void onConstruct(final World world, final int x, final int z, final CallbackInfo ci) {
        this.starlight$blockNibbles = StarLightEngine.getFilledEmptyLight(world);
        this.starlight$skyNibbles = StarLightEngine.getFilledEmptyLight(world);
    }

    /**
     * Also initialize for the other constructor (from priming).
     */
    @Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/ChunkPrimer;II)V", at = @At("RETURN"))
    private void onConstructFromPrimer(final World world, final ChunkPrimer primer, final int x, final int z, final CallbackInfo ci) {
        if (this.starlight$blockNibbles == null) {
            this.starlight$blockNibbles = StarLightEngine.getFilledEmptyLight(world);
        }
        if (this.starlight$skyNibbles == null) {
            this.starlight$skyNibbles = StarLightEngine.getFilledEmptyLight(world);
        }
    }
}
