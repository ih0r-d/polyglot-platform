package io.github.ih0rd.adapter.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

class CompositeScriptSourceTest {

  @Mock private ScriptSource first;
  @Mock private ScriptSource second;
  @Mock private ScriptSource delegate;

  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  void existsReturnsTrueWhenAnyDelegateContainsScript() {
    when(first.exists(SupportedLanguage.PYTHON, "demo")).thenReturn(false);
    when(second.exists(SupportedLanguage.PYTHON, "demo")).thenReturn(true);

    CompositeScriptSource source = new CompositeScriptSource(List.of(first, second));

    assertTrue(source.exists(SupportedLanguage.PYTHON, "demo"));
  }

  @Test
  void existsReturnsFalseWhenNoDelegateContainsScript() {
    when(first.exists(SupportedLanguage.JS, "demo")).thenReturn(false);
    when(second.exists(SupportedLanguage.JS, "demo")).thenReturn(false);

    CompositeScriptSource source = new CompositeScriptSource(List.of(first, second));

    assertFalse(source.exists(SupportedLanguage.JS, "demo"));
  }

  @Test
  void openUsesFirstMatchingDelegate() throws Exception {
    Reader expected = new StringReader("print('ok')");

    when(first.exists(SupportedLanguage.PYTHON, "demo")).thenReturn(true);
    when(second.exists(SupportedLanguage.PYTHON, "demo")).thenReturn(true);
    when(first.open(SupportedLanguage.PYTHON, "demo")).thenReturn(expected);

    CompositeScriptSource source = new CompositeScriptSource(List.of(first, second));

    assertSame(expected, source.open(SupportedLanguage.PYTHON, "demo"));
    verify(first).open(SupportedLanguage.PYTHON, "demo");
  }

  @Test
  void openThrowsWhenScriptMissingFromAllDelegates() {
    when(delegate.exists(SupportedLanguage.PYTHON, "missing")).thenReturn(false);

    CompositeScriptSource source = new CompositeScriptSource(List.of(delegate));

    assertThrows(
        IllegalArgumentException.class, () -> source.open(SupportedLanguage.PYTHON, "missing"));
  }
}
