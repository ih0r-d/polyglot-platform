package io.github.ih0rd.codegen;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

/**
 * Maven plugin that generates Java interfaces from guest-language contracts used by the runtime
 * adapter.
 *
 * <p>The plugin scans the configured input directory for supported script files (e.g. Python,
 * JavaScript), extracts contract definitions and generates corresponding Java interfaces.
 *
 * <p>The plugin is bound to the {@code generate-sources} lifecycle phase and automatically
 * registers the generated sources directory as a compile source root.
 *
 * <p><strong>Default behaviour:</strong>
 *
 * <ul>
 *   <li>Input directory: {@code src/main/resources}
 *   <li>Output directory: {@code target/generated-sources/polyglot}
 *   <li>Base package: {@code ${project.groupId}.polyglot}
 * </ul>
 *
 * <p>If {@code basePackage} is not explicitly configured, it falls back to {@code
 * ${project.groupId}.polyglot}.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public final class PolyglotCodegenMojo extends AbstractMojo {

  /** Creates the Maven Mojo instance used by Maven during plugin execution. */
  public PolyglotCodegenMojo() {
    // Maven instantiates Mojos reflectively.
  }

  /**
   * Directory containing polyglot script contracts.
   *
   * <p>Default: {@code src/main/resources}
   */
  @Parameter(defaultValue = "${project.basedir}/src/main/resources")
  private File inputDirectory;

  /**
   * Output directory for generated Java source files.
   *
   * <p>Default: {@code target/generated-sources/polyglot}
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/polyglot")
  private File outputDirectory;

  /**
   * Base package used for generated Java interfaces.
   *
   * <p>If not specified, defaults to: {@code ${project.groupId}.polyglot}
   */
  @Parameter private String basePackage;

  /**
   * Current Maven project instance.
   *
   * <p>Injected automatically by Maven.
   */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /** Project groupId used when deriving the default generated package. */
  @Parameter(defaultValue = "${project.groupId}", readonly = true)
  private String projectGroupId;

  /**
   * Executes the plugin.
   *
   * @throws MojoExecutionException if validation or generation fails
   */
  @Override
  public void execute() throws MojoExecutionException {

    validateInputDirectory();

    Path outputRoot = prepareOutputDirectory();

    String effectivePackage = resolveBasePackage();

    generateContracts(outputRoot, effectivePackage);

    project.addCompileSourceRoot(outputRoot.toAbsolutePath().toString());
  }

  private void validateInputDirectory() throws MojoExecutionException {

    if (inputDirectory == null || !inputDirectory.exists()) {
      throw new MojoExecutionException("Input directory does not exist: " + inputDirectory);
    }

    if (!inputDirectory.isDirectory()) {
      throw new MojoExecutionException("Input path is not a directory: " + inputDirectory);
    }
  }

  private Path prepareOutputDirectory() throws MojoExecutionException {
    try {
      Path path = outputDirectory.toPath();
      Files.createDirectories(path);
      return path;
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to create output directory", e);
    }
  }

  private String resolveBasePackage() {
    if (basePackage == null || basePackage.isBlank()) {
      return projectGroupId + ".polyglot";
    }
    return basePackage;
  }

  private void generateContracts(Path outputRoot, String effectivePackage)
      throws MojoExecutionException {

    ContractGenerator generator = new DefaultContractGenerator();
    JavaInterfaceGenerator javaGenerator = new JavaInterfaceGenerator();

    try (Stream<Path> files = Files.walk(inputDirectory.toPath())) {

      files
          .filter(Files::isRegularFile)
          .filter(this::isSupported)
          .forEach(
              path -> {
                try {
                  generateForScript(path, generator, javaGenerator, outputRoot, effectivePackage);
                } catch (MojoExecutionException e) {
                  throw new UncheckedMojoExecutionException(e);
                }
              });

    } catch (UncheckedMojoExecutionException e) {
      throw e.mojoExecutionException();
    } catch (IOException e) {
      throw new MojoExecutionException("Failed scanning input directory", e);
    }
  }

  private boolean isSupported(Path path) {
    try {
      SupportedLanguage.fromFileName(path.getFileName().toString());
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private void generateForScript(
      Path script,
      ContractGenerator generator,
      JavaInterfaceGenerator javaGenerator,
      Path outputRoot,
      String effectivePackage)
      throws MojoExecutionException {

    try {
      String source = Files.readString(script);
      String scriptFileName = Objects.requireNonNull(script.getFileName()).toString();

      SupportedLanguage language = SupportedLanguage.fromFileName(scriptFileName);

      ScriptDescriptor descriptor = new ScriptDescriptor(language, source, scriptFileName);

      ContractModel model = generator.generate(descriptor, new CodegenConfig(false));

      for (ContractClass contract : model.classes()) {

        String javaSource = javaGenerator.generate(contract, effectivePackage);

        Path target =
            outputRoot
                .resolve(effectivePackage.replace('.', '/'))
                .resolve(contract.name() + ".java");

        Path targetDirectory = Objects.requireNonNull(target.getParent());
        Files.createDirectories(targetDirectory);
        Files.writeString(target, javaSource);

        getLog().info("Generated: " + target);
      }

    } catch (IOException | RuntimeException e) {
      throw new MojoExecutionException("Failed processing script: " + script, e);
    }
  }

  private static final class UncheckedMojoExecutionException extends RuntimeException {
    private final MojoExecutionException mojoExecutionException;

    private UncheckedMojoExecutionException(MojoExecutionException mojoExecutionException) {
      super(mojoExecutionException);
      this.mojoExecutionException = mojoExecutionException;
    }

    private MojoExecutionException mojoExecutionException() {
      return mojoExecutionException;
    }
  }
}
