package io.github.ih0rd.examples.contracts;

import io.github.ih0rd.adapter.context.SupportedLanguage;
import io.github.ih0rd.polyglot.spring.client.PolyglotClient;
import java.util.List;
import java.util.Map;

@PolyglotClient(languages = SupportedLanguage.PYTHON)
public interface LibrariesApi {

    List<Map<String, Object>> genUsers(int n);

    String formatUsers(int n);

    String fakeParagraphs(int n);
}
