package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.lang.reflect.InvocationHandler;

import io.github.ih0rd.polyglot.SupportedLanguage;

/** Internal optional client decoration; keeps tracing classes out of ordinary client loading. */
public interface PolyglotInvocationTracing {
  /** Decorates once per binding; implementations must preserve invocation results and failures. */
  InvocationHandler decorate(InvocationHandler target, SupportedLanguage language);
}
