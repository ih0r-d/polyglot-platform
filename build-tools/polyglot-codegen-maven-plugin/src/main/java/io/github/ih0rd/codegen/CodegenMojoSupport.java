package io.github.ih0rd.codegen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import io.github.ih0rd.codegen.model.ScriptProcessingContext;
import io.github.ih0rd.codegen.model.SummaryCounters;
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
      boolean failOnContractDrift) {}

  record Summary(
      int supportedScripts,
      int generatedContracts,
      int writtenFiles,
      int skippedUnchangedFiles,
      int driftedFiles) {}

  static void execute(Settings settings, Log log, Mode mode) throws MojoExecutionException {
    validateInputDirectory(settings.inputDirectory());

    Path outputRoot = settings.outputDirectory().toPath();
    if (mode == Mode.GENERATE && !settings.failOnContractDrift()) {
      createOutputDirectory(outputRoot);
    }

    String effectivePackage = resolveBasePackage(settings.basePackage(), settings.projectGroupId());
    Summary summary = runGeneration(settings, log, mode, outputRoot, effectivePackage);

    if (settings.failOnNoContracts() && summary.generatedContracts() == 0) {
      throw new MojoExecutionException(
          "No contracts generated from input directory: " + settings.inputDirectory());
    }

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

  private static void createOutputDirectory(Path outputRoot) throws MojoExecutionException {
    try {
      Files.createDirectories(outputRoot);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to create output directory", e);
    }
  }

  private static String resolveBasePackage(String basePackage, String projectGroupId) {
    if (basePackage == null || basePackage.isBlank()) {
      return projectGroupId + ".polyglot";
    }
    return basePackage;
  }

  private static Summary runGeneration(
      Settings settings, Log log, Mode mode, Path outputRoot, String effectivePackage)
      throws MojoExecutionException {
    ContractGenerator generator = new DefaultContractGenerator();
    JavaInterfaceGenerator javaGenerator = new JavaInterfaceGenerator();

    ScriptProcessingContext context =
        new ScriptProcessingContext(
            generator,
            javaGenerator,
            outputRoot,
            mode == Mode.CHECK || settings.failOnContractDrift());

    SummaryCounters counters =
        new SummaryCounters(
            new AtomicInteger(),
            new AtomicInteger(),
            new AtomicInteger(),
            new AtomicInteger(),
            new AtomicInteger());

    List<String> driftMessages = new ArrayList<>();
    List<Path> scriptFiles;

    try (Stream<Path> files = Files.walk(settings.inputDirectory().toPath())) {
      scriptFiles =
          files.filter(Files::isRegularFile).filter(CodegenMojoSupport::isSupported).toList();
    } catch (IOException e) {
      throw new MojoExecutionException("Failed scanning input directory", e);
    }

    for (Path script : scriptFiles) {
      counters.supportedScripts().incrementAndGet();

      ScriptSummary perScript = processScript(script, context, settings, effectivePackage, log);

      counters.generatedContracts().addAndGet(perScript.generatedContracts());
      counters.writtenFiles().addAndGet(perScript.writtenFiles());
      counters.skippedUnchangedFiles().addAndGet(perScript.skippedUnchangedFiles());
      counters.driftedFiles().addAndGet(perScript.driftedFiles());

      if (driftMessages.size() < MAX_DRIFT_MESSAGES) {
        driftMessages.addAll(perScript.driftMessages());
        if (driftMessages.size() > MAX_DRIFT_MESSAGES) {
          driftMessages.subList(MAX_DRIFT_MESSAGES, driftMessages.size()).clear();
        }
      }
    }

    if (!driftMessages.isEmpty()) {
      for (String message : driftMessages) {
        log.warn(message);
      }
      if (counters.driftedFiles().get() > driftMessages.size()) {
        log.warn(
            "... and "
                + (counters.driftedFiles().get() - driftMessages.size())
                + " more drifted file(s).");
      }
    }

    return new Summary(
        counters.supportedScripts().get(),
        counters.generatedContracts().get(),
        counters.writtenFiles().get(),
        counters.skippedUnchangedFiles().get(),
        counters.driftedFiles().get());
  }

  private static ScriptSummary processScript(
      Path script,
      ScriptProcessingContext context,
      Settings settings,
      String effectivePackage,
      Log log)
      throws MojoExecutionException {
    try {
      String source = Files.readString(script);
      String fileName = Objects.requireNonNull(script.getFileName()).toString();
      SupportedLanguage language = SupportedLanguage.fromFileName(fileName);
      ScriptDescriptor descriptor = new ScriptDescriptor(language, source, fileName);

      ContractModel model =
          context
              .generator()
              .generate(
                  descriptor,
                  new CodegenConfig(settings.onlyIncludedMethods(), settings.strictMode()));

      if (settings.strictMode()) {
        ContractModelValidator.requireNoUnknownTypes(model);
      }

      int written = 0;
      int skippedUnchanged = 0;
      int drifted = 0;
      List<String> driftMessages = new ArrayList<>();

      for (ContractClass contract : model.classes()) {
        String javaSource = context.javaGenerator().generate(contract, effectivePackage);
        Path target =
            context
                .outputDir()
                .resolve(effectivePackage.replace('.', '/'))
                .resolve(contract.name() + ".java");

        boolean drift = false;
        if (context.failOnDrift()) {
          drift = isDrift(target, javaSource);
          if (drift) {
            drifted++;
            driftMessages.add("Drift detected: " + target);
          }
        } else {
          Path targetDirectory = Objects.requireNonNull(target.getParent());
          Files.createDirectories(targetDirectory);

          boolean unchanged =
              settings.skipUnchanged() && Files.exists(target) && !isDrift(target, javaSource);

          if (unchanged) {
            skippedUnchanged++;
            logDebug(log, "Unchanged: " + target);
          } else {
            Files.writeString(target, javaSource);
            written++;
            logInfo(log, "Generated: " + target);
          }
        }
      }

      return new ScriptSummary(
          model.classes().size(), written, skippedUnchanged, drifted, driftMessages);
    } catch (Exception e) {
      throw new MojoExecutionException("Failed processing script: " + script, e);
    }
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

  private record ScriptSummary(
      int generatedContracts,
      int writtenFiles,
      int skippedUnchangedFiles,
      int driftedFiles,
      List<String> driftMessages) {}
}
