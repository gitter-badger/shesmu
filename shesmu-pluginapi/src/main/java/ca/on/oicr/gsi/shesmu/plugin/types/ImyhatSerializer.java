package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImyhatSerializer extends JsonSerializer<Imyhat> {
  private interface Generator {
    void generate(JsonGenerator generator) throws IOException;
  }

  private static Generator field(String name, Generator value) {
    return g -> {
      g.writeFieldName(name);
      value.generate(g);
    };
  }

  private static Generator just(Imyhat imyhat) {
    return g -> g.writeString(imyhat.descriptor());
  }

  @Override
  public void serialize(
      Imyhat imyhat, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    imyhat
        .apply(
            new ImyhatTransformer<Generator>() {
              @Override
              public Generator bool() {
                return just(Imyhat.BOOLEAN);
              }

              @Override
              public Generator date() {
                return just(Imyhat.DATE);
              }

              @Override
              public Generator floating() {
                return just(Imyhat.FLOAT);
              }

              @Override
              public Generator integer() {
                return just(Imyhat.INTEGER);
              }

              @Override
              public Generator json() {
                return just(Imyhat.JSON);
              }

              @Override
              public Generator list(Imyhat inner) {
                return single(inner, "list", Imyhat.EMPTY);
              }

              @Override
              public Generator map(Imyhat key, Imyhat value) {
                final Generator keyGenerator = key.apply(this);
                final Generator valueGenerator = value.apply(this);
                return g -> {
                  g.writeStartObject();
                  g.writeStringField("is", "dictionary");
                  g.writeFieldName("key");
                  keyGenerator.generate(g);
                  g.writeFieldName("value");
                  valueGenerator.generate(g);
                  g.writeEndObject();
                };
              }

              @Override
              public Generator object(Stream<Pair<String, Imyhat>> contents) {
                final List<Generator> fields =
                    contents
                        .map(p -> field(p.first(), p.second().apply(this)))
                        .collect(Collectors.toList());
                return g -> {
                  g.writeStartObject();
                  g.writeStringField("is", "object");
                  g.writeFieldName("fields");
                  g.writeStartObject();
                  for (final Generator field : fields) {
                    field.generate(g);
                  }
                  g.writeEndObject();
                  g.writeEndObject();
                };
              }

              @Override
              public Generator optional(Imyhat inner) {
                return single(inner, "optional", Imyhat.NOTHING);
              }

              @Override
              public Generator path() {
                return just(Imyhat.PATH);
              }

              private Generator single(Imyhat inner, String name, Imyhat whenNull) {
                if (inner == null) return just(whenNull);
                final Generator generator = inner.apply(this);
                return g -> {
                  g.writeStartObject();
                  g.writeStringField("is", name);
                  g.writeFieldName("inner");
                  generator.generate(g);
                  g.writeEndObject();
                };
              }

              @Override
              public Generator string() {
                return just(Imyhat.STRING);
              }

              @Override
              public Generator tuple(Stream<Imyhat> contents) {
                final List<Generator> elements =
                    contents.map(e -> e.apply(this)).collect(Collectors.toList());
                return g -> {
                  g.writeStartArray();
                  for (final Generator element : elements) {
                    element.generate(g);
                  }
                  g.writeEndArray();
                };
              }
            })
        .generate(jsonGenerator);
  }
}
