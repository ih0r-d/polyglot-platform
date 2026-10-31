package io.github.ih0rd.polyglot.console.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;

import io.github.ih0rd.polyglot.annotations.ExperimentalApi;

/**
 * Experimental console settings. The console never changes application authorization.
 *
 * @param enabled Whether to expose the local read-only console.
 * @param path Absolute console path, composed of letters, digits, underscores and hyphens.
 * @param refreshInterval Browser polling interval, between one second and one minute.
 */
@ExperimentalApi
@ConfigurationProperties("polyglot.console")
public record ConsoleProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("/polyglot-console") String path,
    @DefaultValue("5s") Duration refreshInterval) {

  /** Validates path ownership and bounds browser polling frequency. */
  public ConsoleProperties {
    if (!validPath(path)) {
      throw new InvalidConfigurationPropertyValueException(
          "polyglot.console.path",
          path,
          "Use an absolute non-root path such as /polyglot-console; Actuator and error paths are reserved");
    }
    if (refreshInterval == null
        || refreshInterval.compareTo(Duration.ofSeconds(1)) < 0
        || refreshInterval.compareTo(Duration.ofMinutes(1)) > 0) {
      throw new InvalidConfigurationPropertyValueException(
          "polyglot.console.refresh-interval", refreshInterval, "Use a duration between 1s and 1m");
    }
  }

  private static boolean validPath(String path) {
    if (path == null
        || path.length() < 2
        || path.length() > 128
        || !path.startsWith("/")
        || path.startsWith("/actuator")
        || path.startsWith("/error")) {
      return false;
    }
    for (String segment : path.substring(1).split("/", -1)) {
      if (segment.isEmpty() || !segment.chars().allMatch(ConsoleProperties::pathCharacter)) {
        return false;
      }
    }
    return true;
  }

  private static boolean pathCharacter(int character) {
    return character >= 'a' && character <= 'z'
        || character >= 'A' && character <= 'Z'
        || character >= '0' && character <= '9'
        || character == '-'
        || character == '_';
  }
}
