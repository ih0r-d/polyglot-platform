package io.github.ih0rd.adapter.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EvaluationExceptionTest {
  @Test
  void messageOnly() {
    var e = new EvaluationException("boom");
    assertEquals("boom", e.getMessage());
    assertNull(e.getCause());
  }

  @Test
  void messageAndCause() {
    var c = new RuntimeException("cause");
    var e = new EvaluationException("fail", c);
    assertEquals("fail", e.getMessage());
    assertSame(c, e.getCause());
  }
}
