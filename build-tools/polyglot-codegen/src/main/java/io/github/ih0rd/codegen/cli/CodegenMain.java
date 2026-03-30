package io.github.ih0rd.codegen.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import io.github.ih0rd.codegen.ContractGenerator;
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
                            """);
    }

    Path inputDir = Path.of(args[0]);
    Path outputDir = Path.of(args[1]);

    String basePackage = null;
    boolean onlyIncludedMethods = false;

    for (String arg : args) {

      if (arg.startsWith("--package=")) {
        basePackage = arg.substring("--package=".length());
      }

      if (arg.startsWith("--only-included-methods=")) {
        onlyIncludedMethods =
            Boolean.parseBoolean(arg.substring("--only-included-methods=".length()));
      }
    }

    if (basePackage == null || basePackage.isBlank()) {
      throw new IllegalArgumentException("Missing required argument: --package=<basePackage>");
    }

    return new CliArguments(
        inputDir, outputDir, basePackage, new CodegenConfig(onlyIncludedMethods));
  }

  private static void validate(CliArguments cli) {
    if (!Files.isDirectory(cli.inputDir())) {
      throw new IllegalArgumentException("Input path is not a directory: " + cli.inputDir());
    }

    try {
      Files.createDirectories(cli.outputDir());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create output directory: " + cli.outputDir(), e);
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
      throw new RuntimeException("Failed to scan input directory: " + cli.inputDir(), e);
    }
  }

  private static void processFile(
      Path scriptPath,
      CliArguments cli,
      ContractGenerator generator,
      JavaInterfaceGenerator javaGenerator) {
    try {
      String source = Files.readString(scriptPath);

      ScriptDescriptor descriptor =
          new ScriptDescriptor(
              SupportedLanguage.PYTHON,
              source,
              Objects.requireNonNull(scriptPath.getFileName(), "Script path must have a file name")
                  .toString());

      ContractModel model = generator.generate(descriptor, cli.config());

      for (ContractClass contract : model.classes()) {
        String javaSource = javaGenerator.generate(contract, cli.basePackage());

        Path packageDir = cli.outputDir().resolve(cli.basePackage().replace('.', '/'));

        try {
          Files.createDirectories(packageDir);
        } catch (IOException e) {
          throw new RuntimeException("Failed to create package directory", e);
        }

        Path outputFile = packageDir.resolve(contract.name() + ".java");

        Files.writeString(outputFile, javaSource);
      }

    } catch (Exception e) {
      throw new RuntimeException("Failed to process script: " + scriptPath, e);
    }
  }
}
