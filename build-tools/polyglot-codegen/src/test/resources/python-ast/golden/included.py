class Included:
    @adapter_include
    def keep(self, value: int) -> int:
        return value
    def omit(self, value: str) -> str:
        return value
polyglot.export_value('Included', Included)
