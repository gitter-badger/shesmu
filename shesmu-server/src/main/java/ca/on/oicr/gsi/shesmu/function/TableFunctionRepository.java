package ca.on.oicr.gsi.shesmu.function;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.FileWatcher;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Imyhat.BaseImyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.WatchedFileListener;

/**
 * Converts a TSV file into a function
 *
 * The row must be a Shesmu base type for the data in that column. The last
 * column will be treated as the return value and the first columns will be the
 * parameters to match. Every subsequent row is a set of parameters to check,
 * which must either be a value or * to indicate a wild card and a matching
 * return value. If no rows match, the default value for that type is returned.
 */
@MetaInfServices
public class TableFunctionRepository implements FunctionRepository {
	private class Table implements WatchedFileListener {

		private final Path fileName;

		private Optional<FunctionDefinition> function = Optional.empty();

		private final String name;

		public Table(Path fileName) {
			this.fileName = fileName;
			name = RuntimeSupport.removeExtension(fileName, EXTENSION);
		}

		public String configuration() {
			return function.map(f -> f.parameters().map(p -> p.type().name())
					.collect(Collectors.joining(", ", "(", ") " + f.returnType().name()))).orElse("none");
		}

		@Override
		public void start() {
			update();
		}

		@Override
		public void stop() {

		}

		public Stream<FunctionDefinition> stream() {
			return function.map(Stream::of).orElseGet(Stream::empty);
		}

		@Override
		public Optional<Integer> update() {
			function = readLookup(fileName, name);
			return Optional.empty();

		}
	}

	private static final String EXTENSION = ".lookup";

	private static final Pattern TAB = Pattern.compile("\t");

	@RuntimeInterop
	private static Object lookup(List<Function<Object[], Optional<Object>>> attempts, Object defaultValue,
			Object... parameters) {
		return attempts.stream()//
				.map(attempt -> attempt.apply(parameters))//
				.filter(Optional::isPresent)//
				.findFirst()//
				.orElse(Optional.empty())//
				.orElse(defaultValue);
	}

	private static Optional<FunctionDefinition> readLookup(Path filename, String name) {
		try {
			final List<String> lines = Files.readAllLines(filename);

			if (lines.size() < 2) {
				return Optional.empty();
			}

			final List<BaseImyhat> types = TAB.splitAsStream(lines.get(0)).map(Imyhat::forName)
					.collect(Collectors.toList());
			if (types.size() < 2) {
				return Optional.empty();
			}

			final List<String[]> grid = lines.stream().skip(1).map(TAB::split).collect(Collectors.toList());

			if (grid.stream().anyMatch(columns -> columns.length != types.size())) {
				return Optional.empty();
			}

			final List<Function<Object[], Optional<Object>>> attempts = grid.stream()//
					.<Function<Object[], Optional<Object>>>map(columns -> {
						Predicate<Object[]> combiningPredicates = x -> true;
						for (int index = 0; index < columns.length - 1; index++) {
							if (!columns[index].equals("*")) {
								final Object match = types.get(index).parse(columns[index]);
								final int i = index;
								combiningPredicates = combiningPredicates
										.and(parameters -> parameters[i].equals(match));
							}
						}

						final Predicate<Object[]> predicate = combiningPredicates;
						final Object result = types.get(types.size() - 1).parse(columns[columns.length - 1]);
						return parameters -> predicate.test(parameters) ? Optional.of(result) : Optional.empty();
					}).collect(Collectors.toList());
			return Optional.of(FunctionForInstance.bind(TableFunctionRepository.class, mt -> {
				try {
					final MethodHandle lookupMethod = MethodHandles.lookup().findStatic(TableFunctionRepository.class,
							"lookup", MethodType.methodType(Object.class, List.class, Object.class, Object[].class));
					return lookupMethod.bindTo(attempts).bindTo(types.get(types.size() - 1).defaultValue())
							.asVarargsCollector(Object[].class).asType(mt);
				} catch (NoSuchMethodException | IllegalAccessException e) {
					return MethodHandles.throwException(types.get(types.size() - 1).javaType(),
							UnsupportedOperationException.class);
				}
			}, name, String.format("Table-defined lookup from %s.", filename), types.get(types.size() - 1),
					types.stream().limit(types.size() - 1).map(new Function<Imyhat, FunctionParameter>() {
						int index;

						@Override
						public FunctionParameter apply(Imyhat type) {
							return new FunctionParameter(String.format("arg%d", ++index), type);
						}
					}).toArray(FunctionParameter[]::new)));
		} catch (final IOException e) {
			e.printStackTrace();
			return Optional.empty();
		}

	}

	private final AutoUpdatingDirectory<Table> configuration;

	public TableFunctionRepository() {
		this(FileWatcher.DATA_DIRECTORY);
	}

	public TableFunctionRepository(FileWatcher watcher) {
		configuration = new AutoUpdatingDirectory<>(watcher, EXTENSION, Table::new);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		final Map<String, String> map = configuration.stream()
				.collect(Collectors.toMap(t -> t.fileName.toString(), Table::configuration));
		return Stream.of(new Pair<>("Table Functions", map));
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return configuration.stream().flatMap(Table::stream);
	}

}
