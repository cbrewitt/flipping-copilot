package com.flippingcopilot.model;

import com.flippingcopilot.util.ProtoUtils;
import com.google.protobuf.CodedOutputStream;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@AllArgsConstructor
@Data
public class DataDeltaRequest {

    private final Map<Integer, Integer> accountIdTime;

    public byte[] encodeProto() {
        return ProtoUtils.encodeMessage(out -> ProtoUtils.writeMap(
                out,
                1,
                accountIdTime,
                (entryOut, fieldNumber, accountId) -> entryOut.writeString(fieldNumber, Integer.toString(accountId)),
                CodedOutputStream::writeInt32));
    }
}
