package jp.s12kuma01.starlightlegacy.mixin.common.world;

import jp.s12kuma01.starlightlegacy.common.light.StarLightInterface;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(World.class)
public abstract class WorldMixin implements ExtendedWorld {

    @Shadow public int skylightSubtracted;
    @Shadow public WorldProvider provider;

    @Shadow public abstract IBlockState getBlockState(BlockPos pos);
    @Shadow public abstract boolean isValid(BlockPos pos);
    @Shadow public abstract boolean isBlockLoaded(BlockPos pos);

    /**
     * @author Spottedleaf (Starlight)
     * @reason Redirect light checks to Starlight engine
     */
    @Overwrite
    public boolean checkLightFor(final EnumSkyBlock lightType, final BlockPos pos) {
        this.getLightEngine().blockChange(pos);
        return true;
    }

    /**
     * @author Spottedleaf (Starlight)
     * @reason Read light from Starlight SWMR data instead of vanilla NibbleArrays
     */
    @Overwrite
    public int getLightFor(final EnumSkyBlock type, final BlockPos pos) {
        final StarLightInterface lightEngine = this.getLightEngine();
        if (lightEngine == null) {
            return type.defaultLightValue;
        }
        if (type == EnumSkyBlock.SKY) {
            return lightEngine.getSkyLightLevel(pos);
        } else {
            return lightEngine.getBlockLightLevel(pos);
        }
    }

    /**
     * @author Spottedleaf (Starlight)
     * @reason Use Starlight SWMR data for combined light calculation
     */
    @Overwrite
    public int getLight(final BlockPos pos, final boolean checkNeighbors) {
        if (pos.getX() < -30000000 || pos.getZ() < -30000000 || pos.getX() >= 30000000 || pos.getZ() >= 30000000) {
            return 15;
        }

        if (checkNeighbors && this.getBlockState(pos).useNeighborBrightness()) {
            // Vanilla checks 5 directions (up, east, west, south, north — no down)
            int max = this.getLight(pos.up(), false);
            int val = this.getLight(pos.east(), false);
            if (val > max) max = val;
            val = this.getLight(pos.west(), false);
            if (val > max) max = val;
            val = this.getLight(pos.south(), false);
            if (val > max) max = val;
            val = this.getLight(pos.north(), false);
            if (val > max) max = val;
            return max;
        }

        final StarLightInterface lightEngine = this.getLightEngine();
        if (lightEngine == null) {
            return 0;
        }

        if (pos.getY() < 0) {
            return 0;
        }
        if (pos.getY() >= 256) {
            int skyLight = 15 - this.skylightSubtracted;
            return Math.max(skyLight, 0);
        }

        final int skyLight = lightEngine.getSkyLightLevel(pos) - this.skylightSubtracted;
        if (skyLight >= 15) {
            return 15; // sky is max, no need to check block light
        }
        final int blockLight = lightEngine.getBlockLightLevel(pos);
        return Math.max(Math.max(skyLight, 0), blockLight);
    }

    /**
     * @author Spottedleaf (Starlight)
     * @reason Use Starlight SWMR data for neighbor brightness calculation
     */
    @Overwrite
    @SideOnly(Side.CLIENT)
    public int getLightFromNeighborsFor(final EnumSkyBlock type, BlockPos pos) {
        if (type == EnumSkyBlock.SKY && !this.provider.hasSkyLight()) {
            return 0;
        }
        if (pos.getY() < 0) {
            pos = new BlockPos(pos.getX(), 0, pos.getZ());
        }
        if (!this.isValid(pos)) {
            return type.defaultLightValue;
        }
        if (!this.isBlockLoaded(pos)) {
            return type.defaultLightValue;
        }
        if (this.getBlockState(pos).useNeighborBrightness()) {
            int max = 0;
            for (final EnumFacing face : EnumFacing.VALUES) {
                final int val = this.getLightFor(type, pos.offset(face));
                if (val > max) max = val;
                if (max >= 15) return max;
            }
            return max;
        }
        return this.getLightFor(type, pos);
    }
}
