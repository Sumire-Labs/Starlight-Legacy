package jp.s12kuma01.starlightlegacy.common.chunk;

import jp.s12kuma01.starlightlegacy.common.light.SWMRNibbleArray;

public interface ExtendedChunk {

    SWMRNibbleArray[] getBlockNibbles();

    void setBlockNibbles(final SWMRNibbleArray[] nibbles);

    SWMRNibbleArray[] getSkyNibbles();

    void setSkyNibbles(final SWMRNibbleArray[] nibbles);

    boolean[] getSkyEmptinessMap();

    void setSkyEmptinessMap(final boolean[] emptinessMap);

    boolean[] getBlockEmptinessMap();

    void setBlockEmptinessMap(final boolean[] emptinessMap);

    boolean isStarlightLit();

    void setStarlightLit(boolean lit);
}
