package ca.on.oicr.gsi.shesmu.util;

import ca.on.oicr.gsi.status.ConfigurationSection;
import java.util.stream.Stream;

/** Report information on the status page */
public interface LoadedConfiguration {

  /**
   * Describe a list of configuration blocks for the status page
   *
   * @return a stream of pairs; each pair is the title of the block and a map of rows to be
   *     displayed
   */
  Stream<ConfigurationSection> listConfiguration();
}
