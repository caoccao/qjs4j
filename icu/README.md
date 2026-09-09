# ICU data for Javet

`icudtl.dat` is the little-endian ICU 78 data used by Javet 6.0.0's V8 15.3.76.9.
It was copied from `../google/v8/third_party/icu/common/icudtl.dat`.

- ICU revision: `8cc91d9b6ab9991802fd208ee03a69714fd0251c`, pinned by [V8's DEPS](https://github.com/v8/v8/blob/15.3.76.9/DEPS).
- SHA-256: `a3c6d7824935e87fed17e94c2d84b7683ef360ebc5c4ea92e2ca70f361bc9314`.

Update this file together with Javet when its bundled V8 changes ICU versions. Incompatible ICU data can crash
the native runtime during locale and Temporal operations.
