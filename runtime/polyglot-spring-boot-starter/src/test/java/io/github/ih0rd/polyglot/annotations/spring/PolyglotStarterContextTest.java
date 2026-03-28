package io.github.ih0rd.polyglot.annotations.spring;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(classes = PolyglotStarterContextTest.TestConfig.class)
class PolyglotStarterContextTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void contextLoads() {
    Assertions.assertNotNull(applicationContext);
  }

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {}
}
