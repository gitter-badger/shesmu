package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.Imyhat;

public abstract class ImyhatNode {

	private static final Imyhat[] BASE_TYPES = new Imyhat[] { Imyhat.BOOLEAN, Imyhat.DATE, Imyhat.INTEGER,
			Imyhat.STRING };

	public static boolean isBaseType(String name) {
		return Stream.of(BASE_TYPES).anyMatch(t -> t.name().equals(name));
	}

	public static Parser parse(Parser input, Consumer<ImyhatNode> output) {
		final AtomicReference<ImyhatNode> type = new AtomicReference<>();
		Parser result = parse0(input, type::set);
		while (result.isGood()) {
			final AtomicLong index = new AtomicLong();
			Parser next = result//
					.symbol("[")//
					.whitespace()//
					.integer(index::set, 10)//
					.whitespace()//
					.symbol("]")//
					.whitespace();
			if (next.isGood()) {
				type.set(new ImyhatNodeUntuple(type.get(), (int) index.get()));
				result = next;
			} else {
				break;
			}
		}
		output.accept(type.get());
		return result;
	}

	private static Parser parse0(Parser input, Consumer<ImyhatNode> output) {
		final Parser listParser = input.symbol("[");
		if (listParser.isGood()) {
			final AtomicReference<ImyhatNode> inner = new AtomicReference<>();
			final Parser result = listParser//
					.whitespace()//
					.then(ImyhatNode::parse, inner::set)//
					.whitespace()//
					.symbol("]");
			output.accept(new ImyhatNodeList(inner.get()));
			return result;
		}

		final Parser tupleParser = input.symbol("{");
		if (tupleParser.isGood()) {
			final AtomicReference<List<ImyhatNode>> inner = new AtomicReference<>(Collections.emptyList());
			final Parser result = tupleParser//
					.whitespace()//
					.list(inner::set, (p, o) -> parse(p.whitespace(), o).whitespace(), ',')//
					.symbol("}");
			output.accept(new ImyhatNodeTuple(inner.get()));
			return result;
		}
		final Parser unlistParser = input.keyword("In");
		if (unlistParser.isGood()) {
			final AtomicReference<ImyhatNode> inner = new AtomicReference<>();
			final Parser result = unlistParser//
					.whitespace()//
					.then(ImyhatNode::parse, inner::set)//
					.whitespace();
			output.accept(new ImyhatNodeUnlist(inner.get()));
			return result;
		}

		final Parser nestedParser = input.symbol("(");
		if (nestedParser.isGood()) {
			return nestedParser//
					.whitespace()//
					.then(ImyhatNode::parse, output)//
					.whitespace()//
					.symbol(")")//
					.whitespace();
		}

		AtomicReference<String> name = new AtomicReference<String>();
		final Parser result = input.identifier(name::set);
		if (!result.isGood()) {
			return result;
		}

		for (final Imyhat base : BASE_TYPES) {
			if (base.name().equals(name.get())) {
				output.accept(new ImyhatNodeLiteral(base));
				return result;
			}
		}
		output.accept(new ImyhatNodeVariable(name.get()));
		return result;
	}

	public abstract Imyhat render(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler);
}