package io.github.ih0rd.adapter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.github.ih0rd.adapter",
    importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeArchitectureTest {

  @ArchTest
  static final ArchRule runtime_core_should_not_depend_on_spring_starter_packages =
      noClasses()
          .that()
          .resideInAnyPackage("io.github.ih0rd.adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "io.github.ih0rd.polyglot.annotations.spring..", "org.springframework..");
}
