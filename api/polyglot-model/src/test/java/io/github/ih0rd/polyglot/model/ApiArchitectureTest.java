package io.github.ih0rd.polyglot.model;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.github.ih0rd.polyglot",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ApiArchitectureTest {

  @ArchTest
  static final ArchRule api_packages_should_not_depend_on_runtime_spring_or_build_tools =
      noClasses()
          .that()
          .resideInAnyPackage("io.github.ih0rd.polyglot..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "io.github.ih0rd.adapter..",
              "io.github.ih0rd.codegen..",
              "io.github.ih0rd.polyglot.annotations.spring..",
              "org.springframework..");
}
