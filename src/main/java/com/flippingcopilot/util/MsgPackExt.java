package com.flippingcopilot.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.msgpack.jackson.dataformat.ExtensionTypeCustomDeserializers;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MsgPackExt {

    public static final ObjectMapper MSG_PACK_MAPPER = MsgPackExt.createObjectMapper();

    private static final byte EXT_TYPE_INT32_ARRAY = 41;

    public static ObjectMapper createObjectMapper() {
        MessagePackFactory factory = new MessagePackFactory();

        // we use custom unpacking for our int arrays since the default adds a prefix for every item
        ExtensionTypeCustomDeserializers e = new ExtensionTypeCustomDeserializers();
        e.addCustomDeser(EXT_TYPE_INT32_ARRAY, ((byte[] data) -> {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int arrayLength = data.length / 4;
            int[] result = new int[arrayLength];
            for (int i = 0; i < arrayLength; i++) {
                result[i] = (buffer.get() & 0xff) | ((buffer.get() & 0xff) << 8) |
                        ((buffer.get() & 0xff) << 16) | ((buffer.get() & 0xff) << 24);
            }
            return result;
        }));
        factory.setExtTypeCustomDesers(e);

        SimpleModule module = new SimpleModule();
        ObjectMapper mapper = new ObjectMapper(factory);
        module.addDeserializer(int[].class, new JsonDeserializer<int[]>() {
            @Override
            public int[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                if (p.currentToken() == JsonToken.VALUE_EMBEDDED_OBJECT) {
                    Object obj = p.getEmbeddedObject();
                    if (obj instanceof int[]) {
                        return (int[]) obj;
                    }
                }
                return ctxt.readValue(p, int[].class);
            }
        });
        mapper.registerModule(module);
        return mapper;
    }
}