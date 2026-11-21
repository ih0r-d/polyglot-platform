def f(x):
    class Inner:
        def g(self):
            return x
    return Inner
