package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.SampleProvenance;
import ca.on.oicr.gsi.shesmu.cerberus.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.cache.MergingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.pinery.client.HttpResponseException;
import ca.on.oicr.pinery.client.PineryClient;
import ca.on.oicr.ws.dto.InstrumentModelDto;
import ca.on.oicr.ws.dto.RunDto;
import ca.on.oicr.ws.dto.SampleProjectDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;

public class PinerySource extends JsonPluginFile<PineryConfiguration> {
  private final class ItemCache extends ValueCache<Stream<PineryIUSValue>> {
    private ItemCache(Path fileName) {
      super("pinery " + fileName.toString(), 30, ReplacingRecord::new);
    }

    @Override
    protected Stream<PineryIUSValue> fetch(Instant lastUpdated) throws Exception {
      if (!config.isPresent()) {
        return Stream.empty();
      }
      final PineryConfiguration cfg = config.get();
      try (PineryClient c = new PineryClient(cfg.getUrl(), true)) {
        final Map<String, Integer> badSetCounts = new TreeMap<>();
        final Map<String, RunDto> allRuns =
            c.getSequencerRun()
                .all()
                .stream()
                .collect(
                    Collectors.toMap(
                        RunDto::getName,
                        Function.identity(),
                        // If duplicate run names occur, pick one at random, because the universe is
                        // spiteful, so we spite it back.
                        (a, b) -> a));
        final Set<String> completeRuns =
            allRuns
                .values()
                .stream()
                .filter(
                    run ->
                        run.getState().equals("Completed")
                            && run.getCreatedDate() != null
                            && run.getRunDirectory() != null
                            && !run.getRunDirectory().equals(""))
                .map(RunDto::getName)
                .collect(Collectors.toSet());
        final Set<Pair<String, String>> validLanes = new HashSet<>();
        return Stream.concat(
                lanes(
                    c,
                    cfg.getVersion(),
                    cfg.getProvider(),
                    badSetCounts,
                    allRuns,
                    (run, lane) -> validLanes.add(new Pair<>(run, lane))),
                samples(
                    c,
                    cfg.getVersion(),
                    cfg.getProvider(),
                    badSetCounts,
                    allRuns,
                    (run, lane) -> validLanes.contains(new Pair<>(run, lane))))
            .onClose(
                () ->
                    badSetCounts
                        .entrySet()
                        .forEach(
                            e ->
                                badSetMap
                                    .labels(
                                        Stream.concat(
                                                Stream.of(fileName().toString()),
                                                Stream.of(e.getKey().split(":")))
                                            .toArray(String[]::new))
                                    .set(e.getValue())));
      }
    }

    private Stream<PineryIUSValue> lanes(
        PineryClient client,
        String version,
        String provider,
        Map<String, Integer> badSetCounts,
        Map<String, RunDto> allRuns,
        BiConsumer<String, String> addLane)
        throws HttpResponseException {
      return Utils.stream(client.getLaneProvenance().version(version))
          .filter(
              lp ->
                  isRunValid(allRuns.get(lp.getSequencerRunName()))
                      && (lp.getSkip() == null || !lp.getSkip()))
          .map(
              lp -> {
                final RunDto run = allRuns.get(lp.getSequencerRunName());
                if (run == null) {
                  badSetCounts.merge("run:null", 1, Integer::sum);
                  return null;
                }
                final Instant lastModified =
                    lp.getLastModified() == null ? Instant.EPOCH : lp.getLastModified().toInstant();
                addLane.accept(lp.getSequencerRunName(), lp.getLaneNumber());

                return new PineryIUSValue(
                    Paths.get(run.getRunDirectory() == null ? "/" : run.getRunDirectory()),
                    "",
                    "",
                    "",
                    "",
                    new Tuple(
                        lp.getSequencerRunName(),
                        IUSUtils.parseLaneNumber(lp.getLaneNumber()),
                        "NoIndex"),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    0L,
                    "",
                    "",
                    lastModified,
                    new Tuple(lp.getLaneProvenanceId(), provider, lastModified, lp.getVersion()),
                    lp.getCreatedDate() == null ? Instant.EPOCH : lp.getCreatedDate().toInstant(),
                    run.getRunBasesMask() == null ? "" : run.getRunBasesMask(),
                    lp.getSequencerRunPlatformModel(),
                    Optional.empty(),
                    Optional.empty(),
                    getRunField(run, RunDto::getState),
                    false,
                    maybeGetRunField(run, RunDto::getSequencingKit),
                    maybeGetRunField(run, RunDto::getContainerModel),
                    false);
              })
          .filter(Objects::nonNull);
    }

    private Stream<PineryIUSValue> samples(
        PineryClient client,
        String version,
        String provider,
        Map<String, Integer> badSetCounts,
        Map<String, RunDto> allRuns,
        BiPredicate<String, String> hasLane)
        throws HttpResponseException {
      return Utils.stream(client.getSampleProvenance().version(version))
          .filter(
              sp ->
                  isRunValid(allRuns.get(sp.getSequencerRunName()))
                      && hasLane.test(sp.getSequencerRunName(), sp.getLaneNumber()))
          .map(
              sp -> {
                final Set<String> badSetInRecord = new TreeSet<>();
                final Instant lastModified =
                    sp.getLastModified() == null ? Instant.EPOCH : sp.getLastModified().toInstant();
                final RunDto run = allRuns.get(sp.getSequencerRunName());
                if (run == null) return null;
                final PineryIUSValue result =
                    new PineryIUSValue(
                        Paths.get(run.getRunDirectory() == null ? "/" : run.getRunDirectory()),
                        sp.getStudyTitle(),
                        limsAttr(sp, "geo_organism", badSetInRecord::add, true).orElse(""),
                        sp.getSampleName(),
                        sp.getRootSampleName(),
                        new Tuple(
                            sp.getSequencerRunName(),
                            IUSUtils.parseLaneNumber(sp.getLaneNumber()),
                            sp.getIusTag()),
                        limsAttr(sp, "geo_library_source_template_type", badSetInRecord::add, true)
                            .orElse(""),
                        limsAttr(sp, "geo_tissue_type", badSetInRecord::add, true).orElse(""),
                        limsAttr(sp, "geo_tissue_origin", badSetInRecord::add, true).orElse(""),
                        limsAttr(sp, "geo_tissue_preparation", badSetInRecord::add, false)
                            .orElse(""),
                        limsAttr(sp, "geo_targeted_resequencing", badSetInRecord::add, false)
                            .orElse(""),
                        limsAttr(sp, "geo_tissue_region", badSetInRecord::add, false).orElse(""),
                        limsAttr(sp, "geo_group_id", badSetInRecord::add, false).orElse(""),
                        limsAttr(sp, "geo_group_id_description", badSetInRecord::add, false)
                            .orElse(""),
                        limsAttr(sp, "geo_library_size_code", badSetInRecord::add, false)
                            .map(IUSUtils::parseLong)
                            .orElse(0L),
                        limsAttr(sp, "geo_library_type", badSetInRecord::add, false).orElse(""),
                        limsAttr(sp, "geo_prep_kit", badSetInRecord::add, false).orElse(""),
                        lastModified,
                        new Tuple(
                            sp.getSampleProvenanceId(), provider, lastModified, sp.getVersion()),
                        sp.getCreatedDate() == null
                            ? Instant.EPOCH
                            : sp.getCreatedDate().toInstant(),
                        run.getRunBasesMask() == null ? "" : run.getRunBasesMask(),
                        sp.getSequencerRunPlatformModel(),
                        limsAttr(sp, "dv200", badSetInRecord::add, false).map(Double::parseDouble),
                        limsAttr(sp, "rin", badSetInRecord::add, false).map(Double::parseDouble),
                        getRunField(run, RunDto::getState),
                        limsAttr(sp, "umis", badSetInRecord::add, false)
                            .map(Boolean::parseBoolean)
                            .orElse(false),
                        maybeGetRunField(run, RunDto::getSequencingKit),
                        maybeGetRunField(run, RunDto::getContainerModel),
                        true);

                if (badSetInRecord.isEmpty()) {
                  return result;
                } else {
                  badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, Integer::sum));
                  return null;
                }
              })
          .filter(Objects::nonNull);
    }
  }

  private final class PlatformCache extends ValueCache<Optional<Map<String, String>>> {
    private PlatformCache(Path fileName) {
      super("pinery-platform " + fileName.toString(), 30, SimpleRecord::new);
    }

    @Override
    protected Optional<Map<String, String>> fetch(Instant lastUpdated) throws Exception {
      if (!config.isPresent()) {
        return Optional.empty();
      }
      final PineryConfiguration cfg = config.get();
      try (PineryClient c = new PineryClient(cfg.getUrl(), true)) {
        return Optional.of(
            c.getInstrumentModel()
                .all()
                .stream()
                .filter(i -> !i.getName().equals("unspecified"))
                .collect(
                    Collectors.toMap(
                        InstrumentModelDto::getName, InstrumentModelDto::getPlatform)));
      }
    }
  }

  private class ProjectCache extends ValueCache<Stream<SampleProjectDto>> {

    public ProjectCache(Path fileName) {
      super(
          "pinery_projects " + fileName.toString(),
          60,
          MergingRecord.by(SampleProjectDto::getName));
    }

    @Override
    protected Stream<SampleProjectDto> fetch(Instant lastUpdated) throws Exception {
      if (!config.isPresent()) return Stream.empty();
      try (final PineryClient client = new PineryClient(config.get().getUrl())) {
        return client.getSampleProject().all().stream();
      }
    }
  }

  private static boolean isRunValid(RunDto run) {
    return run != null
        && run.getCreatedDate() != null
        && run.getRunDirectory() != null
        && !run.getRunDirectory().equals("");
  }

  private static Optional<String> limsAttr(
      SampleProvenance sp, String key, Consumer<String> isBad, boolean required) {
    return IUSUtils.singleton(
        sp.getSampleAttributes().get(key), reason -> isBad.accept(key + ":" + reason), required);
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Gauge badSetMap =
      Gauge.build(
              "shesmu_pinery_bad_set",
              "The number of provenace records with sets not containing exactly one item.")
          .labelNames("target", "property", "reason")
          .register();
  private final ItemCache cache;
  private Optional<PineryConfiguration> config = Optional.empty();
  private final PlatformCache platforms;
  private final ProjectCache projects;

  public PinerySource(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, PineryConfiguration.class);
    projects = new ProjectCache(fileName);
    cache = new ItemCache(fileName);
    platforms = new PlatformCache(fileName);
  }

  @ShesmuMethod(
      name = "$_active_projects",
      description = "Projects marked active from in Pinery defined in {file}.")
  public Set<String> activeProjects() {
    return projects
        .get()
        .filter(SampleProjectDto::isActive)
        .map(SampleProjectDto::getName)
        .collect(Collectors.toSet());
  }

  @ShesmuMethod(name = "$_projects", description = "All projects from in Pinery defined in {file}.")
  public Set<String> allProjects() {
    return projects.get().map(SampleProjectDto::getName).collect(Collectors.toSet());
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    final Optional<String> url = config.map(PineryConfiguration::getUrl);
    renderer.link("URL", url.orElse("about:blank"), url.orElse("Unknown"));
    renderer.line("Provider", config.map(PineryConfiguration::getProvider).orElse("Unknown"));
  }

  private String getRunField(RunDto run, Function<RunDto, String> getField) {
    Optional<String> val = maybeGetRunField(run, getField);
    return val.orElse("");
  }

  private Optional<String> maybeGetRunField(RunDto run, Function<RunDto, String> getField) {
    Optional<String> maybeVal = Optional.empty();
    if (run != null) {
      maybeVal = Optional.ofNullable(getField.apply(run));
    }
    return maybeVal;
  }

  @ShesmuMethod(
      name = "platform_for_$_instrument_model",
      description = "The the platform of the instrument model for the Pinery defined in {file}.")
  public String platformForInstrumentModel(
      @ShesmuParameter(description = "The instrument model name as found in Pinery")
          String instrumentModel) {
    return platforms.get().orElse(Collections.emptyMap()).getOrDefault(instrumentModel, "UNKNOWN");
  }

  @Override
  public Stream<String> services() {
    return Stream.of("pinery");
  }

  @ShesmuInputSource
  public Stream<PineryIUSValue> streamIUS() {
    return cache.get();
  }

  @ShesmuInputSource
  public Stream<PineryProjectValue> streamProjects() {
    return projects.get().map(PineryProjectValue::new);
  }

  @Override
  protected Optional<Integer> update(PineryConfiguration value) {
    config = Optional.of(value);
    projects.invalidate();
    cache.invalidate();
    return Optional.empty();
  }
}
