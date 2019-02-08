package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates a parameter that will be formatted and saved as INI parameter for a {@link
 * WorkflowAction}
 */
public final class IniParam<T> {
  /** Save a Boolean value as "true" or "false" */
  public static final Stringifier<Boolean> BOOLEAN =
      new Stringifier<Boolean>() {

        @Override
        public String stringify(WorkflowAction action, Boolean value) {
          return value.toString();
        }

        @Override
        public Imyhat type() {
          return Imyhat.BOOLEAN;
        }
      };
  /** Save a file SWID */
  public static final Stringifier<String> FILE_SWID =
      new Stringifier<String>() {

        @Override
        public String stringify(WorkflowAction action, String value) {
          action.addFileSwid(value);
          return value;
        }

        @Override
        public Imyhat type() {
          return Imyhat.STRING;
        }
      };
  /** Save an integer in the way you'd expect */
  public static final Stringifier<Long> INTEGER =
      new Stringifier<Long>() {

        @Override
        public String stringify(WorkflowAction action, Long value) {
          return value.toString();
        }

        @Override
        public Imyhat type() {
          return Imyhat.INTEGER;
        }
      };
  /** Save a processing SWID */
  public static final Stringifier<String> PROCESSING_SWID =
      new Stringifier<String>() {

        @Override
        public String stringify(WorkflowAction action, String value) {
          action.addProcessingSwid(value);
          return value;
        }

        @Override
        public Imyhat type() {
          return Imyhat.STRING;
        }
      };
  /** Save a string exactly as it is passed by the user */
  public static final Stringifier<String> STRING =
      new Stringifier<String>() {

        @Override
        public String stringify(WorkflowAction action, String value) {
          return value;
        }

        @Override
        public Imyhat type() {
          return Imyhat.STRING;
        }
      };
  /** Save a path */
  public static final Stringifier<Path> PATH =
      new Stringifier<Path>() {

        @Override
        public String stringify(WorkflowAction action, Path value) {
          return value.toString();
        }

        @Override
        public Imyhat type() {
          return Imyhat.PATH;
        }
      };

  private String iniName;
  private String name;
  private boolean required;
  private Stringifier<T> type;

  public IniParam() {}

  /**
   * Convert a date to the specified format, in UTC.
   *
   * @param format a format understandable by {@link DateTimeFormatter#ofPattern(String)}
   */
  public static Stringifier<Instant> date(String format) {
    return new Stringifier<Instant>() {
      private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);

      @Override
      public String stringify(WorkflowAction action, Instant value) {
        return formatter.format(LocalDateTime.ofInstant(value, ZoneOffset.UTC));
      }

      @Override
      public Imyhat type() {
        return Imyhat.DATE;
      }
    };
  }

  /**
   * Convert a list of items into a delimited string
   *
   * <p>No attempt is made to check that the items do not contain the delimiter
   *
   * @param delimiter the delimiter between the items
   * @param inner the type of the items to be concatenated
   */
  public static <T> Stringifier<Set<T>> list(String delimiter, Stringifier<T> inner) {
    return new Stringifier<Set<T>>() {

      @Override
      public String stringify(WorkflowAction action, Set<T> values) {
        return values
            .stream()
            .map(value -> inner.stringify(action, value))
            .collect(Collectors.joining(delimiter));
      }

      @Override
      public Imyhat type() {
        return inner.type().asList();
      }
    };
  }

  /**
   * Concatenate a tuple of different items as a delimited string
   *
   * @param delimiter the delimiter between the items
   * @param inner the items in the tuple
   */
  public static Stringifier<Tuple> tuple(String delimiter, Stream<Stringifier<?>> inner) {
    return new Stringifier<Tuple>() {
      private final List<Pair<Integer, Stringifier<?>>> contents =
          inner
              .map(
                  new Function<Stringifier<?>, Pair<Integer, Stringifier<?>>>() {
                    private int index;

                    @Override
                    public Pair<Integer, Stringifier<?>> apply(Stringifier<?> stringifier) {
                      return new Pair<>(index++, stringifier);
                    }
                  })
              .collect(Collectors.toList());

      @SuppressWarnings("unchecked")
      private <T> String apply(WorkflowAction action, Stringifier<T> stringifier, Object value) {
        return stringifier.stringify(action, (T) value);
      }

      @Override
      public String stringify(WorkflowAction action, Tuple value) {
        return contents
            .stream()
            .map(p -> apply(action, p.second(), value.get(p.first())))
            .collect(Collectors.joining(delimiter));
      }

      @Override
      public Imyhat type() {
        return Imyhat.tuple(contents.stream().map(p -> p.second().type()).toArray(Imyhat[]::new));
      }
    };
  }

  /**
   * Save an integer, but first correct the units
   *
   * <p>We have this problem where workflows use different units as parameters (e.g., memory is in
   * megabytes). We want all values in Shesmu to be specified in base units (bytes, bases) because
   * it has convenient suffixes. This will divide the value specified into those units and round
   * accordingly so the user never has to be concerned about this.
   *
   * @param factor the units of the target value (i.e., 1024*1024 for a value in megabytes)
   */
  public static Stringifier<Long> correctInteger(int factor) {
    return new Stringifier<Long>() {

      @Override
      public String stringify(WorkflowAction action, Long value) {
        if (value == 0) {
          return "0";
        }
        int round;
        if (value % factor == 0) {
          round = 0;
        } else {
          round = value < 0 ? -1 : 1;
        }
        return Long.toString(value / factor + round);
      }

      @Override
      public Imyhat type() {
        return Imyhat.INTEGER;
      }
    };
  }

  public boolean getRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  CustomActionParameter<WorkflowAction, T> parameter() {
    return new CustomActionParameter<WorkflowAction, T>(name, required, type.type()) {
      @Override
      public void store(WorkflowAction action, T value) {
        action.ini.put(iniName, type.stringify(action, value));
      }
    };
  }

  public String getIniName() {
    return iniName;
  }

  public void setIniName(String iniName) {
    this.iniName = iniName;
  }

  public Stringifier<T> getType() {
    return type;
  }

  public void setType(Stringifier<T> type) {
    this.type = type;
  }

  @JsonDeserialize(using = StringifierDeserializer.class)
  public abstract static class Stringifier<T> {
    public abstract String stringify(WorkflowAction action, T value);

    public abstract Imyhat type();
  }

  public static class StringifierDeserializer extends JsonDeserializer<Stringifier> {

    private Stringifier<?> deserialize(JsonNode node) {
      if (node.isTextual()) {
        final String str = node.asText();
        switch (str) {
          case "boolean":
            return BOOLEAN;
          case "fileSWID":
            return FILE_SWID;
          case "integer":
            return INTEGER;
          case "path":
            return PATH;
          case "processingSWID":
            return PROCESSING_SWID;
          case "string":
            return STRING;
          default:
            throw new IllegalArgumentException("Unknown INI type: " + str);
        }
      }
      if (node.isNumber()) {
        return correctInteger(node.asInt());
      }
      if (node.isObject()) {
        final String type = node.get("is").asText();
        switch (type) {
          case "date":
            return date(node.get("format").asText());
          case "list":
            return list(node.get("delimiter").asText(), deserialize(node.get("of")));
          case "tuple":
            return tuple(
                node.get("delimiter").asText(),
                Utils.stream(node.get("of")).map(this::deserialize));
          default:
            throw new IllegalArgumentException("Unknown INI type: " + type);
        }
      }
      throw new IllegalArgumentException("Cannot parse INI type: " + node.getNodeType());
    }

    @Override
    public Stringifier deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      final ObjectCodec oc = parser.getCodec();
      final JsonNode node = oc.readTree(parser);
      return deserialize(node);
    }
  }
}