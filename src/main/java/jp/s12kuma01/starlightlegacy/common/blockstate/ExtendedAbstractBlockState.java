package jp.s12kuma01.starlightlegacy.common.blockstate;

public interface ExtendedAbstractBlockState {

    boolean isConditionallyFullOpaque();

    int getOpacityIfCached();
}
