package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Subgroup;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LaneSplittingGrouper<I, T, O> implements Grouper<I, O> {
  private final Function<Set<Long>, BiConsumer<O, I>> collectorForLanes;
  private final Function<I, Optional<T>> identifier;
  private final Function<I, Long> laneNumber;
  private final Function<I, List<List<Long>>> permittedMerges;

  public LaneSplittingGrouper(
      Function<I, List<List<Long>>> permittedMerges,
      Function<I, Optional<T>> identifier,
      Function<I, Long> laneNumber,
      Function<Set<Long>, BiConsumer<O, I>> collectorForLanes) {
    this.permittedMerges = permittedMerges;
    this.identifier = identifier;
    this.laneNumber = laneNumber;
    this.collectorForLanes = collectorForLanes;
  }

  @Override
  public Stream<Subgroup<I, O>> group(List<I> inputs) {
    final Set<List<List<Long>>> permittedMergedLanes =
        inputs.stream().map(permittedMerges).collect(Collectors.toSet());
    if (permittedMergedLanes.size() > 1) {
      // Each sample has a different idea of what merges are permitted by the flowcell. This is no
      // bueno...
      return Stream.empty();
    }
    final Map<Long, Long> canMerge = new TreeMap<>();
    for (List<Long> mergableLanes : permittedMergedLanes.iterator().next()) {
      final OptionalLong target = mergableLanes.stream().mapToLong(Long::longValue).min();
      if (!target.isPresent()) {
        continue;
      }
      // Make all these lanes point to the lowest numbered lane in the group; if a lane has been
      // multiply assigned, drop this mess.
      for (final Long lane : mergableLanes) {
        if (canMerge.put(lane, target.getAsLong()) != null) {
          return Stream.empty();
        }
      }
    }

    // Bin input by the lane it claims to be
    final Map<Long, List<I>> groups =
        inputs
            .stream()
            .collect(Collectors.groupingBy(laneNumber, TreeMap::new, Collectors.toList()));
    // If we weren't given any useful flow cell information (i.e., no mergable groups), just assume
    // everything goes in the same lane.
    final Function<Long, Long> targetLane =
        canMerge.isEmpty() ? Function.identity() : canMerge::get;

    Set<Long> lanes = null;
    Set<T> idsForLane = null;
    final Deque<Subgroup<I, O>> results = new ArrayDeque<>();
    for (Map.Entry<Long, List<I>> entry : groups.entrySet()) {
      final Long target = targetLane.apply(entry.getKey());
      // We are now given a lane that we don't know where to assign. Reject this whole thing.
      if (target == null) {
        return Stream.empty();
      }
      // First thing always goes in a new lane as does anything not in our current group
      if (results.isEmpty() || !lanes.contains(target)) {

        lanes = new TreeSet<>();
        results.add(new Subgroup<>(collectorForLanes.apply(lanes)));
        idsForLane = sampleIdsForLane(entry);
        lanes.add(entry.getKey());
        results.getLast().addAll(entry.getValue());
      } else {
        // If this isn't the first lane in a group, it should have no samples in it or the samples
        // must match the ones in the first lane
        final Set<T> idsForThisLane = sampleIdsForLane(entry);
        if (idsForThisLane.isEmpty() || idsForThisLane.equals(idsForLane)) {
          lanes.add(entry.getKey());
          // Add only the lane and dump the samples
          entry
              .getValue()
              .stream()
              .filter(v -> !identifier.apply(v).isPresent())
              .forEach(results.getLast()::add);
        } else {
          return Stream.empty();
        }
      }
    }
    return results.stream();
  }

  private Set<T> sampleIdsForLane(Map.Entry<Long, List<I>> entry) {
    return entry
        .getValue()
        .stream()
        .flatMap(v -> identifier.apply(v).map(Stream::of).orElseGet(Stream::empty))
        .collect(Collectors.toSet());
  }
}
