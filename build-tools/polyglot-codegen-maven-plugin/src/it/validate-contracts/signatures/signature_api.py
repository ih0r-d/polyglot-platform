import polyglot
class SignatureApi:
    def required(self, name):
        open('business-function-executed', 'w').write('unexpected')
        raise AssertionError('must not execute')
    def optional(self, name='guest'):
        raise AssertionError('must not execute')
    def mixed(self, name, count=2):
        raise AssertionError('must not execute')
polyglot.export_value('SignatureApi', SignatureApi)
