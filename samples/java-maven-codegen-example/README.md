# Java Maven Codegen Example

This consumer generates Java interfaces and binds Python through the adapter. Use JDK 25.
From the repository root, install the current library once:

```bash
./mvnw -B -ntp -DskipTests install
```

## Python contract evolution

The `compatibility` profile binds `check-compatibility` to `verify`. Its explicit, committed
baseline is `compatibility/baseline/greeting_api.py`. It is independent of the sample's third-party
Python packages. The following direct goal commands exercise the real consumer plugin configuration
without installing Python packages or executing Python:

```bash
# COMPATIBLE: add a trailing optional parameter; exit 0.
./mvnw -f samples/java-maven-codegen-example/pom.xml -Pcompatibility polyglot:check-compatibility

# BREAKING / FUNCTION_REMOVED: renamed hello; exit 1.
./mvnw -f samples/java-maven-codegen-example/pom.xml -Pcompatibility -Dcompatibility.scenario=removed-function polyglot:check-compatibility

# BREAKING / POSITIONAL_ARITY: require another argument; exit 1.
./mvnw -f samples/java-maven-codegen-example/pom.xml -Pcompatibility -Dcompatibility.scenario=required-parameter polyglot:check-compatibility

# UNKNOWN / UNSUPPORTED_SYNTAX: staticmethod decoration; exit 1 by default.
./mvnw -f samples/java-maven-codegen-example/pom.xml -Pcompatibility -Dcompatibility.scenario=unknown polyglot:check-compatibility

# Still reports UNKNOWN, but exits 0 under an explicit warning policy.
./mvnw -f samples/java-maven-codegen-example/pom.xml -Pcompatibility -Dcompatibility.scenario=unknown -Dpolyglot.codegen.failOnUnknown=false polyglot:check-compatibility

# CI lifecycle integration, including the ordinary generation/package build:
./mvnw -f samples/java-maven-codegen-example/pom.xml -Pcompatibility verify
```

The tiny fixtures model `interface GreetingApi { String hello(String name); }`.
Changing the default value is UNKNOWN: preserving accepted argument counts alone cannot prove
that callers omitting the default retain their behavior. See the repository's
[compatibility rules](../../docs/codegen.md#python-contract-compatibility) for the supported subset.
