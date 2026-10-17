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

/** Opt-in static symbol checks for explicitly selected Java/Python interface-export contracts. */
@Mojo(name = "validate-contracts", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class PolyglotValidateContractsMojo extends AbstractMojo {

  @Parameter(property = "polyglot.codegen.validateContracts", defaultValue = "false")
  private boolean validateContracts;

  @Parameter(defaultValue = "${project.basedir}/src/main/resources/python")
  private File inputDirectory;

  @Parameter private List<File> contracts;

  /** Creates the contract validation goal. */
  public PolyglotValidateContractsMojo() {}

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!validateContracts) {
      getLog()
          .info("Python contract validation disabled (polyglot.codegen.validateContracts=false).");
      return;
    }
    if (contracts == null || contracts.isEmpty()) {
      throw new MojoFailureException(
          "Configure contracts with at least one Java interface source file.");
    }
    if (inputDirectory == null || !inputDirectory.isDirectory()) {
      throw new MojoFailureException("Python inputDirectory is not a directory: " + inputDirectory);
    }
    for (File contract : contracts) {
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
    getLog().info("Python contract validation passed: " + contracts.size() + " Java source(s).");
  }
}
