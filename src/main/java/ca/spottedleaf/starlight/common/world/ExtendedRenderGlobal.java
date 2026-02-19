package ca.spottedleaf.starlight.common.world;

/**
 * Duck-typing interface for RenderGlobal to avoid direct mixin class references at runtime.
 */
public interface ExtendedRenderGlobal {

    void starlight$processLightUpdates();
}
