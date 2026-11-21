def first(value: int) -> int:
    return value
def second(value: str) -> str:
    return value
def _private(value):
    return value
polyglot.export_value('Ordering', {'z': second, 'b': first, 'Aa': first, '_alias': first, 'BB': first, 'private': _private})
