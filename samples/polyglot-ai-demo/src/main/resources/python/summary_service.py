import logging
import polyglot

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("SummaryService")


class SummaryService:

  def summarize(self, text: str) -> str:
    logger.info("Generating summary")

    sentences = text.split(".")
    sentences = [s.strip() for s in sentences if s.strip()]

    summary = sentences[0] if sentences else text

    logger.info("Summary generated: %s", summary)

    return summary

polyglot.export_value("SummaryService", SummaryService)
