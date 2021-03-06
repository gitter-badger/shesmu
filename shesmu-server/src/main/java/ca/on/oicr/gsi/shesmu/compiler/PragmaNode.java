package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class PragmaNode {
  private static final Parser.ParseDispatch<PragmaNode> DISPATCH = new Parser.ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "Timeout",
        (p, o) -> {
          final AtomicLong timeout = new AtomicLong();
          final AtomicInteger multiplier = new AtomicInteger();
          final Parser result =
              p.whitespace()
                  .integer(timeout::set, 10)
                  .dispatch(ExpressionNode.INT_SUFFIX, multiplier::set)
                  .whitespace()
                  .symbol(";")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new PragmaNodeTimeout((int) (timeout.get() * multiplier.get())));
          }
          return result;
        });

    DISPATCH.addKeyword(
        "RequiredServices",
        (input, output) -> {
          final AtomicReference<List<String>> services =
              new AtomicReference<>(Collections.emptyList());
          final Parser result =
              input
                  .whitespace()
                  .list(services::set, (p, o) -> p.whitespace().identifier(o).whitespace(), ',')
                  .whitespace()
                  .symbol(";")
                  .whitespace();
          if (result.isGood()) {
            output.accept(new PragmaNodeRequiredServices(services.get()));
          }

          return result;
        });

    DISPATCH.addKeyword(
        "Frequency",
        (p, o) -> {
          final AtomicLong frequency = new AtomicLong();
          final AtomicInteger multiplier = new AtomicInteger();
          final Parser result =
              p.whitespace()
                  .integer(frequency::set, 10)
                  .dispatch(ExpressionNode.INT_SUFFIX, multiplier::set)
                  .whitespace()
                  .symbol(";")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new PragmaNodeFrequency((int) (frequency.get() * multiplier.get())));
          }
          return result;
        });
  }

  public static Parser parse(Parser parser, Consumer<PragmaNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output).whitespace();
  }

  public abstract void renderGuard(RootBuilder root);

  public abstract void renderAtExit(RootBuilder root);

  public abstract void timeout(AtomicInteger timeout);
}
