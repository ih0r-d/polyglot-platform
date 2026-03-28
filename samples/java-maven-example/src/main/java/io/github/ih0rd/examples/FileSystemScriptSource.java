package io.github.ih0rd.examples;

import io.github.ih0rd.polyglot.SupportedLanguage;
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
        return Files.exists(resolve(language, scriptName));
    }

    @Override
    public Reader open(SupportedLanguage language, String scriptName) throws IOException {
        return Files.newBufferedReader(
                resolve(language, scriptName),
                StandardCharsets.UTF_8
        );
    }

    private Path resolve(SupportedLanguage language, String scriptName) {
        return baseDir.resolve(scriptName + language.ext());
    }
}
