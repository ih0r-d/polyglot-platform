import polyglot
import logging
import re
from collections import Counter

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("KeywordExtractor")


STOPWORDS = {
  "the","a","an","and","or","but","if","while","is","are","was","were",
  "this","that","of","to","in","for","on","with","as","at","by","from",
  "it","be","has","have","had","will","would","can","could","should"
}


class KeywordExtractor:

  def extract(self, text: str) -> list[str]:
    logger.info("Extracting keywords")

    words = re.findall(r"\b[a-zA-Z]{3,}\b", text.lower())

    filtered = [
      w for w in words
      if w not in STOPWORDS
    ]

    freq = Counter(filtered)

    keywords = [
      word for word, _ in freq.most_common(5)
    ]

    logger.info("Keywords extracted: %s", keywords)

    return keywords

polyglot.export_value("KeywordExtractor", KeywordExtractor)

