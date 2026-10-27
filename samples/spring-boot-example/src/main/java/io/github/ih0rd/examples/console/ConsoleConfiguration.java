package io.github.ih0rd.examples.console;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Sample-only developer console. Its assets are not exposed by the default static handler. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "showcase.console", name = "enabled", havingValue = "true")
public final class ConsoleConfiguration implements WebMvcConfigurer {
  private static final Set<String> LOOPBACK_ADDRESSES =
      Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
  private static final Set<String> LOCAL_HOSTS =
      Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");

  public ConsoleConfiguration(Environment environment) {
    if (!LOCAL_HOSTS.contains(environment.getProperty("server.address", ""))) {
      throw new ApplicationContextException(
          "The experimental console requires server.address=127.0.0.1, localhost or ::1");
    }
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/polyglot-console/**")
        .addResourceLocations("classpath:/polyglot-console/");
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/polyglot-console", "/polyglot-console/");
    registry
        .addViewController("/polyglot-console/")
        .setViewName("forward:/polyglot-console/index.html");
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new LocalConsoleAccess())
        .addPathPatterns("/polyglot-console", "/polyglot-console/**");
  }

  private static final class LocalConsoleAccess implements HandlerInterceptor {
    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("X-Content-Type-Options", "nosniff");
      response.setHeader("X-Frame-Options", "DENY");
      response.setHeader("Referrer-Policy", "no-referrer");
      response.setHeader(
          "Content-Security-Policy",
          "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; "
              + "img-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
      if (!LOOPBACK_ADDRESSES.contains(request.getRemoteAddr())
          || !LOCAL_HOSTS.contains(request.getServerName())) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return false;
      }
      return true;
    }
  }
}
