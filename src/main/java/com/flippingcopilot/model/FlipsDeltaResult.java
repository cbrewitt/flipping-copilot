package com.flippingcopilot.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
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

    public static FlipsDeltaResult decodeProto(byte[] bytes) {
        FlipsDeltaResult res = new FlipsDeltaResult();
        res.flips = new ArrayList<>();
        if (bytes == null || bytes.length == 0) {
            return res;
        }
        try {
            CodedInputStream input = CodedInputStream.newInstance(bytes);
            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) {
                    break;
                }
                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                switch (fieldNumber) {
                    case 1:
                        res.time = input.readInt32();
                        break;
                    case 2:
                        int length = input.readRawVarint32();
                        int limit = input.pushLimit(length);
                        FlipV2 f = FlipV2.decodeProto(input);
                        input.popLimit(limit);
                        if (f != null) {
                            res.flips.add(f);
                        }
                        break;
                    default:
                        input.skipField(tag);
                }
            }
        } catch (IOException e) {
            log.warn("failed decoding flips delta proto", e);
        }
        return res;
    }
}
