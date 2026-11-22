package io.github.ih0rd.polyglot.annotations.spring.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.tools.ToolProvider;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.type.AnnotationMetadata;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.InvalidPolyglotClientTypeException;

class PolyglotClientRegistrarTest {

  @EnablePolyglotClients(basePackages = "io.github.ih0rd.polyglot.annotations.spring.client")
  static class ClientScanConfiguration {}

  @EnablePolyglotClients
  static class DefaultPackageScanConfiguration {}

  @EnablePolyglotClients(basePackages = "child.client")
  static class ChildClientScanConfiguration {}

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
  void registerBeanDefinitionsUsesImportingPackageWhenBasePackagesAreOmitted() {
    PolyglotClientRegistrar registrar = new PolyglotClientRegistrar();
    DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

    registrar.registerBeanDefinitions(
        AnnotationMetadata.introspect(DefaultPackageScanConfiguration.class), registry);

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
  void springCreatesClientFromChildClassLoader(@TempDir Path directory) throws Exception {
    String className = "child.client.ChildApi";
    Path source =
        Files.writeString(
            directory.resolve("ChildApi.java"),
            """
            package child.client;
            import io.github.ih0rd.polyglot.Convention;
            import io.github.ih0rd.polyglot.SupportedLanguage;
            import io.github.ih0rd.polyglot.annotations.PolyglotClient;
            @PolyglotClient(languages = SupportedLanguage.PYTHON, convention = Convention.BY_METHOD_NAME)
            public interface ChildApi { String greet(); }
            """);
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "--release",
                "21",
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                directory.toString(),
                source.toString()));
    ClassLoader parent = PolyglotClientFactoryBean.class.getClassLoader();
    assertThrows(ClassNotFoundException.class, () -> parent.loadClass(className));
    Thread thread = Thread.currentThread();
    ClassLoader original = thread.getContextClassLoader();
    try (var child = new URLClassLoader(new URL[] {directory.toUri().toURL()}, parent);
        var executor =
            PyExecutor.createWithContext(
                Context.newBuilder("python").build(),
                new InMemoryScriptSource(
                    Map.of("python/child_api", "def greet(): return 'child'")));
        var context = new AnnotationConfigApplicationContext()) {
      thread.setContextClassLoader(child);
      context.setClassLoader(child);
      context.registerBean(PolyglotExecutors.class, () -> new PolyglotExecutors(executor, null));
      context.register(ChildClientScanConfiguration.class);
      context.refresh();

      Class<?> clientType = child.loadClass(className);
      Object client = context.getBean(clientType);
      assertSame(child, client.getClass().getClassLoader());
      assertEquals("child", clientType.getMethod("greet").invoke(client));
    } finally {
      thread.setContextClassLoader(original);
    }
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
        validationInvocation(method, registrar, InvalidClient.class.getName()));
  }

  private Executable validationInvocation(
      Method method, PolyglotClientRegistrar registrar, String className) {
    return () -> invokeValidateClientType(method, registrar, className);
  }

  private void invokeValidateClientType(
      Method method, PolyglotClientRegistrar registrar, String className) {
    try {
      method.invoke(registrar, className, getClass().getClassLoader());
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(e.getCause());
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
