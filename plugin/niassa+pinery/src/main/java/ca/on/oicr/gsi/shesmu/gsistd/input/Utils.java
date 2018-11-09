package ca.on.oicr.gsi.shesmu.gsistd.input;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class Utils {
	private static final Pattern LANE_NUMBER = Pattern.compile("^.*_(\\d+)$");

	public static long parseLaneNumber(String laneName) {
		try {
			return Long.parseUnsignedLong(laneName);
		} catch (final NumberFormatException e) {
			// Try something else.
		}
		final Matcher laneMatcher = LANE_NUMBER.matcher(laneName);
		if (laneMatcher.matches()) {
			return parseLong(laneMatcher.group(1));
		}
		return 0;
	}

	public static long parseLong(String input) {
		try {
			return Long.parseLong(input);
		} catch (final NumberFormatException e) {
			return 0;
		}
	}


	public static <T> Optional<T> singleton(Collection<T> items, Consumer<String> isBad, boolean required) {
		if (items == null) {
			if (required) {
				isBad.accept("null");
			}
			return Optional.empty();
		}
		switch (items.size()) {
		case 0:
			if (required) {
				isBad.accept("empty");
			}
			return Optional.empty();
		case 1:
			return Optional.of(items.iterator().next());
		default:
			isBad.accept("multiple");
			return Optional.of(items.iterator().next());
		}
	}

	public static <T> Stream<T> stream(Collection<T> collection) {
		return collection == null ? Stream.empty() : collection.stream();
	}

	private Utils() {
	}
}
