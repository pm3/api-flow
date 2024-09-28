package eu.aston.flow.ognl;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class RuntimeExceptionSerializer extends JsonSerializer<RuntimeException> {

    @Override
    public void serialize(RuntimeException e, JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider) {
        throw e;
    }
}
