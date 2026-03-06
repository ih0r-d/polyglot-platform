# Java Python AOT Adapter Demo

This example demonstrates how to integrate Python code into a Java application using the [Polyglot Platform](../../README.md).

Instead of interacting directly with the low-level [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html), the application uses the Polyglot Platform **adapter layer**.
The adapter handles script loading and typed method binding, that allows Java code to call Python functions through normal Java interfaces.
It also shows how to package and run the application as a fat JAR and as a GraalVM native executable.

## Review the Sample Application

The application performs pricing logic in Python and exposes it to Java.
Java calls the Python implementation through a typed interface.

The example demonstrates how to:

- expose a Python API
- inject a Java object into Python
- call Python through a typed Java interface
- execute the application on the JVM and as a native image

The interaction between Java and Python is managed by the adapter runtime component `PyExecutor`.

### Implementation Details

The Python implementation exports an API using `polyglot.export_value`:
```java
polyglot.export_value("QuoteApi", QuoteApi())
```
This export makes the Python object available to the adapter.
Notice that the interface name and exported Python API name must be the same.

On the Java side, the adapter resolves the exported object and binds it to a Java interface:
```java
QuoteApi api = pyExecutor.bind(QuoteApi.class);
```
Once bound, Java code can call Python methods as regular Java interface methods.

Without `polyglot.export_value`, the adapter would have nothing named `QuoteApi` to bind to.

## Prerequisites

- GraalVM 25. You can install it with SDKMAN!:
    ```bash
    sdk install java 25.0.2-graal
    ```
- Clone the Polyglot Platform repository and run the setup steps.
    ```bash
    git clone https://github.com/ih0r-d/polyglot-platform.git
    ```
    From the root directory, run:
    ```bash
    cd tooling
    mvn -DskipTests install
    cd ../adapter
    mvn -DskipTests -Dmaven.compiler.source=25 -Dmaven.compiler.target=25 install -rf :polyglot-adapter
    ```
    It builds and installs platform artifacts into your local Maven repo (_~/.m2_) so the application can resolve them.

## Package and Run on a JVM

Navigate to the demo directory and package the application:
```bash
cd examples/java-python-aot-adapter
```
```bash
mvn clean package
```
This creates a runnable fat JAR containing all dependencies. Run it:
```bash
java -jar target/java-python-aot-adapter-0.0.1-all.jar
```
The expected output is:
```text
Quote: {'basePrice': 100.0, 'tier': 'GOLD', 'discountRate': 0.15, 'vatRate': 0.2, 'total': 102.0}
```
## Build and Run a Native Image

The project uses the [Maven plugin for GraalVM Native Image](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) to build and package it as a native executable.
```bash
mvn native:compile
```
This produces a native executable in the _/target_ directory.

Run it:
```bash
./target/java-python-aot-adapter
```
The output should be the same as when running on the JVM.

## Why Use the Adapter Instead of the Raw Polyglot API?

You can write this type of application with pure [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) (`Context` and `Value` classes).
However, once an application grows beyond a simple script call, managing script loading, binding, and invocation manually can become difficult.
The Polyglot Platform adapter simplifies this process.

Let's summarize why you should consider the Polyglot Platform with the GraalVM Polyglot API:

- Stronger Java typing: call Python through Java interfaces instead of string-based member lookups.
- Less code: script loading, binding, and invocation are centralized in `PyExecutor`.
- Better maintainability: consistent naming (`QuoteApi` in Java and Python export) is easier to refactor.
- Native-image friendly: in case of this project, adapter integration, metadata, and resource layout are stored in one place.

### What are the changes in the application when using integration layer (adapter) vs raw GraalVM Polyglot API?

_Script(s) loading_

- GraalVM Polyglot API: you manually load/eval scripts (`context.eval(...)`)
- Adapter: you configure a ScriptSource once and bind by interface

_Invocation_

- GraalVM Polyglot API: string-based lookups with `Value.execute(...)
- Adapter: typed calls through Java interfaces (`pyExecutor.bind(QuoteApi.class)`)

_Naming conventions_

- GraalVM Polyglot API: names are usually scattered as string constants
- Adapter: interface name and exported Python API name must be the same