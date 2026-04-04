package io.github.ih0rd.codegen.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import io.github.ih0rd.codegen.ContractGenerator;
import io.github.ih0rd.codegen.ContractModelValidator;
import io.github.ih0rd.codegen.DefaultContractGenerator;
import io.github.ih0rd.codegen.JavaInterfaceGenerator;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

/**
 * Command-line entry point for polyglot contract code generation.
 *
 * <p>Designed for build-time execution, for example via a Maven exec plugin.
 *
 * <p><strong>Responsibilities:</strong>
 *
 * <ul>
 *   <li>Parse CLI arguments
 *   <li>Detect script language
 *   <li>Delegate parsing to {@link ContractGenerator}
 *   <li>Generate Java interfaces
 *   <li>Write generated sources to the output directory
 * </ul>
 *
 * <p><strong>Design notes:</strong>
 *
 * <ul>
 *   <li>No runtime execution
 *   <li>No framework coupling
 *   <li>Language dispatch delegated to the generator
 * </ul>
 */
public final class CodegenMain {

  /** Utility entry point class; not intended to be instantiated. */
  private CodegenMain() {}

  /**
   * Runs contract generation from the command line.
   *
   * @param args CLI arguments in the form {@code <inputDir> <outputDir> --package=<basePackage>}
   */
  public static void main(String[] args) {
    CliArguments cli = parseArguments(args);
    validate(cli);
    run(cli);
  }

  private static CliArguments parseArguments(String[] args) {
    if (args == null || args.length < 3) {
      throw new IllegalArgumentException(
          """
                            Usage:
                              CodegenMain <inputDir> <outputDir> --package=<basePackage>
                              [--only-included-methods=true|false]
                              [--strict-mode=true|false]
                            """);
    }

    Path inputDir = Path.of(args[0]);
    Path outputDir = Path.of(args[1]);

    String basePackage = null;
    boolean onlyIncludedMethods = false;
    boolean strictMode = false;

    for (String arg : args) {

      if (arg.startsWith("--package=")) {
        basePackage = arg.substring("--package=".length());
      }

      if (arg.startsWith("--only-included-methods=")) {
        onlyIncludedMethods =
            Boolean.parseBoolean(arg.substring("--only-included-methods=".length()));
      }

      if (arg.startsWith("--strict-mode=")) {
        strictMode = Boolean.parseBoolean(arg.substring("--strict-mode=".length()));
      }
    }

    if (basePackage == null || basePackage.isBlank()) {
      throw new IllegalArgumentException("Missing required argument: --package=<basePackage>");
    }

    return new CliArguments(
        inputDir, outputDir, basePackage, new CodegenConfig(onlyIncludedMethods, strictMode));
  }

  private static void validate(CliArguments cli) {
    if (!Files.isDirectory(cli.inputDir())) {
      throw new IllegalArgumentException("Input path is not a directory: " + cli.inputDir());
    }

    try {
      Files.createDirectories(cli.outputDir());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create output directory: " + cli.outputDir(), e);
    }
  }

  private static void run(CliArguments cli) {
    ContractGenerator generator = new DefaultContractGenerator();
    JavaInterfaceGenerator javaGenerator = new JavaInterfaceGenerator();

    try (Stream<Path> files = Files.list(cli.inputDir())) {
      files
          .filter(path -> path.toString().endsWith(".py"))
          .forEach(path -> processFile(path, cli, generator, javaGenerator));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to scan input directory: " + cli.inputDir(), e);
    }
  }

  private static void processFile(
      Path scriptPath,
      CliArguments cli,
      ContractGenerator generator,
      JavaInterfaceGenerator javaGenerator) {
    String source = readScript(scriptPath);

    ScriptDescriptor descriptor =
        new ScriptDescriptor(
            SupportedLanguage.PYTHON,
            source,
            Objects.requireNonNull(scriptPath.getFileName(), "Script path must have a file name")
                .toString());

    ContractModel model;
    try {
      model = generator.generate(descriptor, cli.config());
      if (cli.config().strictMode()) {
        ContractModelValidator.requireNoUnknownTypes(model);
      }
    } catch (RuntimeException e) {
      throw new CodegenCliException("Failed to process script: " + scriptPath, e);
    }

    Path packageDir = preparePackageDirectory(cli);
    for (ContractClass contract : model.classes()) {
      writeContract(packageDir, contract, cli.basePackage(), javaGenerator);
    }
  }

  private static String readScript(Path scriptPath) {
    try {
      return Files.readString(scriptPath);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read script: " + scriptPath, e);
    }
  }

  private static Path preparePackageDirectory(CliArguments cli) {
    Path packageDir = cli.outputDir().resolve(cli.basePackage().replace('.', '/'));
    try {
      Files.createDirectories(packageDir);
      return packageDir;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create package directory: " + packageDir, e);
    }
  }

  private static void writeContract(
      Path packageDir,
      ContractClass contract,
      String basePackage,
      JavaInterfaceGenerator javaGenerator) {
    String javaSource = javaGenerator.generate(contract, basePackage);
    Path outputFile = packageDir.resolve(contract.name() + ".java");
    try {
      Files.writeString(outputFile, javaSource);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write generated source: " + outputFile, e);
    }
  }

  private static final class CodegenCliException extends RuntimeException {

    private CodegenCliException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
