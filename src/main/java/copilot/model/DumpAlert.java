package copilot.model;

import com.google.protobuf.*;

import java.io.*;

/** One frame of the v2 dump-alert stream; a keepalive is a zero-length frame and never reaches this decoder. */
public class DumpAlert {

    public Suggestion suggestion;

    public static DumpAlert decodeProto(byte[] bytes) throws IOException {
        var frame = new DumpAlert();
        if (bytes == null || bytes.length == 0) { return frame; }
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                frame.suggestion = Suggestion.decodeProto(input.readByteArray());
            } else {
                input.skipField(tag);
            }
        }
        return frame;
    }
}
