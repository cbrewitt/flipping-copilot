package com.flippingcopilot.model;

import java.util.Iterator;

public class FlipV2MergingIterator implements Iterator<FlipV2> {
    private final Iterator<FlipV2> iterator1;
    private final Iterator<FlipV2> iterator2;
    private FlipV2 next1;
    private FlipV2 next2;

    public FlipV2MergingIterator(Iterator<FlipV2> i1, Iterator<FlipV2> i2) {
        this.iterator1 = i1;
        this.iterator2 = i2;
        next1 = iterator1.hasNext() ? iterator1.next() : null;
        next2 = iterator2.hasNext() ? iterator2.next() : null;
    }

    @Override
    public boolean hasNext() {
        return next1 != null || next2 != null;
    }

    @Override
    public FlipV2 next() {
        FlipV2 result;
        if (next1 == null) {
            result = next2;
            next2 = iterator2.hasNext() ? iterator2.next() : null;
        } else if (next2 == null) {
            result = next1;
            next1 = iterator1.hasNext() ? iterator1.next() : null;
        } else if (FlipV2.CLOSED_TIME_ASC_CMP.compare(next1, next2) <= 0) {
            result = next1;
            next1 = iterator1.hasNext() ? iterator1.next() : null;
        } else {
            result = next2;
            next2 = iterator2.hasNext() ? iterator2.next() : null;
        }
        return result;
    }
}