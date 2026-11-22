def hello(name: str) -> str:
    return name


polyglot.export_value('Api', {'hello': hello})
