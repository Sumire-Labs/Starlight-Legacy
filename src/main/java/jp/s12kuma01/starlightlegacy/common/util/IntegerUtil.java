package jp.s12kuma01.starlightlegacy.common.util;

public final class IntegerUtil {

    public static final int HIGH_BIT_U32 = Integer.MIN_VALUE;
    public static final long HIGH_BIT_U64 = Long.MIN_VALUE;

    private IntegerUtil() {
        throw new RuntimeException();
    }

    public static int ceilLog2(final int value) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(value - 1);
    }

    public static long ceilLog2(final long value) {
        return Long.SIZE - Long.numberOfLeadingZeros(value - 1);
    }

    public static int floorLog2(final int value) {
        return (Integer.SIZE - 1) ^ Integer.numberOfLeadingZeros(value);
    }

    public static int floorLog2(final long value) {
        return (Long.SIZE - 1) ^ Long.numberOfLeadingZeros(value);
    }

    public static int roundCeilLog2(final int value) {
        return HIGH_BIT_U32 >>> (Integer.numberOfLeadingZeros(value - 1) - 1);
    }

    public static long roundCeilLog2(final long value) {
        return HIGH_BIT_U64 >>> (Long.numberOfLeadingZeros(value - 1) - 1);
    }

    public static int roundFloorLog2(final int value) {
        return HIGH_BIT_U32 >>> Integer.numberOfLeadingZeros(value);
    }

    public static long roundFloorLog2(final long value) {
        return HIGH_BIT_U64 >>> Long.numberOfLeadingZeros(value);
    }

    public static boolean isPowerOfTwo(final int n) {
        return IntegerUtil.getTrailingBit(n) == n;
    }

    public static boolean isPowerOfTwo(final long n) {
        return IntegerUtil.getTrailingBit(n) == n;
    }

    public static int getTrailingBit(final int n) {
        return -n & n;
    }

    public static long getTrailingBit(final long n) {
        return -n & n;
    }

    public static int trailingZeros(final int n) {
        return Integer.numberOfTrailingZeros(n);
    }

    public static int trailingZeros(final long n) {
        return Long.numberOfTrailingZeros(n);
    }

    public static int branchlessAbs(final int val) {
        final int mask = val >> (Integer.SIZE - 1);
        return (mask ^ val) - mask;
    }

    public static long branchlessAbs(final long val) {
        final long mask = val >> (Long.SIZE - 1);
        return (mask ^ val) - mask;
    }
}
