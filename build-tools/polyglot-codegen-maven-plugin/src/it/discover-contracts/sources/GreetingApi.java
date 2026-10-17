import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;

@PolyglotClient(languages = SupportedLanguage.PYTHON)
interface GreetingApi {
    String hello();
}
