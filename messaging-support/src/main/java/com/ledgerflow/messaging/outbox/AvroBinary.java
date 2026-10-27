package com.ledgerflow.messaging.outbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

/**
 * Avro binary without the Schema Registry wire format: the payload is stored with its type's full name and rebuilt by
 * the relay, so no registry call happens inside a business transaction (ADR 0013).
 */
final class AvroBinary {

    private AvroBinary() {}

    static byte[] encode(SpecificRecord message) {
        var out = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(out, null);
        try {
            new SpecificDatumWriter<SpecificRecord>(message.getSchema()).write(message, encoder);
            encoder.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }

    static SpecificRecord decode(String type, byte[] payload) {
        var schema = SpecificData.get().getSchema(classOf(type));
        try {
            return new SpecificDatumReader<SpecificRecord>(schema)
                    .read(null, DecoderFactory.get().binaryDecoder(payload, null));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Class<?> classOf(String type) {
        try {
            return Class.forName(type);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("No generated Avro class for outbox payload type " + type, exception);
        }
    }
}
