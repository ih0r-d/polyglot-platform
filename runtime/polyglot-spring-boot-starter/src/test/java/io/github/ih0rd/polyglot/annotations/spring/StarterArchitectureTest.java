package io.github.ih0rd.polyglot.annotations.spring;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

@AnalyzeClasses(
    packages = "io.github.ih0rd.polyglot.annotations.spring",
    importOptions = ImportOption.DoNotIncludeTests.class)
class StarterArchitectureTest {

  private static final String INTERNAL_PACKAGE = "io.github.ih0rd.polyglot.annotations.spring.internal";

  @ArchTest
  static final ArchRule non_internal_starter_classes_should_not_expose_internal_types_in_public_or_protected_signatures =
      classes()
          .that()
          .resideInAnyPackage("io.github.ih0rd.polyglot.annotations.spring..")
          .and()
          .resideOutsideOfPackage("..internal..")
          .should(notExposeInternalStarterTypes());

  private static ArchCondition<JavaClass> notExposeInternalStarterTypes() {
    return new ArchCondition<>("not expose internal starter types in public/protected signatures") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (JavaMethod method : item.getMethods()) {
          if (isPublicOrProtected(method.getModifiers())) {
            checkInternalType(
                item,
                "method return type " + method.getFullName(),
                method.getRawReturnType(),
                events);
            for (JavaClass parameter : method.getRawParameterTypes()) {
              checkInternalType(
                  item,
                  "method parameter in " + method.getFullName(),
                  parameter,
                  events);
            }
          }
        }

        for (JavaConstructor constructor : item.getConstructors()) {
          if (isPublicOrProtected(constructor.getModifiers())) {
            for (JavaClass parameter : constructor.getRawParameterTypes()) {
              checkInternalType(
                  item,
                  "constructor parameter in " + constructor.getFullName(),
                  parameter,
                  events);
            }
          }
        }

        for (JavaField field : item.getFields()) {
          if (isPublicOrProtected(field.getModifiers())) {
            checkInternalType(item, "field " + field.getFullName(), field.getRawType(), events);
          }
        }
      }
    };
  }

  private static boolean isPublicOrProtected(Set<JavaModifier> modifiers) {
    return modifiers.contains(JavaModifier.PUBLIC) || modifiers.contains(JavaModifier.PROTECTED);
  }

  private static void checkInternalType(
      JavaClass owner, String location, JavaClass type, ConditionEvents events) {
    if (type.getPackageName().startsWith(INTERNAL_PACKAGE)) {
      events.add(
          SimpleConditionEvent.violated(
              owner, owner.getName() + " exposes internal starter type " + type.getName() + " via " + location));
    }
  }
}
