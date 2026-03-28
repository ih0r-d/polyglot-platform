package io.github.ih0rd.examples.contracts;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import java.util.List;
import java.util.Map;

@PolyglotClient(languages = SupportedLanguage.PYTHON)
public interface LibrariesApi {

    List<Map<String, Object>> genUsers(int n);

    String formatUsers(int n);

    String fakeParagraphs(int n);
}
