package com.flippingcopilot.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.IOException;

/** The protobuf error body every v2 endpoint replies with on failure; its code mirrors the HTTP status. */
@Getter
@AllArgsConstructor
public class ApiError {

    private final int code;
    private final String displayErr;

    public static ApiError decodeProto(byte[] bytes) throws IOException {
        int code = 0;
        String displayErr = "";
        if (bytes == null || bytes.length == 0) {
            return new ApiError(code, displayErr);
        }
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    code = input.readInt32();
                    break;
                case 2:
                    displayErr = input.readString();
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return new ApiError(code, displayErr);
    }
}
