package consumer;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.Convention;
@PolyglotClient(languages = SupportedLanguage.PYTHON, convention = Convention.BY_METHOD_NAME)
public interface GreetingApi {
    String hello(String name);
    String hello(String name, int count);
}
