def implementation(name, count=1):
    raise AssertionError('must not execute')
polyglot.export_value('hello', implementation)
def hello():
    pass
