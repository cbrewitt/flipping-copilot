package com.flippingcopilot.model;

import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.util.ProtoUtils;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.*;

import java.io.IOException;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class VisualizeFlipResponse {

    public int[] buyTimes;
    public int[] buyVolumes;
    public long[] buyPrices;
    public int[] sellTimes;
    public int[] sellVolumes;
    public long[] sellPrices;
    public Data graphData;
    // set, with everything else empty, when the item has no usable price data
    public String message;

    public static VisualizeFlipResponse decodeProto(byte[] bytes) throws IOException {
        VisualizeFlipResponse r = new VisualizeFlipResponse();
        if (bytes == null || bytes.length == 0) {
            return r;
        }
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    r.graphData = Data.decodeProto(input.readByteArray());
                    break;
                case 2:
                    r.buyTimes = ProtoUtils.readPackedInt32Array(input);
                    break;
                case 3:
                    r.buyVolumes = ProtoUtils.readPackedInt32Array(input);
                    break;
                case 4:
                    r.buyPrices = ProtoUtils.readPackedInt64Array(input);
                    break;
                case 5:
                    r.sellTimes = ProtoUtils.readPackedInt32Array(input);
                    break;
                case 6:
                    r.sellVolumes = ProtoUtils.readPackedInt32Array(input);
                    break;
                case 7:
                    r.sellPrices = ProtoUtils.readPackedInt64Array(input);
                    break;
                case 8:
                    r.message = input.readString();
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return r;
    }
}
