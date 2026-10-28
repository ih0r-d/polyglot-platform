package io.github.ih0rd.polyglot.console.internal.web;

import java.util.*;

import org.jspecify.annotations.*;
import org.springframework.web.servlet.*;

import jakarta.servlet.http.*;

/** Additional local-only restriction. Application security filters always run first. */
public final class ConsoleAccess implements HandlerInterceptor {
  /** Creates the additional local-only guard; application security filters remain authoritative. */
  public ConsoleAccess() {
    // Stateless per-request policy.
  }

  private static final Set<String> PEERS = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
  private static final Set<String> HOSTS =
      Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, @NonNull Object handler) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader(
        "Content-Security-Policy",
        "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; "
            + "img-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
    if (!PEERS.contains(request.getRemoteAddr()) || !HOSTS.contains(request.getServerName())) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return false;
    }
    return true;
  }
}
