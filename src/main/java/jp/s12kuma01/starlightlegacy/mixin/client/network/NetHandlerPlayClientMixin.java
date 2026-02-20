package jp.s12kuma01.starlightlegacy.mixin.client.network;

import jp.s12kuma01.starlightlegacy.common.chunk.ExtendedChunk;
import jp.s12kuma01.starlightlegacy.common.chunk.ExtendedChunkSection;
import jp.s12kuma01.starlightlegacy.common.light.SWMRNibbleArray;
import jp.s12kuma01.starlightlegacy.common.light.StarLightEngine;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin {

    @Shadow
    private WorldClient world;

    /**
     * When a chunk data packet is received, initialize starlight data for the chunk.
     * We hook after the chunk has been loaded into the client world.
     *
     * Instead of re-computing lighting from scratch (full BFS), we convert the
     * server's already-correct vanilla NibbleArrays into SWMR data.
     */
    @Inject(
            method = "handleChunkData",
            at = @At("RETURN")
    )
    private void onHandleChunkData(final SPacketChunkData packet, final CallbackInfo ci) {
        if (this.world == null) {
            return;
        }

        final int chunkX = packet.getChunkX();
        final int chunkZ = packet.getChunkZ();
        final Chunk chunk = this.world.getChunkProvider().provideChunk(chunkX, chunkZ);
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        // Initialize section transparency data (needed for future block changes)
        final ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (final ExtendedBlockStorage section : sections) {
            if (section != null && section != Chunk.NULL_BLOCK_STORAGE) {
                ((ExtendedChunkSection)section).starlight$initKnownTransparenciesData();
            }
        }

        // Initialize SWMR nibble arrays
        final ExtendedChunk exChunk = (ExtendedChunk)chunk;
        final SWMRNibbleArray[] blockNibbles = StarLightEngine.getFilledEmptyLight(this.world);
        final SWMRNibbleArray[] skyNibbles = StarLightEngine.getFilledEmptyLight(this.world);

        // Convert server's vanilla NibbleArrays directly into SWMR data.
        // The server already computed correct lighting — no need to re-run BFS.
        for (int i = 0; i < sections.length; ++i) {
            final ExtendedBlockStorage section = sections[i];
            if (section == null || section == Chunk.NULL_BLOCK_STORAGE) {
                continue;
            }
            // nibble index offset: minLightSection = -1, so section index 0 maps to nibble index 1
            final int nibbleIdx = i + 1;

            if (nibbleIdx >= 0 && nibbleIdx < blockNibbles.length) {
                blockNibbles[nibbleIdx] = SWMRNibbleArray.fromVanilla(section.getBlockLight());
            }
            if (nibbleIdx >= 0 && nibbleIdx < skyNibbles.length && section.getSkyLight() != null) {
                skyNibbles[nibbleIdx] = SWMRNibbleArray.fromVanilla(section.getSkyLight());
            }
        }

        exChunk.setBlockNibbles(blockNibbles);
        exChunk.setSkyNibbles(skyNibbles);
        exChunk.setStarlightLit(true);
    }
}
