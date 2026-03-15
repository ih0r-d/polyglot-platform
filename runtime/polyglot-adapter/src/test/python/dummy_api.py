class DummyApi:
    def add(self, a: int, b: int) -> int:
        return a + b

    def ping(self):
        print("pong: from python")


import polyglot
polyglot.export_value('DummyApi', DummyApi)