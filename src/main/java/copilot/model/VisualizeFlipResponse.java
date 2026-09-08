package copilot.model;
import static copilot.util.ProtoUtils.*;

import copilot.ui.graph.model.Data;
import com.google.protobuf.*;
import copilot.ui.graph.model.*;
import copilot.util.*;
import lombok.*;

import java.io.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class VisualizeFlipResponse {

    public int[] buyTimes, buyVolumes;
    public long[] buyPrices;
    public int[] sellTimes, sellVolumes;
    public long[] sellPrices;
    public Data graphData;
    // set, with everything else empty, when the item has no usable price data
    public String message;

    public static VisualizeFlipResponse decodeProto(byte[] bytes) throws IOException {
        var r = new VisualizeFlipResponse();
        if (bytes == null || bytes.length == 0) { return r; }
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: r.graphData = Data.decodeProto(input.readByteArray()); break;
                case 2: r.buyTimes = readPackedInt32Array(input); break;
                case 3: r.buyVolumes = readPackedInt32Array(input); break;
                case 4: r.buyPrices = readPackedInt64Array(input); break;
                case 5: r.sellTimes = readPackedInt32Array(input); break;
                case 6: r.sellVolumes = readPackedInt32Array(input); break;
                case 7: r.sellPrices = readPackedInt64Array(input); break;
                case 8: r.message = input.readString(); break;
                default:
                    input.skipField(tag);
            }
        }
        return r;
    }
}
