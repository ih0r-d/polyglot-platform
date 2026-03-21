package io.github.ih0rd.adapter.context;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.github.ih0rd.polyglot.SupportedLanguage;

@SuppressWarnings({"resource", "unchecked"})
class PolyglotHelperTest {

  @Test
  void pythonContextCreatedAndInitialized() {
    VirtualFileSystem vfs = mock(VirtualFileSystem.class);

    VirtualFileSystem.Builder vfsBuilder = mock(VirtualFileSystem.Builder.class);
    when(vfsBuilder.resourceDirectory(any())).thenReturn(vfsBuilder);
    when(vfsBuilder.build()).thenReturn(vfs);

    Context.Builder ctxBuilder = mock(Context.Builder.class, RETURNS_SELF);
    Context ctx = mock(Context.class);

    when(ctxBuilder.build()).thenReturn(ctx);

    try (MockedStatic<VirtualFileSystem> vfsStatic = mockStatic(VirtualFileSystem.class);
        MockedStatic<GraalPyResources> pyStatic = mockStatic(GraalPyResources.class)) {

      vfsStatic.when(VirtualFileSystem::newBuilder).thenReturn(vfsBuilder);

      pyStatic.when(() -> GraalPyResources.contextBuilder(vfs)).thenReturn(ctxBuilder);

      PolyglotHelper.newContext(SupportedLanguage.PYTHON);

      verify(ctxBuilder).build();
      verify(ctx).initialize(SupportedLanguage.PYTHON.id());
      verify(ctxBuilder).option("engine.WarnInterpreterOnly", "false");
      verify(ctxBuilder).option("python.WarnExperimentalFeatures", "false");
    }
  }

  @Test
  void jsContextCreatedAndInitialized() {
    Context.Builder builder = mock(Context.Builder.class, RETURNS_SELF);
    Context ctx = mock(Context.class);

    when(builder.build()).thenReturn(ctx);

    try (MockedStatic<Context> ctxStatic = mockStatic(Context.class)) {

      ctxStatic.when(() -> Context.newBuilder(SupportedLanguage.JS.id())).thenReturn(builder);

      PolyglotHelper.newContext(SupportedLanguage.JS);

      verify(builder).build();
      verify(ctx).initialize(SupportedLanguage.JS.id());
      verify(builder).option("engine.WarnInterpreterOnly", "false");
    }
  }

  @Test
  void customizerIsApplied() {
    Context.Builder builder = mock(Context.Builder.class, RETURNS_SELF);
    Context ctx = mock(Context.class);
    when(builder.build()).thenReturn(ctx);

    try (MockedStatic<Context> ctxStatic = mockStatic(Context.class)) {

      ctxStatic.when(() -> Context.newBuilder(SupportedLanguage.JS.id())).thenReturn(builder);

      Consumer<Context.Builder> customizer = mock(Consumer.class);

      PolyglotHelper.newContext(SupportedLanguage.JS, customizer);

      verify(customizer).accept(builder);
      verify(ctx).initialize(SupportedLanguage.JS.id());
    }
  }

  @Test
  void nullLanguageThrows() {
    assertThrows(NullPointerException.class, () -> PolyglotHelper.newContext(null, null));
  }

  @Test
  void defaultOverloadDelegates() {
    Context.Builder builder = mock(Context.Builder.class, RETURNS_SELF);
    Context ctx = mock(Context.class);

    when(builder.build()).thenReturn(ctx);

    try (MockedStatic<Context> ctxStatic = mockStatic(Context.class)) {

      ctxStatic.when(() -> Context.newBuilder("js")).thenReturn(builder);

      PolyglotHelper.newContext(SupportedLanguage.JS);

      verify(builder).build();
      verify(ctx).initialize(SupportedLanguage.JS.id());
    }
  }
}
