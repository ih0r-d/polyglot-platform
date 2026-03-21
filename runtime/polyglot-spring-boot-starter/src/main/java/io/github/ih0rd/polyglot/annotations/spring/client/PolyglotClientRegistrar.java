package io.github.ih0rd.polyglot.annotations.spring.client;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.InvalidPolyglotClientTypeException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientClassNotFoundException;

/**
 * Scans and registers {@link PolyglotClient} interfaces as Spring bean definitions.
 *
 * <p>The registrar is activated through {@link EnablePolyglotClients} and creates {@link
 * PolyglotClientFactoryBean} definitions for discovered client interfaces.
 *
 * <p>Validation rules are intentionally strict: only Java interfaces are accepted as polyglot
 * clients.
 */
public final class PolyglotClientRegistrar implements ImportBeanDefinitionRegistrar {

  /**
   * Registers {@link PolyglotClientFactoryBean} definitions for each discovered client interface.
   *
   * @param metadata importing class metadata
   * @param registry bean definition registry
   */
  @Override
  public void registerBeanDefinitions(
      @NonNull AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry) {
    String[] basePackages = resolveBasePackages(metadata);

    if (basePackages.length == 0) {
      return;
    }

    var scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition bd) {
            return bd.getMetadata().isInterface();
          }
        };
    scanner.addIncludeFilter(new AnnotationTypeFilter(PolyglotClient.class));

    var classLoader = resolveClassLoader();

    for (String basePackage : basePackages) {
      for (var candidate : scanner.findCandidateComponents(basePackage)) {
        String className = candidate.getBeanClassName();
        if (className == null) {
          continue;
        }

        validateClientType(className, classLoader);

        var definition =
            BeanDefinitionBuilder.genericBeanDefinition(PolyglotClientFactoryBean.class)
                .addConstructorArgValue(className)
                .getBeanDefinition();

        registry.registerBeanDefinition(className, definition);
      }
    }
  }

  /**
   * Resolves base packages from {@link EnablePolyglotClients}.
   *
   * @param metadata importing class metadata
   * @return array of base packages, or empty if the annotation is not present
   */
  private String[] resolveBasePackages(AnnotationMetadata metadata) {

    String annotationName = EnablePolyglotClients.class.getName();

    if (!metadata.hasAnnotation(annotationName)) {
      return new String[0];
    }

    Map<String, Object> attrs =
        Objects.requireNonNull(metadata.getAnnotationAttributes(annotationName));

    String[] basePackages = (String[]) attrs.get("basePackages");
    return (basePackages != null) ? basePackages : new String[0];
  }

  /** Resolves the class loader used for scanning and client type validation. */
  private ClassLoader resolveClassLoader() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl != null) {
      return cl;
    }
    return getClass().getClassLoader();
  }

  /**
   * Validates that the discovered type is a valid polyglot client interface.
   *
   * @param className fully qualified class name
   * @param classLoader class loader used to load the type
   */
  private void validateClientType(String className, ClassLoader classLoader) {
    Class<?> type;
    try {
      type = ClassUtils.forName(className, classLoader);
    } catch (ClassNotFoundException e) {
      throw new PolyglotClientClassNotFoundException(
          "Polyglot client type not found: " + className, e);
    }

    if (!type.isInterface()) {
      throw new InvalidPolyglotClientTypeException(className);
    }

    if (type.isAnnotation()) {
      throw new InvalidPolyglotClientTypeException(className);
    }
  }
}
