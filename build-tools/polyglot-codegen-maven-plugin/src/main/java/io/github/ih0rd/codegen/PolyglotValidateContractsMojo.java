package io.github.ih0rd.codegen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.github.ih0rd.codegen.internal.python.PythonAstSession;

/** Opt-in static validation of selected or source-discovered Java/Python contracts. */
@Mojo(name = "validate-contracts", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class PolyglotValidateContractsMojo extends AbstractMojo {

  @Parameter(property = "polyglot.codegen.validateContracts", defaultValue = "false")
  private boolean validateContracts;

  @Parameter(defaultValue = "${project.basedir}/src/main/resources/python")
  private File inputDirectory;

  @Parameter private List<File> contracts;

  @Parameter(property = "polyglot.codegen.discoverContracts", defaultValue = "false")
  private boolean discoverContracts;

  @Parameter(property = "polyglot.codegen.discoverCompileSourceRoots", defaultValue = "false")
  private boolean discoverCompileSourceRoots;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${project.build.sourceDirectory}")
  private File sourceDirectory;

  /** Creates the contract validation goal. */
  public PolyglotValidateContractsMojo() {}

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!validateContracts) {
      getLog()
          .info("Python contract validation disabled (polyglot.codegen.validateContracts=false).");
      return;
    }
    List<File> selected = contracts;
    if (selected == null || selected.isEmpty()) {
      if (!discoverContracts) {
        throw new MojoFailureException(
            "Configure contracts with at least one Java interface source file, or enable polyglot.codegen.discoverContracts.");
      }
      if (sourceDirectory == null) {
        throw new MojoFailureException("Configure sourceDirectory for contract discovery.");
      }
      try {
        selected = discoverSources();
      } catch (IllegalArgumentException ex) {
        throw new MojoFailureException(ex.getMessage(), ex);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Cannot discover Java contracts in " + sourceDirectory, ex);
      }
      if (selected.isEmpty()) {
        throw new MojoFailureException(
            "No @PolyglotClient contract sources discovered in "
                + sourceDirectory
                + "; check sourceDirectory or configure explicit contracts.");
      }
      getLog()
          .info(
              "Discovered "
                  + selected.size()
                  + " @PolyglotClient Java source(s) in "
                  + sourceDirectory);
    }
    if (inputDirectory == null || !inputDirectory.isDirectory()) {
      throw new MojoFailureException("Python inputDirectory is not a directory: " + inputDirectory);
    }
    try (var session = new PythonAstSession()) {
      var validator = new PythonBindingValidator(inputDirectory.toPath(), session);
      for (File contract : selected) {
        if (contract == null || !contract.isFile()) {
          throw new MojoFailureException("Java contract source does not exist: " + contract);
        }
        try {
          validator.validate(contract.toPath());
        } catch (IllegalArgumentException ex) {
          throw new MojoFailureException(ex.getMessage(), ex);
        } catch (IOException ex) {
          throw new MojoExecutionException("Cannot read contract sources for " + contract, ex);
        } catch (IllegalStateException ex) {
          throw new MojoExecutionException(
              "Cannot initialize or execute static GraalPy AST analysis", ex);
        }
      }
    }
    getLog().info("Python contract validation passed: " + selected.size() + " Java source(s).");
  }

  private List<File> discoverSources() throws IOException {
    Path primary = sourceDirectory.toPath().toAbsolutePath().normalize();
    if (!discoverCompileSourceRoots) return AnnotatedContractDiscovery.discover(primary);

    Set<Path> roots = new LinkedHashSet<>();
    roots.add(primary);
    if (project != null) {
      project.getCompileSourceRoots().stream()
          .map(root -> Path.of(root).toAbsolutePath().normalize())
          .forEach(roots::add);
    }
    Set<File> sources = new LinkedHashSet<>();
    for (Path root : roots) {
      if (!Files.notExists(root)) sources.addAll(AnnotatedContractDiscovery.discover(root));
    }
    return List.copyOf(sources);
  }
}
