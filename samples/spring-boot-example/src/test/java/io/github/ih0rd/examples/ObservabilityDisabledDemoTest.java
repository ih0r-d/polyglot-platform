package io.github.ih0rd.examples;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class ObservabilityDisabledDemoTest {

  @Test
  void servesPythonWithoutInvocationMeters() throws IOException, InterruptedException {
    ObservabilityDemoTest.verifyDemo(false);
  }
}
