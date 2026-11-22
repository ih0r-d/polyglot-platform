def greet() -> str:
    return 'first'


polyglot.export_value('FirstApi', {'greet': greet})
polyglot.export_value('SharedApi', {'greet': greet})
