package copilot.model;

import com.google.protobuf.*;
import lombok.*;
import lombok.extern.slf4j.*;

import java.io.*;
import java.util.*;
import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
public class FlipsDelta {

    public int time;
    public List<Flip> flips;

    public static FlipsDelta decodeProto(byte[] bytes) {
        var res = new FlipsDelta();
        res.flips = new ArrayList<>();
        if (bytes == null || bytes.length == 0) { return res; }
        try {
            var input = CodedInputStream.newInstance(bytes);
            for (int tag; (tag = input.readTag()) != 0;) {
                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                switch (fieldNumber) {
                    case 1: res.time = input.readInt32(); break;
                    case 2:
                        int length = input.readRawVarint32(), limit = input.pushLimit(length);
                        Flip f = Flip.decodeProto(input);
                        input.popLimit(limit);
                        if (f != null) { res.flips.add(f); }
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
