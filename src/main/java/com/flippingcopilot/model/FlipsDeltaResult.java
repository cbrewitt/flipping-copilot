package com.flippingcopilot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
public class FlipsDeltaResult {

    public int time;
    public List<FlipV2> flips;

    public static FlipsDeltaResult fromRaw(byte[] raw) {
        FlipsDeltaResult res = new FlipsDeltaResult();
        ByteBuffer b = ByteBuffer.wrap(Arrays.copyOfRange(raw, 0, 4));
        b.order(ByteOrder.BIG_ENDIAN);
        res.time = b.getInt();
        res.flips = FlipV2.listFromRaw(Arrays.copyOfRange(raw, 4, raw.length));
        return res;
    }
}
