package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import ca.on.oicr.gsi.shesmu.runtime.JsonConverter;
import ca.on.oicr.gsi.shesmu.runtime.JsonWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeJsonConvert extends ExpressionNode {
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_JSON_CONVERTER_TYPE = Type.getType(JsonConverter.class);
  private static final Type A_JSON_NODE_TYPE = Type.getType(JsonNode.class);
  private static final Type A_JSON_WRAPPER_TYPE = Type.getType(JsonWrapper.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Method IMYHAT__APPLY =
      new Method(
          "apply", Type.getType(Object.class), new Type[] {Type.getType(ImyhatTransformer.class)});
  private static final Method JSON_CONVERTER__CONVERT =
      new Method(
          "convert",
          Type.getType(JsonNode.class),
          new Type[] {Type.getType(Imyhat.class), Type.getType(Object.class)});
  private static final Method JSON_CONVERTER__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_JSON_NODE_TYPE});
  private final ExpressionNode expression;
  private final ImyhatNode imyhatNode;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeJsonConvert(
      int line, int column, ExpressionNode expression, ImyhatNode imyhatNode) {
    super(line, column);
    this.expression = expression;
    this.imyhatNode = imyhatNode;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    if (type.isSame(Imyhat.JSON)) {
      renderer.loadImyhat(expression.type().descriptor());
      expression.render(renderer);
      renderer.methodGen().valueOf(expression.type().apply(TO_ASM));
      renderer.methodGen().invokeStatic(A_JSON_WRAPPER_TYPE, JSON_CONVERTER__CONVERT);
    } else {
      renderer.loadImyhat(type.descriptor());
      renderer.methodGen().newInstance(A_JSON_CONVERTER_TYPE);
      renderer.methodGen().dup();
      expression.render(renderer);
      renderer.methodGen().invokeConstructor(A_JSON_CONVERTER_TYPE, JSON_CONVERTER__CTOR);
      renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, IMYHAT__APPLY);
      renderer.methodGen().checkCast(A_OPTIONAL_TYPE);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    type = imyhatNode.render(expressionCompilerServices, errorHandler);
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler) && !type.isBad();
  }

  @Override
  public Imyhat type() {
    return type.isSame(Imyhat.JSON) ? Imyhat.JSON : type.asOptional();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (expression.typeCheck(errorHandler)) {
      // We are handling two cases: convert things to JSON (in which case, we don't care what the
      // input type is) or convert from JSON (in which case, the input type must be JSON and we
      // don't care about the output type)
      if (type.isSame(Imyhat.JSON) || expression.type().isSame(Imyhat.JSON)) {
        return true;
      }
      expression.typeError("json", expression.type(), errorHandler);
    }
    return false;
  }
}
