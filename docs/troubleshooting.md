# Troubleshooting

This guide covers common runtime, Spring Boot starter, and code generation failures.

The project is Python-first. JavaScript support exists as an experimental runtime path only, and
JavaScript code generation is not supported in the current release line.

## Script Not Found

Symptom:

- runtime binding fails with `ScriptNotFoundException`
- Spring Boot fail-fast startup fails before the application starts
- preload fails for a configured script name

Likely cause:

- the script file is not in the location expected by the configured `ScriptSource`
- the Java interface name does not match the default snake-case script name
- Spring Boot `polyglot.python.resources-path` or `polyglot.js.resources-path` points to the wrong
  location

Fix:

- For `ForecastService`, use `forecast_service.py` for Python.
- Check that the configured resource path contains the script.
- For Spring Boot, use logical preload names without the `.py` suffix.

Minimal Python script:

```python
import polyglot

class ForecastService:
    def forecast(self, city):
        return {"city": city, "temperature": 21}

polyglot.export_value("ForecastService", ForecastService)
```

Minimal Java binding:

```java
FileSystemScriptSource scripts = new FileSystemScriptSource(Path.of("src/main"));

try (PyExecutor executor = PyExecutor.create(scripts, null)) {
  ForecastService service = executor.bind(ForecastService.class);
  service.forecast("Kyiv");
}
```

With `FileSystemScriptSource(Path.of("src/main"))`, the script should be located at
`src/main/python/forecast_service.py`.

## Export Not Found

Symptom:

- binding fails with a message like `Python class 'ForecastService' not found`
- `validateBinding(...)` fails even though the script file exists

Likely cause:

- the Python script did not call `polyglot.export_value(...)`
- the exported value name does not match the Java interface simple name
- the script raised an error before exporting the value

Fix:

- Export the contract under the Java interface simple name.
- Keep the adapter-facing export small and explicit.

Python class-style export:

```python
import polyglot

class ForecastService:
    def forecast(self, city):
        return {"city": city}

polyglot.export_value("ForecastService", ForecastService)
```

Python dictionary-style export:

```python
import polyglot

def forecast(city):
    return {"city": city}

polyglot.export_value("ForecastService", {"forecast": forecast})
```

## Method Not Executable

Symptom:

- `validateBinding(...)` reports that a Python method is not found or not executable
- the proxy is created, but a method call fails at runtime

Likely cause:

- the Java interface method name does not exist on the exported Python object
- a dictionary-style export is missing the method key
- the exported member exists but is not callable

Fix:

- Match Java method names exactly.
- For dictionary-style exports, map every Java method name to a Python function.

Java contract:

```java
public interface StatsApi {
  Double mean(List<Double> values);
}
```

Python implementation:

```python
import polyglot

class StatsApi:
    def mean(self, values):
        return sum(values) / len(values)

polyglot.export_value("StatsApi", StatsApi)
```

## Wrong Convention

Symptom:

- Python binding fails even though the function exists
- JavaScript binding fails when using `Convention.BY_INTERFACE_EXPORT`
- Spring Boot client creation fails for a client with an unsupported convention

Likely cause:

- `Convention.DEFAULT` and `Convention.BY_INTERFACE_EXPORT` expect a Python export named after the
  Java interface
- `Convention.BY_METHOD_NAME` expects functions to be available directly in language bindings
- JavaScript supports only the method-name style in the current experimental runtime path

Fix:

- For Python class or dictionary exports, use `DEFAULT` or `BY_INTERFACE_EXPORT`.
- For direct top-level Python functions, use `BY_METHOD_NAME`.
- Do not use `BY_INTERFACE_EXPORT` for JavaScript.

Python `BY_METHOD_NAME` example:

```python
def forecast(city):
    return {"city": city}
```

Java:

```java
ForecastService service = executor.bind(ForecastService.class, Convention.BY_METHOD_NAME);
```

## Missing GraalVM Language Dependency

Symptom:

- GraalVM reports that a language is not installed or cannot be initialized
- Python or JavaScript works locally but fails in CI or another application
- Spring Boot creates no executor for an enabled language

Likely cause:

- the application added `polyglot-adapter` but not the language runtime dependency
- the dependency versions are not aligned with the runtime BOM
- CI is not running on the verified GraalVM line

Fix:

- Import `polyglot-bom`.
- Add only the GraalVM language dependencies you use.
- Use GraalVM JDK 25.x for the currently verified runtime line.

Python dependencies:

```xml
<dependency>
  <groupId>org.graalvm.python</groupId>
  <artifactId>python-embedding</artifactId>
</dependency>
<dependency>
  <groupId>org.graalvm.python</groupId>
  <artifactId>python-launcher</artifactId>
</dependency>
```

Experimental JavaScript runtime dependency:

```xml
<dependency>
  <groupId>org.graalvm.js</groupId>
  <artifactId>js</artifactId>
  <type>pom</type>
</dependency>
```

## Spring Boot Bean And Client Resolution Issues

Symptom:

- no Spring bean is created for an interface annotated with `@PolyglotClient`
- startup fails because multiple executors are available
- startup fails because no executor is available for the requested language

Likely cause:

- `@EnablePolyglotClients` scans the wrong package
- the client does not specify a language while both Python and JavaScript executors are available
- the requested language is not enabled under `polyglot.*`
- required language dependencies are missing

Fix:

- Put `@EnablePolyglotClients` on a configuration class in the parent package, or set
  `basePackages`.
- Prefer explicit language selection for public application clients.
- Enable the matching language configuration.

Spring Boot example:

```java
@SpringBootApplication
@EnablePolyglotClients(basePackages = "com.example.contracts")
class DemoApplication {}
```

```java
@PolyglotClient(languages = {SupportedLanguage.PYTHON})
public interface ForecastService {
  Map<String, Object> forecast(String city);
}
```

```yaml
polyglot:
  python:
    enabled: true
    resources-path: classpath:python
```

## Preload And Fail-Fast Startup Failures

Symptom:

- Spring Boot fails during startup when `polyglot.core.fail-fast=true`
- preload fails before any client method is called
- a script appears to run twice: once during preload and once during binding

Likely cause:

- fail-fast eagerly validates `@PolyglotClient` bindings
- preload evaluates named scripts as raw script evaluation
- preload does not bind Java interfaces and does not populate interface caches
- script side effects can happen again when the contract is later bound

Fix:

- Treat preload as startup visibility/warmup, not contract prebinding.
- Keep preload scripts idempotent.
- Use `validateBinding(...)` or Spring fail-fast for contract validation.
- Use logical script names in `preload-scripts`.

Spring Boot example:

```yaml
polyglot:
  core:
    fail-fast: true
  python:
    enabled: true
    resources-path: classpath:python
    warmup-on-startup: true
    preload-scripts:
      - bootstrap
```

This expects `python/bootstrap.py`.

## Codegen Parser Limitations

Symptom:

- `polyglot:generate`, `polyglot:check`, or `polyglot:doctor` fails while parsing Python
- generated Java uses `Object` where a more precise type was expected
- no Java interfaces are generated

Likely cause:

- the Python parser supports a documented adapter-oriented subset, not full Python semantic analysis
- the script does not use `polyglot.export_value(...)`
- type hints are missing or outside the supported type model
- JavaScript code generation is not implemented

Fix:

- Keep generated contracts simple and explicit.
- Use `polyglot.export_value(...)`.
- Use `strictMode=true` for contracts intended to become stable Java-facing APIs.
- Do not expect JavaScript code generation in the current release line.

Python codegen-friendly example:

```python
import polyglot

class ForecastService:
    def forecast(self, city: str) -> dict:
        return {"city": city}

polyglot.export_value("ForecastService", ForecastService)
```

## Codegen Drift And Check Failures

Symptom:

- `polyglot:check` fails with generated file drift
- CI fails but local generation appears to work
- generated sources differ from committed generated files

Likely cause:

- Python contracts changed but generated Java sources were not refreshed
- codegen configuration differs between local and CI
- generated files were edited manually

Fix:

- Run `polyglot:generate` locally and commit the generated source changes.
- Use the same plugin configuration in local and CI builds.
- Treat generated Java as output from Python contracts, not hand-maintained source.

Common workflow:

```bash
mvn -B -ntp polyglot:generate
mvn -B -ntp polyglot:check
```

## JavaScript Support Limitations

Symptom:

- JavaScript binding works only for direct function-style contracts
- `BY_INTERFACE_EXPORT` fails for JavaScript
- JavaScript code generation throws `UnsupportedOperationException`

Likely cause:

- JavaScript is an experimental runtime-only path
- the current JavaScript runtime expects functions in JavaScript bindings by Java method name
- JavaScript contract generation is not implemented

Fix:

- Keep JavaScript usage narrow and runtime-only.
- Use `Convention.DEFAULT` or `Convention.BY_METHOD_NAME` for JavaScript.
- Do not use JavaScript as a parity path with Python before the project explicitly promotes it.

Experimental JavaScript example:

```javascript
function forecast(city) {
  return { city: city, temperature: 21 };
}
```

Java:

```java
ForecastService service = jsExecutor.bind(ForecastService.class, Convention.BY_METHOD_NAME);
```

## Trusted Scripts And `allowAllAccess`

Symptom:

- security review asks whether guest scripts are sandboxed
- an application wants to execute user-supplied Python or JavaScript

Likely cause:

- contexts created by the adapter use GraalVM host access settings intended for trusted
  application-owned scripts
- this project is not a sandboxing framework

Fix:

- Execute only trusted scripts packaged with or controlled by the application.
- Do not pass user-supplied code to the adapter.
- If untrusted execution is required, design and verify a separate sandboxing boundary outside this
  library.

Security rule:

```text
Application-owned scripts: supported use case.
Untrusted user-supplied scripts: not a supported use case.
```
