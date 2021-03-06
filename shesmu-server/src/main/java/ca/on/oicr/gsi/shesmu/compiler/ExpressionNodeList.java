package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeList extends ExpressionNode {

  private static final Type A_COLLECTIONS_TYPE = Type.getType(Collections.class);
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Method METHOD_COLLECTIONS__EMPTY_SET =
      new Method("emptySet", A_SET_TYPE, new Type[0]);

  private static final Method METHOD_IMYHAT__NEW_SET =
      new Method("newSet", A_SET_TYPE, new Type[] {});

  private static final Method METHOD_SET__ADD =
      new Method("add", Type.BOOLEAN_TYPE, new Type[] {A_OBJECT_TYPE});

  private final List<ExpressionNode> items;

  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeList(int line, int column, List<ExpressionNode> items) {
    super(line, column);
    this.items = items;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    items.forEach(item -> item.collectFreeVariables(names, predicate));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    items.forEach(item -> item.collectPlugins(pluginFileNames));
  }

  @Override
  public void render(Renderer renderer) {
    if (items.isEmpty()) {
      renderer.methodGen().invokeStatic(A_COLLECTIONS_TYPE, METHOD_COLLECTIONS__EMPTY_SET);
    } else {
      renderer.mark(line());
      renderer.loadImyhat(items.get(0).type().descriptor());
      renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__NEW_SET);
      items.forEach(
          item -> {
            renderer.methodGen().dup();
            item.render(renderer);
            renderer.methodGen().valueOf(item.type().apply(TypeUtils.TO_ASM));
            renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__ADD);
            renderer.methodGen().pop();
          });
      renderer.mark(line());
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return items.stream().filter(item -> item.resolve(defs, errorHandler)).count() == items.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return items
            .stream()
            .filter(item -> item.resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        == items.size();
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (items.isEmpty()) {
      type = Imyhat.EMPTY;
      return true;
    }
    boolean ok =
        items.stream().filter(item -> item.typeCheck(errorHandler)).count() == items.size();
    if (ok) {
      final AtomicReference<Imyhat> resultType = new AtomicReference<>(items.get(0).type());
      ok =
          items
                  .stream()
                  .skip(1)
                  .filter(
                      item -> {
                        final boolean isSame = item.type().isSame(resultType.get());
                        if (isSame) {
                          resultType.updateAndGet(item.type()::unify);
                          return true;
                        }
                        item.typeError(resultType.get(), item.type(), errorHandler);
                        return false;
                      })
                  .count()
              == items.size() - 1;
      type = resultType.get().asList();
    }
    return ok;
  }
}
