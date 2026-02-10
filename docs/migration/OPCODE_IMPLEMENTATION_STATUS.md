# QuickJS Opcode Implementation Status

## Overview

This document tracks the implementation status of all QuickJS opcodes in qjs4j. QuickJS has 245 main opcodes (including SHORT_OPCODES optimizations). This document lists all opcodes and their implementation status.

**Total Opcodes**: 245 (all 245 are now DEFINED in Opcode.java enum)
**Enum Definitions**: 262 total opcodes in qjs4j Opcode.java (includes 144 original + 118 newly added)
**Fully Implemented**: 164 (66.7%) - have VM handlers in VirtualMachine.java
**Defined but Not Implemented**: 81 (33.3%) - added to Opcode.java, need VM implementation

**Last Updated**: 2026-02-10

## Important Notes

### Opcode Numbering

qjs4j uses **custom opcode numbers** that differ from QuickJS:
- QuickJS opcodes: 0-244 (sequential)
- qjs4j opcodes: 0-261 (custom numbering to avoid breaking existing code)

Each qjs4j opcode includes a comment indicating its corresponding QuickJS opcode number. For example:
```java
GET_VAR_UNDEF(141, 3, 0, 1),  // QuickJS opcode 55
```

### Implementation Status

- ✅ **Implemented**: Opcode is defined in Opcode.java AND has a handler in VirtualMachine.java
- ⏳ **Defined**: Opcode is in Opcode.java enum but needs VM handler implementation
- ⏳ **Missing** (legacy status): These entries will be updated to **Defined** status

### Recent Updates (2026-02-10)

- Completed all high-priority opcode handlers in `VirtualMachine.java`.
- Added regression tests in `src/test/java/com/caoccao/qjs4j/vm/HighPriorityOpcodeTest.java`.

## Opcode Categories

### Push/Load Operations (0-13)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 0 | INVALID | 1 | ✅ Implemented | - | Never emitted |
| 1 | PUSH_I32 | 5 | ✅ Implemented | - | Push 32-bit integer |
| 2 | PUSH_CONST | 5 | ✅ Implemented | - | Push constant from pool |
| 3 | FCLOSURE | 5 | ✅ Implemented | - | Create function closure |
| 4 | PUSH_ATOM_VALUE | 5 | ✅ Implemented | - | Push atom value |
| 5 | PRIVATE_SYMBOL | 5 | ✅ Implemented | - | Create private symbol |
| 6 | UNDEFINED | 1 | ✅ Implemented | - | Push undefined |
| 7 | NULL | 1 | ✅ Implemented | - | Push null |
| 8 | PUSH_THIS | 1 | ✅ Implemented | - | Push 'this' value |
| 9 | PUSH_FALSE | 1 | ✅ Implemented | - | Push false |
| 10 | PUSH_TRUE | 1 | ✅ Implemented | - | Push true |
| 11 | OBJECT | 1 | ✅ Implemented | - | Create empty object |
| 12 | SPECIAL_OBJECT | 2 | ✅ Implemented | - | Create special object (arguments, etc.) |
| 13 | REST | 3 | ✅ Implemented | - | Collect rest parameters |

### Stack Operations (14-32)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 14 | DROP | 1 | ✅ Implemented | - | Pop and discard |
| 15 | NIP | 1 | ✅ Implemented | - | Remove second element |
| 16 | NIP1 | 1 | ✅ Implemented | - | Remove third element |
| 17 | DUP | 1 | ✅ Implemented | - | Duplicate top |
| 18 | DUP1 | 1 | ✅ Implemented | - | Duplicate second: a b -> a a b |
| 19 | DUP2 | 1 | ✅ Implemented | - | Duplicate pair |
| 20 | DUP3 | 1 | ✅ Implemented | - | Duplicate triple |
| 21 | INSERT2 | 1 | ✅ Implemented | - | Insert before second |
| 22 | INSERT3 | 1 | ✅ Implemented | - | Insert before third |
| 23 | INSERT4 | 1 | ✅ Implemented | - | Insert before fourth |
| 24 | PERM3 | 1 | ✅ Implemented | - | Permute 3 elements |
| 25 | PERM4 | 1 | ✅ Implemented | - | Permute 4 elements |
| 26 | PERM5 | 1 | ✅ Implemented | - | Permute 5 elements |
| 27 | SWAP | 1 | ✅ Implemented | - | Swap top two |
| 28 | SWAP2 | 1 | ✅ Implemented | - | Swap two pairs |
| 29 | ROT3L | 1 | ✅ Implemented | - | Rotate 3 left |
| 30 | ROT3R | 1 | ✅ Implemented | - | Rotate 3 right |
| 31 | ROT4L | 1 | ✅ Implemented | - | Rotate 4 left |
| 32 | ROT5L | 1 | ✅ Implemented | - | Rotate 5 left |

### Call Operations (33-54)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 33 | CALL_CONSTRUCTOR | 3 | ✅ Implemented | - | Call constructor |
| 34 | CALL | 3 | ✅ Implemented | - | Call function |
| 35 | TAIL_CALL | 3 | ✅ Implemented | - | Tail call optimization |
| 36 | CALL_METHOD | 3 | ✅ Implemented | - | Call method |
| 37 | TAIL_CALL_METHOD | 3 | ✅ Implemented | - | Tail call method |
| 38 | ARRAY_FROM | 3 | ✅ Implemented | - | Create array from args |
| 39 | APPLY | 3 | ✅ Implemented | - | Function.apply |
| 40 | RETURN | 1 | ✅ Implemented | - | Return from function |
| 41 | RETURN_UNDEF | 1 | ✅ Implemented | - | Return undefined |
| 42 | CHECK_CTOR_RETURN | 1 | ✅ Implemented | - | Check constructor return |
| 43 | CHECK_CTOR | 1 | ✅ Implemented | - | Check constructor |
| 44 | INIT_CTOR | 1 | ✅ Implemented | - | Initialize constructor |
| 45 | CHECK_BRAND | 1 | ✅ Implemented | - | Check private brand |
| 46 | ADD_BRAND | 1 | ✅ Implemented | - | Add private brand |
| 47 | RETURN_ASYNC | 1 | ✅ Implemented | - | Return from async function |
| 48 | THROW | 1 | ✅ Implemented | - | Throw exception |
| 49 | THROW_ERROR | 6 | ✅ Implemented | - | Throw error with atom |
| 50 | EVAL | 5 | ✅ Implemented | - | Eval function |
| 51 | APPLY_EVAL | 3 | ✅ Implemented | - | Apply eval |
| 52 | REGEXP | 1 | ✅ Implemented | - | Create RegExp |
| 53 | GET_SUPER | 1 | ✅ Implemented | - | Get super |
| 54 | IMPORT | 1 | ✅ Implemented | - | Dynamic import |

### Variable Operations (55-60)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 55 | GET_VAR_UNDEF | 3 | ✅ Implemented | - | Get var, return undefined if not exists |
| 56 | GET_VAR | 3 | ✅ Implemented | - | Get variable |
| 57 | PUT_VAR | 3 | ✅ Implemented | - | Set variable |
| 58 | PUT_VAR_INIT | 3 | ✅ Implemented | - | Initialize global lexical variable |
| 59 | GET_REF_VALUE | 1 | ⏳ **Missing** | MEDIUM | Get reference value |
| 60 | PUT_REF_VALUE | 1 | ⏳ **Missing** | MEDIUM | Set reference value |

### Property Operations (61-84)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 61 | GET_FIELD | 5 | ✅ Implemented | - | Get object field |
| 62 | GET_FIELD2 | 5 | ✅ Implemented | - | Get field, keep obj |
| 63 | PUT_FIELD | 5 | ✅ Implemented | - | Set object field |
| 64 | GET_PRIVATE_FIELD | 1 | ✅ Implemented | - | Get private field |
| 65 | PUT_PRIVATE_FIELD | 1 | ✅ Implemented | - | Set private field |
| 66 | DEFINE_PRIVATE_FIELD | 1 | ✅ Implemented | - | Define private field |
| 67 | GET_ARRAY_EL | 1 | ✅ Implemented | - | Get array element |
| 68 | GET_ARRAY_EL2 | 1 | ⏳ **Missing** | MEDIUM | Get element, keep obj |
| 69 | GET_ARRAY_EL3 | 1 | ⏳ **Missing** | LOW | Get element, keep obj and prop |
| 70 | PUT_ARRAY_EL | 1 | ✅ Implemented | - | Set array element |
| 71 | GET_SUPER_VALUE | 1 | ✅ Implemented | - | Get super property |
| 72 | PUT_SUPER_VALUE | 1 | ✅ Implemented | - | Set super property |
| 73 | DEFINE_FIELD | 5 | ✅ Implemented | - | Define field |
| 74 | SET_NAME | 5 | ⏳ **Missing** | MEDIUM | Set function name |
| 75 | SET_NAME_COMPUTED | 1 | ⏳ **Missing** | MEDIUM | Set computed name |
| 76 | SET_PROTO | 1 | ✅ Implemented | - | Set prototype |
| 77 | SET_HOME_OBJECT | 1 | ✅ Implemented | - | Set home object for super |
| 78 | DEFINE_ARRAY_EL | 1 | ⏳ **Missing** | MEDIUM | Define array element |
| 79 | APPEND | 1 | ⏳ **Missing** | MEDIUM | Append to array |
| 80 | COPY_DATA_PROPERTIES | 2 | ⏳ **Missing** | MEDIUM | Copy properties (spread) |
| 81 | DEFINE_METHOD | 6 | ✅ Implemented | - | Define method |
| 82 | DEFINE_METHOD_COMPUTED | 2 | ⏳ **Missing** | MEDIUM | Define computed method |
| 83 | DEFINE_CLASS | 6 | ✅ Implemented | - | Define class |
| 84 | DEFINE_CLASS_COMPUTED | 6 | ⏳ **Missing** | MEDIUM | Define class with computed name |

### Local Variable Operations (85-103)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 85 | GET_LOC | 3 | ✅ Implemented | - | Get local variable |
| 86 | PUT_LOC | 3 | ✅ Implemented | - | Set local variable |
| 87 | SET_LOC | 3 | ✅ Implemented | - | Set local, keep value |
| 88 | GET_ARG | 3 | ✅ Implemented | - | Get argument |
| 89 | PUT_ARG | 3 | ✅ Implemented | - | Set argument |
| 90 | SET_ARG | 3 | ✅ Implemented | - | Set arg, keep value |
| 91 | GET_VAR_REF | 3 | ✅ Implemented | - | Get var ref (closure) |
| 92 | PUT_VAR_REF | 3 | ✅ Implemented | - | Set var ref |
| 93 | SET_VAR_REF | 3 | ✅ Implemented | - | Set var ref, keep value |
| 94 | SET_LOC_UNINITIALIZED | 3 | ⏳ **Missing** | MEDIUM | Mark local uninitialized |
| 95 | GET_LOC_CHECK | 3 | ⏳ **Missing** | MEDIUM | Get local with TDZ check |
| 96 | PUT_LOC_CHECK | 3 | ⏳ **Missing** | MEDIUM | Set local with TDZ check |
| 97 | SET_LOC_CHECK | 3 | ⏳ **Missing** | MEDIUM | Set local with TDZ check |
| 98 | PUT_LOC_CHECK_INIT | 3 | ⏳ **Missing** | MEDIUM | Initialize local |
| 99 | GET_LOC_CHECKTHIS | 3 | ⏳ **Missing** | MEDIUM | Get 'this' with check |
| 100 | GET_VAR_REF_CHECK | 3 | ⏳ **Missing** | MEDIUM | Get var ref with check |
| 101 | PUT_VAR_REF_CHECK | 3 | ⏳ **Missing** | MEDIUM | Set var ref with check |
| 102 | PUT_VAR_REF_CHECK_INIT | 3 | ⏳ **Missing** | MEDIUM | Initialize var ref |
| 103 | CLOSE_LOC | 3 | ✅ Implemented | - | Close over local (create closure) |

### Control Flow (104-113)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 104 | IF_FALSE | 5 | ✅ Implemented | - | Conditional jump if false |
| 105 | IF_TRUE | 5 | ✅ Implemented | - | Conditional jump if true |
| 106 | GOTO | 5 | ✅ Implemented | - | Unconditional jump |
| 107 | CATCH | 5 | ✅ Implemented | - | Catch exception |
| 108 | GOSUB | 5 | ✅ Implemented | - | Execute finally block |
| 109 | RET | 1 | ✅ Implemented | - | Return from finally |
| 110 | NIP_CATCH | 1 | ⏳ **Missing** | MEDIUM | catch ... a -> a |
| 111 | TO_OBJECT | 1 | ✅ Implemented | - | Convert to object |
| 112 | TO_STRING | 1 | ✅ Implemented | - | Convert to string |
| 113 | TO_PROPKEY | 1 | ✅ Implemented | - | Convert to property key |

### With/Scope Operations (114-122)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 114 | WITH_GET_VAR | 10 | ✅ Implemented | - | Get var in with scope |
| 115 | WITH_PUT_VAR | 10 | ✅ Implemented | - | Set var in with scope |
| 116 | WITH_DELETE_VAR | 10 | ✅ Implemented | - | Delete var in with scope |
| 117 | WITH_MAKE_REF | 10 | ✅ Implemented | - | Make ref in with scope |
| 118 | WITH_GET_REF | 10 | ✅ Implemented | - | Get ref in with scope |
| 119 | MAKE_LOC_REF | 7 | ⏳ **Missing** | MEDIUM | Make local reference |
| 120 | MAKE_ARG_REF | 7 | ⏳ **Missing** | MEDIUM | Make argument reference |
| 121 | MAKE_VAR_REF_REF | 7 | ⏳ **Missing** | MEDIUM | Make var ref reference |
| 122 | MAKE_VAR_REF | 5 | ⏳ **Missing** | MEDIUM | Make variable reference |

### Iteration Operations (123-138)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 123 | FOR_IN_START | 1 | ✅ Implemented | - | Start for-in loop |
| 124 | FOR_OF_START | 1 | ✅ Implemented | - | Start for-of loop |
| 125 | FOR_AWAIT_OF_START | 1 | ✅ Implemented | - | Start for-await-of loop |
| 126 | FOR_IN_NEXT | 1 | ✅ Implemented | - | Get next for-in property |
| 127 | FOR_OF_NEXT | 2 | ✅ Implemented | - | Get next for-of value |
| 128 | FOR_AWAIT_OF_NEXT | 1 | ✅ Implemented | - | Get next for-await-of value |
| 129 | ITERATOR_CHECK_OBJECT | 1 | ✅ Implemented | - | Check iterator result |
| 130 | ITERATOR_GET_VALUE_DONE | 1 | ✅ Implemented | - | Extract value and done |
| 131 | ITERATOR_CLOSE | 1 | ✅ Implemented | - | Close iterator |
| 132 | ITERATOR_NEXT | 1 | ✅ Implemented | - | Call iterator.next() |
| 133 | ITERATOR_CALL | 2 | ✅ Implemented | - | Call iterator method |
| 134 | INITIAL_YIELD | 1 | ✅ Implemented | - | Initial generator yield |
| 135 | YIELD | 1 | ✅ Implemented | - | Yield value |
| 136 | YIELD_STAR | 1 | ✅ Implemented | - | Yield* delegation |
| 137 | ASYNC_YIELD_STAR | 1 | ✅ Implemented | - | Async yield* |
| 138 | AWAIT | 1 | ✅ Implemented | - | Await promise |

### Arithmetic/Unary Operations (139-152)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 139 | NEG | 1 | ✅ Implemented | - | Unary minus |
| 140 | PLUS | 1 | ✅ Implemented | - | Unary plus |
| 141 | DEC | 1 | ✅ Implemented | - | Decrement |
| 142 | INC | 1 | ✅ Implemented | - | Increment |
| 143 | POST_DEC | 1 | ✅ Implemented | - | Post-decrement |
| 144 | POST_INC | 1 | ✅ Implemented | - | Post-increment |
| 145 | DEC_LOC | 2 | ⏳ **Missing** | LOW | Decrement local |
| 146 | INC_LOC | 2 | ⏳ **Missing** | LOW | Increment local |
| 147 | ADD_LOC | 2 | ⏳ **Missing** | LOW | Add to local |
| 148 | NOT | 1 | ✅ Implemented | - | Bitwise NOT |
| 149 | LNOT | 1 | ✅ Implemented | - | Logical NOT (!) |
| 150 | TYPEOF | 1 | ✅ Implemented | - | typeof operator |
| 151 | DELETE | 1 | ✅ Implemented | - | Delete property |
| 152 | DELETE_VAR | 5 | ⏳ **Missing** | MEDIUM | Delete variable |

### Binary Operations (153-174)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 153 | MUL | 1 | ✅ Implemented | - | Multiplication |
| 154 | DIV | 1 | ✅ Implemented | - | Division |
| 155 | MOD | 1 | ✅ Implemented | - | Modulo |
| 156 | ADD | 1 | ✅ Implemented | - | Addition |
| 157 | SUB | 1 | ✅ Implemented | - | Subtraction |
| 158 | POW | 1 | ✅ Implemented | - | Exponentiation (**) |
| 159 | SHL | 1 | ✅ Implemented | - | Left shift |
| 160 | SAR | 1 | ✅ Implemented | - | Arithmetic right shift |
| 161 | SHR | 1 | ✅ Implemented | - | Logical right shift |
| 162 | LT | 1 | ✅ Implemented | - | Less than |
| 163 | LTE | 1 | ✅ Implemented | - | Less than or equal |
| 164 | GT | 1 | ✅ Implemented | - | Greater than |
| 165 | GTE | 1 | ✅ Implemented | - | Greater than or equal |
| 166 | INSTANCEOF | 1 | ✅ Implemented | - | instanceof operator |
| 167 | IN | 1 | ✅ Implemented | - | in operator |
| 168 | EQ | 1 | ✅ Implemented | - | Equality (==) |
| 169 | NEQ | 1 | ✅ Implemented | - | Inequality (!=) |
| 170 | STRICT_EQ | 1 | ✅ Implemented | - | Strict equality (===) |
| 171 | STRICT_NEQ | 1 | ✅ Implemented | - | Strict inequality (!==) |
| 172 | AND | 1 | ✅ Implemented | - | Bitwise AND |
| 173 | XOR | 1 | ✅ Implemented | - | Bitwise XOR |
| 174 | OR | 1 | ✅ Implemented | - | Bitwise OR |

### Type Check Operations (175-177)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 175 | IS_UNDEFINED_OR_NULL | 1 | ✅ Implemented | - | Check null/undefined |
| 176 | PRIVATE_IN | 1 | ✅ Implemented | - | Check private field |
| 177 | PUSH_BIGINT_I32 | 5 | ⏳ **Missing** | MEDIUM | Push BigInt from i32 |
| 178 | NOP | 1 | ⏳ **Missing** | LOW | No operation |

### SHORT_OPCODES (Optimizations 179-244)

#### Short Push Operations (179-189)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 179 | PUSH_MINUS1 | 1 | ⏳ **Missing** | LOW | Push -1 |
| 180 | PUSH_0 | 1 | ⏳ **Missing** | LOW | Push 0 |
| 181 | PUSH_1 | 1 | ⏳ **Missing** | LOW | Push 1 |
| 182 | PUSH_2 | 1 | ⏳ **Missing** | LOW | Push 2 |
| 183 | PUSH_3 | 1 | ⏳ **Missing** | LOW | Push 3 |
| 184 | PUSH_4 | 1 | ⏳ **Missing** | LOW | Push 4 |
| 185 | PUSH_5 | 1 | ⏳ **Missing** | LOW | Push 5 |
| 186 | PUSH_6 | 1 | ⏳ **Missing** | LOW | Push 6 |
| 187 | PUSH_7 | 1 | ⏳ **Missing** | LOW | Push 7 |
| 188 | PUSH_I8 | 2 | ⏳ **Missing** | LOW | Push 8-bit integer |
| 189 | PUSH_I16 | 3 | ⏳ **Missing** | LOW | Push 16-bit integer |

#### Short Constant Operations (190-193)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 190 | PUSH_CONST8 | 2 | ⏳ **Missing** | LOW | Push const (8-bit index) |
| 191 | FCLOSURE8 | 2 | ⏳ **Missing** | LOW | Create closure (8-bit index) |
| 192 | PUSH_EMPTY_STRING | 1 | ⏳ **Missing** | LOW | Push "" |

#### Short Local Operations (193-205)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 193 | GET_LOC8 | 2 | ⏳ **Missing** | MEDIUM | Get local (8-bit index) |
| 194 | PUT_LOC8 | 2 | ⏳ **Missing** | MEDIUM | Set local (8-bit index) |
| 195 | SET_LOC8 | 2 | ⏳ **Missing** | MEDIUM | Set local, keep value (8-bit) |
| 196 | GET_LOC0 | 1 | ⏳ **Missing** | MEDIUM | Get local 0 |
| 197 | GET_LOC1 | 1 | ⏳ **Missing** | MEDIUM | Get local 1 |
| 198 | GET_LOC2 | 1 | ⏳ **Missing** | MEDIUM | Get local 2 |
| 199 | GET_LOC3 | 1 | ⏳ **Missing** | MEDIUM | Get local 3 |
| 200 | PUT_LOC0 | 1 | ⏳ **Missing** | MEDIUM | Set local 0 |
| 201 | PUT_LOC1 | 1 | ⏳ **Missing** | MEDIUM | Set local 1 |
| 202 | PUT_LOC2 | 1 | ⏳ **Missing** | MEDIUM | Set local 2 |
| 203 | PUT_LOC3 | 1 | ⏳ **Missing** | MEDIUM | Set local 3 |
| 204 | SET_LOC0 | 1 | ⏳ **Missing** | MEDIUM | Set local 0, keep value |
| 205 | SET_LOC1 | 1 | ⏳ **Missing** | MEDIUM | Set local 1, keep value |
| 206 | SET_LOC2 | 1 | ⏳ **Missing** | MEDIUM | Set local 2, keep value |
| 207 | SET_LOC3 | 1 | ⏳ **Missing** | MEDIUM | Set local 3, keep value |

#### Short Argument Operations (208-219)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 208 | GET_ARG0 | 1 | ⏳ **Missing** | LOW | Get argument 0 |
| 209 | GET_ARG1 | 1 | ⏳ **Missing** | LOW | Get argument 1 |
| 210 | GET_ARG2 | 1 | ⏳ **Missing** | LOW | Get argument 2 |
| 211 | GET_ARG3 | 1 | ⏳ **Missing** | LOW | Get argument 3 |
| 212 | PUT_ARG0 | 1 | ⏳ **Missing** | LOW | Set argument 0 |
| 213 | PUT_ARG1 | 1 | ⏳ **Missing** | LOW | Set argument 1 |
| 214 | PUT_ARG2 | 1 | ⏳ **Missing** | LOW | Set argument 2 |
| 215 | PUT_ARG3 | 1 | ⏳ **Missing** | LOW | Set argument 3 |
| 216 | SET_ARG0 | 1 | ⏳ **Missing** | LOW | Set arg 0, keep value |
| 217 | SET_ARG1 | 1 | ⏳ **Missing** | LOW | Set arg 1, keep value |
| 218 | SET_ARG2 | 1 | ⏳ **Missing** | LOW | Set arg 2, keep value |
| 219 | SET_ARG3 | 1 | ⏳ **Missing** | LOW | Set arg 3, keep value |

#### Short Var Ref Operations (220-231)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 220 | GET_VAR_REF0 | 1 | ⏳ **Missing** | LOW | Get var ref 0 |
| 221 | GET_VAR_REF1 | 1 | ⏳ **Missing** | LOW | Get var ref 1 |
| 222 | GET_VAR_REF2 | 1 | ⏳ **Missing** | LOW | Get var ref 2 |
| 223 | GET_VAR_REF3 | 1 | ⏳ **Missing** | LOW | Get var ref 3 |
| 224 | PUT_VAR_REF0 | 1 | ⏳ **Missing** | LOW | Set var ref 0 |
| 225 | PUT_VAR_REF1 | 1 | ⏳ **Missing** | LOW | Set var ref 1 |
| 226 | PUT_VAR_REF2 | 1 | ⏳ **Missing** | LOW | Set var ref 2 |
| 227 | PUT_VAR_REF3 | 1 | ⏳ **Missing** | LOW | Set var ref 3 |
| 228 | SET_VAR_REF0 | 1 | ⏳ **Missing** | LOW | Set var ref 0, keep value |
| 229 | SET_VAR_REF1 | 1 | ⏳ **Missing** | LOW | Set var ref 1, keep value |
| 230 | SET_VAR_REF2 | 1 | ⏳ **Missing** | LOW | Set var ref 2, keep value |
| 231 | SET_VAR_REF3 | 1 | ⏳ **Missing** | LOW | Set var ref 3, keep value |

#### Short Misc Operations (232-244)
| # | Opcode | Size | Status | Priority | Notes |
|---|--------|------|--------|----------|-------|
| 232 | GET_LENGTH | 1 | ⏳ **Missing** | MEDIUM | Get array length |
| 233 | IF_FALSE8 | 2 | ⏳ **Missing** | LOW | Conditional jump (8-bit) |
| 234 | IF_TRUE8 | 2 | ⏳ **Missing** | LOW | Conditional jump (8-bit) |
| 235 | GOTO8 | 2 | ⏳ **Missing** | LOW | Jump (8-bit offset) |
| 236 | GOTO16 | 3 | ⏳ **Missing** | LOW | Jump (16-bit offset) |
| 237 | CALL0 | 1 | ⏳ **Missing** | LOW | Call with 0 args |
| 238 | CALL1 | 1 | ⏳ **Missing** | LOW | Call with 1 arg |
| 239 | CALL2 | 1 | ⏳ **Missing** | LOW | Call with 2 args |
| 240 | CALL3 | 1 | ⏳ **Missing** | LOW | Call with 3 args |
| 241 | IS_UNDEFINED | 1 | ⏳ **Missing** | LOW | Check if undefined |
| 242 | IS_NULL | 1 | ⏳ **Missing** | LOW | Check if null |
| 243 | TYPEOF_IS_UNDEFINED | 1 | ⏳ **Missing** | LOW | typeof === 'undefined' |
| 244 | TYPEOF_IS_FUNCTION | 1 | ⏳ **Missing** | LOW | typeof === 'function' |

## Implementation Priority

### HIGH Priority (Core Functionality)
Completed on 2026-02-10.

### MEDIUM Priority (Enhanced Functionality)
These opcodes provide important but non-critical features:

1. **GET_REF_VALUE**, **PUT_REF_VALUE** (59-60) - Reference operations
2. **GET_ARRAY_EL2** (68) - Array access variants
3. **SET_NAME**, **SET_NAME_COMPUTED** (74-75) - Function naming
4. **DEFINE_ARRAY_EL**, **APPEND** (78-79) - Array construction
5. **COPY_DATA_PROPERTIES** (80) - Object spread
6. **DEFINE_METHOD_COMPUTED**, **DEFINE_CLASS_COMPUTED** (82, 84) - Computed names
7. **Local check operations** (94-102) - TDZ (Temporal Dead Zone) checks
8. **Reference operations** (119-122) - Closure references
9. **GET_LENGTH** (232) - Array length optimization

### LOW Priority (Optimizations)
These are performance optimizations that can be implemented later:

1. **SHORT_OPCODES** (179-244) - All short opcode variants
2. **DEC_LOC**, **INC_LOC**, **ADD_LOC** (145-147) - Local optimizations
3. **Short local/arg/varref operations** (193-231) - Fast path for common cases

## Temporary Opcodes (Compiler-Only)

These opcodes are used during compilation and removed before final bytecode:

- **enter_scope**, **leave_scope** - Scope tracking
- **label** - Label placeholder
- **scope_get_var_undef**, **scope_get_var**, **scope_put_var**, **scope_delete_var** - Scope resolution
- **scope_make_ref**, **scope_get_ref**, **scope_put_var_init** - Scope references
- **scope_get_var_checkthis** - 'this' check in derived constructors
- **scope_get_private_field**, **scope_get_private_field2**, **scope_put_private_field** - Private field resolution
- **scope_in_private_field** - Private field check
- **get_field_opt_chain**, **get_array_el_opt_chain** - Optional chaining
- **set_class_name** - Class name setting
- **line_num** - Line number tracking

## Implementation Strategy

1. **Phase 1: Core Opcodes** (HIGH priority)
   - Implement essential opcodes for basic functionality
   - Focus on variable access, closures, and control flow
   - Estimated: 20-30 opcodes

2. **Phase 2: Enhanced Opcodes** (MEDIUM priority)
   - Add opcodes for advanced features
   - Implement computed properties, spread, etc.
   - Estimated: 30-40 opcodes

3. **Phase 3: Optimizations** (LOW priority)
   - Add SHORT_OPCODES for performance
   - Implement fast paths for common operations
   - Estimated: 60+ opcodes

4. **Phase 4: Compiler Temporary Opcodes**
   - Enhance compiler to use temporary opcodes
   - Improve bytecode generation quality
   - These never appear in final bytecode

## Testing Strategy

For each implemented opcode:
1. Add unit test in `OpcodeTest.java`
2. Add VM execution test in `VirtualMachineTest.java`
3. Add integration test in relevant feature test
4. Verify against QuickJS behavior
5. Add to Test262 coverage

## References

- QuickJS Source: `../quickjs/quickjs-opcode.h`
- QuickJS VM: `../quickjs/quickjs.c` (bytecode interpreter)
- qjs4j Opcode Enum: `src/main/java/com/caoccao/qjs4j/vm/Opcode.java`
- qjs4j VM: `src/main/java/com/caoccao/qjs4j/vm/VirtualMachine.java`
