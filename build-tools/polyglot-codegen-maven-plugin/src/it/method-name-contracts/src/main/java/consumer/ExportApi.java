package consumer;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.SupportedLanguage;
@PolyglotClient(languages = SupportedLanguage.PYTHON)
public interface ExportApi { String hello(String name); }
