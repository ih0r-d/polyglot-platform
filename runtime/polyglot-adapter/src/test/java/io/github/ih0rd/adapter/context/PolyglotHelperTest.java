package io.github.ih0rd.adapter.context;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.github.ih0rd.polyglot.SupportedLanguage;

@SuppressWarnings({"resource", "unchecked"})
class PolyglotHelperTest {

  @Test
  void pythonContextCreatedAndInitialized() throws IOException {
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
      verify(vfs, never()).close();
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

  @Test
  void initializationFailureClosesOnlyNewContext() {
    assertFailedInitialization(new IllegalStateException("initialization"), null);
  }

  @Test
  void initializationErrorIsPreserved() {
    assertFailedInitialization(new AssertionError("initialization"), null);
  }

  @Test
  void cleanupFailureIsSuppressedOnOriginalFailure() {
    assertFailedInitialization(
        new IllegalStateException("initialization"), new IllegalStateException("cleanup"));
  }

  private void assertFailedInitialization(Throwable failure, Throwable cleanup) {
    Context.Builder builder = mock(Context.Builder.class, RETURNS_SELF);
    Context context = mock(Context.class);
    Engine engine = mock(Engine.class);
    when(builder.build()).thenReturn(context);
    when(context.initialize("js")).thenThrow(failure);
    if (cleanup != null) doThrow(cleanup).when(context).close();
    try (MockedStatic<Context> contexts = mockStatic(Context.class)) {
      contexts.when(() -> Context.newBuilder("js")).thenReturn(builder);
      Throwable actual =
          assertThrows(
              failure.getClass(),
              () -> PolyglotHelper.newContext(SupportedLanguage.JS, b -> b.engine(engine)));
      assertSame(failure, actual);
      assertArrayEquals(
          cleanup == null ? new Throwable[0] : new Throwable[] {cleanup}, actual.getSuppressed());
      verify(context).close();
      verifyNoInteractions(engine);
    }
  }

  @Test
  void pythonFailureClosesVfsEvenWhenContextCleanupFails() throws IOException {
    var vfs = mock(VirtualFileSystem.class);
    var vfsBuilder = mock(VirtualFileSystem.Builder.class, RETURNS_SELF);
    when(vfsBuilder.build()).thenReturn(vfs);
    var builder = mock(Context.Builder.class, RETURNS_SELF);
    var context = mock(Context.class);
    var failure = new IllegalStateException("initialization");
    var contextCleanup = new IllegalStateException("context cleanup");
    var vfsCleanup = new IOException("vfs cleanup");
    when(builder.build()).thenReturn(context);
    when(context.initialize("python")).thenThrow(failure);
    doThrow(contextCleanup).when(context).close();
    doThrow(vfsCleanup).when(vfs).close();
    try (MockedStatic<VirtualFileSystem> filesystems = mockStatic(VirtualFileSystem.class);
        MockedStatic<GraalPyResources> resources = mockStatic(GraalPyResources.class)) {
      filesystems.when(VirtualFileSystem::newBuilder).thenReturn(vfsBuilder);
      resources.when(() -> GraalPyResources.contextBuilder(vfs)).thenReturn(builder);
      assertSame(
          failure,
          assertThrows(
              IllegalStateException.class,
              () -> PolyglotHelper.newContext(SupportedLanguage.PYTHON)));
      verify(context).close();
      verify(vfs).close();
      assertArrayEquals(new Throwable[] {contextCleanup, vfsCleanup}, failure.getSuppressed());
    }
  }

  @Test
  void pythonCustomizerFailureClosesVfsBeforeContextCreation() throws IOException {
    var vfs = mock(VirtualFileSystem.class);
    var vfsBuilder = mock(VirtualFileSystem.Builder.class, RETURNS_SELF);
    when(vfsBuilder.build()).thenReturn(vfs);
    var builder = mock(Context.Builder.class, RETURNS_SELF);
    var failure = new IllegalArgumentException("configuration");
    try (MockedStatic<VirtualFileSystem> filesystems = mockStatic(VirtualFileSystem.class);
        MockedStatic<GraalPyResources> resources = mockStatic(GraalPyResources.class)) {
      filesystems.when(VirtualFileSystem::newBuilder).thenReturn(vfsBuilder);
      resources.when(() -> GraalPyResources.contextBuilder(vfs)).thenReturn(builder);
      assertSame(
          failure,
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  PolyglotHelper.newContext(
                      SupportedLanguage.PYTHON,
                      b -> {
                        throw failure;
                      })));
      verify(vfs).close();
      verify(builder, never()).build();
    }
  }
}
