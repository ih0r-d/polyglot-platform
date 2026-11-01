package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.lang.reflect.InvocationHandler;

import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/** Application-parented synchronous spans without runtime observer changes. */
public final class PolyglotInvocationTracer implements PolyglotInvocationTracing {
  private static final System.Logger LOG =
      System.getLogger(PolyglotInvocationTracer.class.getName());
  private static final String CALLBACK_FAILURE = "Polyglot tracing callback failed";
  private final Tracer tracer;

  /** Uses the application-owned tracer; never creates an SDK or exporter. */
  public PolyglotInvocationTracer(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public InvocationHandler decorate(InvocationHandler target, SupportedLanguage language) {
    return (proxy, method, args) -> {
      Span span = start(language);
      if (span == null) return target.invoke(proxy, method, args);
      Tracer.SpanInScope scope;
      try {
        scope = tracer.withSpan(span);
      } catch (RuntimeException failure) {
        LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE);
        end(span);
        return target.invoke(proxy, method, args);
      }
      boolean failed = true;
      try {
        Object result = target.invoke(proxy, method, args);
        failed = false;
        return result;
      } finally {
        complete(span, scope, failed);
      }
    };
  }

  private Span start(SupportedLanguage language) {
    Span span = null;
    try {
      span = tracer.nextSpan();
      span.name("polyglot.invoke")
          .tag("polyglot.language", language.id())
          .tag("polyglot.operation", "invoke")
          .start();
      return span;
    } catch (RuntimeException failure) {
      LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE);
      if (span != null) end(span);
      return null;
    }
  }

  private static void complete(Span span, Tracer.SpanInScope scope, boolean failed) {
    try {
      scope.close();
    } catch (RuntimeException failure) {
      LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE);
    }
    try {
      span.tag("polyglot.outcome", failed ? "failure" : "success");
      if (failed) {
        span.tag("polyglot.error.category", "invocation");
        // Never pass the application/guest exception: exporters include its message and stack.
        InvocationException sanitized = new InvocationException("Polyglot invocation failed");
        sanitized.setStackTrace(new StackTraceElement[0]);
        span.error(sanitized);
      }
    } catch (RuntimeException failure) {
      LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE);
    } finally {
      end(span);
    }
  }

  private static void end(Span span) {
    try {
      span.end();
    } catch (RuntimeException failure) {
      LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE);
    }
  }
}
