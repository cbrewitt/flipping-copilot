package copilot.model;

import com.google.protobuf.*;
import lombok.*;
import copilot.util.*;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.List;

@Getter
@NoArgsConstructor
public class PortfolioResponse {
    private List<Suggestion.PortfolioItem> portfolioItems = new ArrayList<>();
    private Instant time;

    public static PortfolioResponse decodeProto(byte[] bytes) throws IOException {
        var result = new PortfolioResponse();
        if (bytes == null || bytes.length == 0) { return result; }

        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            switch (fieldNumber) {
                case 1: {
                    int length = input.readRawVarint32(), limit = input.pushLimit(length);
                    result.portfolioItems.add(Suggestion.PortfolioItem.decodeProto(input));
                    input.popLimit(limit);
                    break;
                }
                case 2: result.time = ProtoUtils.decodeTimestamp(input); break;
                default:
                    input.skipField(tag);
            }
        }
        return result;
    }
}
