package ca.spottedleaf.starlight.mixin.common;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExtendedBlockStorage.class)
public abstract class ExtendedBlockStorageMixin {

    @Shadow
    public abstract boolean isEmpty();

    // This mixin provides isEmpty() access which is used throughout Starlight
    // to determine if a section is empty (equivalent to hasOnlyAir() in modern MC)
}
