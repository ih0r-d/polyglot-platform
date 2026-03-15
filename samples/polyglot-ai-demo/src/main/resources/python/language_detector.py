import logging
from langdetect import detect
import polyglot
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("LanguageDetector")


class LanguageDetector:

  def detect(self, text: str) -> str:
    logger.info("Detecting language")

    language = detect(text)

    logger.info("Language detected: %s", language)

    return language

polyglot.export_value("LanguageDetector", LanguageDetector)
