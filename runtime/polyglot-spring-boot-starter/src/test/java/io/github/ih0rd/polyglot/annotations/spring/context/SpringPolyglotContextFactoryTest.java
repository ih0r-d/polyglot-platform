package io.github.ih0rd.polyglot.annotations.spring.context;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

class SpringPolyglotContextFactoryTest {

  @Test
  void createUsesPythonSafeDefaultsAndAppliesCustomizers() {
    PolyglotProperties properties =
        new PolyglotProperties(
            null,
            new PolyglotProperties.PythonProperties(
                true, "classpath:python", false, false, List.of()),
            null,
            null,
            null);
    ObjectProvider<PolyglotContextCustomizer> customizers = mock(ObjectProvider.class);
    PolyglotContextCustomizer customizer = mock(PolyglotContextCustomizer.class);
    when(customizers.orderedStream()).thenReturn(List.of(customizer).stream());

    Context context = mock(Context.class);
    AtomicBoolean customizeCalled = new AtomicBoolean(false);

    try (MockedStatic<PolyglotHelper> polyglotHelper = mockStatic(PolyglotHelper.class)) {
      polyglotHelper
          .when(() -> PolyglotHelper.newContext(eq(SupportedLanguage.PYTHON), eq(false), any()))
          .thenAnswer(
              invocation -> {
                invocation
                    .<java.util.function.Consumer<Context.Builder>>getArgument(2)
                    .accept(mock(Context.Builder.class));
                customizeCalled.set(true);
                return context;
              });

      SpringPolyglotContextFactory factory =
          new SpringPolyglotContextFactory(properties, customizers);

      assertSame(context, factory.create(SupportedLanguage.PYTHON));
      assertTrue(customizeCalled.get());
    }
  }

  @Test
  void createAlwaysUsesRecommendedDefaultsForJs() {
    ObjectProvider<PolyglotContextCustomizer> customizers = mock(ObjectProvider.class);
    when(customizers.orderedStream()).thenReturn(List.<PolyglotContextCustomizer>of().stream());
    Context context = mock(Context.class);

    try (MockedStatic<PolyglotHelper> polyglotHelper = mockStatic(PolyglotHelper.class)) {
      polyglotHelper
          .when(() -> PolyglotHelper.newContext(eq(SupportedLanguage.JS), eq(true), any()))
          .thenReturn(context);

      SpringPolyglotContextFactory factory =
          new SpringPolyglotContextFactory(
              new PolyglotProperties(null, null, null, null, null), customizers);

      assertSame(context, factory.create(SupportedLanguage.JS));
    }
  }
}
