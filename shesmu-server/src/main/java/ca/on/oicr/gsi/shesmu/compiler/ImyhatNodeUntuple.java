package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ImyhatNodeUntuple extends ImyhatNode {
	private final ImyhatNode outer;
	private final int index;

	public ImyhatNodeUntuple(ImyhatNode outer, int index) {
		super();
		this.outer = outer;
		this.index = index;
	}

	@Override
	public Imyhat render(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
		Imyhat type = outer.render(definedTypes, errorHandler);
		if (type instanceof Imyhat.TupleImyhat) {
			Imyhat inner = ((Imyhat.TupleImyhat) type).get(index);
			if (inner.isBad()) {
				errorHandler.accept(
						String.format("Tuple type %s does not contain an element at index %d.", type.name(), index));
			}
			return inner;
		}
		errorHandler.accept(String.format("Type %s is not a tuple and it must be to destructure.", type.name()));
		return Imyhat.BAD;
	}

}