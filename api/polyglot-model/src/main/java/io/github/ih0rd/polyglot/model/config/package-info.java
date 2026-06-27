/**
 * Configuration and script source abstractions shared by parser, runtime, and Spring integration
 * layers.
 *
 * <p>{@link io.github.ih0rd.polyglot.model.config.ScriptSource} is the public contract used by
 * executors to resolve Python scripts and other guest-language resources without depending on a
 * specific storage mechanism.
 */
package io.github.ih0rd.polyglot.model.config;
