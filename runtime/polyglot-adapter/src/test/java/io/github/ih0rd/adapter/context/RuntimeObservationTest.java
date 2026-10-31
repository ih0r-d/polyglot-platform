package io.github.ih0rd.adapter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.observability.InvalidationReason;
import io.github.ih0rd.adapter.observability.RuntimeCache;
import io.github.ih0rd.adapter.observability.RuntimeObserver;
import io.github.ih0rd.adapter.observability.RuntimeOperation;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

class RuntimeObservationTest {
  @Test
  void disabledObservationIsANoop() {
    assertThat(RuntimeObservation.start(null, RuntimeOperation.RELOAD)).isZero();
    assertThatCode(
            () -> {
              RuntimeObservation.finish(null, RuntimeOperation.RELOAD, 0, true);
              RuntimeObservation.cache(null, RuntimeCache.MODULE, true);
              RuntimeObservation.invalidate(
                  null, RuntimeCache.MODULE, InvalidationReason.RELOAD, 1);
              RuntimeObservation.closed(null);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void attachingOnlyCreatesOneObserverAndDoesNotAttachAfterClose() {
    RuntimeObserver observer = mock(RuntimeObserver.class);
    AtomicInteger creations = new AtomicInteger();
    try (PyExecutor executor =
        new PyExecutor(mock(Context.class), mock(ScriptSource.class), ContextOwnership.BORROWED)) {
      Supplier<RuntimeObserver> factory =
          () -> {
            creations.incrementAndGet();
            return observer;
          };
      assertThat(executor.attachRuntimeObserver(factory)).isTrue();
      assertThat(executor.attachRuntimeObserver(factory)).isFalse();
      executor.close();
      assertThat(executor.attachRuntimeObserver(factory)).isFalse();
      assertThat(creations).hasValue(1);
    }
    verify(observer).closed();
  }

  @Test
  void factoryCannotAttachAfterClosingTheExecutorReentrantly() {
    RuntimeObserver observer = mock(RuntimeObserver.class);
    try (PyExecutor executor =
        new PyExecutor(mock(Context.class), mock(ScriptSource.class), ContextOwnership.BORROWED)) {
      assertThat(
              executor.attachRuntimeObserver(
                  () -> {
                    executor.close();
                    return observer;
                  }))
          .isFalse();
      assertThat(executor.runtimeObserver()).isNull();
    }
  }

  @Test
  void callbackFailuresNeverEscapeIntoExecutionOrCleanup() {
    RuntimeObserver observer = mock(RuntimeObserver.class);
    IllegalStateException failure = new IllegalStateException("Observer failed");
    doThrow(failure).when(observer).started(any());
    doThrow(failure).when(observer).completed(any(), anyLong(), anyBoolean());
    doThrow(failure).when(observer).cacheAccess(any(), anyBoolean());
    doThrow(failure).when(observer).cacheInvalidated(any(), any(), anyInt());
    doThrow(failure).when(observer).closed();
    assertThatCode(
            () -> {
              long started = RuntimeObservation.start(observer, RuntimeOperation.RELOAD);
              RuntimeObservation.finish(observer, RuntimeOperation.RELOAD, started, true);
              RuntimeObservation.cache(observer, RuntimeCache.MODULE, false);
              RuntimeObservation.invalidate(
                  observer, RuntimeCache.MODULE, InvalidationReason.RELOAD, 1);
              RuntimeObservation.closed(observer);
            })
        .doesNotThrowAnyException();
  }
}
