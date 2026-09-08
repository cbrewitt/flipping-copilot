package copilot.model;

import lombok.*;
import copilot.util.*;
import com.google.protobuf.*;

import java.util.*;

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
