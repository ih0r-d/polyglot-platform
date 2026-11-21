class Api:
    def hello(self, *, name: str) -> str:
        return name
polyglot.export_value('Api', Api)
