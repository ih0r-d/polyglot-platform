package io.github.ih0rd.polyglot.annotations.spring.integration.methodname;

import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;

@PolyglotClient(languages = SupportedLanguage.PYTHON, convention = Convention.BY_METHOD_NAME)
public interface MethodNameSpringClient {

  int callCount();

  int failNow();
}
