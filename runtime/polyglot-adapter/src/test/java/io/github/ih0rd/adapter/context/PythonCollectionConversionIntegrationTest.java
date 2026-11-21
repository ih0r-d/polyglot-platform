package io.github.ih0rd.adapter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;

class PythonCollectionConversionIntegrationTest {
  interface CollectionApi {
    List<?> values();
  }

  static Stream<Arguments> conversions() {
    return Stream.of(
        Arguments.of("[]", List.of(), true),
        Arguments.of("[1, 2]", List.of(1, 2), true),
        Arguments.of("()", List.of(), true),
        Arguments.of("(1, 2)", List.of(1, 2), true),
        Arguments.of("(1, 'two')", List.of(1, "two"), true),
        Arguments.of("set()", List.of(), false),
        Arguments.of("{1, 2}", List.of(), false));
  }

  @ParameterizedTest
  @MethodSource("conversions")
  void standardConversionMatchesTypedProxy(String expression, List<?> expected, boolean supported) {
    try (PyExecutor executor =
        PyExecutor.createWithContext(
            Context.newBuilder("python").allowAllAccess(true).build(),
            new InMemoryScriptSource(
                Map.of("python/collection_api", "def values(): return " + expression)))) {
      var client = executor.bind(CollectionApi.class, Convention.BY_METHOD_NAME);
      if (supported) {
        var actual =
            client.values().stream()
                .map(value -> value instanceof Number number ? number.longValue() : value)
                .toList();
        var normalized =
            expected.stream()
                .map(value -> value instanceof Number number ? number.longValue() : value)
                .toList();
        assertThat(actual).isEqualTo(normalized);
      } else {
        assertThatThrownBy(client::values).isInstanceOf(ClassCastException.class);
      }
    }
  }
}
