package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.CollectNodeConcatenate.ConcatentationType;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The terminal operations in <tt>For</tt> expressions */
public abstract class CollectNode {
  private interface DefaultConstructor {
    CollectNode create(int line, int column, ExpressionNode selector, ExpressionNode alternative);
  }

  private interface OptionalConstructor {
    CollectNode create(int line, int column, ExpressionNode selector);
  }

  private static final Parser.ParseDispatch<CollectNode> DISPATCH = new Parser.ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "Dict",
        (p, o) -> {
          final AtomicReference<ExpressionNode> key = new AtomicReference<>();
          final AtomicReference<ExpressionNode> value = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .then(ExpressionNode::parse0, key::set)
                  .symbol("=")
                  .whitespace()
                  .then(ExpressionNode::parse0, value::set);
          if (result.isGood()) {
            o.accept(new CollectNodeDictionary(p.line(), p.column(), key.get(), value.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "List",
        (p, o) -> {
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result = p.whitespace().then(ExpressionNode::parse0, expression::set);
          if (result.isGood()) {
            o.accept(new CollectNodeList(p.line(), p.column(), expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "PartitionCount",
        (p, o) -> {
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result = p.whitespace().then(ExpressionNode::parse0, expression::set);
          if (result.isGood()) {
            o.accept(new CollectNodePartitionCount(p.line(), p.column(), expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Count",
        (p, o) -> {
          o.accept(new CollectNodeCount(p.line(), p.column()));
          return p;
        });
    DISPATCH.addKeyword("First", optional(CollectNodeFirst::new));
    DISPATCH.addKeyword("Univalued", optional(CollectNodeUnivalued::new));
    DISPATCH.addKeyword("Max", optima(true));
    DISPATCH.addKeyword("Min", optima(false));
    DISPATCH.addKeyword(
        "Reduce",
        (p, o) -> {
          final AtomicReference<DestructuredArgumentNode> accumulatorName = new AtomicReference<>();
          final AtomicReference<ExpressionNode> defaultExpression = new AtomicReference<>();
          final AtomicReference<ExpressionNode> initialExpression = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("(")
                  .whitespace()
                  .then(DestructuredArgumentNode::parse, accumulatorName::set)
                  .whitespace()
                  .symbol("=")
                  .whitespace()
                  .then(ExpressionNode::parse, initialExpression::set)
                  .symbol(")")
                  .whitespace()
                  .then(ExpressionNode::parse0, defaultExpression::set);
          if (result.isGood()) {
            o.accept(
                new CollectNodeReduce(
                    p.line(),
                    p.column(),
                    accumulatorName.get(),
                    defaultExpression.get(),
                    initialExpression.get()));
          }
          return result;
        });
    for (final Match matchType : Match.values()) {
      DISPATCH.addKeyword(
          matchType.syntax(),
          (p, o) -> {
            final AtomicReference<ExpressionNode> selectExpression = new AtomicReference<>();
            final Parser result =
                p.whitespace().then(ExpressionNode::parse0, selectExpression::set).whitespace();
            if (result.isGood()) {
              o.accept(
                  new CollectNodeMatches(p.line(), p.column(), matchType, selectExpression.get()));
            }
            return result;
          });
    }
    for (final ConcatentationType concatType : ConcatentationType.values()) {
      DISPATCH.addKeyword(
          concatType.syntax(),
          (p, o) -> {
            final AtomicReference<ExpressionNode> getterExpression = new AtomicReference<>();
            final AtomicReference<ExpressionNode> delimiterExpression = new AtomicReference<>();
            final Parser result =
                p.whitespace()
                    .then(ExpressionNode::parse, getterExpression::set)
                    .keyword("With")
                    .whitespace()
                    .then(ExpressionNode::parse0, delimiterExpression::set);
            if (result.isGood()) {
              o.accept(
                  new CollectNodeConcatenate(
                      p.line(),
                      p.column(),
                      concatType,
                      getterExpression.get(),
                      delimiterExpression.get()));
            }
            return result;
          });
    }
  }

  private static Rule<CollectNode> optima(boolean max) {
    return optional((l, c, s) -> new CollectNodeOptima(l, c, max, s));
  }

  public static Parser parse(Parser parser, Consumer<CollectNode> output) {
    return parser.dispatch(DISPATCH, output);
  }

  private static Rule<CollectNode> optional(OptionalConstructor optionalConstructor) {
    return (p, o) -> {
      final AtomicReference<ExpressionNode> selectExpression = new AtomicReference<>();
      Parser result = p.whitespace().then(ExpressionNode::parse0, selectExpression::set);
      if (result.isGood()) {
        o.accept(optionalConstructor.create(p.line(), p.column(), selectExpression.get()));
      }
      return result;
    };
  }

  private final int column;

  private final int line;

  protected CollectNode(int line, int column) {
    this.line = line;
    this.column = column;
  }

  /** Add all free variable names to the set provided. */
  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public int column() {
    return column;
  }

  public int line() {
    return line;
  }

  public boolean orderingCheck(Ordering ordering, Consumer<String> errorHandler) {
    return true;
  }

  public abstract void render(JavaStreamBuilder builder, LoadableConstructor name);

  /** Resolve all variable plugins in this expression and its children. */
  public abstract boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler);

  /** Resolve all functions plugins in this expression */
  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract Imyhat type();

  public abstract boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler);
}
