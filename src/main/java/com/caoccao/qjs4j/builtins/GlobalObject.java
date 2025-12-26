/*
 * Copyright (c) 2025-2026. caoccao.com Sam Cao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The global object with built-in functions.
 * Based on ECMAScript specification global properties and functions.
 *
 * Implements:
 * - Global value properties (NaN, Infinity, undefined)
 * - Global function properties (parseInt, parseFloat, isNaN, isFinite, eval)
 * - URI handling functions (encodeURI, decodeURI, encodeURIComponent, decodeURIComponent)
 */
public final class GlobalObject {

    /**
     * Initialize the global object with all built-in global properties and functions.
     */
    public static void initialize(JSContext ctx, JSObject global) {
        // Global value properties
        global.set("undefined", JSUndefined.INSTANCE);
        global.set("NaN", new JSNumber(Double.NaN));
        global.set("Infinity", new JSNumber(Double.POSITIVE_INFINITY));

        // Global function properties
        global.set("parseInt", createNativeFunction(ctx, "parseInt", GlobalObject::parseInt, 2));
        global.set("parseFloat", createNativeFunction(ctx, "parseFloat", GlobalObject::parseFloat, 1));
        global.set("isNaN", createNativeFunction(ctx, "isNaN", GlobalObject::isNaN, 1));
        global.set("isFinite", createNativeFunction(ctx, "isFinite", GlobalObject::isFinite, 1));
        global.set("eval", createNativeFunction(ctx, "eval", GlobalObject::eval, 1));

        // URI handling functions
        global.set("encodeURI", createNativeFunction(ctx, "encodeURI", GlobalObject::encodeURI, 1));
        global.set("decodeURI", createNativeFunction(ctx, "decodeURI", GlobalObject::decodeURI, 1));
        global.set("encodeURIComponent", createNativeFunction(ctx, "encodeURIComponent", GlobalObject::encodeURIComponent, 1));
        global.set("decodeURIComponent", createNativeFunction(ctx, "decodeURIComponent", GlobalObject::decodeURIComponent, 1));

        // Console object for debugging
        initializeConsoleObject(ctx, global);

        // Global this reference
        global.set("globalThis", global);

        // Built-in constructors and their prototypes
        initializeObjectConstructor(ctx, global);
        initializeBooleanConstructor(ctx, global);
        initializeArrayConstructor(ctx, global);
        initializeStringConstructor(ctx, global);
        initializeNumberConstructor(ctx, global);
        initializeFunctionConstructor(ctx, global);
        initializeDateConstructor(ctx, global);
        initializeRegExpConstructor(ctx, global);
        initializeSymbolConstructor(ctx, global);
        initializeBigIntConstructor(ctx, global);
        initializeMapConstructor(ctx, global);
        initializeSetConstructor(ctx, global);
        initializeWeakMapConstructor(ctx, global);
        initializeWeakSetConstructor(ctx, global);
        initializeMathObject(ctx, global);
        initializeJSONObject(ctx, global);
        initializeReflectObject(ctx, global);
        initializeProxyConstructor(ctx, global);
        initializePromiseConstructor(ctx, global);
        initializeGeneratorPrototype(ctx, global);

        // Binary data constructors
        initializeArrayBufferConstructor(ctx, global);
        initializeDataViewConstructor(ctx, global);
        initializeTypedArrayConstructors(ctx, global);

        // Error constructors
        initializeErrorConstructors(ctx, global);
    }

    /**
     * Initialize Object constructor and static methods.
     */
    private static void initializeObjectConstructor(JSContext ctx, JSObject global) {
        // Create Object.prototype
        JSObject objectPrototype = new JSObject();
        objectPrototype.set("hasOwnProperty", createNativeFunction(ctx, "hasOwnProperty", ObjectConstructor::hasOwnProperty, 1));
        objectPrototype.set("toString", createNativeFunction(ctx, "toString", ObjectConstructor::hasOwnProperty, 0)); // TODO: implement proper toString

        // Create Object constructor
        JSObject objectConstructor = new JSObject();
        objectConstructor.set("prototype", objectPrototype);

        // Object static methods
        objectConstructor.set("keys", createNativeFunction(ctx, "keys", ObjectConstructor::keys, 1));
        objectConstructor.set("values", createNativeFunction(ctx, "values", ObjectConstructor::values, 1));
        objectConstructor.set("entries", createNativeFunction(ctx, "entries", ObjectConstructor::entries, 1));
        objectConstructor.set("assign", createNativeFunction(ctx, "assign", ObjectConstructor::assign, 2));
        objectConstructor.set("create", createNativeFunction(ctx, "create", ObjectConstructor::create, 2));
        objectConstructor.set("getPrototypeOf", createNativeFunction(ctx, "getPrototypeOf", ObjectConstructor::getPrototypeOf, 1));
        objectConstructor.set("setPrototypeOf", createNativeFunction(ctx, "setPrototypeOf", ObjectConstructor::setPrototypeOf, 2));
        objectConstructor.set("freeze", createNativeFunction(ctx, "freeze", ObjectConstructor::freeze, 1));
        objectConstructor.set("seal", createNativeFunction(ctx, "seal", ObjectConstructor::seal, 1));
        objectConstructor.set("isFrozen", createNativeFunction(ctx, "isFrozen", ObjectConstructor::isFrozen, 1));
        objectConstructor.set("isSealed", createNativeFunction(ctx, "isSealed", ObjectConstructor::isSealed, 1));

        global.set("Object", objectConstructor);
    }

    /**
     * Initialize Boolean constructor and prototype.
     */
    private static void initializeBooleanConstructor(JSContext ctx, JSObject global) {
        // Create Boolean.prototype
        JSObject booleanPrototype = new JSObject();
        booleanPrototype.set("toString", createNativeFunction(ctx, "toString", BooleanPrototype::toString, 0));
        booleanPrototype.set("valueOf", createNativeFunction(ctx, "valueOf", BooleanPrototype::valueOf, 0));

        // Create Boolean constructor (placeholder)
        JSObject booleanConstructor = new JSObject();
        booleanConstructor.set("prototype", booleanPrototype);

        global.set("Boolean", booleanConstructor);
    }

    /**
     * Initialize Array constructor and prototype.
     */
    private static void initializeArrayConstructor(JSContext ctx, JSObject global) {
        // Create Array.prototype
        JSObject arrayPrototype = new JSObject();
        arrayPrototype.set("push", createNativeFunction(ctx, "push", ArrayPrototype::push, 1));
        arrayPrototype.set("pop", createNativeFunction(ctx, "pop", ArrayPrototype::pop, 0));
        arrayPrototype.set("shift", createNativeFunction(ctx, "shift", ArrayPrototype::shift, 0));
        arrayPrototype.set("unshift", createNativeFunction(ctx, "unshift", ArrayPrototype::unshift, 1));
        arrayPrototype.set("slice", createNativeFunction(ctx, "slice", ArrayPrototype::slice, 2));
        arrayPrototype.set("splice", createNativeFunction(ctx, "splice", ArrayPrototype::splice, 2));
        arrayPrototype.set("concat", createNativeFunction(ctx, "concat", ArrayPrototype::concat, 1));
        arrayPrototype.set("join", createNativeFunction(ctx, "join", ArrayPrototype::join, 1));
        arrayPrototype.set("reverse", createNativeFunction(ctx, "reverse", ArrayPrototype::reverse, 0));
        arrayPrototype.set("sort", createNativeFunction(ctx, "sort", ArrayPrototype::sort, 1));
        arrayPrototype.set("indexOf", createNativeFunction(ctx, "indexOf", ArrayPrototype::indexOf, 1));
        arrayPrototype.set("lastIndexOf", createNativeFunction(ctx, "lastIndexOf", ArrayPrototype::lastIndexOf, 1));
        arrayPrototype.set("includes", createNativeFunction(ctx, "includes", ArrayPrototype::includes, 1));
        arrayPrototype.set("map", createNativeFunction(ctx, "map", ArrayPrototype::map, 1));
        arrayPrototype.set("filter", createNativeFunction(ctx, "filter", ArrayPrototype::filter, 1));
        arrayPrototype.set("reduce", createNativeFunction(ctx, "reduce", ArrayPrototype::reduce, 1));
        arrayPrototype.set("reduceRight", createNativeFunction(ctx, "reduceRight", ArrayPrototype::reduceRight, 1));
        arrayPrototype.set("forEach", createNativeFunction(ctx, "forEach", ArrayPrototype::forEach, 1));
        arrayPrototype.set("values", createNativeFunction(ctx, "values", IteratorPrototype::arrayValues, 0));
        arrayPrototype.set("keys", createNativeFunction(ctx, "keys", IteratorPrototype::arrayKeys, 0));
        arrayPrototype.set("entries", createNativeFunction(ctx, "entries", IteratorPrototype::arrayEntries, 0));
        // Array.prototype[Symbol.iterator] is the same as values()
        arrayPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), createNativeFunction(ctx, "[Symbol.iterator]", IteratorPrototype::arrayValues, 0));
        arrayPrototype.set("find", createNativeFunction(ctx, "find", ArrayPrototype::find, 1));
        arrayPrototype.set("findIndex", createNativeFunction(ctx, "findIndex", ArrayPrototype::findIndex, 1));
        arrayPrototype.set("every", createNativeFunction(ctx, "every", ArrayPrototype::every, 1));
        arrayPrototype.set("some", createNativeFunction(ctx, "some", ArrayPrototype::some, 1));
        arrayPrototype.set("flat", createNativeFunction(ctx, "flat", ArrayPrototype::flat, 0));
        arrayPrototype.set("toString", createNativeFunction(ctx, "toString", ArrayPrototype::toString, 0));

        // Create Array constructor with static methods
        JSObject arrayConstructor = new JSObject();
        arrayConstructor.set("prototype", arrayPrototype);

        // Array static methods
        arrayConstructor.set("isArray", createNativeFunction(ctx, "isArray", ArrayConstructor::isArray, 1));
        arrayConstructor.set("of", createNativeFunction(ctx, "of", ArrayConstructor::of, 0));
        arrayConstructor.set("from", createNativeFunction(ctx, "from", ArrayConstructor::from, 1));

        global.set("Array", arrayConstructor);
    }

    /**
     * Initialize String constructor and prototype.
     */
    private static void initializeStringConstructor(JSContext ctx, JSObject global) {
        // Create String.prototype
        JSObject stringPrototype = new JSObject();
        stringPrototype.set("charAt", createNativeFunction(ctx, "charAt", StringPrototype::charAt, 1));
        stringPrototype.set("charCodeAt", createNativeFunction(ctx, "charCodeAt", StringPrototype::charCodeAt, 1));
        stringPrototype.set("codePointAt", createNativeFunction(ctx, "codePointAt", StringPrototype::codePointAt, 1));
        stringPrototype.set("concat", createNativeFunction(ctx, "concat", StringPrototype::concat, 1));
        stringPrototype.set("endsWith", createNativeFunction(ctx, "endsWith", StringPrototype::endsWith, 1));
        stringPrototype.set("startsWith", createNativeFunction(ctx, "startsWith", StringPrototype::startsWith, 1));
        stringPrototype.set("includes", createNativeFunction(ctx, "includes", StringPrototype::includes, 1));
        stringPrototype.set("indexOf", createNativeFunction(ctx, "indexOf", StringPrototype::indexOf, 1));
        stringPrototype.set("lastIndexOf", createNativeFunction(ctx, "lastIndexOf", StringPrototype::lastIndexOf, 1));
        stringPrototype.set("padEnd", createNativeFunction(ctx, "padEnd", StringPrototype::padEnd, 1));
        stringPrototype.set("padStart", createNativeFunction(ctx, "padStart", StringPrototype::padStart, 1));
        stringPrototype.set("repeat", createNativeFunction(ctx, "repeat", StringPrototype::repeat, 1));
        stringPrototype.set("replace", createNativeFunction(ctx, "replace", StringPrototype::replace, 2));
        stringPrototype.set("replaceAll", createNativeFunction(ctx, "replaceAll", StringPrototype::replaceAll, 2));
        stringPrototype.set("slice", createNativeFunction(ctx, "slice", StringPrototype::slice, 2));
        stringPrototype.set("split", createNativeFunction(ctx, "split", StringPrototype::split, 2));
        stringPrototype.set("substring", createNativeFunction(ctx, "substring", StringPrototype::substring, 2));
        stringPrototype.set("substr", createNativeFunction(ctx, "substr", StringPrototype::substr, 2));
        stringPrototype.set("toLowerCase", createNativeFunction(ctx, "toLowerCase", StringPrototype::toLowerCase, 0));
        stringPrototype.set("toUpperCase", createNativeFunction(ctx, "toUpperCase", StringPrototype::toUpperCase, 0));
        stringPrototype.set("trim", createNativeFunction(ctx, "trim", StringPrototype::trim, 0));
        stringPrototype.set("trimStart", createNativeFunction(ctx, "trimStart", StringPrototype::trimStart, 0));
        stringPrototype.set("trimEnd", createNativeFunction(ctx, "trimEnd", StringPrototype::trimEnd, 0));
        stringPrototype.set("toString", createNativeFunction(ctx, "toString", StringPrototype::toStringMethod, 0));
        stringPrototype.set("valueOf", createNativeFunction(ctx, "valueOf", StringPrototype::valueOf, 0));
        // String.prototype[Symbol.iterator]
        stringPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), createNativeFunction(ctx, "[Symbol.iterator]", IteratorPrototype::stringIterator, 0));

        // String constructor is a placeholder
        JSObject stringConstructor = new JSObject();
        stringConstructor.set("prototype", stringPrototype);

        global.set("String", stringConstructor);
    }

    /**
     * Initialize Number constructor and prototype.
     */
    private static void initializeNumberConstructor(JSContext ctx, JSObject global) {
        // Create Number.prototype
        JSObject numberPrototype = new JSObject();
        numberPrototype.set("toFixed", createNativeFunction(ctx, "toFixed", NumberPrototype::toFixed, 1));
        numberPrototype.set("toExponential", createNativeFunction(ctx, "toExponential", NumberPrototype::toExponential, 1));
        numberPrototype.set("toPrecision", createNativeFunction(ctx, "toPrecision", NumberPrototype::toPrecision, 1));
        numberPrototype.set("toString", createNativeFunction(ctx, "toString", NumberPrototype::toString, 1));
        numberPrototype.set("toLocaleString", createNativeFunction(ctx, "toLocaleString", NumberPrototype::toLocaleString, 0));
        numberPrototype.set("valueOf", createNativeFunction(ctx, "valueOf", NumberPrototype::valueOf, 0));

        // Number constructor with static methods
        JSObject numberConstructor = new JSObject();
        numberConstructor.set("prototype", numberPrototype);
        numberConstructor.set("isNaN", createNativeFunction(ctx, "isNaN", NumberPrototype::isNaN, 1));
        numberConstructor.set("isFinite", createNativeFunction(ctx, "isFinite", NumberPrototype::isFinite, 1));
        numberConstructor.set("isInteger", createNativeFunction(ctx, "isInteger", NumberPrototype::isInteger, 1));
        numberConstructor.set("isSafeInteger", createNativeFunction(ctx, "isSafeInteger", NumberPrototype::isSafeInteger, 1));
        numberConstructor.set("parseFloat", createNativeFunction(ctx, "parseFloat", NumberPrototype::parseFloat, 1));
        numberConstructor.set("parseInt", createNativeFunction(ctx, "parseInt", NumberPrototype::parseInt, 2));

        global.set("Number", numberConstructor);
    }

    /**
     * Initialize Function constructor and prototype.
     */
    private static void initializeFunctionConstructor(JSContext ctx, JSObject global) {
        // Create Function.prototype
        JSObject functionPrototype = new JSObject();
        functionPrototype.set("call", createNativeFunction(ctx, "call", FunctionPrototype::call, 1));
        functionPrototype.set("apply", createNativeFunction(ctx, "apply", FunctionPrototype::apply, 2));
        functionPrototype.set("bind", createNativeFunction(ctx, "bind", FunctionPrototype::bind, 1));
        functionPrototype.set("toString", createNativeFunction(ctx, "toString", FunctionPrototype::toStringMethod, 0));

        // Function constructor is a placeholder
        JSObject functionConstructor = new JSObject();
        functionConstructor.set("prototype", functionPrototype);

        global.set("Function", functionConstructor);
    }

    /**
     * Initialize Date constructor and prototype.
     */
    private static void initializeDateConstructor(JSContext ctx, JSObject global) {
        // Create Date.prototype
        JSObject datePrototype = new JSObject();
        datePrototype.set("getTime", createNativeFunction(ctx, "getTime", DatePrototype::getTime, 0));
        datePrototype.set("getFullYear", createNativeFunction(ctx, "getFullYear", DatePrototype::getFullYear, 0));
        datePrototype.set("getMonth", createNativeFunction(ctx, "getMonth", DatePrototype::getMonth, 0));
        datePrototype.set("getDate", createNativeFunction(ctx, "getDate", DatePrototype::getDate, 0));
        datePrototype.set("getDay", createNativeFunction(ctx, "getDay", DatePrototype::getDay, 0));
        datePrototype.set("getHours", createNativeFunction(ctx, "getHours", DatePrototype::getHours, 0));
        datePrototype.set("getMinutes", createNativeFunction(ctx, "getMinutes", DatePrototype::getMinutes, 0));
        datePrototype.set("getSeconds", createNativeFunction(ctx, "getSeconds", DatePrototype::getSeconds, 0));
        datePrototype.set("getMilliseconds", createNativeFunction(ctx, "getMilliseconds", DatePrototype::getMilliseconds, 0));
        datePrototype.set("getUTCFullYear", createNativeFunction(ctx, "getUTCFullYear", DatePrototype::getUTCFullYear, 0));
        datePrototype.set("getUTCMonth", createNativeFunction(ctx, "getUTCMonth", DatePrototype::getUTCMonth, 0));
        datePrototype.set("getUTCDate", createNativeFunction(ctx, "getUTCDate", DatePrototype::getUTCDate, 0));
        datePrototype.set("getUTCHours", createNativeFunction(ctx, "getUTCHours", DatePrototype::getUTCHours, 0));
        datePrototype.set("toISOString", createNativeFunction(ctx, "toISOString", DatePrototype::toISOString, 0));
        datePrototype.set("toJSON", createNativeFunction(ctx, "toJSON", DatePrototype::toJSON, 0));
        datePrototype.set("toString", createNativeFunction(ctx, "toString", DatePrototype::toStringMethod, 0));
        datePrototype.set("valueOf", createNativeFunction(ctx, "valueOf", DatePrototype::valueOf, 0));

        // Create Date constructor with static methods
        JSObject dateConstructor = new JSObject();
        dateConstructor.set("prototype", datePrototype);
        dateConstructor.set("[[DateConstructor]]", JSBoolean.TRUE); // Mark as Date constructor

        // Date static methods
        dateConstructor.set("now", createNativeFunction(ctx, "now", DateConstructor::now, 0));
        dateConstructor.set("parse", createNativeFunction(ctx, "parse", DateConstructor::parse, 1));
        dateConstructor.set("UTC", createNativeFunction(ctx, "UTC", DateConstructor::UTC, 7));

        global.set("Date", dateConstructor);
    }

    /**
     * Initialize RegExp constructor and prototype.
     */
    private static void initializeRegExpConstructor(JSContext ctx, JSObject global) {
        // Create RegExp.prototype
        JSObject regexpPrototype = new JSObject();
        regexpPrototype.set("test", createNativeFunction(ctx, "test", RegExpPrototype::test, 1));
        regexpPrototype.set("exec", createNativeFunction(ctx, "exec", RegExpPrototype::exec, 1));
        regexpPrototype.set("toString", createNativeFunction(ctx, "toString", RegExpPrototype::toStringMethod, 0));

        // Create RegExp constructor
        JSObject regexpConstructor = new JSObject();
        regexpConstructor.set("prototype", regexpPrototype);
        regexpConstructor.set("[[RegExpConstructor]]", JSBoolean.TRUE); // Mark as RegExp constructor

        global.set("RegExp", regexpConstructor);
    }

    /**
     * Initialize Symbol constructor and static methods.
     */
    private static void initializeSymbolConstructor(JSContext ctx, JSObject global) {
        // Create Symbol.prototype
        JSObject symbolPrototype = new JSObject();
        symbolPrototype.set("toString", createNativeFunction(ctx, "toString", SymbolPrototype::toString, 0));
        symbolPrototype.set("valueOf", createNativeFunction(ctx, "valueOf", SymbolPrototype::valueOf, 0));
        // Symbol.prototype.description is a getter, set as property for now
        symbolPrototype.set("description", JSUndefined.INSTANCE);
        symbolPrototype.set("[Symbol.toPrimitive]", createNativeFunction(ctx, "[Symbol.toPrimitive]", SymbolPrototype::toPrimitive, 1));
        symbolPrototype.set("[Symbol.toStringTag]", new JSString("Symbol"));

        // Create Symbol constructor
        JSObject symbolConstructor = new JSObject();
        symbolConstructor.set("prototype", symbolPrototype);
        symbolConstructor.set("[[SymbolConstructor]]", JSBoolean.TRUE); // Mark as Symbol constructor

        // Symbol static methods
        symbolConstructor.set("for", createNativeFunction(ctx, "for", SymbolConstructor::symbolFor, 1));
        symbolConstructor.set("keyFor", createNativeFunction(ctx, "keyFor", SymbolConstructor::keyFor, 1));

        // Well-known symbols
        symbolConstructor.set("iterator", JSSymbol.ITERATOR);
        symbolConstructor.set("toStringTag", JSSymbol.TO_STRING_TAG);
        symbolConstructor.set("hasInstance", JSSymbol.HAS_INSTANCE);
        symbolConstructor.set("isConcatSpreadable", JSSymbol.IS_CONCAT_SPREADABLE);
        symbolConstructor.set("toPrimitive", JSSymbol.TO_PRIMITIVE);

        global.set("Symbol", symbolConstructor);
    }

    /**
     * Initialize BigInt constructor and static methods.
     */
    private static void initializeBigIntConstructor(JSContext ctx, JSObject global) {
        // Create BigInt.prototype
        JSObject bigIntPrototype = new JSObject();
        bigIntPrototype.set("toString", createNativeFunction(ctx, "toString", BigIntPrototype::toString, 1));
        bigIntPrototype.set("valueOf", createNativeFunction(ctx, "valueOf", BigIntPrototype::valueOf, 0));
        bigIntPrototype.set("toLocaleString", createNativeFunction(ctx, "toLocaleString", BigIntPrototype::toLocaleString, 0));

        // Create BigInt constructor
        JSObject bigIntConstructor = new JSObject();
        bigIntConstructor.set("prototype", bigIntPrototype);
        bigIntConstructor.set("[[BigIntConstructor]]", JSBoolean.TRUE); // Mark as BigInt constructor

        // BigInt static methods
        bigIntConstructor.set("asIntN", createNativeFunction(ctx, "asIntN", BigIntConstructor::asIntN, 2));
        bigIntConstructor.set("asUintN", createNativeFunction(ctx, "asUintN", BigIntConstructor::asUintN, 2));

        global.set("BigInt", bigIntConstructor);
    }

    /**
     * Initialize Map constructor and prototype methods.
     */
    private static void initializeMapConstructor(JSContext ctx, JSObject global) {
        // Create Map.prototype
        JSObject mapPrototype = new JSObject();
        mapPrototype.set("set", createNativeFunction(ctx, "set", MapPrototype::set, 2));
        mapPrototype.set("get", createNativeFunction(ctx, "get", MapPrototype::get, 1));
        mapPrototype.set("has", createNativeFunction(ctx, "has", MapPrototype::has, 1));
        mapPrototype.set("delete", createNativeFunction(ctx, "delete", MapPrototype::delete, 1));
        mapPrototype.set("clear", createNativeFunction(ctx, "clear", MapPrototype::clear, 0));
        mapPrototype.set("forEach", createNativeFunction(ctx, "forEach", MapPrototype::forEach, 1));
        mapPrototype.set("entries", createNativeFunction(ctx, "entries", IteratorPrototype::mapEntriesIterator, 0));
        mapPrototype.set("keys", createNativeFunction(ctx, "keys", IteratorPrototype::mapKeysIterator, 0));
        mapPrototype.set("values", createNativeFunction(ctx, "values", IteratorPrototype::mapValuesIterator, 0));
        // Map.prototype[Symbol.iterator] is the same as entries()
        mapPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), createNativeFunction(ctx, "[Symbol.iterator]", IteratorPrototype::mapEntriesIterator, 0));

        // Create Map constructor
        JSObject mapConstructor = new JSObject();
        mapConstructor.set("prototype", mapPrototype);
        mapConstructor.set("[[MapConstructor]]", JSBoolean.TRUE); // Mark as Map constructor

        global.set("Map", mapConstructor);
    }

    /**
     * Initialize Set constructor and prototype methods.
     */
    private static void initializeSetConstructor(JSContext ctx, JSObject global) {
        // Create Set.prototype
        JSObject setPrototype = new JSObject();
        setPrototype.set("add", createNativeFunction(ctx, "add", SetPrototype::add, 1));
        setPrototype.set("has", createNativeFunction(ctx, "has", SetPrototype::has, 1));
        setPrototype.set("delete", createNativeFunction(ctx, "delete", SetPrototype::delete, 1));
        setPrototype.set("clear", createNativeFunction(ctx, "clear", SetPrototype::clear, 0));
        setPrototype.set("forEach", createNativeFunction(ctx, "forEach", SetPrototype::forEach, 1));
        setPrototype.set("entries", createNativeFunction(ctx, "entries", IteratorPrototype::setEntriesIterator, 0));
        setPrototype.set("keys", createNativeFunction(ctx, "keys", IteratorPrototype::setKeysIterator, 0));
        setPrototype.set("values", createNativeFunction(ctx, "values", IteratorPrototype::setValuesIterator, 0));
        // Set.prototype[Symbol.iterator] is the same as values()
        setPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), createNativeFunction(ctx, "[Symbol.iterator]", IteratorPrototype::setValuesIterator, 0));

        // Create Set constructor
        JSObject setConstructor = new JSObject();
        setConstructor.set("prototype", setPrototype);
        setConstructor.set("[[SetConstructor]]", JSBoolean.TRUE); // Mark as Set constructor

        global.set("Set", setConstructor);
    }

    /**
     * Initialize WeakMap constructor and prototype methods.
     */
    private static void initializeWeakMapConstructor(JSContext ctx, JSObject global) {
        // Create WeakMap.prototype
        JSObject weakMapPrototype = new JSObject();
        weakMapPrototype.set("set", createNativeFunction(ctx, "set", WeakMapPrototype::set, 2));
        weakMapPrototype.set("get", createNativeFunction(ctx, "get", WeakMapPrototype::get, 1));
        weakMapPrototype.set("has", createNativeFunction(ctx, "has", WeakMapPrototype::has, 1));
        weakMapPrototype.set("delete", createNativeFunction(ctx, "delete", WeakMapPrototype::delete, 1));

        // Create WeakMap constructor
        JSObject weakMapConstructor = new JSObject();
        weakMapConstructor.set("prototype", weakMapPrototype);
        weakMapConstructor.set("[[WeakMapConstructor]]", JSBoolean.TRUE); // Mark as WeakMap constructor

        global.set("WeakMap", weakMapConstructor);
    }

    /**
     * Initialize WeakSet constructor and prototype methods.
     */
    private static void initializeWeakSetConstructor(JSContext ctx, JSObject global) {
        // Create WeakSet.prototype
        JSObject weakSetPrototype = new JSObject();
        weakSetPrototype.set("add", createNativeFunction(ctx, "add", WeakSetPrototype::add, 1));
        weakSetPrototype.set("has", createNativeFunction(ctx, "has", WeakSetPrototype::has, 1));
        weakSetPrototype.set("delete", createNativeFunction(ctx, "delete", WeakSetPrototype::delete, 1));

        // Create WeakSet constructor
        JSObject weakSetConstructor = new JSObject();
        weakSetConstructor.set("prototype", weakSetPrototype);
        weakSetConstructor.set("[[WeakSetConstructor]]", JSBoolean.TRUE); // Mark as WeakSet constructor

        global.set("WeakSet", weakSetConstructor);
    }

    /**
     * Initialize Math object.
     */
    private static void initializeMathObject(JSContext ctx, JSObject global) {
        JSObject math = new JSObject();

        // Math constants
        math.set("E", new JSNumber(MathObject.E));
        math.set("LN10", new JSNumber(MathObject.LN10));
        math.set("LN2", new JSNumber(MathObject.LN2));
        math.set("LOG10E", new JSNumber(MathObject.LOG10E));
        math.set("LOG2E", new JSNumber(MathObject.LOG2E));
        math.set("PI", new JSNumber(MathObject.PI));
        math.set("SQRT1_2", new JSNumber(MathObject.SQRT1_2));
        math.set("SQRT2", new JSNumber(MathObject.SQRT2));

        // Math methods
        math.set("abs", createNativeFunction(ctx, "abs", MathObject::abs, 1));
        math.set("acos", createNativeFunction(ctx, "acos", MathObject::acos, 1));
        math.set("acosh", createNativeFunction(ctx, "acosh", MathObject::acosh, 1));
        math.set("asin", createNativeFunction(ctx, "asin", MathObject::asin, 1));
        math.set("asinh", createNativeFunction(ctx, "asinh", MathObject::asinh, 1));
        math.set("atan", createNativeFunction(ctx, "atan", MathObject::atan, 1));
        math.set("atanh", createNativeFunction(ctx, "atanh", MathObject::atanh, 1));
        math.set("atan2", createNativeFunction(ctx, "atan2", MathObject::atan2, 2));
        math.set("cbrt", createNativeFunction(ctx, "cbrt", MathObject::cbrt, 1));
        math.set("ceil", createNativeFunction(ctx, "ceil", MathObject::ceil, 1));
        math.set("clz32", createNativeFunction(ctx, "clz32", MathObject::clz32, 1));
        math.set("cos", createNativeFunction(ctx, "cos", MathObject::cos, 1));
        math.set("cosh", createNativeFunction(ctx, "cosh", MathObject::cosh, 1));
        math.set("exp", createNativeFunction(ctx, "exp", MathObject::exp, 1));
        math.set("expm1", createNativeFunction(ctx, "expm1", MathObject::expm1, 1));
        math.set("floor", createNativeFunction(ctx, "floor", MathObject::floor, 1));
        math.set("fround", createNativeFunction(ctx, "fround", MathObject::fround, 1));
        math.set("hypot", createNativeFunction(ctx, "hypot", MathObject::hypot, 0));
        math.set("imul", createNativeFunction(ctx, "imul", MathObject::imul, 2));
        math.set("log", createNativeFunction(ctx, "log", MathObject::log, 1));
        math.set("log1p", createNativeFunction(ctx, "log1p", MathObject::log1p, 1));
        math.set("log10", createNativeFunction(ctx, "log10", MathObject::log10, 1));
        math.set("log2", createNativeFunction(ctx, "log2", MathObject::log2, 1));
        math.set("max", createNativeFunction(ctx, "max", MathObject::max, 2));
        math.set("min", createNativeFunction(ctx, "min", MathObject::min, 2));
        math.set("pow", createNativeFunction(ctx, "pow", MathObject::pow, 2));
        math.set("random", createNativeFunction(ctx, "random", MathObject::random, 0));
        math.set("round", createNativeFunction(ctx, "round", MathObject::round, 1));
        math.set("sign", createNativeFunction(ctx, "sign", MathObject::sign, 1));
        math.set("sin", createNativeFunction(ctx, "sin", MathObject::sin, 1));
        math.set("sinh", createNativeFunction(ctx, "sinh", MathObject::sinh, 1));
        math.set("sqrt", createNativeFunction(ctx, "sqrt", MathObject::sqrt, 1));
        math.set("tan", createNativeFunction(ctx, "tan", MathObject::tan, 1));
        math.set("tanh", createNativeFunction(ctx, "tanh", MathObject::tanh, 1));
        math.set("trunc", createNativeFunction(ctx, "trunc", MathObject::trunc, 1));

        global.set("Math", math);
    }

    /**
     * Initialize JSON object.
     */
    private static void initializeJSONObject(JSContext ctx, JSObject global) {
        JSObject json = new JSObject();
        json.set("parse", createNativeFunction(ctx, "parse", JSONObject::parse, 1));
        json.set("stringify", createNativeFunction(ctx, "stringify", JSONObject::stringify, 1));

        global.set("JSON", json);
    }

    /**
     * Initialize Reflect object.
     */
    private static void initializeReflectObject(JSContext ctx, JSObject global) {
        JSObject reflect = new JSObject();
        reflect.set("get", createNativeFunction(ctx, "get", ReflectObject::get, 2));
        reflect.set("set", createNativeFunction(ctx, "set", ReflectObject::set, 3));
        reflect.set("has", createNativeFunction(ctx, "has", ReflectObject::has, 2));
        reflect.set("deleteProperty", createNativeFunction(ctx, "deleteProperty", ReflectObject::deleteProperty, 2));
        reflect.set("getPrototypeOf", createNativeFunction(ctx, "getPrototypeOf", ReflectObject::getPrototypeOf, 1));
        reflect.set("setPrototypeOf", createNativeFunction(ctx, "setPrototypeOf", ReflectObject::setPrototypeOf, 2));
        reflect.set("ownKeys", createNativeFunction(ctx, "ownKeys", ReflectObject::ownKeys, 1));
        reflect.set("apply", createNativeFunction(ctx, "apply", ReflectObject::apply, 3));
        reflect.set("construct", createNativeFunction(ctx, "construct", ReflectObject::construct, 2));
        reflect.set("isExtensible", createNativeFunction(ctx, "isExtensible", ReflectObject::isExtensible, 1));
        reflect.set("preventExtensions", createNativeFunction(ctx, "preventExtensions", ReflectObject::preventExtensions, 1));

        global.set("Reflect", reflect);
    }

    /**
     * Initialize Proxy constructor.
     */
    private static void initializeProxyConstructor(JSContext ctx, JSObject global) {
        // Create Proxy constructor (special handling required in VM)
        JSObject proxyConstructor = new JSObject();
        proxyConstructor.set("[[ProxyConstructor]]", JSBoolean.TRUE); // Mark as Proxy constructor

        // Add static methods
        proxyConstructor.set("revocable", createNativeFunction(ctx, "revocable", ProxyConstructor::revocable, 2));

        global.set("Proxy", proxyConstructor);
    }

    /**
     * Initialize Promise constructor and prototype methods.
     */
    private static void initializePromiseConstructor(JSContext ctx, JSObject global) {
        // Create Promise.prototype
        JSObject promisePrototype = new JSObject();
        promisePrototype.set("then", createNativeFunction(ctx, "then", PromisePrototype::then, 2));
        promisePrototype.set("catch", createNativeFunction(ctx, "catch", PromisePrototype::catchMethod, 1));
        promisePrototype.set("finally", createNativeFunction(ctx, "finally", PromisePrototype::finallyMethod, 1));

        // Create Promise constructor
        JSObject promiseConstructor = new JSObject();
        promiseConstructor.set("prototype", promisePrototype);
        promiseConstructor.set("[[PromiseConstructor]]", JSBoolean.TRUE); // Mark as Promise constructor

        // Static methods
        promiseConstructor.set("resolve", createNativeFunction(ctx, "resolve", PromiseConstructor::resolve, 1));
        promiseConstructor.set("reject", createNativeFunction(ctx, "reject", PromiseConstructor::reject, 1));
        promiseConstructor.set("all", createNativeFunction(ctx, "all", PromiseConstructor::all, 1));
        promiseConstructor.set("race", createNativeFunction(ctx, "race", PromiseConstructor::race, 1));
        promiseConstructor.set("allSettled", createNativeFunction(ctx, "allSettled", PromiseConstructor::allSettled, 1));
        promiseConstructor.set("any", createNativeFunction(ctx, "any", PromiseConstructor::any, 1));

        global.set("Promise", promiseConstructor);
    }

    /**
     * Initialize Generator prototype methods.
     * Note: Generator functions (function*) would require compiler support.
     * This provides the prototype for manually created generators.
     */
    private static void initializeGeneratorPrototype(JSContext ctx, JSObject global) {
        // Create Generator.prototype
        JSObject generatorPrototype = new JSObject();
        generatorPrototype.set("next", createNativeFunction(ctx, "next", GeneratorPrototype::next, 1));
        generatorPrototype.set("return", createNativeFunction(ctx, "return", GeneratorPrototype::returnMethod, 1));
        generatorPrototype.set("throw", createNativeFunction(ctx, "throw", GeneratorPrototype::throwMethod, 1));
        // Generator.prototype[Symbol.iterator] returns this
        generatorPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR),
                createNativeFunction(ctx, "[Symbol.iterator]", (context, thisArg, args) -> thisArg, 0));

        // Create GeneratorFunction constructor (placeholder)
        JSObject generatorFunction = new JSObject();
        generatorFunction.set("prototype", generatorPrototype);

        // Store for use by generator instances
        global.set("GeneratorFunction", generatorFunction);
    }

    /**
     * Initialize ArrayBuffer constructor and prototype.
     */
    private static void initializeArrayBufferConstructor(JSContext ctx, JSObject global) {
        // Create ArrayBuffer.prototype
        JSObject arrayBufferPrototype = new JSObject();
        arrayBufferPrototype.set("slice", createNativeFunction(ctx, "slice", ArrayBufferPrototype::slice, 2));
        // byteLength getter
        JSNativeFunction byteLengthGetter = createNativeFunction(ctx, "get byteLength", ArrayBufferPrototype::getByteLength, 0);
        arrayBufferPrototype.set("byteLength", byteLengthGetter); // Simplified: should be a getter

        // Create ArrayBuffer constructor
        JSObject arrayBufferConstructor = new JSObject();
        arrayBufferConstructor.set("prototype", arrayBufferPrototype);
        arrayBufferConstructor.set("[[ArrayBufferConstructor]]", JSBoolean.TRUE);

        // Static methods
        arrayBufferConstructor.set("isView", createNativeFunction(ctx, "isView", ArrayBufferConstructor::isView, 1));

        global.set("ArrayBuffer", arrayBufferConstructor);
    }

    /**
     * Initialize DataView constructor and prototype.
     */
    private static void initializeDataViewConstructor(JSContext ctx, JSObject global) {
        // Create DataView.prototype
        JSObject dataViewPrototype = new JSObject();

        // Getters (simplified as regular properties)
        dataViewPrototype.set("buffer", createNativeFunction(ctx, "get buffer", DataViewPrototype::getBuffer, 0));
        dataViewPrototype.set("byteLength", createNativeFunction(ctx, "get byteLength", DataViewPrototype::getByteLength, 0));
        dataViewPrototype.set("byteOffset", createNativeFunction(ctx, "get byteOffset", DataViewPrototype::getByteOffset, 0));

        // Int8/Uint8 methods
        dataViewPrototype.set("getInt8", createNativeFunction(ctx, "getInt8", DataViewPrototype::getInt8, 1));
        dataViewPrototype.set("setInt8", createNativeFunction(ctx, "setInt8", DataViewPrototype::setInt8, 2));
        dataViewPrototype.set("getUint8", createNativeFunction(ctx, "getUint8", DataViewPrototype::getUint8, 1));
        dataViewPrototype.set("setUint8", createNativeFunction(ctx, "setUint8", DataViewPrototype::setUint8, 2));

        // Int16 methods
        dataViewPrototype.set("getInt16", createNativeFunction(ctx, "getInt16", DataViewPrototype::getInt16, 2));
        dataViewPrototype.set("setInt16", createNativeFunction(ctx, "setInt16", DataViewPrototype::setInt16, 3));

        // Int32 methods
        dataViewPrototype.set("getInt32", createNativeFunction(ctx, "getInt32", DataViewPrototype::getInt32, 2));
        dataViewPrototype.set("setInt32", createNativeFunction(ctx, "setInt32", DataViewPrototype::setInt32, 3));

        // Float32 methods
        dataViewPrototype.set("getFloat32", createNativeFunction(ctx, "getFloat32", DataViewPrototype::getFloat32, 2));
        dataViewPrototype.set("setFloat32", createNativeFunction(ctx, "setFloat32", DataViewPrototype::setFloat32, 3));

        // Float64 methods
        dataViewPrototype.set("getFloat64", createNativeFunction(ctx, "getFloat64", DataViewPrototype::getFloat64, 2));
        dataViewPrototype.set("setFloat64", createNativeFunction(ctx, "setFloat64", DataViewPrototype::setFloat64, 3));

        // Create DataView constructor
        JSObject dataViewConstructor = new JSObject();
        dataViewConstructor.set("prototype", dataViewPrototype);
        dataViewConstructor.set("[[DataViewConstructor]]", JSBoolean.TRUE);

        global.set("DataView", dataViewConstructor);
    }

    /**
     * Initialize all TypedArray constructors.
     */
    private static void initializeTypedArrayConstructors(JSContext ctx, JSObject global) {
        // Int8Array
        JSObject int8ArrayConstructor = new JSObject();
        int8ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Int8Array"));
        int8ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(1));
        global.set("Int8Array", int8ArrayConstructor);

        // Uint8Array
        JSObject uint8ArrayConstructor = new JSObject();
        uint8ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Uint8Array"));
        uint8ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(1));
        global.set("Uint8Array", uint8ArrayConstructor);

        // Uint8ClampedArray
        JSObject uint8ClampedArrayConstructor = new JSObject();
        uint8ClampedArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Uint8ClampedArray"));
        uint8ClampedArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(1));
        global.set("Uint8ClampedArray", uint8ClampedArrayConstructor);

        // Int16Array
        JSObject int16ArrayConstructor = new JSObject();
        int16ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Int16Array"));
        int16ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(2));
        global.set("Int16Array", int16ArrayConstructor);

        // Uint16Array
        JSObject uint16ArrayConstructor = new JSObject();
        uint16ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Uint16Array"));
        uint16ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(2));
        global.set("Uint16Array", uint16ArrayConstructor);

        // Int32Array
        JSObject int32ArrayConstructor = new JSObject();
        int32ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Int32Array"));
        int32ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(4));
        global.set("Int32Array", int32ArrayConstructor);

        // Uint32Array
        JSObject uint32ArrayConstructor = new JSObject();
        uint32ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Uint32Array"));
        uint32ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(4));
        global.set("Uint32Array", uint32ArrayConstructor);

        // Float32Array
        JSObject float32ArrayConstructor = new JSObject();
        float32ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Float32Array"));
        float32ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(4));
        global.set("Float32Array", float32ArrayConstructor);

        // Float64Array
        JSObject float64ArrayConstructor = new JSObject();
        float64ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Float64Array"));
        float64ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(8));
        global.set("Float64Array", float64ArrayConstructor);
    }

    /**
     * Helper to create a native function.
     */
    private static JSNativeFunction createNativeFunction(JSContext ctx, String name,
                                                         JSNativeFunction.NativeCallback implementation, int length) {
        return new JSNativeFunction(name, length, implementation);
    }

    // Global function implementations

    /**
     * parseInt(string, radix)
     * Parse a string and return an integer of the specified radix.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-parseint-string-radix">ECMAScript parseInt</a>
     */
    public static JSValue parseInt(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // Get input string
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(input).getValue().trim();

        // Get radix
        int radix = 10;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            double radixNum = JSTypeConversions.toNumber(args[1]).value();
            radix = (int) radixNum;
        }

        // Handle empty string
        if (inputString.isEmpty()) {
            return new JSNumber(Double.NaN);
        }

        // Determine sign
        int sign = 1;
        int index = 0;
        char firstChar = inputString.charAt(0);
        if (firstChar == '+') {
            index = 1;
        } else if (firstChar == '-') {
            sign = -1;
            index = 1;
        }

        // Auto-detect radix 16 for "0x" prefix
        if (radix == 0 || radix == 16) {
            if (index + 1 < inputString.length() &&
                inputString.charAt(index) == '0' &&
                (inputString.charAt(index + 1) == 'x' || inputString.charAt(index + 1) == 'X')) {
                radix = 16;
                index += 2;
            } else if (radix == 0) {
                radix = 10;
            }
        }

        // Validate radix
        if (radix < 2 || radix > 36) {
            return new JSNumber(Double.NaN);
        }

        // Parse digits
        long result = 0;
        boolean foundDigit = false;
        while (index < inputString.length()) {
            char c = inputString.charAt(index);
            int digit = Character.digit(c, radix);
            if (digit == -1) {
                break; // Stop at first invalid character
            }
            result = result * radix + digit;
            foundDigit = true;
            index++;
        }

        if (!foundDigit) {
            return new JSNumber(Double.NaN);
        }

        return new JSNumber(sign * result);
    }

    /**
     * parseFloat(string)
     * Parse a string and return a floating point number.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-parsefloat-string">ECMAScript parseFloat</a>
     */
    public static JSValue parseFloat(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // Get input string
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(input).getValue().trim();

        if (inputString.isEmpty()) {
            return new JSNumber(Double.NaN);
        }

        // Try to parse as double
        try {
            // Handle Infinity
            if (inputString.startsWith("Infinity") || inputString.startsWith("+Infinity")) {
                return new JSNumber(Double.POSITIVE_INFINITY);
            }
            if (inputString.startsWith("-Infinity")) {
                return new JSNumber(Double.NEGATIVE_INFINITY);
            }

            // Parse as much as possible from the beginning
            StringBuilder validPart = new StringBuilder();
            boolean hasDecimal = false;
            boolean hasExponent = false;
            int i = 0;

            // Sign
            if (i < inputString.length() && (inputString.charAt(i) == '+' || inputString.charAt(i) == '-')) {
                validPart.append(inputString.charAt(i));
                i++;
            }

            // Digits, decimal point, exponent
            while (i < inputString.length()) {
                char c = inputString.charAt(i);
                if (Character.isDigit(c)) {
                    validPart.append(c);
                } else if (c == '.' && !hasDecimal && !hasExponent) {
                    validPart.append(c);
                    hasDecimal = true;
                } else if ((c == 'e' || c == 'E') && !hasExponent && validPart.length() > 0) {
                    validPart.append(c);
                    hasExponent = true;
                    // Check for exponent sign
                    if (i + 1 < inputString.length() &&
                        (inputString.charAt(i + 1) == '+' || inputString.charAt(i + 1) == '-')) {
                        i++;
                        validPart.append(inputString.charAt(i));
                    }
                } else {
                    break;
                }
                i++;
            }

            if (validPart.length() == 0 || validPart.toString().equals("+") || validPart.toString().equals("-")) {
                return new JSNumber(Double.NaN);
            }

            double result = Double.parseDouble(validPart.toString());
            return new JSNumber(result);
        } catch (NumberFormatException e) {
            return new JSNumber(Double.NaN);
        }
    }

    /**
     * isNaN(value)
     * Determine whether a value is NaN.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-isnan-number">ECMAScript isNaN</a>
     */
    public static JSValue isNaN(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double num = JSTypeConversions.toNumber(value).value();
        return JSBoolean.valueOf(Double.isNaN(num));
    }

    /**
     * isFinite(value)
     * Determine whether a value is a finite number.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-isfinite-number">ECMAScript isFinite</a>
     */
    public static JSValue isFinite(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double num = JSTypeConversions.toNumber(value).value();
        return JSBoolean.valueOf(!Double.isNaN(num) && !Double.isInfinite(num));
    }

    /**
     * eval(code)
     * Evaluate JavaScript code in the current context.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-eval-x">ECMAScript eval</a>
     */
    public static JSValue eval(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue x = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // If x is not a string, return it unchanged
        if (!(x instanceof JSString)) {
            return x;
        }

        String code = ((JSString) x).getValue();
        return ctx.eval(code);
    }

    /**
     * encodeURI(uri)
     * Encode a URI by escaping certain characters.
     * Does not encode: A-Z a-z 0-9 ; , / ? : @ & = + $ - _ . ! ~ * ' ( ) #
     *
     * @see <a href="https://tc39.es/ecma262/#sec-encodeuri-uri">ECMAScript encodeURI</a>
     */
    public static JSValue encodeURI(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue uriValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String uriString = JSTypeConversions.toString(uriValue).getValue();

        try {
            // Encode, but preserve URI structure characters
            String encoded = URLEncoder.encode(uriString, StandardCharsets.UTF_8);

            // Restore characters that should not be encoded by encodeURI
            encoded = encoded
                .replace("%3B", ";").replace("%2C", ",")
                .replace("%2F", "/").replace("%3F", "?")
                .replace("%3A", ":").replace("%40", "@")
                .replace("%26", "&").replace("%3D", "=")
                .replace("%2B", "+").replace("%24", "$")
                .replace("%2D", "-").replace("%5F", "_")
                .replace("%2E", ".").replace("%21", "!")
                .replace("%7E", "~").replace("%2A", "*")
                .replace("%27", "'").replace("%28", "(")
                .replace("%29", ")").replace("%23", "#");

            return new JSString(encoded);
        } catch (Exception e) {
            return ctx.throwError("URIError", "URI malformed");
        }
    }

    /**
     * decodeURI(encodedURI)
     * Decode a URI that was encoded by encodeURI.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-decodeuri-encodeduri">ECMAScript decodeURI</a>
     */
    public static JSValue decodeURI(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue encodedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String encodedString = JSTypeConversions.toString(encodedValue).getValue();

        try {
            String decoded = URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
            return new JSString(decoded);
        } catch (Exception e) {
            return ctx.throwError("URIError", "URI malformed");
        }
    }

    /**
     * encodeURIComponent(uriComponent)
     * Encode a URI component by escaping certain characters.
     * More aggressive than encodeURI - also encodes: ; , / ? : @ & = + $ #
     *
     * @see <a href="https://tc39.es/ecma262/#sec-encodeuricomponent-uricomponent">ECMAScript encodeURIComponent</a>
     */
    public static JSValue encodeURIComponent(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue componentValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String componentString = JSTypeConversions.toString(componentValue).getValue();

        try {
            String encoded = URLEncoder.encode(componentString, StandardCharsets.UTF_8);

            // Restore only the unreserved characters: - _ . ! ~ * ' ( )
            encoded = encoded
                .replace("%2D", "-").replace("%5F", "_")
                .replace("%2E", ".").replace("%21", "!")
                .replace("%7E", "~").replace("%2A", "*")
                .replace("%27", "'").replace("%28", "(")
                .replace("%29", ")");

            return new JSString(encoded);
        } catch (Exception e) {
            return ctx.throwError("URIError", "URI malformed");
        }
    }

    /**
     * decodeURIComponent(encodedURIComponent)
     * Decode a URI component that was encoded by encodeURIComponent.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-decodeuricomponent-encodeduricomponent">ECMAScript decodeURIComponent</a>
     */
    public static JSValue decodeURIComponent(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue encodedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String encodedString = JSTypeConversions.toString(encodedValue).getValue();

        try {
            String decoded = URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
            return new JSString(decoded);
        } catch (Exception e) {
            return ctx.throwError("URIError", "URI malformed");
        }
    }

    /**
     * Initialize console object.
     */
    private static void initializeConsoleObject(JSContext ctx, JSObject global) {
        JSObject console = new JSObject();
        console.set("log", createNativeFunction(ctx, "log", GlobalObject::consoleLog, 0));
        console.set("info", createNativeFunction(ctx, "info", GlobalObject::consoleLog, 0));
        console.set("warn", createNativeFunction(ctx, "warn", GlobalObject::consoleWarn, 0));
        console.set("error", createNativeFunction(ctx, "error", GlobalObject::consoleError, 0));

        global.set("console", console);
    }

    /**
     * console.log(...args)
     * Print values to standard output.
     */
    public static JSValue consoleLog(JSContext ctx, JSValue thisArg, JSValue[] args) {
        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.out.print(" ");
            System.out.print(formatValue(args[i]));
        }
        System.out.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * console.warn(...args)
     * Print warning to standard error.
     */
    public static JSValue consoleWarn(JSContext ctx, JSValue thisArg, JSValue[] args) {
        System.err.print("[WARN] ");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.err.print(" ");
            System.err.print(formatValue(args[i]));
        }
        System.err.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * console.error(...args)
     * Print error to standard error.
     */
    public static JSValue consoleError(JSContext ctx, JSValue thisArg, JSValue[] args) {
        System.err.print("[ERROR] ");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.err.print(" ");
            System.err.print(formatValue(args[i]));
        }
        System.err.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * Format a value for console output.
     */
    private static String formatValue(JSValue value) {
        if (value instanceof JSUndefined) {
            return "undefined";
        }
        if (value instanceof JSNull) {
            return "null";
        }
        if (value instanceof JSBoolean b) {
            return String.valueOf(b.value());
        }
        if (value instanceof JSNumber n) {
            return JSTypeConversions.toString(value).getValue();
        }
        if (value instanceof JSString s) {
            return s.getValue();
        }
        if (value instanceof JSArray arr) {
            StringBuilder sb = new StringBuilder("[");
            for (long i = 0; i < arr.getLength(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatValue(arr.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof JSObject) {
            return "[object Object]";
        }
        return String.valueOf(value);
    }

    /**
     * Initialize Error constructors.
     */
    private static void initializeErrorConstructors(JSContext ctx, JSObject global) {
        // Base Error constructor
        global.set("Error", createErrorConstructor(ctx, "Error"));

        // Derived Error types
        global.set("TypeError", createErrorConstructor(ctx, "TypeError"));
        global.set("ReferenceError", createErrorConstructor(ctx, "ReferenceError"));
        global.set("RangeError", createErrorConstructor(ctx, "RangeError"));
        global.set("SyntaxError", createErrorConstructor(ctx, "SyntaxError"));
        global.set("URIError", createErrorConstructor(ctx, "URIError"));
        global.set("EvalError", createErrorConstructor(ctx, "EvalError"));
    }

    /**
     * Create an Error constructor.
     */
    private static JSObject createErrorConstructor(JSContext ctx, String errorName) {
        // Error.prototype
        JSObject errorPrototype = new JSObject();
        errorPrototype.set("name", new JSString(errorName));
        errorPrototype.set("message", new JSString(""));
        errorPrototype.set("toString", createNativeFunction(ctx, "toString", GlobalObject::errorToString, 0));

        // For now, Error constructor is a placeholder (like Array, String, etc.)
        JSObject errorConstructor = new JSObject();
        errorConstructor.set("prototype", errorPrototype);
        // Store error name for constructor use
        errorConstructor.set("[[ErrorName]]", new JSString(errorName));

        return errorConstructor;
    }

    /**
     * Error.prototype.toString()
     * Converts an Error object to a string.
     */
    public static JSValue errorToString(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject error)) {
            return new JSString("[object Object]");
        }

        JSValue nameValue = error.get("name");
        JSValue messageValue = error.get("message");

        String name = nameValue instanceof JSString ? ((JSString) nameValue).getValue() : "Error";
        String message = messageValue instanceof JSString ? ((JSString) messageValue).getValue() : "";

        if (message.isEmpty()) {
            return new JSString(name);
        }

        return new JSString(name + ": " + message);
    }
}
