class Api:
    def hello(self, name: str) -> set[int]:
        return {1}
polyglot.export_value('Api', Api)
