package io.github.ih0rd.codegen;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.github.ih0rd.codegen.compatibility.CompatibilityOutcome;
import io.github.ih0rd.codegen.compatibility.PythonCompatibilityChecker;

/** Checks explicit baseline Python contracts against current Java-facing contracts. */
@Mojo(name = "check-compatibility", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class PolyglotCheckCompatibilityMojo extends AbstractMojo {
  /** Explicit baseline .py file or directory; never inferred from Git. */
  @Parameter(property = "polyglot.codegen.baseline", required = true)
  private File baseline;

  /** Current Python source root. */
  @Parameter(
      property = "polyglot.codegen.currentDirectory",
      defaultValue = "${project.basedir}/src/main/resources/python",
      required = true)
  private File currentDirectory;

  /** Fail on uncertainty by default; false still reports UNKNOWN as warnings. */
  @Parameter(property = "polyglot.codegen.failOnUnknown", defaultValue = "true")
  private boolean failOnUnknown = true;

  /** Creates the compatibility goal. */
  public PolyglotCheckCompatibilityMojo() {}

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (baseline == null || currentDirectory == null) {
      throw new MojoFailureException(
          "Configure an explicit baseline file/directory and currentDirectory for check-compatibility.");
    }
    try {
      var report =
          new PythonCompatibilityChecker().compare(baseline.toPath(), currentDirectory.toPath());
      report
          .diagnostics()
          .forEach(
              d -> {
                if (d.outcome() == CompatibilityOutcome.BREAKING) getLog().error(d.format());
                else getLog().warn(d.format());
              });
      getLog()
          .info(
              "Python contract compatibility: "
                  + report.outcome()
                  + " ("
                  + report.diagnostics().size()
                  + " finding(s)).");
      if (report.outcome() == CompatibilityOutcome.BREAKING
          || (failOnUnknown && report.outcome() == CompatibilityOutcome.UNKNOWN)) {
        throw new MojoFailureException(
            "Python contract compatibility: "
                + report.outcome()
                + ". See source-located diagnostics above; baseline="
                + baseline
                + ", currentDirectory="
                + currentDirectory
                + ".");
      }
    } catch (IllegalArgumentException ex) {
      throw new MojoFailureException(ex.getMessage(), ex);
    } catch (IOException ex) {
      throw new MojoExecutionException("Cannot read Python compatibility sources.", ex);
    }
  }
}
