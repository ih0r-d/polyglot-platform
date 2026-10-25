package io.github.ih0rd.polyglot.annotations.spring.internal;

import io.github.ih0rd.polyglot.SupportedLanguage;

/** Internal hook for observing invocations made through Spring-managed polyglot clients. */
public interface PolyglotInvocationObserver {

  /** Starts timing one client method invocation. */
  long start();

  /** Records elapsed time and the invocation outcome. */
  void recordInvocation(SupportedLanguage language, boolean failed, long startedAt);
}
