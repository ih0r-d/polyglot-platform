package io.github.ih0rd.polyglot.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class RepositoryArchitectureTest {

  private static final Pattern DEPENDENCY_ARTIFACT_PATTERN =
      Pattern.compile("<dependency>.*?<artifactId>([^<]+)</artifactId>.*?</dependency>", Pattern.DOTALL);
  @Test
  void api_modules_do_not_depend_on_runtime_spring_or_build_tools() throws IOException {
    Path root = repositoryRoot();

    List<Path> apiPoms =
        List.of(root.resolve("api/polyglot-annotations/pom.xml"), root.resolve("api/polyglot-model/pom.xml"));

    for (Path pom : apiPoms) {
      Set<String> dependencies = pomDependencies(pom);
      assertForbiddenDependencies(
          pom,
          dependencies,
          Set.of(
              "polyglot-adapter",
              "polyglot-spring-boot-starter",
              "polyglot-codegen",
              "polyglot-codegen-maven-plugin",
              "spring-boot-autoconfigure",
              "spring-boot-starter-actuator",
              "spring-boot-starter-test",
              "spring-boot-configuration-processor"));
    }
  }

  @Test
  void build_tools_do_not_depend_on_runtime_modules() throws IOException {
    Path root = repositoryRoot();

    List<Path> buildPoms =
        List.of(
            root.resolve("build-tools/polyglot-codegen/pom.xml"),
            root.resolve("build-tools/polyglot-codegen-maven-plugin/pom.xml"));

    for (Path pom : buildPoms) {
      Set<String> dependencies = pomDependencies(pom);
      assertForbiddenDependencies(pom, dependencies, Set.of("polyglot-adapter", "polyglot-spring-boot-starter"));
    }
  }

  @Test
  void runtime_core_does_not_depend_on_spring_starter_code() throws IOException {
    Path root = repositoryRoot();
    Path adapterPom = root.resolve("runtime/polyglot-adapter/pom.xml");

    Set<String> dependencies = pomDependencies(adapterPom);
    assertForbiddenDependencies(
        adapterPom,
        dependencies,
        Set.of(
            "polyglot-spring-boot-starter",
            "spring-boot-autoconfigure",
            "spring-boot-starter-actuator",
            "spring-boot-starter-test",
            "spring-boot-configuration-processor"));
  }

  @Test
  void internal_module_dependency_graph_has_no_cycles() throws IOException {
    Path root = repositoryRoot();
    Map<String, Set<String>> graph = internalModuleDependencyGraph(root);
    Set<String> visited = new LinkedHashSet<>();
    Set<String> visiting = new LinkedHashSet<>();

    for (String module : graph.keySet()) {
      detectCycle(module, graph, visited, visiting, new ArrayDeque<>());
    }
  }

  @Test
  void experimental_javascript_entrypoints_stay_explicitly_marked() throws IOException {
    Path root = repositoryRoot();

    assertContains(
        root.resolve("api/polyglot-annotations/src/main/java/io/github/ih0rd/polyglot/SupportedLanguage.java"),
        "@ExperimentalApi");
    assertContains(
        root.resolve("runtime/polyglot-adapter/src/main/java/io/github/ih0rd/adapter/context/JsExecutor.java"),
        "@ExperimentalApi");
    assertContains(
        root.resolve(
            "runtime/polyglot-spring-boot-starter/src/main/java/io/github/ih0rd/polyglot/annotations/spring/config/PolyglotJsAutoConfiguration.java"),
        "@ExperimentalApi");
    assertContains(
        root.resolve(
            "runtime/polyglot-spring-boot-starter/src/main/java/io/github/ih0rd/polyglot/annotations/spring/properties/PolyglotProperties.java"),
        "@ExperimentalApi");
  }

  private static void detectCycle(
      String module,
      Map<String, Set<String>> graph,
      Set<String> visited,
      Set<String> visiting,
      Deque<String> stack) {

    if (visited.contains(module)) {
      return;
    }

    if (visiting.contains(module)) {
      List<String> cycle = new ArrayList<>(stack);
      cycle.add(module);
      throw new AssertionError("Detected internal module dependency cycle: " + String.join(" -> ", cycle));
    }

    visiting.add(module);
    stack.addLast(module);
    for (String dependency : graph.getOrDefault(module, Set.of())) {
      detectCycle(dependency, graph, visited, visiting, stack);
    }
    stack.removeLast();
    visiting.remove(module);
    visited.add(module);
  }

  private static Map<String, Set<String>> internalModuleDependencyGraph(Path root) throws IOException {
    Map<String, Path> modulePoms =
        Map.of(
            "polyglot-annotations", root.resolve("api/polyglot-annotations/pom.xml"),
            "polyglot-model", root.resolve("api/polyglot-model/pom.xml"),
            "polyglot-adapter", root.resolve("runtime/polyglot-adapter/pom.xml"),
            "polyglot-spring-boot-starter", root.resolve("runtime/polyglot-spring-boot-starter/pom.xml"),
            "polyglot-codegen", root.resolve("build-tools/polyglot-codegen/pom.xml"),
            "polyglot-codegen-maven-plugin", root.resolve("build-tools/polyglot-codegen-maven-plugin/pom.xml"));

    Map<String, Set<String>> graph = new LinkedHashMap<>();
    Set<String> moduleIds = modulePoms.keySet();

    for (Map.Entry<String, Path> entry : modulePoms.entrySet()) {
      Set<String> dependencies = pomDependencies(entry.getValue());
      graph.put(
          entry.getKey(),
          dependencies.stream()
              .filter(moduleIds::contains)
              .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    return graph;
  }

  private static void assertForbiddenDependencies(Path pom, Set<String> dependencies, Set<String> forbidden) {
    Set<String> violating =
        dependencies.stream()
            .filter(forbidden::contains)
            .collect(Collectors.toCollection(TreeSet::new));

    assertTrue(
        violating.isEmpty(),
        () -> "Forbidden module dependency in " + pom + ": " + String.join(", ", violating));
  }

  private static Set<String> pomDependencies(Path pom) throws IOException {
    String content = Files.readString(pom);
    Matcher matcher = DEPENDENCY_ARTIFACT_PATTERN.matcher(content);
    Set<String> artifactIds = new LinkedHashSet<>();
    while (matcher.find()) {
      artifactIds.add(matcher.group(1).trim());
    }
    return artifactIds;
  }

  private static void assertContains(Path file, String expected) throws IOException {
    String content = Files.readString(file);
    assertTrue(content.contains(expected), () -> file + " is expected to contain " + expected);
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null) {
      if (looksLikeRepositoryRoot(current)) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Could not locate repository root from " + Path.of("").toAbsolutePath());
  }

  private static boolean looksLikeRepositoryRoot(Path candidate) {
    return Files.isDirectory(candidate.resolve("api"))
        && Files.isDirectory(candidate.resolve("runtime"))
        && Files.isDirectory(candidate.resolve("build-tools"))
        && Files.isRegularFile(candidate.resolve("pom.xml"));
  }
}
