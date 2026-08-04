package io.github.ih0rd.examples;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptNames;
import io.github.ih0rd.polyglot.model.config.ScriptSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSystemScriptSource implements ScriptSource {

    private final Path baseDir;

    public FileSystemScriptSource(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public boolean exists(SupportedLanguage language, String scriptName) {
        Path resolved = resolve(language, scriptName);
        if (!Files.exists(resolved)) {
            return false;
        }
        try {
            resolveRealPath(scriptName, resolved);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Reader open(SupportedLanguage language, String scriptName) throws IOException {
        return Files.newBufferedReader(
                resolveRealPath(scriptName, resolve(language, scriptName)),
                StandardCharsets.UTF_8
        );
    }

    private Path resolve(SupportedLanguage language, String scriptName) {
        String logicalName = ScriptNames.requireValid(scriptName);
        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(logicalName + language.ext()).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IllegalArgumentException(
                    "Invalid logical script name '" + scriptName + "': resolved path escapes base directory");
        }
        return resolved;
    }

    private Path resolveRealPath(String scriptName, Path resolved) throws IOException {
        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path baseReal = normalizedBase.toRealPath();
        Path resolvedReal = resolved.toRealPath();
        if (!resolvedReal.startsWith(baseReal)) {
            throw new IllegalArgumentException(
                    "Invalid logical script name '" + scriptName + "': resolved path escapes base directory");
        }
        return resolvedReal;
    }
}
