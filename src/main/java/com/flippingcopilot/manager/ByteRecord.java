package com.flippingcopilot.manager;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.nio.ByteBuffer;
import java.util.UUID;

@AllArgsConstructor
@EqualsAndHashCode
public class ByteRecord implements Comparable<ByteRecord> {

    public final ByteBuffer data;
    public final int timeBytePosition;
    public final int updatedTimeBytePosition;

    public int getUpdatedTime() {
        return data.getInt(updatedTimeBytePosition);
    }

    public int getTime() {
        return data.getInt(timeBytePosition);
    }

    public UUID getUUID() {
        return new UUID(data.getLong(0), data.getLong(8));
    }

    public int compareTo(ByteRecord b) {
        if (getTime() == b.getTime()) {
            return getUUID().compareTo(b.getUUID());
        }
        return getTime() > b.getTime() ? 1 : -1;
    }
}
