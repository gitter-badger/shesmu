package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A “part” in a string literal
 *
 * <p>Either a string literal or an expression that can be converted to a string literal
 */
public abstract class StringNode {
  private static final Pattern DATE_FORMAT = Pattern.compile("^[GyMdhHmsSEDFwWakKz]");
  private static final Pattern ESCAPE = Pattern.compile("^\\\\([\\\\\"nt{}])");
  private static final Pattern LITERAL = Pattern.compile("^[^\\\\\"{]+");
  private static final Parser.ParseDispatch<StringNode> PARTS = new Parser.ParseDispatch<>();

  static {
    PARTS.addRaw(
        "literal",
        (p, o) ->
            p.regex(
                LITERAL,
                m -> o.accept(new StringNodeLiteral(m.group(0))),
                "Expected string body."));
    PARTS.addRaw(
        "escape",
        (p, o) ->
            p.regex(
                ESCAPE,
                m -> o.accept(new StringNodeLiteral(expand(m.group(1)))),
                "Expected string escape."));
    PARTS.addSymbol(
        "{",
        (p, o) -> {
          final Parser gangParser = p.whitespace().symbol("@");
          if (gangParser.isGood()) {
            final AtomicReference<String> name = new AtomicReference<>();
            final Parser gangResult =
                gangParser.whitespace().identifier(name::set).whitespace().symbol("}");
            if (gangResult.isGood()) {
              o.accept(new StringNodeGangName(gangParser.line(), gangParser.column(), name.get()));
            }
            return gangResult;
          }
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          Parser result = ExpressionNode.parse(p.whitespace(), expression::set).whitespace();
          if (!result.isGood()) {
            return result;
          }
          if (result.lookAhead(':')) {
            final AtomicLong width = new AtomicLong();
            final Parser widthResult = result.symbol(":").integer(width::set, 10);
            if (widthResult.isGood()) {
              o.accept(
                  new StringNodeInteger(p.line(), p.column(), expression.get(), (int) width.get()));
              result = widthResult;
            } else {
              result =
                  result
                      .symbol(":")
                      .regex(
                          DATE_FORMAT,
                          m ->
                              o.accept(
                                  new StringNodeDate(
                                      p.line(), p.column(), expression.get(), m.group(0))),
                          "Expected date format code.");
            }
          } else {
            o.accept(new StringNodeExpression(expression.get()));
          }
          return result.symbol("}");
        });
  }

  private static String expand(String escape) {
    switch (escape) {
      case "n":
        return "\n";
      case "t":
        return "\t";
      case "{":
      case "}":
      case "\\":
      case "\"":
        return escape;
      default:
        throw new UnsupportedOperationException(
            "Internal error: parsed escape sequence is not defined.");
    }
  }

  public static Parser parse(Parser input, Consumer<StringNode> output) {
    return input.dispatch(PARTS, output);
  }

  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract boolean isPassive();

  public abstract void render(Renderer renderer);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract String text();

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
