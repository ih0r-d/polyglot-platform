# Compiled Java contract validation consumer

This fixture compiles `GreetingApi`, `ExportApi` and `Consumer` using the annotation artifact only.
Its Maven `verify` execution discovers both clients and statically validates Python. It needs no
GraalPy runtime or Spring application. Python business methods raise if executed.

The existing `plugin-it` profile substitutes `@project.version@`, installs the plugin into its
isolated repository and checks all configured success/failure runs:

```bash
./mvnw -B -ntp -Pplugin-it -pl build-tools/polyglot-codegen-maven-plugin -am verify
```

`valid` passes positional/default/variadic checks; `alias` passes with executable export precedence.
`missing`, `arity`, and `required-keyword` fail as INCOMPATIBLE. `unknown` and `missing-export` fail
as UNKNOWN. Java consumer compilation precedes the validation execution in every scenario.
