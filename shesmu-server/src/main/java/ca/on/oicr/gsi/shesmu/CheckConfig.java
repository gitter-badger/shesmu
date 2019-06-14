package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.VariadicFunction;
import ca.on.oicr.gsi.shesmu.plugin.signature.DynamicSigner;
import ca.on.oicr.gsi.shesmu.plugin.signature.StaticSigner;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ReturnTypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import ca.on.oicr.gsi.status.SectionRenderer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.cli.*;

public class CheckConfig {
  public static void main(String[] args) throws XMLStreamException {
    final Options options = new Options();
    options.addOption("h", "help", false, "This dreck.");
    options.addOption(
        "r", "remote", true, "The remote instance with all the actions/functions/etc.");
    final CommandLineParser parser = new DefaultParser();
    String[] files;
    try {
      final CommandLine cmd = parser.parse(options, args);

      if (cmd.hasOption("h")) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Shesmu Config File checker", options);
        System.exit(0);
        return;
      }

      if (cmd.getArgs().length == 0) {
        System.err.println("At least one file must be specified to check.");
        System.exit(1);
        return;
      }
      files = cmd.getArgs();
    } catch (final ParseException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    }
    final ServiceLoader<PluginFileType> pluginFileTypes = ServiceLoader.load(PluginFileType.class);
    for (final String file : files) {
      final Path path = Paths.get(file);
      final String fileName = path.getFileName().toString();
      boolean missing = true;
      for (PluginFileType<?> type : pluginFileTypes) {
        if (fileName.endsWith(type.extension())) {
          showPlugin(type, path);
          missing = false;
          break;
        }
      }
      if (missing) {
        System.err.printf("No plugin found for %s. Maybe it isn't on the class path?\n", file);
      }
    }
  }

  private static <T extends PluginFile> void showPlugin(PluginFileType<T> type, Path path)
      throws XMLStreamException {
    System.out.println(path);
    final PluginFile pluginFile =
        type.create(
            path,
            "test",
            new Definer<T>() {
              @Override
              public void clearActions() {
                // Dummy.
              }

              @Override
              public void clearConstants() {
                // Dummy.
              }

              @Override
              public void clearFunctions() {
                // Dummy.
              }

              @Override
              public <A extends Action> void defineAction(
                  String name,
                  String description,
                  Class<A> clazz,
                  Supplier<A> supplier,
                  Stream<CustomActionParameter<A>> parameters) {
                System.out.printf("Action %s bound to %s.\n", name, clazz.getName());
              }

              @Override
              public void defineConstant(
                  String name, String description, Imyhat type, Object value) {
                System.out.printf("Constant %s of type %s.\n", name, type.name());
              }

              @Override
              public <R> void defineConstant(
                  String name, String description, ReturnTypeGuarantee<R> returnType, R value) {
                System.out.printf("Constant %s of type %s.\n", name, returnType.type().name());
              }

              @Override
              public <R> void defineConstant(
                  String name,
                  String description,
                  ReturnTypeGuarantee<R> returnType,
                  Supplier<R> constant) {
                System.out.printf("Constant %s of type %s.\n", name, returnType.type().name());
              }

              @Override
              public <R> void defineDynamicSigner(
                  String name,
                  ReturnTypeGuarantee<R> returnType,
                  Supplier<? extends DynamicSigner<R>> signer) {
                System.out.printf("Signer %s of type %s.\n", name, returnType.type().name());
              }

              @Override
              public void defineFunction(
                  String name,
                  String description,
                  Imyhat returnType,
                  VariadicFunction function,
                  FunctionParameter... parameters) {
                System.out.printf(
                    "Function %s of type (%s) %s.\n",
                    name,
                    Stream.of(parameters)
                        .map(p -> p.type().name())
                        .collect(Collectors.joining(",")),
                    returnType.name());
              }

              @Override
              public <A, R> void defineFunction(
                  String name,
                  String description,
                  ReturnTypeGuarantee<R> returnType,
                  String parameterName,
                  TypeGuarantee<A> parameterType,
                  Function<A, R> function) {
                System.out.printf(
                    "Function %s of type (%s) %s.\n",
                    name, parameterType.type().name(), returnType.type().name());
                // Dummy.

              }

              @Override
              public <A, B, R> void defineFunction(
                  String name,
                  String description,
                  ReturnTypeGuarantee<R> returnType,
                  String parameter1Name,
                  TypeGuarantee<A> parameter1Type,
                  String parameter2Name,
                  TypeGuarantee<B> parameter2Type,
                  BiFunction<A, B, R> function) {
                System.out.printf(
                    "Function %s of type (%s, %s) %s.\n",
                    name,
                    parameter1Type.type().name(),
                    parameter2Type.type().name(),
                    returnType.type().name());
              }

              @Override
              public <R> void defineStaticSigner(
                  String name,
                  ReturnTypeGuarantee<R> returnType,
                  Supplier<? extends StaticSigner<R>> signer) {
                System.out.printf("Signer %s of type %s.\n", name, returnType.type().name());
              }

              @Override
              public T get() {
                return null;
              }
            });
    pluginFile.update().ifPresent(t -> System.out.printf("Requested reload in %d minutes.\n", t));
    pluginFile.configuration(
        new SectionRenderer() {
          private final Instant now = Instant.now();

          @Override
          public void javaScriptLink(String name, String code, String value) {
            System.out.printf("%s = [%s](%s)\n", name, value, code);
          }

          @Override
          public void line(Stream<Pair<String, String>> attributes, String name, String value) {
            attributes.close();
            System.out.printf("%s = %s\n", name, value);
          }

          @Override
          public void line(String name, Instant value) {
            System.out.printf("%s = %s\n", name, value);
          }

          @Override
          public void line(String name, int value) {
            System.out.printf("%s = %d\n", name, value);
          }

          @Override
          public void line(String name, long value) {
            System.out.printf("%s = %d\n", name, value);
          }

          @Override
          public void line(String name, String value) {
            System.out.printf("%s = %s\n", name, value);
          }

          @Override
          public void lineSpan(String name, Instant value) {
            System.out.printf("%s = %s %s\n", name, value, Duration.between(now, value));
          }

          @Override
          public void link(String name, String link, String value) {
            System.out.printf("%s = [%s](%s)\n", name, value, link);
          }
        });
  }
}