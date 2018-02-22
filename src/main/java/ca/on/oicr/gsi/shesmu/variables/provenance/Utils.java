package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.on.oicr.gsi.provenance.ProviderLoader;

public final class Utils {
	private static final Pattern LANE_NUMBER = Pattern.compile("^.*_(\\d+)$");

	public static final Optional<ProviderLoader> LOADER = Optional.ofNullable(System.getenv("PROVENANCE_SETTINGS"))//
			.map(Paths::get)//
			.flatMap(path -> {
				try {
					return Optional.of(new ProviderLoader(new String(Files.readAllBytes(path))));

				} catch (final Exception e) {
					e.printStackTrace();
					return Optional.empty();
				}
			});

	public static long parseLaneNumber(String laneName) {
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

	public static Optional<String> singleton(Collection<String> items, Runnable isBad) {
		switch (items.size()) {
		case 0:
			isBad.run();
			return Optional.empty();
		case 1:
			return Optional.of(items.iterator().next());
		default:
			isBad.run();
			return Optional.of(items.iterator().next());
		}
	}

	private Utils() {
	}
}
