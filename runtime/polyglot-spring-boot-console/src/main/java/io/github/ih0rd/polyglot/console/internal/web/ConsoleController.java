package io.github.ih0rd.polyglot.console.internal.web;

import java.net.*;

import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;

import io.github.ih0rd.polyglot.console.internal.dto.ConsoleSnapshot;
import io.github.ih0rd.polyglot.console.internal.metrics.ConsoleSnapshotService;
import io.github.ih0rd.polyglot.console.properties.ConsoleProperties;
import jakarta.servlet.http.*;

/** Internal routes, registered explicitly only by the enabled auto-configuration. */
@Controller
@RequestMapping("${polyglot.console.path:/polyglot-console}")
public final class ConsoleController {
  private final ConsoleProperties properties;
  private final ConsoleSnapshotService data;

  /**
   * Creates internal UI/data routes.
   *
   * @param properties validated console path
   * @param data in-process snapshot service
   */
  public ConsoleController(ConsoleProperties properties, ConsoleSnapshotService data) {
    this.properties = properties;
    this.data = data;
  }

  @GetMapping
  ResponseEntity<Void> redirect(HttpServletRequest request) {
    return ResponseEntity.status(302)
        .location(URI.create(request.getContextPath() + properties.path() + "/"))
        .build();
  }

  @GetMapping("/")
  ResponseEntity<Resource> index() {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(new ClassPathResource("META-INF/polyglot-console/index.html"));
  }

  @GetMapping("/data")
  @ResponseBody
  ConsoleSnapshot snapshot() {
    return data.snapshot();
  }
}
