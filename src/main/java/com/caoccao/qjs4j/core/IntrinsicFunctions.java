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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.builtins.*;
import com.caoccao.qjs4j.core.JSGlobalObject.GlobalFunction;

import java.util.function.Consumer;

/** Ordered native property definitions shared across realms. Symbol aliases are installed separately. */
enum IntrinsicFunctions {
    ARRAY_BUFFER_PROTOTYPE(properties -> {
        properties.method("resize", 1, ArrayBufferPrototype::resize);
        properties.method("slice", 2, ArrayBufferPrototype::slice);
        properties.method("transfer", 0, ArrayBufferPrototype::transfer);
        properties.method("transferToFixedLength", 0, ArrayBufferPrototype::transferToFixedLength);
        properties.method("transferToImmutable", 0, ArrayBufferPrototype::transferToImmutable);
        properties.getter("byteLength", ArrayBufferPrototype::getByteLength);
        properties.getter("detached", ArrayBufferPrototype::getDetached);
        properties.getter("immutable", ArrayBufferPrototype::getImmutable);
        properties.getter("maxByteLength", ArrayBufferPrototype::getMaxByteLength);
        properties.getter("resizable", ArrayBufferPrototype::getResizable);
    }),
    ARRAY_CONSTRUCTOR(properties -> {
        properties.method("from", 1, ArrayConstructor::from);
        properties.method("fromAsync", 1, ArrayConstructor::fromAsync);
        properties.method("isArray", 1, ArrayConstructor::isArray);
        properties.method("of", 0, ArrayConstructor::of);
    }),
    ARRAY_PROTOTYPE(properties -> {
        properties.method("at", 1, ArrayPrototype::at);
        properties.method("concat", 1, ArrayPrototype::concat);
        properties.method("copyWithin", 2, ArrayPrototype::copyWithin);
        properties.method("entries", 0, IteratorPrototype::arrayEntries);
        properties.method("every", 1, ArrayPrototype::every);
        properties.method("fill", 1, ArrayPrototype::fill);
        properties.method("filter", 1, ArrayPrototype::filter);
        properties.method("find", 1, ArrayPrototype::find);
        properties.method("findIndex", 1, ArrayPrototype::findIndex);
        properties.method("findLast", 1, ArrayPrototype::findLast);
        properties.method("findLastIndex", 1, ArrayPrototype::findLastIndex);
        properties.method("flat", 0, ArrayPrototype::flat);
        properties.method("flatMap", 1, ArrayPrototype::flatMap);
        properties.method("forEach", 1, ArrayPrototype::forEach);
        properties.method("includes", 1, ArrayPrototype::includes);
        properties.method("indexOf", 1, ArrayPrototype::indexOf);
        properties.method("join", 1, ArrayPrototype::join);
        properties.method("keys", 0, IteratorPrototype::arrayKeys);
        properties.method("lastIndexOf", 1, ArrayPrototype::lastIndexOf);
        properties.method("map", 1, ArrayPrototype::map);
        properties.method("pop", 0, ArrayPrototype::pop);
        properties.method("push", 1, ArrayPrototype::push);
        properties.method("reduce", 1, ArrayPrototype::reduce);
        properties.method("reduceRight", 1, ArrayPrototype::reduceRight);
        properties.method("reverse", 0, ArrayPrototype::reverse);
        properties.method("shift", 0, ArrayPrototype::shift);
        properties.method("slice", 2, ArrayPrototype::slice);
        properties.method("some", 1, ArrayPrototype::some);
        properties.method("sort", 1, ArrayPrototype::sort);
        properties.method("splice", 2, ArrayPrototype::splice);
        properties.method("toLocaleString", 0, ArrayPrototype::toLocaleString);
        properties.method("toReversed", 0, ArrayPrototype::toReversed);
        properties.method("toSorted", 1, ArrayPrototype::toSorted);
        properties.method("toSpliced", 2, ArrayPrototype::toSpliced);
        properties.method("toString", 0, ArrayPrototype::toString);
        properties.method("unshift", 1, ArrayPrototype::unshift);
    }),
    BIG_INT_PROTOTYPE(properties -> {
        properties.method("toLocaleString", 0, BigIntPrototype::toLocaleString);
        properties.method("toString", 0, BigIntPrototype::toString);
        properties.method("valueOf", 0, BigIntPrototype::valueOf);
    }),
    DATA_VIEW_ACCESSORS(properties -> {
        properties.getter("buffer", DataViewPrototype::getBuffer);
        properties.getter("byteLength", DataViewPrototype::getByteLength);
        properties.getter("byteOffset", DataViewPrototype::getByteOffset);
    }),
    DATA_VIEW_METHODS(properties -> {
        properties.method("getInt8", 1, DataViewPrototype::getInt8);
        properties.method("setInt8", 2, DataViewPrototype::setInt8);
        properties.method("getUint8", 1, DataViewPrototype::getUint8);
        properties.method("setUint8", 2, DataViewPrototype::setUint8);
        properties.method("getInt16", 1, DataViewPrototype::getInt16);
        properties.method("setInt16", 2, DataViewPrototype::setInt16);
        properties.method("getUint16", 1, DataViewPrototype::getUint16);
        properties.method("setUint16", 2, DataViewPrototype::setUint16);
        properties.method("getInt32", 1, DataViewPrototype::getInt32);
        properties.method("setInt32", 2, DataViewPrototype::setInt32);
        properties.method("getUint32", 1, DataViewPrototype::getUint32);
        properties.method("setUint32", 2, DataViewPrototype::setUint32);
        properties.method("getBigInt64", 1, DataViewPrototype::getBigInt64);
        properties.method("setBigInt64", 2, DataViewPrototype::setBigInt64);
        properties.method("getBigUint64", 1, DataViewPrototype::getBigUint64);
        properties.method("setBigUint64", 2, DataViewPrototype::setBigUint64);
        properties.method("getFloat16", 1, DataViewPrototype::getFloat16);
        properties.method("setFloat16", 2, DataViewPrototype::setFloat16);
        properties.method("getFloat32", 1, DataViewPrototype::getFloat32);
        properties.method("setFloat32", 2, DataViewPrototype::setFloat32);
        properties.method("getFloat64", 1, DataViewPrototype::getFloat64);
        properties.method("setFloat64", 2, DataViewPrototype::setFloat64);
    }),
    DATE_CONSTRUCTOR(properties -> {
        properties.method("UTC", 7, DateConstructor::UTC);
        properties.method("now", 0, DateConstructor::now);
        properties.method("parse", 1, DateConstructor::parse);
    }),
    DATE_PROTOTYPE(properties -> {
        properties.method("getDate", 0, DatePrototype::getDate);
        properties.method("getDay", 0, DatePrototype::getDay);
        properties.method("getFullYear", 0, DatePrototype::getFullYear);
        properties.method("getHours", 0, DatePrototype::getHours);
        properties.method("getMilliseconds", 0, DatePrototype::getMilliseconds);
        properties.method("getMinutes", 0, DatePrototype::getMinutes);
        properties.method("getMonth", 0, DatePrototype::getMonth);
        properties.method("getSeconds", 0, DatePrototype::getSeconds);
        properties.method("getTime", 0, DatePrototype::getTime);
        properties.method("getTimezoneOffset", 0, DatePrototype::getTimezoneOffset);
        properties.method("getUTCDate", 0, DatePrototype::getUTCDate);
        properties.method("getUTCDay", 0, DatePrototype::getUTCDay);
        properties.method("getUTCFullYear", 0, DatePrototype::getUTCFullYear);
        properties.method("getUTCHours", 0, DatePrototype::getUTCHours);
        properties.method("getUTCMilliseconds", 0, DatePrototype::getUTCMilliseconds);
        properties.method("getUTCMinutes", 0, DatePrototype::getUTCMinutes);
        properties.method("getUTCMonth", 0, DatePrototype::getUTCMonth);
        properties.method("getUTCSeconds", 0, DatePrototype::getUTCSeconds);
        properties.method("getYear", 0, DatePrototype::getYear);
        properties.method("setDate", 1, DatePrototype::setDate);
        properties.method("setFullYear", 3, DatePrototype::setFullYear);
        properties.method("setHours", 4, DatePrototype::setHours);
        properties.method("setMilliseconds", 1, DatePrototype::setMilliseconds);
        properties.method("setMinutes", 3, DatePrototype::setMinutes);
        properties.method("setMonth", 2, DatePrototype::setMonth);
        properties.method("setSeconds", 2, DatePrototype::setSeconds);
        properties.method("setTime", 1, DatePrototype::setTime);
        properties.method("setUTCDate", 1, DatePrototype::setUTCDate);
        properties.method("setUTCFullYear", 3, DatePrototype::setUTCFullYear);
        properties.method("setUTCHours", 4, DatePrototype::setUTCHours);
        properties.method("setUTCMilliseconds", 1, DatePrototype::setUTCMilliseconds);
        properties.method("setUTCMinutes", 3, DatePrototype::setUTCMinutes);
        properties.method("setUTCMonth", 2, DatePrototype::setUTCMonth);
        properties.method("setUTCSeconds", 2, DatePrototype::setUTCSeconds);
        properties.method("setYear", 1, DatePrototype::setYear);
        properties.method("toDateString", 0, DatePrototype::toDateString);
    }),
    DATE_STRING_METHODS(properties -> {
        properties.method("toISOString", 0, DatePrototype::toISOString);
        properties.method("toJSON", 1, DatePrototype::toJSON);
        properties.method("toLocaleDateString", 0, DatePrototype::toLocaleDateString);
        properties.method("toLocaleString", 0, DatePrototype::toLocaleString);
        properties.method("toLocaleTimeString", 0, DatePrototype::toLocaleTimeString);
        properties.method("toString", 0, DatePrototype::toStringMethod);
        properties.method("toTimeString", 0, DatePrototype::toTimeString);
    }),
    DATE_TIME_FORMAT_PROTOTYPE(properties -> {
        properties.getter("format", JSIntlObject::dateTimeFormatFormatGetter);
        properties.method("resolvedOptions", 0, JSIntlObject::dateTimeFormatResolvedOptions);
        properties.method("formatToParts", 1, JSIntlObject::dateTimeFormatFormatToParts);
        properties.method("formatRange", 2, JSIntlObject::dateTimeFormatFormatRange);
        properties.method("formatRangeToParts", 2, JSIntlObject::dateTimeFormatFormatRangeToParts);
    }),
    DURATION_FORMAT_PROTOTYPE(properties -> {
        properties.method("format", 1, JSIntlObject::durationFormatFormat);
        properties.method("formatToParts", 1, JSIntlObject::durationFormatFormatToParts);
        properties.method("resolvedOptions", 0, JSIntlObject::durationFormatResolvedOptions);
    }),
    FUNCTION_PROTOTYPE(properties -> {
        properties.method("apply", 2, FunctionPrototype::apply);
        properties.method("bind", 1, FunctionPrototype::bind);
        properties.method("call", 1, FunctionPrototype::call);
        properties.method("toString", 0, FunctionPrototype::toString_);
    }),
    GENERATOR_PROTOTYPE(properties -> {
        properties.method("next", 1, GeneratorPrototype::next);
        properties.method("return", 1, GeneratorPrototype::returnMethod);
        properties.method("throw", 1, GeneratorPrototype::throwMethod);
    }),
    GLOBAL_OBJECT(properties -> {
        properties.method("isFinite", 1, GlobalFunction::isFinite);
        properties.method("isNaN", 1, GlobalFunction::isNaN);
        properties.method("parseFloat", 1, GlobalFunction::parseFloat);
        properties.method("parseInt", 2, GlobalFunction::parseInt);
        properties.method("decodeURI", 1, GlobalFunction::decodeURI);
        properties.method("decodeURIComponent", 1, GlobalFunction::decodeURIComponent);
        properties.method("encodeURI", 1, GlobalFunction::encodeURI);
        properties.method("encodeURIComponent", 1, GlobalFunction::encodeURIComponent);
        properties.method("escape", 1, GlobalFunction::escape);
        properties.method("unescape", 1, GlobalFunction::unescape);
    }),
    ITERATOR_CONSTRUCTOR(properties -> {
        properties.method("concat", 0, IteratorPrototype::concat);
        properties.method("from", 1, IteratorPrototype::from);
        properties.method("zip", 1, IteratorPrototype::zip);
        properties.method("zipKeyed", 1, IteratorPrototype::zipKeyed);
    }),
    ITERATOR_PROTOTYPE(properties -> {
        properties.method("drop", 1, IteratorPrototype::drop);
        properties.method("every", 1, IteratorPrototype::every);
        properties.method("filter", 1, IteratorPrototype::filter);
        properties.method("find", 1, IteratorPrototype::find);
        properties.method("flatMap", 1, IteratorPrototype::flatMap);
        properties.method("forEach", 1, IteratorPrototype::forEach);
        properties.method("map", 1, IteratorPrototype::map);
        properties.method("next", 0, IteratorPrototype::next);
        properties.method("reduce", 1, IteratorPrototype::reduce);
        properties.method("some", 1, IteratorPrototype::some);
        properties.method("take", 1, IteratorPrototype::take);
        properties.method("toArray", 0, IteratorPrototype::toArray);
    }),
    LIST_FORMAT_PROTOTYPE(properties -> {
        properties.method("format", 1, JSIntlObject::listFormatFormat);
        properties.method("formatToParts", 1, JSIntlObject::listFormatFormatToParts);
        properties.method("resolvedOptions", 0, JSIntlObject::listFormatResolvedOptions);
    }),
    LOCALE_PROTOTYPE(properties -> {
        properties.method("toString", 0, JSIntlObject::localeToString);
        properties.getter("baseName", JSIntlObject::localeGetBaseName);
        properties.getter("calendar", JSIntlObject::localeGetCalendar);
        properties.getter("caseFirst", JSIntlObject::localeGetCaseFirst);
        properties.getter("collation", JSIntlObject::localeGetCollation);
        properties.getter("hourCycle", JSIntlObject::localeGetHourCycle);
        properties.getter("language", JSIntlObject::localeGetLanguage);
        properties.getter("numberingSystem", JSIntlObject::localeGetNumberingSystem);
        properties.getter("numeric", JSIntlObject::localeGetNumeric);
        properties.getter("script", JSIntlObject::localeGetScript);
        properties.getter("region", JSIntlObject::localeGetRegion);
        properties.getter("variants", JSIntlObject::localeGetVariants);
        properties.getter("firstDayOfWeek", JSIntlObject::localeGetFirstDayOfWeek);
        properties.method("maximize", 0, JSIntlObject::localeMaximize);
        properties.method("minimize", 0, JSIntlObject::localeMinimize);
        properties.method("getCalendars", 0, JSIntlObject::localeGetCalendars);
        properties.method("getCollations", 0, JSIntlObject::localeGetCollations);
        properties.method("getHourCycles", 0, JSIntlObject::localeGetHourCycles);
        properties.method("getNumberingSystems", 0, JSIntlObject::localeGetNumberingSystems);
        properties.method("getTextInfo", 0, JSIntlObject::localeGetTextInfo);
        properties.method("getTimeZones", 0, JSIntlObject::localeGetTimeZones);
        properties.method("getWeekInfo", 0, JSIntlObject::localeGetWeekInfo);
    }),
    MAP_PROTOTYPE(properties -> {
        properties.method("clear", 0, MapPrototype::clear);
        properties.method("delete", 1, MapPrototype::delete);
        properties.method("forEach", 1, MapPrototype::forEach);
        properties.method("get", 1, MapPrototype::get);
        properties.method("getOrInsert", 2, MapPrototype::getOrInsert);
        properties.method("getOrInsertComputed", 2, MapPrototype::getOrInsertComputed);
        properties.method("has", 1, MapPrototype::has);
        properties.method("set", 2, MapPrototype::set);
    }),
    MATH(properties -> {
        properties.method("abs", 1, MathObject::abs);
        properties.method("acos", 1, MathObject::acos);
        properties.method("acosh", 1, MathObject::acosh);
        properties.method("asin", 1, MathObject::asin);
        properties.method("asinh", 1, MathObject::asinh);
        properties.method("atan", 1, MathObject::atan);
        properties.method("atanh", 1, MathObject::atanh);
        properties.method("atan2", 2, MathObject::atan2);
        properties.method("cbrt", 1, MathObject::cbrt);
        properties.method("ceil", 1, MathObject::ceil);
        properties.method("clz32", 1, MathObject::clz32);
        properties.method("cos", 1, MathObject::cos);
        properties.method("cosh", 1, MathObject::cosh);
        properties.method("exp", 1, MathObject::exp);
        properties.method("expm1", 1, MathObject::expm1);
        properties.method("f16round", 1, MathObject::f16round);
        properties.method("floor", 1, MathObject::floor);
        properties.method("fround", 1, MathObject::fround);
        properties.method("hypot", 2, MathObject::hypot);
        properties.method("imul", 2, MathObject::imul);
        properties.method("log", 1, MathObject::log);
        properties.method("log1p", 1, MathObject::log1p);
        properties.method("log10", 1, MathObject::log10);
        properties.method("log2", 1, MathObject::log2);
        properties.method("max", 2, MathObject::max);
        properties.method("min", 2, MathObject::min);
        properties.method("pow", 2, MathObject::pow);
        properties.method("random", 0, MathObject::random);
        properties.method("round", 1, MathObject::round);
        properties.method("sign", 1, MathObject::sign);
        properties.method("sin", 1, MathObject::sin);
        properties.method("sinh", 1, MathObject::sinh);
        properties.method("sqrt", 1, MathObject::sqrt);
        properties.method("sumPrecise", 1, MathObject::sumPrecise);
        properties.method("tan", 1, MathObject::tan);
        properties.method("tanh", 1, MathObject::tanh);
        properties.method("trunc", 1, MathObject::trunc);
    }),
    NUMBER_CONSTRUCTOR(properties -> {
        properties.method("isFinite", 1, NumberPrototype::isFinite);
        properties.method("isInteger", 1, NumberPrototype::isInteger);
        properties.method("isNaN", 1, NumberPrototype::isNaN);
        properties.method("isSafeInteger", 1, NumberPrototype::isSafeInteger);
    }),
    NUMBER_FORMAT_PROTOTYPE(properties -> {
        properties.getter("format", JSIntlObject::numberFormatFormatGetter);
        properties.method("formatRange", 2, JSIntlObject::numberFormatFormatRange);
        properties.method("formatRangeToParts", 2, JSIntlObject::numberFormatFormatRangeToParts);
        properties.method("formatToParts", 1, JSIntlObject::numberFormatFormatToParts);
        properties.method("resolvedOptions", 0, JSIntlObject::numberFormatResolvedOptions);
    }),
    NUMBER_PROTOTYPE(properties -> {
        properties.method("toExponential", 1, NumberPrototype::toExponential);
        properties.method("toFixed", 1, NumberPrototype::toFixed);
        properties.method("toLocaleString", 0, NumberPrototype::toLocaleString);
        properties.method("toPrecision", 1, NumberPrototype::toPrecision);
        properties.method("toString", 1, NumberPrototype::toString);
        properties.method("valueOf", 0, NumberPrototype::valueOf);
    }),
    OBJECT_CONSTRUCTOR(properties -> {
        properties.method("assign", 2, ObjectConstructor::assign);
        properties.method("create", 2, ObjectConstructor::create);
        properties.method("defineProperties", 2, ObjectConstructor::defineProperties);
        properties.method("defineProperty", 3, ObjectPrototype::defineProperty);
        properties.method("entries", 1, ObjectConstructor::entries);
        properties.method("freeze", 1, ObjectConstructor::freeze);
        properties.method("fromEntries", 1, ObjectConstructor::fromEntries);
        properties.method("getOwnPropertyDescriptor", 2, ObjectConstructor::getOwnPropertyDescriptor);
        properties.method("getOwnPropertyDescriptors", 1, ObjectConstructor::getOwnPropertyDescriptors);
        properties.method("getOwnPropertyNames", 1, ObjectConstructor::getOwnPropertyNames);
        properties.method("getOwnPropertySymbols", 1, ObjectConstructor::getOwnPropertySymbols);
        properties.method("getPrototypeOf", 1, ObjectConstructor::getPrototypeOf);
        properties.method("groupBy", 2, ObjectConstructor::groupBy);
        properties.method("hasOwn", 2, ObjectConstructor::hasOwn);
        properties.method("is", 2, ObjectConstructor::is);
        properties.method("isExtensible", 1, ObjectConstructor::isExtensible);
        properties.method("isFrozen", 1, ObjectConstructor::isFrozen);
        properties.method("isSealed", 1, ObjectConstructor::isSealed);
        properties.method("keys", 1, ObjectConstructor::keys);
        properties.method("preventExtensions", 1, ObjectConstructor::preventExtensions);
        properties.method("seal", 1, ObjectConstructor::seal);
        properties.method("setPrototypeOf", 2, ObjectConstructor::setPrototypeOf);
        properties.method("values", 1, ObjectConstructor::values);
    }),
    OBJECT_PROTOTYPE(properties -> {
        properties.method("__defineGetter__", 2, ObjectPrototype::__defineGetter__);
        properties.method("__defineSetter__", 2, ObjectPrototype::__defineSetter__);
        properties.method("__lookupGetter__", 1, ObjectPrototype::__lookupGetter__);
        properties.method("__lookupSetter__", 1, ObjectPrototype::__lookupSetter__);
        properties.method("hasOwnProperty", 1, ObjectPrototype::hasOwnProperty);
        properties.method("isPrototypeOf", 1, ObjectPrototype::isPrototypeOf);
        properties.method("propertyIsEnumerable", 1, ObjectPrototype::propertyIsEnumerable);
        properties.method("toLocaleString", 0, ObjectPrototype::toLocaleString);
        properties.method("toString", 0, ObjectPrototype::toString);
        properties.method("valueOf", 0, ObjectPrototype::valueOf);
    }),
    PLURAL_RULES_PROTOTYPE(properties -> {
        properties.method("select", 1, JSIntlObject::pluralRulesSelect);
        properties.method("selectRange", 2, JSIntlObject::pluralRulesSelectRange);
        properties.method("resolvedOptions", 0, JSIntlObject::pluralRulesResolvedOptions);
    }),
    PROMISE_CONSTRUCTOR(properties -> {
        properties.method("all", 1, PromiseConstructor::all);
        properties.method("allKeyed", 1, PromiseConstructor::allKeyed);
        properties.method("allSettled", 1, PromiseConstructor::allSettled);
        properties.method("allSettledKeyed", 1, PromiseConstructor::allSettledKeyed);
        properties.method("any", 1, PromiseConstructor::any);
        properties.method("race", 1, PromiseConstructor::race);
        properties.method("reject", 1, PromiseConstructor::reject);
        properties.method("resolve", 1, PromiseConstructor::resolve);
        properties.method("try", 1, PromiseConstructor::tryMethod);
        properties.method("withResolvers", 0, PromiseConstructor::withResolvers);
    }),
    PROMISE_PROTOTYPE(properties -> {
        properties.method("catch", 1, PromisePrototype::catchMethod);
        properties.method("finally", 1, PromisePrototype::finallyMethod);
        properties.method("then", 2, PromisePrototype::then);
    }),
    REFLECT(properties -> {
        properties.method("apply", 3, JSReflectObject::apply);
        properties.method("construct", 2, JSReflectObject::construct);
        properties.method("defineProperty", 3, JSReflectObject::defineProperty);
        properties.method("deleteProperty", 2, JSReflectObject::deleteProperty);
        properties.method("get", 2, JSReflectObject::get);
        properties.method("getOwnPropertyDescriptor", 2, JSReflectObject::getOwnPropertyDescriptor);
        properties.method("getPrototypeOf", 1, JSReflectObject::getPrototypeOf);
        properties.method("has", 2, JSReflectObject::has);
        properties.method("isExtensible", 1, JSReflectObject::isExtensible);
        properties.method("ownKeys", 1, JSReflectObject::ownKeys);
        properties.method("preventExtensions", 1, JSReflectObject::preventExtensions);
        properties.method("set", 3, JSReflectObject::set);
        properties.method("setPrototypeOf", 2, JSReflectObject::setPrototypeOf);
    }),
    REGEXP_PROTOTYPE(properties -> {
        properties.getter("dotAll", RegExpPrototype::getDotAllAccessor);
        properties.getter("flags", RegExpPrototype::getFlagsAccessor);
        properties.getter("global", RegExpPrototype::getGlobalAccessor);
        properties.getter("hasIndices", RegExpPrototype::getHasIndicesAccessor);
        properties.getter("ignoreCase", RegExpPrototype::getIgnoreCaseAccessor);
        properties.getter("multiline", RegExpPrototype::getMultilineAccessor);
        properties.getter("source", RegExpPrototype::getSourceAccessor);
        properties.getter("sticky", RegExpPrototype::getStickyAccessor);
        properties.getter("unicode", RegExpPrototype::getUnicodeAccessor);
        properties.getter("unicodeSets", RegExpPrototype::getUnicodeSetsAccessor);
    }),
    RELATIVE_TIME_FORMAT_PROTOTYPE(properties -> {
        properties.method("format", 2, JSIntlObject::relativeTimeFormatFormat);
        properties.method("formatToParts", 2, JSIntlObject::relativeTimeFormatFormatToParts);
        properties.method("resolvedOptions", 0, JSIntlObject::relativeTimeFormatResolvedOptions);
    }),
    SET_PROTOTYPE(properties -> {
        properties.method("add", 1, SetPrototype::add);
        properties.method("clear", 0, SetPrototype::clear);
        properties.method("delete", 1, SetPrototype::delete);
        properties.method("difference", 1, SetPrototype::difference);
        properties.method("entries", 0, SetPrototype::entries);
        properties.method("forEach", 1, SetPrototype::forEach);
        properties.method("has", 1, SetPrototype::has);
        properties.method("intersection", 1, SetPrototype::intersection);
        properties.method("isDisjointFrom", 1, SetPrototype::isDisjointFrom);
        properties.method("isSubsetOf", 1, SetPrototype::isSubsetOf);
        properties.method("isSupersetOf", 1, SetPrototype::isSupersetOf);
        properties.method("symmetricDifference", 1, SetPrototype::symmetricDifference);
        properties.method("union", 1, SetPrototype::union);
    }),
    SHARED_ARRAY_BUFFER_PROTOTYPE(properties -> {
        properties.method("grow", 1, SharedArrayBufferPrototype::grow);
        properties.method("slice", 2, SharedArrayBufferPrototype::slice);
        properties.getter("byteLength", SharedArrayBufferPrototype::getByteLength);
        properties.getter("growable", SharedArrayBufferPrototype::getGrowable);
        properties.getter("maxByteLength", SharedArrayBufferPrototype::getMaxByteLength);
    }),
    STRING_CONSTRUCTOR(properties -> {
        properties.method("fromCharCode", 1, StringConstructor::fromCharCode);
        properties.method("fromCodePoint", 1, StringConstructor::fromCodePoint);
        properties.method("raw", 1, StringConstructor::raw);
    }),
    STRING_PROTOTYPE(properties -> {
        properties.method("at", 1, StringPrototype::at);
        properties.method("charAt", 1, StringPrototype::charAt);
        properties.method("charCodeAt", 1, StringPrototype::charCodeAt);
        properties.method("codePointAt", 1, StringPrototype::codePointAt);
        properties.method("concat", 1, StringPrototype::concat);
        properties.method("endsWith", 1, StringPrototype::endsWith);
        properties.method("includes", 1, StringPrototype::includes);
        properties.method("indexOf", 1, StringPrototype::indexOf);
        properties.method("lastIndexOf", 1, StringPrototype::lastIndexOf);
        properties.method("localeCompare", 1, StringPrototype::localeCompare);
        properties.method("match", 1, StringPrototype::match);
        properties.method("matchAll", 1, StringPrototype::matchAll);
        properties.method("padEnd", 1, StringPrototype::padEnd);
        properties.method("padStart", 1, StringPrototype::padStart);
        properties.method("repeat", 1, StringPrototype::repeat);
        properties.method("replace", 2, StringPrototype::replace);
        properties.method("replaceAll", 2, StringPrototype::replaceAll);
        properties.method("search", 1, StringPrototype::search);
        properties.method("slice", 2, StringPrototype::slice);
        properties.method("split", 2, StringPrototype::split);
        properties.method("startsWith", 1, StringPrototype::startsWith);
        properties.method("substr", 2, StringPrototype::substr);
        properties.method("substring", 2, StringPrototype::substring);
        properties.method("toLowerCase", 0, StringPrototype::toLowerCase);
        properties.method("toString", 0, StringPrototype::toString_);
        properties.method("toUpperCase", 0, StringPrototype::toUpperCase);
        properties.method("trim", 0, StringPrototype::trim);
    }),
    STRING_PROTOTYPE_AFTER_TRIM(properties -> {
        properties.method("valueOf", 0, StringPrototype::valueOf);

    // Legacy HTML methods from Annex B.
        properties.method("anchor", 1, StringPrototype::anchor);
        properties.method("big", 0, StringPrototype::big);
        properties.method("blink", 0, StringPrototype::blink);
        properties.method("bold", 0, StringPrototype::bold);
        properties.method("fixed", 0, StringPrototype::fixed);
        properties.method("fontcolor", 1, StringPrototype::fontcolor);
        properties.method("fontsize", 1, StringPrototype::fontsize);
        properties.method("italics", 0, StringPrototype::italics);
        properties.method("link", 1, StringPrototype::link);
        properties.method("small", 0, StringPrototype::small);
        properties.method("strike", 0, StringPrototype::strike);
        properties.method("sub", 0, StringPrototype::sub);
        properties.method("sup", 0, StringPrototype::sup);
        properties.method("isWellFormed", 0, StringPrototype::isWellFormed);
        properties.method("normalize", 0, StringPrototype::normalize);
        properties.method("toLocaleLowerCase", 0, StringPrototype::toLocaleLowerCase);
        properties.method("toLocaleUpperCase", 0, StringPrototype::toLocaleUpperCase);
        properties.method("toWellFormed", 0, StringPrototype::toWellFormed);
    }),
    TYPED_ARRAY_ACCESSORS(properties -> {
        properties.getter("buffer", TypedArrayPrototype::getBuffer);
        properties.getter("byteLength", TypedArrayPrototype::getByteLength);
        properties.getter("byteOffset", TypedArrayPrototype::getByteOffset);
        properties.getter("length", TypedArrayPrototype::getLength);
    }),
    TYPED_ARRAY_PROTOTYPE(properties -> {
        properties.method("at", 1, TypedArrayPrototype::at);
        properties.method("copyWithin", 2, TypedArrayPrototype::copyWithin);
        properties.method("entries", 0, TypedArrayPrototype::entries);
        properties.method("every", 1, TypedArrayPrototype::every);
        properties.method("fill", 1, TypedArrayPrototype::fill);
        properties.method("filter", 1, TypedArrayPrototype::filter);
        properties.method("find", 1, TypedArrayPrototype::find);
        properties.method("findIndex", 1, TypedArrayPrototype::findIndex);
        properties.method("findLast", 1, TypedArrayPrototype::findLast);
        properties.method("findLastIndex", 1, TypedArrayPrototype::findLastIndex);
        properties.method("forEach", 1, TypedArrayPrototype::forEach);
        properties.method("includes", 1, TypedArrayPrototype::includes);
        properties.method("indexOf", 1, TypedArrayPrototype::indexOf);
        properties.method("join", 1, TypedArrayPrototype::join);
        properties.method("keys", 0, TypedArrayPrototype::keys);
        properties.method("lastIndexOf", 1, TypedArrayPrototype::lastIndexOf);
        properties.method("map", 1, TypedArrayPrototype::map);
        properties.method("reduce", 1, TypedArrayPrototype::reduce);
        properties.method("reduceRight", 1, TypedArrayPrototype::reduceRight);
        properties.method("reverse", 0, TypedArrayPrototype::reverse);
        properties.method("set", 1, TypedArrayPrototype::set);
        properties.method("slice", 2, TypedArrayPrototype::slice);
        properties.method("some", 1, TypedArrayPrototype::some);
        properties.method("sort", 1, TypedArrayPrototype::sort);
        properties.method("subarray", 2, TypedArrayPrototype::subarray);
        properties.method("toLocaleString", 0, TypedArrayPrototype::toLocaleString);
        properties.method("toReversed", 0, TypedArrayPrototype::toReversed);
        properties.method("toSorted", 1, TypedArrayPrototype::toSorted);
    }),
    UINT8_ARRAY_PROTOTYPE(properties -> {
        properties.method("toBase64", 0, Uint8ArrayBase64Hex::toBase64);
        properties.method("toHex", 0, Uint8ArrayBase64Hex::toHex);
        properties.method("setFromBase64", 1, Uint8ArrayBase64Hex::setFromBase64);
        properties.method("setFromHex", 1, Uint8ArrayBase64Hex::setFromHex);
    }),
    WEAK_MAP_PROTOTYPE(properties -> {
        properties.method("set", 2, WeakMapPrototype::set);
        properties.method("get", 1, WeakMapPrototype::get);
        properties.method("getOrInsert", 2, WeakMapPrototype::getOrInsert);
        properties.method("getOrInsertComputed", 2, WeakMapPrototype::getOrInsertComputed);
        properties.method("has", 1, WeakMapPrototype::has);
        properties.method("delete", 1, WeakMapPrototype::delete);
    }),
    WEAK_SET_PROTOTYPE(properties -> {
        properties.method("add", 1, WeakSetPrototype::add);
        properties.method("has", 1, WeakSetPrototype::has);
        properties.method("delete", 1, WeakSetPrototype::delete);
    });

    private final BuiltinFunctionList functions;

    IntrinsicFunctions(Consumer<BuiltinFunctionList.Builder> definitions) {
        this.functions = new BuiltinFunctionList(definitions);
    }

    void install(JSObject target) {
        functions.install(target);
    }
}
