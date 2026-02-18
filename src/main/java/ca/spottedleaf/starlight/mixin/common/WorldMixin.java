package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class WorldMixin implements ExtendedWorld, StarLightLightingProvider {

    @Shadow public abstract IChunkProvider getChunkProvider();

    @Shadow @Final public boolean isRemote;

    @Unique
    private StarLightInterface lightEngine;

    @Override
    public StarLightInterface getLightEngine() {
        return this.lightEngine;
    }

    @Override
    public Chunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        return this.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
    }

    @Override
    public Chunk getAnyChunkImmediately(final int chunkX, final int chunkZ) {
        return this.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/storage/ISaveHandler;Lnet/minecraft/world/storage/WorldInfo;Lnet/minecraft/world/WorldProvider;Lnet/minecraft/profiler/Profiler;Z)V", at = @At("RETURN"))
    private void onInit(ISaveHandler saveHandler, WorldInfo info, WorldProvider provider, Profiler profiler, boolean client, CallbackInfo ci) {
        this.lightEngine = new StarLightInterface((World)(Object)this, provider.hasSkyLight(), true);
    }

    /**
     * @author Starlight
     * @reason Replace vanilla checkLightFor with Starlight's implementation
     */
    @Overwrite
    public boolean checkLightFor(EnumSkyBlock lightType, BlockPos pos) {
        // Starlight handles all light updates through its own engine
        // This is called by vanilla but we redirect to Starlight
        if (this.lightEngine != null) {
            this.lightEngine.blockChange(pos);
        }
        return true;
    }

    /**
     * @author Starlight
     * @reason Get light from Starlight nibbles
     */
    @Overwrite
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        if (this.lightEngine == null) {
            return 0;
        }

        final Chunk chunk = this.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return type == EnumSkyBlock.SKY ? 15 : 0;
        }

        if (type == EnumSkyBlock.SKY) {
            return this.lightEngine.getSkyLightValue(pos, chunk);
        } else {
            return this.lightEngine.getBlockLightValue(pos, chunk);
        }
    }
}
