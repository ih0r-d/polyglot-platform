def greet() -> str:
    return 'second'


polyglot.export_value('SharedApi', {'greet': greet})
