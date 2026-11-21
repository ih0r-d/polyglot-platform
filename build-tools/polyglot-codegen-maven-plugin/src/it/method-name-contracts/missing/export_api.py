class ExportApi:
    def hello(self, name):
        raise AssertionError('must not execute')
polyglot.export_value('ExportApi', ExportApi)
