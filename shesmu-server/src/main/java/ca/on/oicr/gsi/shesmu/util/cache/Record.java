package ca.on.oicr.gsi.shesmu.util.cache;

import java.time.Instant;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import io.prometheus.client.Counter;

/**
 * A record stored in a cache of some kind
 *
 * @param <V>
 *            the type of cached item
 */
public interface Record<V> {
	public static final Counter staleRefreshError = Counter
			.build("shesmu_cache_refresh_error",
					"Attempted to refresh a value stored in cache, but the refresh failed.")
			.labelNames("name").register();

	public static final LatencyHistogram refreshLatency = new LatencyHistogram("shesmu_cache_refresh_latency",
			"Attempted to refresh a value stored in cache, but the refresh failed.", "name");

	/**
	 * Force the cached item to be reloaded on the next use.
	 */
	void invalidate();

	/**
	 * Get the last time the item was updated
	 */
	Instant lastUpdate();

	/**
	 * Get the current item value, fetching if necessary
	 */
	V refresh();

	/**
	 * The number of items stored in this cache record
F	 */
	int collectionSize();
}