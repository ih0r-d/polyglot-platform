def old_method() -> str:
    return 'retired'


polyglot.export_value('RetiredApi', {'old_method': old_method})
