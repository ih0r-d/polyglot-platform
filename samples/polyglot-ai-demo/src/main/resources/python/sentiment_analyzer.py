import logging
from textblob import TextBlob
import polyglot

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("SentimentAnalyzer")


class SentimentAnalyzer:

  def analyze(self, text: str) -> float:
    logger.info("Running sentiment analysis")

    blob = TextBlob(text)
    score = blob.sentiment.polarity

    logger.info("Sentiment score: %s", score)

    return score

polyglot.export_value("SentimentAnalyzer", SentimentAnalyzer)
