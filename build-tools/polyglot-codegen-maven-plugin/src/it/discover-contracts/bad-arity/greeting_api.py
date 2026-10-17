import polyglot
class GreetingApi:
    def hello(self, required):
        open('business-function-executed', 'w').write('unexpected')
        raise AssertionError('must not execute')
polyglot.export_value('GreetingApi', GreetingApi)
