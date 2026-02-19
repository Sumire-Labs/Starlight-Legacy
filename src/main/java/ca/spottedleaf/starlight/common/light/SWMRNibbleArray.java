package ca.spottedleaf.starlight.common.light;

import net.minecraft.world.chunk.NibbleArray;
import java.util.ArrayDeque;
import java.util.Arrays;

// SWMR -> Single Writer Multi Reader Nibble Array
public final class SWMRNibbleArray {

    protected static final int INIT_STATE_NULL   = 0; // null
    protected static final int INIT_STATE_UNINIT = 1; // uninitialised
    protected static final int INIT_STATE_INIT   = 2; // initialised
    protected static final int INIT_STATE_HIDDEN = 3; // initialised, but conversion to Vanilla data should be treated as if NULL

    public static final int ARRAY_SIZE = 16 * 16 * 16 / (8/4); // blocks / bytes per block = 2048
    private static final int POOL_MAX_SIZE = 128;
    static final ThreadLocal<ArrayDeque<byte[]>> WORKING_BYTES_POOL = ThreadLocal.withInitial(ArrayDeque::new);

    private static byte[] allocateBytes() {
        final byte[] inPool = WORKING_BYTES_POOL.get().pollFirst();
        if (inPool != null) {
            return inPool;
        }
        return new byte[ARRAY_SIZE];
    }

    private static void freeBytes(final byte[] bytes) {
        final ArrayDeque<byte[]> pool = WORKING_BYTES_POOL.get();
        if (pool.size() < POOL_MAX_SIZE) {
            pool.addFirst(bytes);
        }
    }

    public static SWMRNibbleArray fromVanilla(final NibbleArray nibble) {
        if (nibble == null) {
            return new SWMRNibbleArray(null, true);
        }
        final byte[] data = nibble.getData();
        boolean allZero = true;
        for (final byte b : data) {
            if (b != 0) {
                allZero = false;
                break;
            }
        }
        if (allZero) {
            return new SWMRNibbleArray();
        }
        return new SWMRNibbleArray(data.clone());
    }

    /**
     * Copies SWMR data into a vanilla NibbleArray for rendering/mod compat.
     */
    public NibbleArray toVanillaNibble() {
        synchronized (this) {
            switch (this.stateVisible) {
                case INIT_STATE_HIDDEN:
                case INIT_STATE_NULL:
                    return null;
                case INIT_STATE_UNINIT:
                    return new NibbleArray();
                case INIT_STATE_INIT:
                    return new NibbleArray(this.storageVisible.clone());
                default:
                    throw new IllegalStateException();
            }
        }
    }

    /**
     * Copies SWMR visible data into the given vanilla NibbleArray in place.
     * Returns true if the copy was performed, false if no data available.
     */
    public boolean syncToVanillaNibble(final NibbleArray target) {
        synchronized (this) {
            if (this.stateVisible == INIT_STATE_INIT && this.storageVisible != null) {
                System.arraycopy(this.storageVisible, 0, target.getData(), 0, ARRAY_SIZE);
                return true;
            } else if (this.stateVisible == INIT_STATE_UNINIT) {
                Arrays.fill(target.getData(), (byte)0);
                return true;
            }
            return false;
        }
    }

    protected int stateUpdating;
    protected volatile int stateVisible;

    protected byte[] storageUpdating;
    protected boolean updatingDirty;
    protected volatile byte[] storageVisible;

    public SWMRNibbleArray() {
        this(null, false); // lazy init
    }

    public SWMRNibbleArray(final byte[] bytes) {
        this(bytes, false);
    }

    public SWMRNibbleArray(final byte[] bytes, final boolean isNullNibble) {
        if (bytes != null && bytes.length != ARRAY_SIZE) {
            throw new IllegalArgumentException("Data of wrong length: " + bytes.length);
        }
        this.stateVisible = this.stateUpdating = bytes == null ? (isNullNibble ? INIT_STATE_NULL : INIT_STATE_UNINIT) : INIT_STATE_INIT;
        this.storageUpdating = this.storageVisible = bytes;
    }

    public SWMRNibbleArray(final byte[] bytes, final int state) {
        if (bytes != null && bytes.length != ARRAY_SIZE) {
            throw new IllegalArgumentException("Data of wrong length: " + bytes.length);
        }
        if (bytes == null && (state == INIT_STATE_INIT || state == INIT_STATE_HIDDEN)) {
            throw new IllegalArgumentException("Data cannot be null and have state be initialised");
        }
        this.stateUpdating = this.stateVisible = state;
        this.storageUpdating = this.storageVisible = bytes;
    }

    public SaveState getSaveState() {
        synchronized (this) {
            final int state = this.stateVisible;
            final byte[] data = this.storageVisible;
            if (state == INIT_STATE_NULL) {
                return null;
            }
            if (state == INIT_STATE_UNINIT) {
                return new SaveState(null, state);
            }
            final boolean zero = isAllZero(data);
            if (zero) {
                return state == INIT_STATE_INIT ? new SaveState(null, INIT_STATE_UNINIT) : null;
            } else {
                return new SaveState(data.clone(), state);
            }
        }
    }

    protected static boolean isAllZero(final byte[] data) {
        for (int i = 0; i < (ARRAY_SIZE >>> 4); ++i) {
            byte whole = data[i << 4];
            for (int k = 1; k < (1 << 4); ++k) {
                whole |= data[(i << 4) | k];
            }
            if (whole != 0) {
                return false;
            }
        }
        return true;
    }

    public void extrudeLower(final SWMRNibbleArray other) {
        if (other.stateUpdating == INIT_STATE_NULL) {
            throw new IllegalArgumentException();
        }
        if (other.storageUpdating == null) {
            this.setUninitialised();
            return;
        }
        final byte[] src = other.storageUpdating;
        final byte[] into;
        if (!this.updatingDirty) {
            if (this.storageUpdating != null) {
                into = this.storageUpdating = allocateBytes();
            } else {
                this.storageUpdating = into = allocateBytes();
                this.stateUpdating = INIT_STATE_INIT;
            }
            this.updatingDirty = true;
        } else {
            into = this.storageUpdating;
        }
        final int start = 0;
        final int end = (15 | (15 << 4)) >>> 1;
        for (int y = 0; y <= 15; ++y) {
            System.arraycopy(src, start, into, y << (8 - 1), end - start + 1);
        }
    }

    public void setFull() {
        if (this.stateUpdating != INIT_STATE_HIDDEN) {
            this.stateUpdating = INIT_STATE_INIT;
        }
        Arrays.fill(this.storageUpdating == null || !this.updatingDirty ? this.storageUpdating = allocateBytes() : this.storageUpdating, (byte)-1);
        this.updatingDirty = true;
    }

    public void setZero() {
        if (this.stateUpdating != INIT_STATE_HIDDEN) {
            this.stateUpdating = INIT_STATE_INIT;
        }
        Arrays.fill(this.storageUpdating == null || !this.updatingDirty ? this.storageUpdating = allocateBytes() : this.storageUpdating, (byte)0);
        this.updatingDirty = true;
    }

    public void setNonNull() {
        if (this.stateUpdating == INIT_STATE_HIDDEN) {
            this.stateUpdating = INIT_STATE_INIT;
            return;
        }
        if (this.stateUpdating != INIT_STATE_NULL) {
            return;
        }
        this.stateUpdating = INIT_STATE_UNINIT;
    }

    public void setNull() {
        this.stateUpdating = INIT_STATE_NULL;
        if (this.updatingDirty && this.storageUpdating != null) {
            freeBytes(this.storageUpdating);
        }
        this.storageUpdating = null;
        this.updatingDirty = false;
    }

    public void setUninitialised() {
        this.stateUpdating = INIT_STATE_UNINIT;
        if (this.storageUpdating != null && this.updatingDirty) {
            freeBytes(this.storageUpdating);
        }
        this.storageUpdating = null;
        this.updatingDirty = false;
    }

    public void setHidden() {
        if (this.stateUpdating == INIT_STATE_HIDDEN) {
            return;
        }
        if (this.stateUpdating != INIT_STATE_INIT) {
            this.setNull();
        } else {
            this.stateUpdating = INIT_STATE_HIDDEN;
        }
    }

    public boolean isDirty() {
        return this.stateUpdating != this.stateVisible || this.updatingDirty;
    }

    public boolean isNullNibbleUpdating() {
        return this.stateUpdating == INIT_STATE_NULL;
    }

    public boolean isNullNibbleVisible() {
        return this.stateVisible == INIT_STATE_NULL;
    }

    public boolean isUninitialisedUpdating() {
        return this.stateUpdating == INIT_STATE_UNINIT;
    }

    public boolean isUninitialisedVisible() {
        return this.stateVisible == INIT_STATE_UNINIT;
    }

    public boolean isInitialisedUpdating() {
        return this.stateUpdating == INIT_STATE_INIT;
    }

    public boolean isInitialisedVisible() {
        return this.stateVisible == INIT_STATE_INIT;
    }

    public boolean isHiddenUpdating() {
        return this.stateUpdating == INIT_STATE_HIDDEN;
    }

    protected void swapUpdatingAndMarkDirty() {
        if (this.updatingDirty) {
            return;
        }
        if (this.storageUpdating == null) {
            this.storageUpdating = allocateBytes();
            Arrays.fill(this.storageUpdating, (byte)0);
        } else {
            System.arraycopy(this.storageUpdating, 0, this.storageUpdating = allocateBytes(), 0, ARRAY_SIZE);
        }
        if (this.stateUpdating != INIT_STATE_HIDDEN) {
            this.stateUpdating = INIT_STATE_INIT;
        }
        this.updatingDirty = true;
    }

    public boolean updateVisible() {
        if (!this.isDirty()) {
            return false;
        }
        synchronized (this) {
            if (this.stateUpdating == INIT_STATE_NULL || this.stateUpdating == INIT_STATE_UNINIT) {
                this.storageVisible = null;
            } else {
                if (this.storageVisible == null) {
                    this.storageVisible = this.storageUpdating.clone();
                } else {
                    if (this.storageUpdating != this.storageVisible) {
                        System.arraycopy(this.storageUpdating, 0, this.storageVisible, 0, ARRAY_SIZE);
                    }
                }
                if (this.storageUpdating != this.storageVisible) {
                    freeBytes(this.storageUpdating);
                }
                this.storageUpdating = this.storageVisible;
            }
            this.updatingDirty = false;
            this.stateVisible = this.stateUpdating;
        }
        return true;
    }

    /* x | (z << 4) | (y << 8) */

    public int getUpdating(final int x, final int y, final int z) {
        return this.getUpdating((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    public int getUpdating(final int index) {
        final byte[] bytes = this.storageUpdating;
        if (bytes == null) {
            return 0;
        }
        final byte value = bytes[index >>> 1];
        return ((value >>> ((index & 1) << 2)) & 0xF);
    }

    public int getVisible(final int x, final int y, final int z) {
        return this.getVisible((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    public int getVisible(final int index) {
        synchronized (this) {
            final byte[] visibleBytes = this.storageVisible;
            if (visibleBytes == null) {
                return 0;
            }
            final byte value = visibleBytes[index >>> 1];
            return ((value >>> ((index & 1) << 2)) & 0xF);
        }
    }

    public void set(final int x, final int y, final int z, final int value) {
        this.set((x & 15) | ((z & 15) << 4) | ((y & 15) << 8), value);
    }

    public void set(final int index, final int value) {
        if (!this.updatingDirty) {
            this.swapUpdatingAndMarkDirty();
        }
        final int shift = (index & 1) << 2;
        final int i = index >>> 1;
        this.storageUpdating[i] = (byte)((this.storageUpdating[i] & (0xF0 >>> shift)) | (value << shift));
    }

    public static final class SaveState {
        public final byte[] data;
        public final int state;

        public SaveState(final byte[] data, final int state) {
            this.data = data;
            this.state = state;
        }
    }
}
