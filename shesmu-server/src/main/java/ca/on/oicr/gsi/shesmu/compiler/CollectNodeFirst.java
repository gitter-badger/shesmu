package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class CollectNodeFirst extends CollectNodeOptional {

  protected CollectNodeFirst(int line, int column, ExpressionNode selector) {
    super(line, column, selector);
  }

  @Override
  protected void finishMethod(Renderer renderer) {}

  @Override
  protected Renderer makeMethod(
      JavaStreamBuilder builder,
      LoadableConstructor name,
      Imyhat returnType,
      LoadableValue[] loadables) {
    final Renderer map = builder.map(line(), column(), name, returnType, loadables);
    builder.first();
    return map;
  }

  @Override
  protected Imyhat returnType(Imyhat incomingType, Imyhat selectorType) {
    return selectorType;
  }

  @Override
  protected boolean typeCheckExtra(Consumer<String> errorHandler) {
    return true;
  }
}
