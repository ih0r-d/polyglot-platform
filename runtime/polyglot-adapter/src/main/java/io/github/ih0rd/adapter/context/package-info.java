/**
 * Runtime executors and GraalVM context helpers used to bind Java contracts to guest-language
 * scripts.
 *
 * <p>{@link io.github.ih0rd.adapter.context.PyExecutor} is the primary public runtime entry point
 * for Python-backed contracts. {@link io.github.ih0rd.adapter.context.JsExecutor} is available for
 * bounded JavaScript experiments and remains explicitly experimental in the current release line.
 *
 * <p>Most users should create executors through the Spring Boot starter or the static factory
 * methods on the executor classes instead of subclassing runtime internals.
 */
package io.github.ih0rd.adapter.context;
