package io.github.ih0rd.codegen;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.github.ih0rd.codegen",
    importOptions = ImportOption.DoNotIncludeTests.class)
class BuildToolsArchitectureTest {

  @ArchTest
  static final ArchRule build_tools_should_not_depend_on_runtime_packages =
      noClasses()
          .that()
          .resideInAnyPackage("io.github.ih0rd.codegen..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "io.github.ih0rd.adapter..", "io.github.ih0rd.polyglot.annotations.spring..");
}
