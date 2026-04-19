package io.github.ih0rd.codegen;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

abstract class AbstractPolyglotCodegenCheckLikeMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.basedir}/src/main/resources")
  private File inputDirectory;

  @Parameter(defaultValue = "${project.build.directory}/generated-sources/polyglot")
  private File outputDirectory;

  @Parameter private String basePackage;

  @Parameter(defaultValue = "${project.groupId}", readonly = true)
  private String projectGroupId;

  @Parameter(property = "polyglot.codegen.onlyIncludedMethods", defaultValue = "false")
  private boolean onlyIncludedMethods;

  @Parameter(property = "polyglot.codegen.strictMode", defaultValue = "false")
  private boolean strictMode;

  @Parameter(property = "polyglot.codegen.failOnNoContracts", defaultValue = "false")
  private boolean failOnNoContracts;

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
            false,
            true),
        getLog(),
        CodegenMojoSupport.Mode.CHECK);
  }
}
