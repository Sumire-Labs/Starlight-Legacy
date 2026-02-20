package jp.s12kuma01.starlightlegacy.mixin.client.world;

import jp.s12kuma01.starlightlegacy.common.light.LightChunkGetter;
import jp.s12kuma01.starlightlegacy.common.light.StarLightInterface;
import jp.s12kuma01.starlightlegacy.common.light.VariableBlockLightHandler;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedWorld;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldClient.class)
public abstract class ClientWorldMixin implements ExtendedWorld {

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
                return ClientWorldMixin.this.getChunkAtImmediately(chunkX, chunkZ);
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
        final World self = (World)(Object)this;
        // On client, use the chunk provider to get already-loaded chunks
        if (self.getChunkProvider().isChunkGeneratedAt(chunkX, chunkZ)) {
            return self.getChunkProvider().provideChunk(chunkX, chunkZ);
        }
        return null;
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
