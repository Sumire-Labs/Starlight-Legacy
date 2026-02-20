package jp.s12kuma01.starlightlegacy.common.util;

public final class IntegerUtil {

    private IntegerUtil() {
        throw new RuntimeException();
    }

    public static int getTrailingBit(final int n) {
        return -n & n;
    }

    public static int trailingZeros(final int n) {
        return Integer.numberOfTrailingZeros(n);
    }

    public static int branchlessAbs(final int val) {
        final int mask = val >> (Integer.SIZE - 1);
        return (mask ^ val) - mask;
    }
}
