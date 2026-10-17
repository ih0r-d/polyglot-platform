package io.github.ih0rd.codegen;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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
        selected = AnnotatedContractDiscovery.discover(sourceDirectory.toPath());
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
    for (File contract : selected) {
      if (contract == null || !contract.isFile()) {
        throw new MojoFailureException("Java contract source does not exist: " + contract);
      }
      try {
        PythonBindingValidator.validate(contract.toPath(), inputDirectory.toPath());
      } catch (IllegalArgumentException ex) {
        throw new MojoFailureException(ex.getMessage(), ex);
      } catch (IOException ex) {
        throw new MojoExecutionException("Cannot read contract sources for " + contract, ex);
      }
    }
    getLog().info("Python contract validation passed: " + selected.size() + " Java source(s).");
  }
}
