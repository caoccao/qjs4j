# Property Deletion Analysis: QuickJS C vs Java Implementation

## Executive Summary

**Question:** Does JSObject.delete() really need to track deleted properties?

**Answer:** **YES**, the Java implementation DOES need to track deleted properties, but for different reasons than QuickJS C.

The Java implementation uses a fundamentally different architecture (immutable shape tree, V8-style) compared to QuickJS C (mutable shapes with in-place modification). The `deletedProperties` set is **necessary and correct** for the current Java architecture.

---

## QuickJS C Implementation

### Architecture
- **Mutable shapes** with hash table-based property lookup
- Properties stored in a single array with hash chains for collision resolution
- Direct in-place modification of shape structure

### Deletion Process (from `quickjs.c:8895`)

```c
static int delete_property(JSContext *ctx, JSObject *p, JSAtom atom)
{
    // 1. Search for property in hash table
    h1 = atom & sh->prop_hash_mask;
    h = prop_hash_end(sh)[-h1 - 1];

    // 2. Find the property and check configurability
    if (!(pr->flags & JS_PROP_CONFIGURABLE))
        return FALSE;

    // 3. Remove property from hash chain
    if (lpr) {
        lpr->hash_next = pr->hash_next;
    } else {
        prop_hash_end(sh)[-h1 - 1] = pr->hash_next;
    }

    // 4. Track deletion for compaction
    sh->deleted_prop_count++;

    // 5. Free the property
    free_property(ctx->rt, pr1, pr->flags);
    JS_FreeAtom(ctx, pr->atom);
    pr->flags = 0;
    pr->atom = JS_ATOM_NULL;
    pr1->u.value = JS_UNDEFINED;

    // 6. Compact if too many deletions
    if (sh->deleted_prop_count >= 8 &&
        sh->deleted_prop_count >= ((unsigned)sh->prop_count / 2)) {
        compact_properties(ctx, p);
    }

    return TRUE;
}
```

### Key Features
1. **Actual removal**: Property is removed from hash table immediately
2. **Deletion tracking**: `deleted_prop_count` tracks deletions for compaction
3. **Automatic compaction**: When deletions reach threshold (>=8 and >=50% of properties), array is compacted
4. **Memory reclamation**: Property value is freed immediately

---

## Java Implementation

### Architecture
- **Immutable shape tree** (V8-style hidden classes)
- Shapes are never modified after creation
- Shape transitions create new shapes when properties are added
- Multiple objects can share the same shape

### Deletion Process (from `JSObject.java:104`)

```java
public boolean delete(PropertyKey key) {
    // 1. Check object state
    if (sealed || frozen) {
        return false;
    }

    // 2. Find property in shape
    int offset = shape.getPropertyOffset(key);
    if (offset < 0) {
        // Check sparse properties
        if (key.isIndex() && sparseProperties != null) {
            return sparseProperties.remove(key.asIndex()) != null;
        }
        return true; // Property doesn't exist
    }

    // 3. Check configurability
    PropertyDescriptor desc = shape.getDescriptorAt(offset);
    if (!desc.isConfigurable()) {
        return false;
    }

    // 4. Mark as deleted (NOT removed from shape)
    if (deletedProperties == null) {
        deletedProperties = new HashSet<>();
    }
    deletedProperties.add(key);

    // 5. Set to undefined
    propertyValues[offset] = JSUndefined.INSTANCE;
    return true;
}
```

### Why deletedProperties Set is Necessary

The set is checked in **every property access operation**:

1. **get()** (line 208): Skip deleted properties
```java
if (deletedProperties != null && deletedProperties.contains(key)) {
    // Continue to prototype chain
}
```

2. **hasOwnProperty()** (line 291): Return false for deleted properties
```java
if (deletedProperties != null && deletedProperties.contains(key)) {
    return false;
}
```

3. **getOwnPropertyKeys()** (line 240): Exclude deleted properties
```java
if (deletedProperties == null || !deletedProperties.contains(key)) {
    keys.add(key);
}
```

4. **getEnumerableKeys()** (line 149): Skip deleted properties in enumeration

5. **ownKeys()** (line 345): Exclude from Reflect.ownKeys()

6. **set()** (line 409): Un-delete when setting value
```java
if (deletedProperties != null) {
    deletedProperties.remove(key);
}
```

7. **defineProperty()** (line 71): Un-delete when redefining
```java
if (deletedProperties != null) {
    deletedProperties.remove(key);
}
```

---

## Architectural Differences

| Aspect | QuickJS C | Java Implementation |
|--------|-----------|-------------------|
| **Shape mutability** | Mutable | Immutable |
| **Deletion strategy** | Remove from shape | Mark as deleted |
| **Deletion tracking** | Counter (`deleted_prop_count`) | Set (`deletedProperties`) |
| **Compaction** | Automatic when threshold reached | None |
| **Shape sharing** | Not applicable (mutable) | Multiple objects share shapes |
| **Memory efficiency** | Good (compaction reclaims space) | Poor (deleted properties remain) |
| **Implementation complexity** | Higher (hash table, compaction) | Lower (simple set check) |

---

## Why Different Approaches?

### QuickJS C: Mutable Shapes
- Each object has its own shape instance
- Can modify shape in-place when properties are deleted
- Compaction reorganizes the property array to reclaim space
- No shape sharing between objects (different design)

### Java: Immutable Shapes (V8-style)
- Shapes are shared between objects with same property structure
- Cannot modify shape (would affect all objects sharing it)
- Must use separate tracking mechanism for deletions
- Shape transitions only happen on property *addition*

### Example of Shape Sharing:
```java
// Both objects share the same shape
JSObject obj1 = new JSObject();
obj1.set("x", new JSNumber(1));
obj1.set("y", new JSNumber(2));

JSObject obj2 = new JSObject();
obj2.set("x", new JSNumber(3));
obj2.set("y", new JSNumber(4));

// obj1.shape == obj2.shape (same shape, different values)

// If obj1 deletes "x", we can't modify the shared shape
obj1.delete("x");
// Instead, we mark it in obj1.deletedProperties
// obj2 is unaffected
```

---

## Current Implementation Issues

### 1. Memory Inefficiency
- Deleted properties remain in the shape and propertyValues array forever
- No compaction means memory is never reclaimed
- Objects with many deletions waste significant space

### 2. Performance Overhead
- Set lookup on every property access: `deletedProperties.contains(key)`
- Additional null check: `deletedProperties != null`
- Impacts all property operations even when no deletions have occurred

### 3. No Garbage Collection of Deleted Properties
- QuickJS C compacts when `deleted_prop_count >= 8 AND >= prop_count/2`
- Java implementation never compacts
- Long-lived objects accumulate deleted properties

---

## Potential Improvements

### Option 1: Keep Current Architecture (Recommended)
- **Pros**: Maintains V8-style hidden classes, simple implementation
- **Cons**: Memory inefficiency, performance overhead
- **Improvements**:
  1. Use a more efficient data structure (e.g., BitSet if keys are sequential)
  2. Implement lazy compaction by creating a new shape when deletions exceed threshold
  3. Add shape transition for common deletion patterns

### Option 2: Switch to Mutable Shapes (QuickJS-faithful)
- **Pros**: More faithful to QuickJS, better memory efficiency
- **Cons**: Loses shape sharing benefits, major architectural change
- **Required changes**:
  1. Make shapes mutable
  2. Implement property removal from shape
  3. Implement compaction logic
  4. Remove shape sharing and transitions

### Option 3: Hybrid Approach
- Use immutable shapes for common cases (additions)
- Create new compacted shape when deletions exceed threshold
- Keep deletedProperties for small numbers of deletions
- **Example**:
```java
public boolean delete(PropertyKey key) {
    // ... existing checks ...

    deletedProperties.add(key);
    propertyValues[offset] = JSUndefined.INSTANCE;

    // Compact if too many deletions
    if (deletedProperties.size() >= 8 &&
        deletedProperties.size() >= shape.getPropertyCount() / 2) {
        compactProperties();
    }

    return true;
}

private void compactProperties() {
    // Create new shape without deleted properties
    JSShape newShape = JSShape.getRoot();
    List<JSValue> newValues = new ArrayList<>();

    for (int i = 0; i < shape.getPropertyCount(); i++) {
        PropertyKey key = shape.getPropertyKeys()[i];
        if (!deletedProperties.contains(key)) {
            newShape = newShape.addProperty(key, shape.getDescriptorAt(i));
            newValues.add(propertyValues[i]);
        }
    }

    this.shape = newShape;
    this.propertyValues = newValues.toArray(new JSValue[0]);
    this.deletedProperties = null;
}
```

---

## Conclusion

**The `deletedProperties` set is NECESSARY and CORRECT for the current Java implementation.**

The Java implementation uses an immutable shape tree architecture (similar to V8) rather than QuickJS's mutable shapes. Given this architectural choice:

1. ✅ **Tracking deleted properties is required** - without it, deleted properties would still be visible
2. ✅ **Using a Set is reasonable** - simple and correct implementation
3. ❌ **No compaction is a problem** - memory is never reclaimed
4. ❌ **Performance overhead exists** - set lookup on every property access

**Recommendation**: Keep the current architecture but add **lazy compaction** when deletion threshold is reached (Option 3). This maintains the benefits of immutable shapes while addressing the memory efficiency concerns.

---

## References

- QuickJS source: `../quickjs/quickjs.c:8895` - `delete_property()` function
- QuickJS source: `../quickjs/quickjs.c:10499` - `JS_DeleteProperty()` function
- Java implementation: `src/main/java/com/caoccao/qjs4j/core/JSObject.java:104` - `delete()` method
- Java implementation: `src/main/java/com/caoccao/qjs4j/core/JSShape.java` - immutable shape tree
