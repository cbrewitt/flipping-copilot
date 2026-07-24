package com.flippingcopilot.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import java.io.IOException;

/** One frame of the v2 dump-alert stream; a keepalive is a zero-length frame and never reaches this decoder. */
public class DumpAlert {

    public Suggestion suggestion;

    public static DumpAlert decodeProto(byte[] bytes) throws IOException {
        DumpAlert frame = new DumpAlert();
        if (bytes == null || bytes.length == 0) {
            return frame;
        }
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                frame.suggestion = Suggestion.decodeProto(input.readByteArray());
            } else {
                input.skipField(tag);
            }
        }
        return frame;
    }
}
