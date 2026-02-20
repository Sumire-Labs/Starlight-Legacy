package jp.s12kuma01.starlightlegacy.common.chunk;

public interface ExtendedChunkSection {

    long BLOCK_IS_TRANSPARENT = 0b00;
    long BLOCK_IS_FULL_OPAQUE = 0b01;
    long BLOCK_UNKNOWN_TRANSPARENCY = 0b10;
    long BLOCK_SPECIAL_TRANSPARENCY = 0b11;

    /* NOTE: Index is y | (x << 4) | (z << 8) */
    long getKnownTransparency(final int blockIndex);

    long getBitsetForColumn(final int columnX, final int columnZ);

    void starlight$initKnownTransparenciesData();
}
