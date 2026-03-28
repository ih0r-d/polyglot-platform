class MyApi:
    def add(self, a: int, b: int) -> int:
        return a + b

    def ping(self) -> None:
        return None

import polyglot
polyglot.export_value("MyApi", MyApi)
