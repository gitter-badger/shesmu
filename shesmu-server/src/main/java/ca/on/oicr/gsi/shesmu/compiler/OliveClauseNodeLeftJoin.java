package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public final class OliveClauseNodeLeftJoin extends OliveClauseNode {

  private final List<GroupNode> children;
  protected final int column;
  private List<Target> discriminators;
  private final String format;
  private InputFormatDefinition inputFormat;
  private final List<Consumer<JoinBuilder>> joins = new ArrayList<>();
  protected final int line;
  private final ExpressionNode outerKey;
  private final ExpressionNode innerKey;
  private final String variablePrefix;
  private final Optional<ExpressionNode> where;

  public OliveClauseNodeLeftJoin(
      int line,
      int column,
      String format,
      ExpressionNode outerKey,
      String variablePrefix,
      ExpressionNode innerKey,
      List<GroupNode> children,
      Optional<ExpressionNode> where) {
    this.line = line;
    this.column = column;
    this.format = format;
    this.outerKey = outerKey;
    this.variablePrefix = variablePrefix;
    this.innerKey = innerKey;
    this.children = children;
    this.where = where;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    boolean ok = true;
    for (final GroupNode child : children) {
      if (!child.isRead()) {
        ok = false;
        errorHandler.accept(
            String.format(
                "%d:%d: Collected result “%s” is never used.",
                child.line(), child.column(), child.name()));
      }
    }
    return ok;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    outerKey.collectPlugins(pluginFileNames);
    innerKey.collectPlugins(pluginFileNames);
    children.forEach(child -> child.collectPlugins(pluginFileNames));
    where.ifPresent(w -> w.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    final Set<String> joinedNames =
        inputFormat
            .baseStreamVariables()
            .map(Target::name)
            .map(variablePrefix::concat)
            .collect(Collectors.toSet());
    final Set<String> whereInputs = new TreeSet<>();
    where.ifPresent(w -> w.collectFreeVariables(whereInputs, Flavour::isStream));
    return Stream.of(
        new OliveClauseRow(
            "LeftJoin",
            line,
            column,
            true,
            true,
            Stream.concat(
                children
                    .stream()
                    .map(
                        child -> {
                          final Set<String> inputs = new TreeSet<>(whereInputs);
                          child.collectFreeVariables(inputs, Flavour::isStream);
                          inputs.removeAll(joinedNames);
                          return new VariableInformation(
                              child.name(), child.type(), inputs.stream(), Behaviour.DEFINITION);
                        }),
                discriminators
                    .stream()
                    .map(
                        discriminator ->
                            new VariableInformation(
                                discriminator.name(),
                                discriminator.type(),
                                Stream.of(discriminator.name()),
                                Behaviour.PASSTHROUGH)))));
  }

  @Override
  public final ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE) {
      outerKey.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
    }
    return state;
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
    final String prefix = String.format("LeftJoin %d:%d To %s ", line, column, inputFormat.name());
    final Set<String> freeVariables = new HashSet<>();
    children.forEach(group -> group.collectFreeVariables(freeVariables, Flavour::needsCapture));
    outerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);
    innerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);
    where.ifPresent(w -> w.collectFreeVariables(freeVariables, Flavour::needsCapture));
    final Set<String> innerSignatures = new HashSet<>();
    children.forEach(
        group -> group.collectFreeVariables(innerSignatures, Flavour.STREAM_SIGNATURE::equals));
    innerKey.collectFreeVariables(innerSignatures, Flavour.STREAM_SIGNATURE::equals);
    final Set<String> innerSignables = new HashSet<>();
    children.forEach(
        group -> group.collectFreeVariables(innerSignables, Flavour.STREAM_SIGNABLE::equals));
    innerKey.collectFreeVariables(innerSignables, Flavour.STREAM_SIGNABLE::equals);
    final List<Target> signables =
        inputFormat
            .baseStreamVariables()
            .filter(input -> innerSignables.contains(variablePrefix + input.name()))
            .collect(Collectors.toList());

    builder
        .signatureVariables()
        .filter(signature -> innerSignatures.contains(variablePrefix + signature.name()))
        .forEach(
            signatureDefinition ->
                oliveBuilder.createSignature(prefix, inputFormat, signables, signatureDefinition));

    oliveBuilder.line(line);
    final Pair<JoinBuilder, RegroupVariablesBuilder> leftJoin =
        oliveBuilder.leftJoin(
            line,
            column,
            inputFormat,
            outerKey.type(),
            (signatureDefinition, renderer) -> {
              oliveBuilder.renderSigner(prefix, signatureDefinition, renderer);
            },
            oliveBuilder
                .loadableValues()
                .filter(value -> freeVariables.contains(value.name()))
                .toArray(LoadableValue[]::new));
    joins.forEach(a -> a.accept(leftJoin.first()));

    leftJoin.first().outerKey().methodGen().visitCode();
    outerKey.render(leftJoin.first().outerKey());
    leftJoin.first().outerKey().methodGen().returnValue();
    leftJoin.first().outerKey().methodGen().visitMaxs(0, 0);
    leftJoin.first().outerKey().methodGen().visitEnd();

    leftJoin.first().innerKey().methodGen().visitCode();
    innerKey.render(leftJoin.first().innerKey());
    leftJoin.first().innerKey().methodGen().returnValue();
    leftJoin.first().innerKey().methodGen().visitMaxs(0, 0);
    leftJoin.first().innerKey().methodGen().visitEnd();

    leftJoin.first().finish();

    discriminators.forEach(
        discriminator -> {
          leftJoin
              .second()
              .addKey(
                  discriminator.type().apply(TypeUtils.TO_ASM),
                  discriminator.name(),
                  context -> {
                    context.loadStream();
                    context
                        .methodGen()
                        .invokeVirtual(
                            context.streamType(),
                            new Method(
                                discriminator.name(),
                                discriminator.type().apply(TypeUtils.TO_ASM),
                                new Type[] {}));
                  });
        });

    final Regrouper regrouper =
        where.map(w -> leftJoin.second().addWhere(w::render)).orElse(leftJoin.second());
    children.forEach(group -> group.render(regrouper, builder));

    leftJoin.second().finish();

    oliveBuilder.measureFlow(builder.sourcePath(), line, column);
  }

  private class PrefixedTarget implements Target {
    private final Target backing;

    private PrefixedTarget(Target backing) {
      this.backing = backing;
    }

    @Override
    public Flavour flavour() {
      return backing.flavour();
    }

    @Override
    public String name() {
      return variablePrefix + backing.name();
    }

    @Override
    public void read() {
      // Whatever. Don't care.
    }

    @Override
    public Imyhat type() {
      return backing.type();
    }
  }

  private class PrefixedVariable implements InputVariable {
    private final InputVariable backing;

    private PrefixedVariable(InputVariable backing) {
      this.backing = backing;
    }

    @Override
    public void extract(GeneratorAdapter method) {
      backing.extract(method);
    }

    @Override
    public Flavour flavour() {
      return backing.flavour();
    }

    @Override
    public String name() {
      return variablePrefix + backing.name();
    }

    @Override
    public void read() {
      // Whatever. Don't care.
    }

    @Override
    public Imyhat type() {
      return backing.type();
    }
  }

  private class PrefixedSignatureDefinition extends SignatureDefinition {
    private final SignatureDefinition backing;

    private PrefixedSignatureDefinition(SignatureDefinition backing) {
      super(variablePrefix + backing.name(), backing.storage(), backing.type());
      this.backing = backing;
    }

    @Override
    public void build(GeneratorAdapter method, Type streamType, Stream<Target> variables) {
      backing.build(method, streamType, variables);
    }

    @Override
    public Path filename() {
      return backing.filename();
    }
  }

  @Override
  public final NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    inputFormat = oliveCompilerServices.inputFormat(format);
    if (inputFormat == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown input format “%s” in LeftJoin.", line, column, format));
      return defs.fail(false);
    }

    final Set<String> newNames =
        Stream.concat(inputFormat.baseStreamVariables(), oliveCompilerServices.signatures())
            .map(Target::name)
            .map(variablePrefix::concat)
            .collect(Collectors.toSet());

    final List<String> duplicates =
        defs.stream()
            .filter(n -> n.flavour().isStream() && newNames.contains(n.name()))
            .map(Target::name)
            .sorted()
            .collect(Collectors.toList());

    if (duplicates.isEmpty()) {
      defs.stream()
          .filter(n -> n.flavour().isStream())
          .forEach(n -> joins.add(jb -> jb.add(n, true)));
      inputFormat
          .baseStreamVariables()
          .forEach(n -> joins.add(jb -> jb.add(n, variablePrefix + n.name(), false)));
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate variables on both sides of LeftJoin. Please rename or drop the following using a Let: %s",
              line, column, String.join(", ", duplicates)));
      return defs.fail(false);
    }

    /*
     * This code uses PrefixedTarget, PrefixedSignatureDefinition, and PrefixedInputVariable and you might wonder why
     * three given they all just slap a prefix on the name and why are they where they are. The answer is that when
     * reading a variable, the code behaves differently for three cases. If the variable is coming from the outside
     * world, it will be of type Object and we need to do a little dance to load it since it might be from a
     * user-defined class or a tuple generated from JSON importation. Similarly, signatures know how to render
     * themselves, so we need to wrap them. Contextually, the inner key deals with the raw input data while the where
     * and collectors deal with a new class that contains the outer and inner data merged. Therefore, in that context,
     * we need a non-self loading version of the input variables.
     */
    discriminators =
        defs.stream()
            .filter(t -> t.flavour().isStream() && t.flavour() != Flavour.STREAM_SIGNATURE)
            .collect(Collectors.toList());

    final NameDefinitions joinedDefs =
        defs.replaceStream(
            Stream.of(
                    discriminators.stream(),
                    inputFormat.baseStreamVariables().map(PrefixedTarget::new),
                    oliveCompilerServices.signatures().map(PrefixedSignatureDefinition::new))
                .flatMap(Function.identity()),
            true);

    final boolean ok =
        children
                    .stream()
                    .filter(
                        group -> {
                          final boolean isDuplicate =
                              discriminators.stream().anyMatch(t -> t.name().equals(group.name()));
                          if (isDuplicate) {
                            errorHandler.accept(
                                String.format(
                                    "%d:%d: Redefinition of variable “%s”.",
                                    group.line(), group.column(), group.name()));
                          }
                          return group.resolve(joinedDefs, defs, errorHandler) && !isDuplicate;
                        })
                    .count()
                == children.size()
            & outerKey.resolve(defs, errorHandler)
            & innerKey.resolve(
                defs.replaceStream(
                    inputFormat.baseStreamVariables().map(PrefixedVariable::new), true),
                errorHandler)
            & where.map(w -> w.resolve(joinedDefs, errorHandler)).orElse(true);

    return defs.replaceStream(
        Stream.concat(discriminators.stream().map(Target::wrap), children.stream()), ok);
  }

  @Override
  public final boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    boolean ok =
        children
                .stream()
                .filter(group -> group.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == children.size();
    if (children.stream().map(GroupNode::name).distinct().count() != children.size()) {
      ok = false;
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate collected variables in “LeftJoin” clause. Should be: %s",
              line,
              column,
              children
                  .stream()
                  .map(GroupNode::name)
                  .sorted()
                  .distinct()
                  .collect(Collectors.joining(", "))));
    }
    return ok
        & outerKey.resolveDefinitions(oliveCompilerServices, errorHandler)
        & innerKey.resolveDefinitions(oliveCompilerServices, errorHandler)
        & where.map(w -> w.resolveDefinitions(oliveCompilerServices, errorHandler)).orElse(true);
  }

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = outerKey.typeCheck(errorHandler) & innerKey.typeCheck(errorHandler);
    if (ok && !outerKey.type().isSame(innerKey.type())) {
      innerKey.typeError(outerKey.type(), innerKey.type(), errorHandler);
      ok = false;
    }
    return ok
        & children.stream().filter(group -> group.typeCheck(errorHandler)).count()
            == children.size()
        & where
            .map(
                w -> {
                  boolean whereOk = w.typeCheck(errorHandler);
                  if (whereOk) {
                    if (!w.type().isSame(Imyhat.BOOLEAN)) {
                      w.typeError(Imyhat.BOOLEAN, w.type(), errorHandler);
                      whereOk = false;
                    }
                  }
                  return whereOk;
                })
            .orElse(true);
  }
}
