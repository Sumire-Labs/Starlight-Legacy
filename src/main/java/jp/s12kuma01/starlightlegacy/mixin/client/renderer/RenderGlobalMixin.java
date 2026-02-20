package jp.s12kuma01.starlightlegacy.mixin.client.renderer;

import jp.s12kuma01.starlightlegacy.common.util.DeduplicatedLongQueue;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedRenderGlobal;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@SideOnly(Side.CLIENT)
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin implements ExtendedRenderGlobal {

    @Unique
    private final DeduplicatedLongQueue starlight$lightUpdatesQueue = new DeduplicatedLongQueue(8192);

    @Shadow
    private ChunkRenderDispatcher renderDispatcher;

    @Shadow
    protected abstract void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean updateImmediately);

    /**
     * @author Spottedleaf (Starlight)
     * @reason Use a deduplicated long queue instead of a set for light updates
     */
    @Overwrite
    public void notifyLightSet(final BlockPos blockPos) {
        this.starlight$lightUpdatesQueue.enqueue(blockPos.toLong());
    }

    /**
     * Disable vanilla light update processing in updateClouds - we process our own queue.
     */
    @Redirect(method = "updateClouds", at = @At(value = "INVOKE", target = "Ljava/util/Set;isEmpty()Z", ordinal = 0))
    private boolean disableVanillaLightUpdates(final Set<BlockPos> instance) {
        return true;
    }

    /**
     * Process batched light updates. Called from MinecraftMixin.
     */
    @Override
    public void starlight$processLightUpdates() {
        if (this.starlight$lightUpdatesQueue.isEmpty() || this.renderDispatcher.hasNoFreeRenderBuilders()) {
            return;
        }

        while (!this.starlight$lightUpdatesQueue.isEmpty()) {
            final long longPos = this.starlight$lightUpdatesQueue.dequeue();
            final BlockPos pos = BlockPos.fromLong(longPos);

            final int x = pos.getX();
            final int y = pos.getY();
            final int z = pos.getZ();

            this.markBlocksForUpdate(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
        }

        this.starlight$lightUpdatesQueue.newDeduplicationSet();
    }
}
