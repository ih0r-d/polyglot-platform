package io.github.ih0rd.examples;

import io.github.ih0rd.contract.SupportedLanguage;
import io.github.ih0rd.contract.ScriptSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemScriptSource implements ScriptSource {

    private final Path baseDir;

    FileSystemScriptSource(Path baseDir) {
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
