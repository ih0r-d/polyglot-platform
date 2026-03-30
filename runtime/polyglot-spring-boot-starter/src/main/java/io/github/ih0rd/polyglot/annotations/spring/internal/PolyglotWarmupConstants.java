package io.github.ih0rd.polyglot.annotations.spring.internal;

/** Shared script expressions used during starter warmup. */
public final class PolyglotWarmupConstants {

  private PolyglotWarmupConstants() {}

  /** No-op expression used to initialize guest runtimes safely. */
  public static final String NOOP_EXPRESSION = "1 + 1";
}
