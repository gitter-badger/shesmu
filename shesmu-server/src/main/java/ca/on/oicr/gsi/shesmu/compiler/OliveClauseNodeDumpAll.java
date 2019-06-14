package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OliveClauseNodeDumpAll extends OliveClauseNodeBaseDump implements RejectNode {
  private List<Target> columns;

  public OliveClauseNodeDumpAll(int line, int column, String dumper) {
    super(line, column, dumper);
  }

  @Override
  protected Predicate<String> captureVariable() {
    return x -> false;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables) {
    // No free variables.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // No plugins.
  }

  @Override
  protected int columnCount() {
    return columns.size();
  }

  @Override
  protected Stream<String> columnInputs(int index) {
    return Stream.of(columns.get(index).name());
  }

  @Override
  public Imyhat columnType(int index) {
    return columns.get(index).type();
  }

  @Override
  protected void renderColumn(int index, Renderer renderer) {
    renderer.loadTarget(columns.get(index));
  }

  @Override
  public NameDefinitions resolve(
      InputFormatDefinition inputFormatDefinition,
      Function<String, InputFormatDefinition> definedFormats,
      NameDefinitions defs,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      ConstantRetriever constants,
      Consumer<String> errorHandler) {
    columns =
        defs.stream()
            .filter(i -> i.flavour() == Flavour.STREAM || i.flavour() == Flavour.STREAM_SIGNABLE)
            .sorted(Comparator.comparing(Target::name))
            .collect(Collectors.toList());
    return defs;
  }

  @Override
  protected boolean resolveDefinitionsExtra(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  protected boolean typeCheckExtra(Consumer<String> errorHandler) {
    return true;
  }
}