package io.github.ih0rd.adapter.exceptions;

/// # ScriptNotFoundException
///
/// Thrown when a guest language script/module cannot be found
/// on the classpath or filesystem.
public class ScriptNotFoundException extends EvaluationException {

  public ScriptNotFoundException(String message) {
    super(message);
  }

  public ScriptNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
