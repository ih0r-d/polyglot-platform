package io.github.ih0rd.codegen.types;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

class PythonTypeMapperTest {

  private final PythonTypeMapper mapper = new PythonTypeMapper();

  @Test
  void mapPrimitive_ShouldMapBasicTypes() {
    assertEquals(PolyPrimitive.INT, mapper.mapPrimitive("int"));
    assertEquals(PolyPrimitive.FLOAT, mapper.mapPrimitive("float"));
    assertEquals(PolyPrimitive.STRING, mapper.mapPrimitive("str"));
    assertEquals(PolyPrimitive.BOOLEAN, mapper.mapPrimitive("bool"));
  }

  @Test
  void mapPrimitive_ShouldHandleNullAndEmpty() {
    assertInstanceOf(PolyUnknown.class, mapper.mapPrimitive(null));
    assertInstanceOf(PolyUnknown.class, mapper.mapPrimitive(""));
    assertInstanceOf(PolyUnknown.class, mapper.mapPrimitive("   "));
  }

  @Test
  void mapPrimitive_ShouldHandleWhitespace() {
    assertEquals(PolyPrimitive.INT, mapper.mapPrimitive(" int "));
  }

  @Test
  void mapPrimitive_ShouldMapList() {
    PolyType result = mapper.mapPrimitive("list");
    assertInstanceOf(PolyList.class, result);
    assertInstanceOf(PolyUnknown.class, ((PolyList) result).elementType());
  }

  @Test
  void mapPrimitive_ShouldMapDict() {
    PolyType result = mapper.mapPrimitive("dict");
    assertInstanceOf(PolyMap.class, result);
    assertEquals(PolyPrimitive.STRING, ((PolyMap) result).keyType());
    assertInstanceOf(PolyUnknown.class, ((PolyMap) result).valueType());
  }

  @Test
  void mapPrimitive_ShouldMapGenericList() {
    // List[int]
    PolyType result = mapper.mapPrimitive("List[int]");
    assertInstanceOf(PolyList.class, result);
    assertEquals(PolyPrimitive.INT, ((PolyList) result).elementType());

    // list[str] (lowercase)
    result = mapper.mapPrimitive("list[str]");
    assertInstanceOf(PolyList.class, result);
    assertEquals(PolyPrimitive.STRING, ((PolyList) result).elementType());
  }

  @Test
  void mapPrimitive_ShouldMapGenericSet() {
    // Set[float] -> List<Double>
    PolyType result = mapper.mapPrimitive("Set[float]");
    assertInstanceOf(PolyList.class, result);
    assertEquals(PolyPrimitive.FLOAT, ((PolyList) result).elementType());
  }

  @Test
  void mapPrimitive_ShouldMapGenericTuple_Homogeneous() {
    // Tuple[int, int] -> List<Integer>
    PolyType result = mapper.mapPrimitive("Tuple[int, int]");
    assertInstanceOf(PolyList.class, result);
    assertEquals(PolyPrimitive.INT, ((PolyList) result).elementType());
  }

  @Test
  void mapPrimitive_ShouldMapGenericTuple_Mixed() {
    // Tuple[int, str] -> List<Object> (PolyUnknown)
    PolyType result = mapper.mapPrimitive("Tuple[int, str]");
    assertInstanceOf(PolyList.class, result);
    assertInstanceOf(PolyUnknown.class, ((PolyList) result).elementType());
  }

  @Test
  void mapPrimitive_ShouldMapGenericDict() {
    // Dict[str, int]
    PolyType result = mapper.mapPrimitive("Dict[str, int]");
    assertInstanceOf(PolyMap.class, result);
    assertEquals(PolyPrimitive.STRING, ((PolyMap) result).keyType());
    assertEquals(PolyPrimitive.INT, ((PolyMap) result).valueType());
  }

  @Test
  void mapPrimitive_ShouldMapGenericDict_ComplexKey() {
    // Dict[int, bool] -> Map<Integer, Boolean> (Allowed by mapper, though parser/renderer might
    // constrain keys to String)
    PolyType result = mapper.mapPrimitive("Dict[int, bool]");
    assertInstanceOf(PolyMap.class, result);
    assertEquals(PolyPrimitive.INT, ((PolyMap) result).keyType());
    assertEquals(PolyPrimitive.BOOLEAN, ((PolyMap) result).valueType());
  }

  @Test
  void mapPrimitive_ShouldHandleNestedGenerics() {
    // List[List[int]]
    PolyType result = mapper.mapPrimitive("List[List[int]]");
    assertInstanceOf(PolyList.class, result);
    PolyType inner = ((PolyList) result).elementType();
    assertInstanceOf(PolyList.class, inner);
    assertEquals(PolyPrimitive.INT, ((PolyList) inner).elementType());
  }

  @Test
  void mapPrimitive_ShouldHandleOptional() {
    // Optional[str] -> String
    PolyType result = mapper.mapPrimitive("Optional[str]");
    assertEquals(PolyPrimitive.STRING, result);

    // Optional[] -> Unknown
    assertInstanceOf(PolyUnknown.class, mapper.mapPrimitive("Optional[]"));
  }

  @Test
  void mapPrimitive_ShouldHandleUnion() {
    // Union[int, str] -> Unknown
    assertInstanceOf(PolyUnknown.class, mapper.mapPrimitive("Union[int, str]"));
  }

  @Test
  void mapPrimitive_ShouldHandleAny() {
    assertInstanceOf(PolyUnknown.class, mapper.mapPrimitive("Any"));
  }

  @Test
  void mapPrimitive_ShouldHandleUnknownTypes() {
    assertInstanceOf(PolyUnknown.class, mapper.mapPrimitive("FooBar"));
  }

  @Test
  void mapPrimitive_ShouldHandleMalformedGenerics() {
    // List[ -> Unknown
    assertInstanceOf(PolyUnknown.class, mapper.mapPrimitive("List["));
    // List] -> Unknown
    assertInstanceOf(PolyUnknown.class, mapper.mapPrimitive("List]"));
  }

  @Test
  void mapPrimitive_ShouldReuseCachedMappingsForNormalizedInput() {
    PolyType first = mapper.mapPrimitive(" List[int] ");
    PolyType second = mapper.mapPrimitive("List[int]");
    assertSame(first, second);
  }
}
