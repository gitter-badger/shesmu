package ca.on.oicr.gsi.shesmu.plugin.input;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a method in a value class is exported to the Shesmu compiler via
 * {@link InputFormat}
 *
 * <p>The name of the method must be a valid Shesmu identifier
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ShesmuVariable {
  /** Whether the value should be included in the automatic signature generation */
  boolean signable() default false;

  /** How times should be represented in JSON. */
  TimeFormat timeFormat() default TimeFormat.MILLIS_NUMERIC;

  /** A string containing the {@link Imyhat} type descriptor of the return type of the method */
  String type() default "";

  /** A set of gangs this variable should belong to */
  Gang[] gangs() default {};
}
