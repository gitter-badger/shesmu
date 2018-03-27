package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

/**
 * A collection action in a “Smash” clause
 *
 * Also usable as the variable definition for the result
 */
public final class SmashNode extends ByChildNode {
	public static Parser parse(Parser input, Consumer<SmashNode> output) {
		final AtomicReference<String> name = new AtomicReference<>();
		final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
		final AtomicReference<ExpressionNode> condition = new AtomicReference<>();

		final Parser result = input.whitespace()//
				.identifier(name::set)//
				.whitespace()//
				.keyword("=")//
				.whitespace()//
				.then(ExpressionNode::parse, expression::set)//
				.symbol("Where")//
				.whitespace()//
				.then(ExpressionNode::parse, condition::set)//
				.whitespace();
		if (result.isGood()) {
			output.accept(new SmashNode(input.line(), input.column(), name.get(), expression.get(), condition.get()));
		}
		return result;

	}

	private final ExpressionNode condition;
	private final ExpressionNode expression;

	public SmashNode(int line, int column, String name, ExpressionNode expression, ExpressionNode condition) {
		super(line, column, name);
		this.expression = expression;
		this.condition = condition;
	}

	public void collectFreeVariables(Set<String> freeVariables) {
		expression.collectFreeVariables(freeVariables);
		condition.collectFreeVariables(freeVariables);
	}

	public void render(RegroupVariablesBuilder regroup, RootBuilder rootBuilder) {
		regroup.addSmash(expression.type().asmType(), name(), condition::render, expression::render);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler) & condition.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, Lookup> definedLookups, Function<String, ActionDefinition> definedActions,
			Consumer<String> errorHandler) {
		return expression.resolveLookups(definedLookups, errorHandler)
				& condition.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public Imyhat type() {
		return expression.type();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = expression.typeCheck(errorHandler);
		if (condition.typeCheck(errorHandler)) {
			if (!Imyhat.BOOLEAN.isSame(condition.type())) {
				errorHandler.accept(String.format("%d:%d: Condition must return boolean but got %s.", line(), column(),
						condition.type()));
				return false;
			}
			return ok;
		} else {
			return false;
		}
	}
}