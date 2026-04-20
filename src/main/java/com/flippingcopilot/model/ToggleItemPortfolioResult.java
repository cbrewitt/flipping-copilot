package com.flippingcopilot.model;

import com.flippingcopilot.util.ProtoUtils;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class ToggleItemPortfolioResult {
    private List<Suggestion.PortfolioItem> portfolioItems = new ArrayList<>();
    private Instant time;

    public static ToggleItemPortfolioResult decodeProto(byte[] bytes) throws IOException {
        ToggleItemPortfolioResult result = new ToggleItemPortfolioResult();
        if (bytes == null || bytes.length == 0) {
            return result;
        }

        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            switch (fieldNumber) {
                case 1: {
                    int length = input.readRawVarint32();
                    int limit = input.pushLimit(length);
                    result.portfolioItems.add(Suggestion.PortfolioItem.decodeProto(input));
                    input.popLimit(limit);
                    break;
                }
                case 2:
                    result.time = ProtoUtils.decodeTimestamp(input);
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return result;
    }
}
