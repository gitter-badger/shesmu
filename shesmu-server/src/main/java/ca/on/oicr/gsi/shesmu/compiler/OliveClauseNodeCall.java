package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class OliveClauseNodeCall extends OliveClauseNode {

  private final List<ExpressionNode> arguments;
  private final int column;
  private final int line;
  private final String name;
  private OliveNodeDefinition target;

  public OliveClauseNodeCall(int line, int column, String name, List<ExpressionNode> arguments) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.arguments = arguments;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    arguments.forEach(arg -> arg.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    return target.dashboardInner();
  }

  @Override
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler) {
    switch (state) {
      case BAD:
        return ClauseStreamOrder.BAD;
      case TRANSFORMED:
        errorHandler.accept(
            String.format("%d:%d: Call clause cannot be applied to grouped result.", line, column));
        return ClauseStreamOrder.BAD;
      case PURE:
        signableNames.addAll(target.signableNames);
        return target.isRoot() ? ClauseStreamOrder.PURE : ClauseStreamOrder.TRANSFORMED;
      default:
        return ClauseStreamOrder.BAD;
    }
  }

  @Override
  public int line() {
    return line;
  }

  @Override
  public void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Map<String, OliveDefineBuilder> definitions) {
    oliveBuilder.line(line);
    oliveBuilder.call(
        definitions.get(name),
        arguments.stream().map(argument -> renderer -> argument.render(renderer)));

    oliveBuilder.measureFlow(builder.sourcePath(), line, column);
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    final NameDefinitions limitedDefs = defs.replaceStream(Stream.empty(), true);
    boolean good =
        arguments.stream().filter(argument -> argument.resolve(limitedDefs, errorHandler)).count()
            == arguments.size();
    final Optional<Stream<Target>> replacements =
        target.outputStreamVariables(oliveCompilerServices, errorHandler);
    good = good && replacements.isPresent();
    return defs.replaceStream(replacements.orElseGet(Stream::empty), good);
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    final boolean ok =
        arguments
                .stream()
                .filter(
                    argument -> argument.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == arguments.size();
    target = oliveCompilerServices.olive(name);
    if (target != null) {
      if (target.parameterCount() != arguments.size()) {
        errorHandler.accept(
            String.format(
                "%d:%d: “Define %s” specifies %d parameters, but only %d arguments provided.",
                line, column, name, target.parameterCount(), arguments.size()));
        return false;
      }
      return ok;
    }
    errorHandler.accept(
        String.format("%d:%d: Cannot find matching “Define %s” for call.", line, column, name));
    return false;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return IntStream.range(0, arguments.size())
            .filter(
                index -> {
                  if (!arguments.get(index).typeCheck(errorHandler)) {
                    return false;
                  }
                  final boolean isSame =
                      arguments.get(index).type().isSame(target.parameterType(index));
                  if (!isSame) {
                    errorHandler.accept(
                        String.format(
                            "%d:%d: Parameter %d to “%s” expects %s, but got %s.",
                            line,
                            column,
                            index,
                            name,
                            target.parameterType(index).name(),
                            arguments.get(index).type().name()));
                  }
                  return isSame;
                })
            .count()
        == arguments.size();
  }
}
