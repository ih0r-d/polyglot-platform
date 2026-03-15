import logging
import re
import polyglot

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("TextCleaner")


class TextCleaner:

  def clean(self, text: str) -> str:
    logger.info("Cleaning input text")

    text = text.lower()

    text = re.sub(r"http\S+", "", text)
    text = re.sub(r"[^a-zA-Z0-9\s]", "", text)

    text = re.sub(r"\s+", " ", text).strip()

    logger.info("Cleaned text: %s", text)

    return text

polyglot.export_value("TextCleaner", TextCleaner)

