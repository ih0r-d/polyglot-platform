def hello(name, /, count=1, *args, suffix='!', **kwargs):
    raise AssertionError('must not execute')
