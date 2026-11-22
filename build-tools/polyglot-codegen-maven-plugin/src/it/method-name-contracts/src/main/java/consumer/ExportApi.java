package consumer;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.SupportedLanguage;
@PolyglotClient(languages = SupportedLanguage.PYTHON)
public interface ExportApi {
    static String version() { return "exports-1"; }
    String hello(String name);
}
