package ca.spottedleaf.starlight.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Previously held a duplicate propagateChanges() call.
 * Light propagation is now handled solely by RenderGlobalMixin.onUpdateClouds()
 * which runs every frame, avoiding double processing.
 * This class is kept but removed from the mixin config.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
}
