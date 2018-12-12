package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;

public final class OliveNodeConstant extends OliveNode implements Target {
	private final ExpressionNode body;

	private final int column;

	private final int line;

	private final String name;

	public OliveNodeConstant(int line, int column, String name, ExpressionNode body) {
		this.line = line;
		this.column = column;
		this.name = name;
		this.body = body;
	}

	@Override
	public void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
		builder.defineConstant(name, body.type().asmType(), method -> body.render(builder.rootRenderer(false)));
	}

	@Override
	public boolean checkVariableStream(Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean collectDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Map<String, Target> definedConstants, Consumer<String> errorHandler) {
		if (definedConstants.containsKey(name)) {
			errorHandler.accept(String.format("%d:%d: Cannot redefine constant “%s”.", line, column, name));
			return false;
		}
		definedConstants.put(name, this);
		return true;
	}

	@Override
	public boolean collectFunctions(Predicate<String> isDefined, Consumer<FunctionDefinition> defineFunctions,
			Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public Stream<OliveTable> dashboard() {
		return Stream.empty();
	}

	@Override
	public Flavour flavour() {
		return Flavour.CONSTANT;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
		// Nothing to do.

	}

	@Override
	public boolean resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, Consumer<String> errorHandler,
			ConstantRetriever constants) {
		return body.resolve(new NameDefinitions(
				constants.get(false).collect(Collectors.toMap(Target::name, Function.identity())), true), errorHandler);
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		return body.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public boolean resolveTypes(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
		return body.typeCheck(errorHandler);
	}

	@Override
	public Imyhat type() {
		return body.type();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return body.typeCheck(errorHandler);
	}

}