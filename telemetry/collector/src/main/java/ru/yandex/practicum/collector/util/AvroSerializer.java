package ru.yandex.practicum.collector.util;

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class AvroSerializer {

    public <T extends SpecificRecord> byte[] serialize(T data) {
        DatumWriter<T> writer = new SpecificDatumWriter<>(data.getSchema());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
            writer.write(data, encoder);
            encoder.flush();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка сериализации события в Avro", e);
        }
        return outputStream.toByteArray();
    }
}