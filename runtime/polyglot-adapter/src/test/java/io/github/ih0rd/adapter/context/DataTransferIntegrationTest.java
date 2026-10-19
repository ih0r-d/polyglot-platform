package io.github.ih0rd.adapter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.spi.InMemoryScriptSource;

class DataTransferIntegrationTest {
  interface DataApi {
    int[] echoInts(int[] values);

    int[] copyInts(int[] values);

    int[] fromList(List<Integer> values);

    int[] guestInts();

    List<?> guestList();

    void setFirst(int value);

    ByteBuffer echoBuffer(ByteBuffer values);

    byte[] copyBuffer(ByteBuffer values);

    byte[] guestBytes();

    int[] nullElement();

    int[] overflow();

    int[] invalidElement();
  }

  private static final String SCRIPT =
      """
      class DataApi:
          def __init__(self):
              self.values = [1, 2, 3]
          def echoInts(self, values):
              return values
          def copyInts(self, values):
              return None if values is None else list(values)
          def fromList(self, values):
              return list(values)
          def guestInts(self):
              return self.values
          def guestList(self):
              return self.values
          def setFirst(self, value):
              self.values[0] = value
          def echoBuffer(self, values):
              return values
          def copyBuffer(self, values):
              return [values.get(i) for i in range(values.capacity())]
          def guestBytes(self):
              return bytes([0, 127, 128, 255])
          def nullElement(self):
              return [None]
          def overflow(self):
              return [2**80]
          def invalidElement(self):
              return ['not an int']
      """;

  @Test
  void primitiveArraysPreserveHostIdentityAndGuestCopiesAcrossSizes() {
    try (PyExecutor executor =
        executor(Context.newBuilder("python").allowAllAccess(true).build())) {
      DataApi client = executor.bind(DataApi.class);
      for (int size : new int[] {0, 100, 1000, 100000}) {
        int[] values = new int[size];
        for (int i = 0; i < size; i++) values[i] = i;
        assertThat(client.echoInts(values)).isSameAs(values);
        int[] copy = client.copyInts(values);
        assertThat(copy).containsExactly(values).isNotSameAs(values);
        if (size != 0) {
          copy[0] = -1;
          assertThat(values[0]).isZero();
        }
      }
      assertThat(client.echoInts(null)).isNull();
      assertThat(client.copyInts(null)).isNull();
      assertThat(client.fromList(List.of(-1, 0, Integer.MAX_VALUE)))
          .containsExactly(-1, 0, Integer.MAX_VALUE);
    }
  }

  @Test
  void guestArraysAreDetachedButListViewsRemainContextBound() {
    List<?> view;
    int[] detached;
    try (PyExecutor executor =
        executor(Context.newBuilder("python").allowAllAccess(true).build())) {
      DataApi client = executor.bind(DataApi.class);
      view = client.guestList();
      detached = client.guestInts();
      client.setFirst(99);
      assertThat(view.get(0)).isEqualTo(99);
      assertThat(detached).containsExactly(1, 2, 3);
    }
    assertThat(detached).containsExactly(1, 2, 3);
    assertThatThrownBy(() -> view.get(0)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void heapDirectAndReadOnlyBuffersPreserveIdentitySignedBytesAndPosition() {
    byte[] bytes = {-128, -1, 0, 127};
    ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length);
    direct.put(bytes).flip();
    try (PyExecutor executor =
        executor(Context.newBuilder("python").allowAllAccess(true).build())) {
      DataApi client = executor.bind(DataApi.class);
      for (ByteBuffer buffer :
          new ByteBuffer[] {ByteBuffer.wrap(bytes), direct, direct.asReadOnlyBuffer()}) {
        buffer.position(2);
        assertThat(client.echoBuffer(buffer)).isSameAs(buffer);
        assertThat(client.copyBuffer(buffer)).containsExactly(bytes);
        assertThat(buffer.position()).isEqualTo(2);
      }
      assertThat(client.guestBytes()).containsExactly((byte) 0, (byte) 127, (byte) -128, (byte) -1);
    }
  }

  @Test
  void configuredWholeArrayTargetMappingIsHonored() {
    HostAccess access =
        HostAccess.newBuilder(HostAccess.ALL)
            .targetTypeMapping(
                Value.class,
                int[].class,
                Value::hasArrayElements,
                value -> new int[] {99},
                HostAccess.TargetMappingPrecedence.HIGHEST)
            .build();
    try (PyExecutor executor =
        executor(
            Context.newBuilder("python").allowAllAccess(true).allowHostAccess(access).build())) {
      assertThat(executor.bind(DataApi.class).guestInts()).containsExactly(99);
    }
  }

  @Test
  void invalidArrayElementsKeepStandardValueConversionFailures() {
    try (Context context = Context.newBuilder("python").allowAllAccess(true).build();
        PyExecutor executor = executor(context)) {
      DataApi client = executor.bind(DataApi.class);
      Class<? extends Throwable> nullFailure =
          catchThrowable(() -> context.eval("python", "[None]").as(int[].class)).getClass();
      Class<? extends Throwable> overflowFailure =
          catchThrowable(() -> context.eval("python", "[2**80]").as(int[].class)).getClass();
      Class<? extends Throwable> invalidFailure =
          catchThrowable(() -> context.eval("python", "['not an int']").as(int[].class)).getClass();
      assertThatThrownBy(client::nullElement).isExactlyInstanceOf(nullFailure);
      assertThatThrownBy(client::overflow).isExactlyInstanceOf(overflowFailure);
      assertThatThrownBy(client::invalidElement).isExactlyInstanceOf(invalidFailure);
    }
  }

  private static PyExecutor executor(Context context) {
    String script =
        "if 'DataApi' not in globals():\n" + (SCRIPT + "\nDataApi = DataApi()\n").indent(4);
    return PyExecutor.createWithContext(
        context, new InMemoryScriptSource(Map.of("python/data_api", script)));
  }
}
