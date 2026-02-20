package jp.s12kuma01.starlightlegacy.common.world;

/**
 * Duck-typing interface for RenderGlobal to avoid direct mixin class references at runtime.
 */
public interface ExtendedRenderGlobal {

    void starlight$processLightUpdates();
}
