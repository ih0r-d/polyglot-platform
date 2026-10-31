package io.github.ih0rd.adapter.observability;

/** Fixed operation boundaries emitted by an executor; durations may nest and must not be summed. */
public enum RuntimeOperation {
  INVOCATION_WAIT,
  INVOCATION_EXECUTION,
  SOURCE_LOAD,
  SCRIPT_EVALUATION,
  INLINE_EVALUATION,
  CONTRACT_VALIDATION,
  INSTANCE_CREATION,
  RELOAD
}
