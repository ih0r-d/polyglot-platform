from typing import List as Sequence
import typing as types
class Aliased:
    def unresolved(self, values: Sequence[int]) -> types.Dict[str, int]:
        return {}
polyglot.export_value('Aliased', Aliased)
