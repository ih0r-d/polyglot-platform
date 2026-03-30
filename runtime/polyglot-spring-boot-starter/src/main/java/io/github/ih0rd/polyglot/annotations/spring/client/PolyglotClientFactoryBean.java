package io.github.ih0rd.polyglot.annotations.spring.client;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.Assert;

import io.github.ih0rd.adapter.context.AbstractPolyglotExecutor;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.MissingPolyglotClientAnnotationException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientBindingException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientClassNotFoundException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientResolutionException;

/**
 * {@link FactoryBean} that creates a Spring-managed polyglot client.
 *
 * <p>The produced bean is backed by an {@link AbstractPolyglotExecutor} and delegates method calls
 * through the runtime adapter's {@code bind(Class)} mechanism.
 *
 * @param <T> Java client contract type
 */
public final class PolyglotClientFactoryBean<T> implements FactoryBean<T>, BeanFactoryAware {

  /** Java interface type annotated with {@link PolyglotClient}. */
  private final Class<T> clientType;

  /** Bean factory used to resolve executors lazily. */
  private BeanFactory beanFactory;

  /**
   * Creates a new factory bean for the given client interface.
   *
   * @param className fully qualified name of the client interface
   * @throws PolyglotClientClassNotFoundException if the class cannot be loaded
   */
  @SuppressWarnings("unchecked")
  public PolyglotClientFactoryBean(String className) {
    try {
      this.clientType = (Class<T>) Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new PolyglotClientClassNotFoundException(className, e);
    }
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  /** Creates the actual polyglot-backed client bean instance. */
  @Override
  public T getObject() {
    PolyglotExecutors executors = executors();
    PolyglotClient annotation = clientType.getAnnotation(PolyglotClient.class);
    if (annotation == null) {
      throw new MissingPolyglotClientAnnotationException(clientType.getName());
    }

    SupportedLanguage language = resolveLanguage(annotation);
    Convention convention = annotation.convention();
    @SuppressWarnings("resource")
    AbstractPolyglotExecutor executor = resolveExecutor(language);

    try {
      executor.validateBinding(clientType, convention);
      return executor.bind(clientType, convention);
    } catch (RuntimeException ex) {
      throw new PolyglotClientBindingException(clientType.getName(), language.id(), ex);
    }
  }

  /** Resolves which guest language should be used for the client contract. */
  private SupportedLanguage resolveLanguage(PolyglotClient annotation) {
    PolyglotExecutors executors = executors();
    SupportedLanguage[] languages = annotation.languages();

    boolean pyPresent = executors.python().isPresent();
    boolean jsPresent = executors.js().isPresent();

    // Explicit language
    if (languages.length == 1) {
      return languages[0];
    }

    if (languages.length > 1) {
      throw new PolyglotClientResolutionException(
          "Multiple languages specified for @PolyglotClient on " + clientType.getName());
    }

    // AUTO resolution (decision table)
    if (pyPresent) {
      if (jsPresent) {
        throw new PolyglotClientResolutionException(
            "Multiple polyglot executors available. "
                + "Specify language explicitly for "
                + clientType.getName());
      }
      return SupportedLanguage.PYTHON;
    }

    if (jsPresent) {
      return SupportedLanguage.JS;
    }

    throw new PolyglotClientResolutionException(
        "No polyglot executors available for " + clientType.getName());
  }

  /** Resolves the executor matching the selected guest language. */
  private AbstractPolyglotExecutor resolveExecutor(SupportedLanguage language) {
    PolyglotExecutors executors = executors();
    return switch (language) {
      case PYTHON -> require(executors.python().orElse(null), SupportedLanguage.PYTHON.id());
      case JS -> require(executors.js().orElse(null), SupportedLanguage.JS.id());
    };
  }

  /** Ensures that a required executor is present before binding the client. */
  private AbstractPolyglotExecutor require(AbstractPolyglotExecutor executor, String languageId) {
    if (executor == null) {
      throw new PolyglotClientResolutionException(
          "Polyglot executor not available for language: " + languageId);
    }
    return executor;
  }

  /** Returns the Java type produced by this factory bean. */
  @Override
  public Class<?> getObjectType() {
    return clientType;
  }

  private PolyglotExecutors executors() {
    Assert.state(beanFactory != null, "BeanFactory not initialized");
    return beanFactory.getBean(PolyglotExecutors.class);
  }
}
