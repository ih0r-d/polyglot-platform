import external_library
import external_library
class Base:
    pass
class SourceApi(Base):
    def hello(self, name: str, /, times: int = 1, *args, suffix='!', **kwargs) -> str:
        return name
    def nested(self, values: list[dict[str, list[int]]]) -> dict[str, list[int]]:
        return {'a': [1]}
    def unknown(self, value: ExternalType):
        return value
    def inferred(self):
        return [{'a': [1, 2]}, {'b': [3]}]
    def mixed(self):
        return [{'a': 1}, {'b': 'text'}]
    def annotation(self) -> Tuple[List[int], List[str]]:
        return ([], [])
    def maps(self) -> Tuple[Dict[str, int], Dict[str, int]]:
        return ({}, {})
    def nested_maps(self) -> Tuple[List[Dict[str, int]], List[Dict[str, int]]]:
        return ([], [])
    def unknown_union(self) -> Union[set[int], int]:
        return 1
    def _private(self):
        pass
polyglot.export_value('GoldenApi', SourceApi)
