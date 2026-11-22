package io.github.ih0rd.codegen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

/** Shared execution logic for Maven codegen goals. */
final class CodegenMojoSupport {

  private static final int MAX_DRIFT_MESSAGES = 10;

  private CodegenMojoSupport() {}

  enum Mode {
    GENERATE,
    CHECK
  }

  record Settings(
      File inputDirectory,
      File outputDirectory,
      String basePackage,
      String projectGroupId,
      boolean onlyIncludedMethods,
      boolean strictMode,
      boolean failOnNoContracts,
      boolean skipUnchanged,
      boolean failOnContractDrift,
      boolean emitPolyglotClient) {}

  record Summary(
      int supportedScripts,
      int generatedContracts,
      int writtenFiles,
      int skippedUnchangedFiles,
      int driftedFiles) {}

  private record ContractSource(Path script, ContractClass contract, String pythonScript) {}

  static void execute(Settings settings, Log log, Mode mode) throws MojoExecutionException {
    validateInputDirectory(settings.inputDirectory());

    String effectivePackage = resolveBasePackage(settings.basePackage(), settings.projectGroupId());
    Summary summary = runGeneration(settings, log, mode, effectivePackage);

    if (mode == Mode.CHECK && summary.driftedFiles() > 0) {
      throw new MojoExecutionException(
          "Detected "
              + summary.driftedFiles()
              + " generated file drift(s). Run "
              + "'mvn polyglot:generate' to refresh generated sources.");
    }

    if (settings.failOnContractDrift() && summary.driftedFiles() > 0) {
      throw new MojoExecutionException(
          "Detected "
              + summary.driftedFiles()
              + " generated file drift(s) with failOnContractDrift=true.");
    }

    log.info(
        "Codegen summary: scripts="
            + summary.supportedScripts()
            + ", contracts="
            + summary.generatedContracts()
            + ", writtenFiles="
            + summary.writtenFiles()
            + ", skippedUnchanged="
            + summary.skippedUnchangedFiles()
            + ", drifted="
            + summary.driftedFiles());

    if (mode == Mode.CHECK && summary.driftedFiles() == 0) {
      log.info("Codegen check passed: generated contracts are up-to-date.");
    }
  }

  private static void validateInputDirectory(File inputDirectory) throws MojoExecutionException {
    if (inputDirectory == null || !inputDirectory.exists()) {
      throw new MojoExecutionException("Input directory does not exist: " + inputDirectory);
    }
    if (!inputDirectory.isDirectory()) {
      throw new MojoExecutionException("Input path is not a directory: " + inputDirectory);
    }
  }

  private static String resolveBasePackage(String basePackage, String projectGroupId) {
    if (basePackage == null || basePackage.isBlank()) {
      return projectGroupId + ".polyglot";
    }
    return basePackage;
  }

  private static Summary runGeneration(
      Settings settings, Log log, Mode mode, String effectivePackage)
      throws MojoExecutionException {
    try {
      JavaContractValidator.validatePackageName(effectivePackage);
    } catch (JavaContractValidationException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    Path outputRoot = settings.outputDirectory().toPath();
    Path outputPackage = outputRoot.resolve(effectivePackage.replace('.', '/'));
    List<Path> scripts = scanScripts(settings.inputDirectory().toPath());
    Map<String, ContractSource> contracts = new LinkedHashMap<>();
    try (var generator = DefaultContractGenerator.openBatch()) {
      for (Path script : scripts) {
        collectContracts(generator, settings, script, outputPackage, contracts);
      }
    }
    if (settings.failOnNoContracts() && contracts.isEmpty()) {
      throw new MojoExecutionException(
          "No contracts generated from input directory: " + settings.inputDirectory());
    }

    var javaGenerator = new JavaInterfaceGenerator();
    Map<Path, String> sources = new LinkedHashMap<>();
    for (ContractSource source : contracts.values()) {
      try {
        String javaSource =
            javaGenerator.generate(
                source.contract(), effectivePackage, source.pythonScript(), contracts.keySet());
        sources.put(outputPackage.resolve(source.contract().name() + ".java"), javaSource);
      } catch (IllegalArgumentException e) {
        throw new MojoExecutionException(
            "Invalid generated Java contract in script " + source.script() + ": " + e.getMessage(),
            e);
      }
    }

    try {
      rejectStaleSources(outputPackage, sources.keySet(), log);
      boolean checkOnly = mode == Mode.CHECK || settings.failOnContractDrift();
      if (!checkOnly) {
        for (Path target : sources.keySet()) {
          if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !hasGeneratedHeader(target)) {
            throw new MojoExecutionException(
                "Refusing to overwrite a file without a codegen header: " + target);
          }
        }
        Files.createDirectories(sources.isEmpty() ? outputRoot : outputPackage);
      }
      return updateSources(scripts.size(), sources, settings.skipUnchanged(), checkOnly, log);
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Failed reconciling generated Java sources in " + outputPackage, e);
    }
  }

  private static List<Path> scanScripts(Path input) throws MojoExecutionException {
    try (Stream<Path> files = Files.walk(input)) {
      return files
          .filter(Files::isRegularFile)
          .filter(CodegenMojoSupport::isSupported)
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new MojoExecutionException("Failed scanning input directory", e);
    }
  }

  private static void collectContracts(
      DefaultContractGenerator generator,
      Settings settings,
      Path script,
      Path outputPackage,
      Map<String, ContractSource> contracts)
      throws MojoExecutionException {
    String fileName = Objects.requireNonNull(script.getFileName()).toString();
    SupportedLanguage language = SupportedLanguage.fromFileName(fileName);
    ContractModel model;
    try {
      var descriptor = new ScriptDescriptor(language, Files.readString(script), fileName);
      model =
          generator.generate(
              descriptor, new CodegenConfig(settings.onlyIncludedMethods(), settings.strictMode()));
      if (settings.strictMode()) ContractModelValidator.requireNoUnknownTypes(model);
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
      throw new MojoExecutionException("Failed processing script: " + script, e);
    }

    String pythonScript = null;
    if (settings.emitPolyglotClient() && language == SupportedLanguage.PYTHON) {
      String relative = settings.inputDirectory().toPath().relativize(script).toString();
      pythonScript =
          relative
              .substring(0, relative.length() - language.ext().length())
              .replace(File.separatorChar, '/');
    }
    for (ContractClass contract : model.classes()) {
      ContractSource previous =
          contracts.putIfAbsent(
              contract.name(), new ContractSource(script, contract, pythonScript));
      if (previous != null) {
        Path target = outputPackage.resolve(contract.name() + ".java");
        throw new MojoExecutionException(
            "Generated Java path collision for "
                + target
                + ": "
                + previous.script()
                + " and "
                + script);
      }
    }
  }

  private static void rejectStaleSources(Path outputPackage, Set<Path> expected, Log log)
      throws IOException, MojoExecutionException {
    if (!Files.isDirectory(outputPackage)) return;
    int stale = 0;
    try (var files = Files.list(outputPackage)) {
      for (Path file : files.sorted().toList()) {
        if (expected.contains(file)
            || !file.toString().endsWith(".java")
            || !hasGeneratedHeader(file)) continue;
        if (stale++ < MAX_DRIFT_MESSAGES) log.warn("Stale generated Java source: " + file);
      }
    }
    if (stale > 0) {
      throw new MojoExecutionException(
          "Detected "
              + stale
              + " stale generated Java source(s) in "
              + outputPackage
              + ". Review and remove the reported files before regenerating.");
    }
  }

  private static boolean hasGeneratedHeader(Path file) throws IOException {
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false;
    byte[] generatedHeader =
        JavaInterfaceGenerator.GENERATED_HEADER_PREFIX.getBytes(StandardCharsets.UTF_8);
    try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
      return Arrays.equals(generatedHeader, input.readNBytes(generatedHeader.length));
    }
  }

  private static Summary updateSources(
      int scripts, Map<Path, String> sources, boolean skipUnchanged, boolean checkOnly, Log log)
      throws IOException {
    int written = 0;
    int skipped = 0;
    int drifted = 0;
    for (var entry : sources.entrySet()) {
      Path target = entry.getKey();
      if (checkOnly) {
        if (isDrift(target, entry.getValue())) {
          drifted++;
          if (drifted <= MAX_DRIFT_MESSAGES) log.warn("Drift detected: " + target);
        }
      } else if (skipUnchanged && !isDrift(target, entry.getValue())) {
        skipped++;
        logDebug(log, "Unchanged: " + target);
      } else {
        Files.writeString(target, entry.getValue());
        written++;
        logInfo(log, "Generated: " + target);
      }
    }
    if (drifted > MAX_DRIFT_MESSAGES) {
      log.warn("... and " + (drifted - MAX_DRIFT_MESSAGES) + " more drifted file(s).");
    }
    return new Summary(scripts, sources.size(), written, skipped, drifted);
  }

  private static boolean isSupported(Path path) {
    try {
      SupportedLanguage.fromFileName(path.getFileName().toString());
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static boolean isDrift(Path target, String expectedContent) throws IOException {
    if (!Files.exists(target)) {
      return true;
    }
    String current = Files.readString(target);
    return !current.equals(expectedContent);
  }

  private static void logInfo(Log log, String message) {
    if (log != null) {
      log.info(message);
    }
  }

  private static void logDebug(Log log, String message) {
    if (log != null) {
      log.debug(message);
    }
  }
}
