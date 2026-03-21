package io.github.ih0rd.polyglot.annotations.spring.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.type.AnnotationMetadata;

import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.InvalidPolyglotClientTypeException;

class PolyglotClientRegistrarTest {

  @EnablePolyglotClients(basePackages = "io.github.ih0rd.polyglot.annotations.spring.client")
  static class ClientScanConfiguration {}

  @PolyglotClient
  interface DiscoveredClient {}

  @PolyglotClient
  static class InvalidClient {}

  @Test
  void registerBeanDefinitionsRegistersDiscoveredInterfaces() {
    PolyglotClientRegistrar registrar = new PolyglotClientRegistrar();
    DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

    registrar.registerBeanDefinitions(
        AnnotationMetadata.introspect(ClientScanConfiguration.class), registry);

    assertEquals(
        PolyglotClientFactoryBean.class.getName(),
        registry.getBeanDefinition(DiscoveredClient.class.getName()).getBeanClassName());
  }

  @Test
  void registerBeanDefinitionsSkipsWhenAnnotationIsMissing() {
    PolyglotClientRegistrar registrar = new PolyglotClientRegistrar();
    DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

    registrar.registerBeanDefinitions(
        AnnotationMetadata.introspect(DiscoveredClient.class), registry);

    assertEquals(0, registry.getBeanDefinitionCount());
  }

  @Test
  void validateClientTypeRejectsAnnotatedClassesThatAreNotInterfaces() throws Exception {
    PolyglotClientRegistrar registrar = new PolyglotClientRegistrar();
    Method method =
        PolyglotClientRegistrar.class.getDeclaredMethod(
            "validateClientType", String.class, ClassLoader.class);
    method.setAccessible(true);

    assertThrows(
        InvalidPolyglotClientTypeException.class,
        () -> {
          try {
            method.invoke(registrar, InvalidClient.class.getName(), getClass().getClassLoader());
          } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
              throw runtimeException;
            }
            throw new RuntimeException(e.getCause());
          }
        });
  }
}
