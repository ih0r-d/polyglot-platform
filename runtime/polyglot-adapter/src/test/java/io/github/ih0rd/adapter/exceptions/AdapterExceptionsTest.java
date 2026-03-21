package io.github.ih0rd.adapter.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class AdapterExceptionsTest {

  @Test
  void bindingExceptionConstructorsPreserveMessageAndCause() {
    RuntimeException cause = new RuntimeException("binding-cause");

    BindingException messageOnly = new BindingException("binding");
    BindingException withCause = new BindingException("binding", cause);

    assertEquals("binding", messageOnly.getMessage());
    assertEquals("binding", withCause.getMessage());
    assertSame(cause, withCause.getCause());
  }

  @Test
  void invocationExceptionConstructorsPreserveMessageAndCause() {
    RuntimeException cause = new RuntimeException("invocation-cause");

    InvocationException messageOnly = new InvocationException("invocation");
    InvocationException withCause = new InvocationException("invocation", cause);

    assertEquals("invocation", messageOnly.getMessage());
    assertEquals("invocation", withCause.getMessage());
    assertSame(cause, withCause.getCause());
  }

  @Test
  void scriptNotFoundExceptionConstructorsPreserveMessageAndCause() {
    RuntimeException cause = new RuntimeException("script-cause");

    ScriptNotFoundException messageOnly = new ScriptNotFoundException("missing");
    ScriptNotFoundException withCause = new ScriptNotFoundException("missing", cause);

    assertEquals("missing", messageOnly.getMessage());
    assertEquals("missing", withCause.getMessage());
    assertSame(cause, withCause.getCause());
  }
}
