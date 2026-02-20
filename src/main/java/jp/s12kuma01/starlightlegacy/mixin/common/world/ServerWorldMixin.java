package jp.s12kuma01.starlightlegacy.mixin.common.world;

import jp.s12kuma01.starlightlegacy.common.light.LightChunkGetter;
import jp.s12kuma01.starlightlegacy.common.light.StarLightInterface;
import jp.s12kuma01.starlightlegacy.common.light.VariableBlockLightHandler;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedWorld;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldServer.class)
public abstract class ServerWorldMixin implements ExtendedWorld {

    @Shadow
    public abstract ChunkProviderServer getChunkProvider();

    @Unique
    private StarLightInterface starlight$lightEngine;

    @Unique
    private VariableBlockLightHandler starlight$customLightHandler;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onInit(final CallbackInfo ci) {
        final World self = (World)(Object)this;
        final LightChunkGetter getter = new LightChunkGetter() {
            @Override
            public Chunk getChunkForLighting(final int chunkX, final int chunkZ) {
                return ServerWorldMixin.this.getChunkAtImmediately(chunkX, chunkZ);
            }
            @Override
            public World getWorld() {
                return self;
            }
        };
        final boolean hasSkyLight = !self.provider.isNether();
        this.starlight$lightEngine = new StarLightInterface(getter, hasSkyLight, true);
    }

    @Override
    public Chunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        final ChunkProviderServer provider = this.getChunkProvider();
        // In 1.12.2, getLoadedChunk returns already-loaded chunks without triggering generation
        return provider.getLoadedChunk(chunkX, chunkZ);
    }

    @Override
    public VariableBlockLightHandler getCustomLightHandler() {
        return this.starlight$customLightHandler;
    }

    @Override
    public void setCustomLightHandler(final VariableBlockLightHandler handler) {
        this.starlight$customLightHandler = handler;
    }

    @Override
    public StarLightInterface getLightEngine() {
        return this.starlight$lightEngine;
    }
}
