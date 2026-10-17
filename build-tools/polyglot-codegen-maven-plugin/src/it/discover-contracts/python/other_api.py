import polyglot
class OtherApi:
    def hello(self):
        open('business-function-executed', 'w').write('unexpected')
        raise AssertionError('must not execute')
polyglot.export_value('OtherApi', OtherApi)
