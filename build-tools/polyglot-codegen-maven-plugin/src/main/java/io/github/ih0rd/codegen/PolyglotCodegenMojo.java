package io.github.ih0rd.codegen;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Maven goal that generates Java contracts from supported scripts. */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public final class PolyglotCodegenMojo extends AbstractMojo {

  /** Creates a new instance. */
  public PolyglotCodegenMojo() {}

  @Parameter(defaultValue = "${project.basedir}/src/main/resources")
  private File inputDirectory;

  @Parameter(defaultValue = "${project.build.directory}/generated-sources/polyglot")
  private File outputDirectory;

  @Parameter private String basePackage;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${project.groupId}", readonly = true)
  private String projectGroupId;

  @Parameter(property = "polyglot.codegen.onlyIncludedMethods", defaultValue = "false")
  private boolean onlyIncludedMethods;

  @Parameter(property = "polyglot.codegen.strictMode", defaultValue = "false")
  private boolean strictMode;

  @Parameter(property = "polyglot.codegen.failOnNoContracts", defaultValue = "false")
  private boolean failOnNoContracts;

  @Parameter(property = "polyglot.codegen.skipUnchanged", defaultValue = "true")
  private boolean skipUnchanged;

  @Parameter(property = "polyglot.codegen.failOnContractDrift", defaultValue = "false")
  private boolean failOnContractDrift;

  @Override
  public void execute() throws MojoExecutionException {
    CodegenMojoSupport.execute(
        new CodegenMojoSupport.Settings(
            inputDirectory,
            outputDirectory,
            basePackage,
            projectGroupId,
            onlyIncludedMethods,
            strictMode,
            failOnNoContracts,
            skipUnchanged,
            failOnContractDrift),
        getLog(),
        CodegenMojoSupport.Mode.GENERATE);

    project.addCompileSourceRoot(outputDirectory.toPath().toAbsolutePath().toString());
  }
}
