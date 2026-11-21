class Api:
    """
    def from_docstring(self): pass
    """
    # def from_comment(self): pass
    def hello(self, name: str, /, *args, count=1, **kwargs) -> str:
        default = 1
        def nested():
            return default
        return name
polyglot.export_value('Api', Api)
