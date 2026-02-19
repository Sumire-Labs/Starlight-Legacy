package ca.spottedleaf.starlight.common.util;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class DeduplicatedLongQueue {

    private final LongArrayFIFOQueue queue;
    private LongOpenHashSet set;

    public DeduplicatedLongQueue(final int capacity) {
        set = new LongOpenHashSet(capacity);
        queue = new LongArrayFIFOQueue(capacity);
    }

    public void enqueue(final long value) {
        if (set.add(value))
            queue.enqueue(value);
    }

    public long dequeue() {
        return queue.dequeueLong();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void newDeduplicationSet() {
        set = new LongOpenHashSet(queue.size());
    }
}
