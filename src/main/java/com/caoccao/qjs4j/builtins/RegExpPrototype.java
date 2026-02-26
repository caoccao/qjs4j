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
import com.caoccao.qjs4j.regexp.RegExpEngine;

/**
 * Implementation of JavaScript RegExp.prototype methods.
 * Based on ES2020 RegExp specification.
 */
public final class RegExpPrototype {
    private static final PropertyKey PROPERTY_DOT_ALL = PropertyKey.fromString("dotAll");
    private static final PropertyKey PROPERTY_EXEC = PropertyKey.EXEC;
    private static final PropertyKey PROPERTY_FLAGS = PropertyKey.fromString("flags");
    private static final PropertyKey PROPERTY_GLOBAL = PropertyKey.fromString("global");
    private static final PropertyKey PROPERTY_HAS_INDICES = PropertyKey.fromString("hasIndices");
    private static final PropertyKey PROPERTY_IGNORE_CASE = PropertyKey.fromString("ignoreCase");
    private static final PropertyKey PROPERTY_MULTILINE = PropertyKey.fromString("multiline");
    private static final PropertyKey PROPERTY_STICKY = PropertyKey.fromString("sticky");
    private static final PropertyKey PROPERTY_UNICODE = PropertyKey.fromString("unicode");
    private static final PropertyKey PROPERTY_UNICODE_SETS = PropertyKey.fromString("unicodeSets");

    private static int advanceStringIndexUnicode(String s, int index) {
        if (index + 1 >= s.length()) {
            return index + 1;
        }
        char c = s.charAt(index);
        if (Character.isHighSurrogate(c) && Character.isLowSurrogate(s.charAt(index + 1))) {
            return index + 2;
        }
        return index + 1;
    }

    private static String buildFlagsStringFromObject(JSContext context, JSObject regexpObject) {
        StringBuilder flagsBuilder = new StringBuilder(8);
        JSValue hasIndicesValue = regexpObject.get(context, PROPERTY_HAS_INDICES);
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeConversions.toBoolean(hasIndicesValue).value()) {
            flagsBuilder.append('d');
        }
        JSValue globalValue = regexpObject.get(context, PROPERTY_GLOBAL);
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeConversions.toBoolean(globalValue).value()) {
            flagsBuilder.append('g');
        }
        JSValue ignoreCaseValue = regexpObject.get(context, PROPERTY_IGNORE_CASE);
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeConversions.toBoolean(ignoreCaseValue).value()) {
            flagsBuilder.append('i');
        }
        JSValue multilineValue = regexpObject.get(context, PROPERTY_MULTILINE);
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeConversions.toBoolean(multilineValue).value()) {
            flagsBuilder.append('m');
        }
        JSValue dotAllValue = regexpObject.get(context, PROPERTY_DOT_ALL);
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeConversions.toBoolean(dotAllValue).value()) {
            flagsBuilder.append('s');
        }
        JSValue unicodeValue = regexpObject.get(context, PROPERTY_UNICODE);
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeConversions.toBoolean(unicodeValue).value()) {
            flagsBuilder.append('u');
        }
        JSValue unicodeSetsValue = regexpObject.get(context, PROPERTY_UNICODE_SETS);
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeConversions.toBoolean(unicodeSetsValue).value()) {
            flagsBuilder.append('v');
        }
        JSValue stickyValue = regexpObject.get(context, PROPERTY_STICKY);
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeConversions.toBoolean(stickyValue).value()) {
            flagsBuilder.append('y');
        }
        return flagsBuilder.toString();
    }

    /**
     * RegExp.prototype.compile(pattern, flags)
     * AnnexB B.2.5.1
     * Reinitializes the RegExp object in-place.
     */
    public static JSValue compile(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.compile called on non-RegExp");
        }
        JSValue realmRegExpConstructor = context.getGlobalObject().get(JSRegExp.NAME);
        JSValue receiverConstructor = regexp.get(PropertyKey.CONSTRUCTOR);
        if (realmRegExpConstructor != receiverConstructor) {
            return context.throwTypeError("RegExp.prototype.compile called on incompatible receiver");
        }

        String pattern = "";
        String flags = "";
        if (args.length > 0) {
            JSValue patternArg = args[0];
            if (patternArg instanceof JSRegExp existingRegExp) {
                if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
                    return context.throwTypeError("Cannot supply flags when constructing one RegExp from another");
                }
                pattern = existingRegExp.getPattern();
                flags = existingRegExp.getFlags();
            } else if (!(patternArg instanceof JSUndefined)) {
                pattern = JSTypeConversions.toString(context, patternArg).value();
            }
            if (args.length > 1 && !(args[1] instanceof JSUndefined) && !(args[0] instanceof JSRegExp)) {
                flags = JSTypeConversions.toString(context, args[1]).value();
            }
        }

        try {
            regexp.reinitialize(pattern, flags);
        } catch (Exception e) {
            return context.throwSyntaxError("Invalid regular expression: " + e.getMessage());
        }

        // Spec step 12: Perform ? Set(obj, "lastIndex", 0, true).
        // The 'true' means throw TypeError if the property is non-writable.
        PropertyDescriptor lastIndexDesc = regexp.getOwnPropertyDescriptor(PropertyKey.LAST_INDEX);
        if (lastIndexDesc != null && !lastIndexDesc.isWritable()) {
            return context.throwTypeError("Cannot assign to read only property 'lastIndex'");
        }
        regexp.setLastIndex(0);

        return regexp;
    }

    private static JSObject constructMatcherFromSpecies(JSContext context, JSObject regexpObject, String flags) {
        JSValue constructorValue = getRegExpSpeciesConstructor(context, regexpObject);
        if (context.hasPendingException()) {
            return null;
        }
        JSArray argsArray = context.createJSArray();
        argsArray.push(regexpObject);
        argsArray.push(new JSString(flags));
        JSValue constructed = JSReflectObject.construct(context, JSUndefined.INSTANCE, new JSValue[]{constructorValue, argsArray});
        if (context.hasPendingException()) {
            return null;
        }
        if (!(constructed instanceof JSObject constructedObject)) {
            context.throwTypeError("RegExp species constructor did not return an object");
            return null;
        }
        return constructedObject;
    }

    private static JSValue createIndexPairValue(JSContext context, int[] pair) {
        if (pair == null || pair.length < 2 || pair[0] < 0 || pair[1] < 0) {
            return JSUndefined.INSTANCE;
        }
        JSArray range = context.createJSArray();
        range.push(JSNumber.of(pair[0]));
        range.push(JSNumber.of(pair[1]));
        return range;
    }

    private static JSValue createIndicesValue(JSContext context, int[][] indices, String[] groupNames) {
        if (indices == null) {
            return JSUndefined.INSTANCE;
        }
        JSArray indicesArray = context.createJSArray();
        JSObject groupIndices = new JSObject();
        groupIndices.setPrototype(null);
        for (int i = 0; i < indices.length; i++) {
            JSValue pairValue = createIndexPairValue(context, indices[i]);
            indicesArray.push(pairValue);
            if (i > 0 && groupNames != null && i < groupNames.length) {
                String groupName = groupNames[i];
                if (groupName != null) {
                    if (!(pairValue instanceof JSUndefined) || !groupIndices.hasOwnProperty(groupName)) {
                        groupIndices.defineProperty(null, PropertyKey.fromString(groupName), pairValue, PropertyDescriptor.DataState.All);
                    }
                }
            }
        }
        JSValue groupsValue = groupNames != null ? groupIndices : JSUndefined.INSTANCE;
        indicesArray.defineProperty(null, PropertyKey.GROUPS, groupsValue, PropertyDescriptor.DataState.All);
        return indicesArray;
    }

    private static JSObject createIteratorResultObject(JSContext context, JSValue value, boolean done) {
        JSObject resultObject = context.createJSObject();
        resultObject.defineProperty(null, PropertyKey.VALUE, value, PropertyDescriptor.DataState.All);
        resultObject.defineProperty(null, PropertyKey.DONE, JSBoolean.valueOf(done), PropertyDescriptor.DataState.All);
        return resultObject;
    }

    private static JSValue createNamedGroupsValue(String[] captures, String[] groupNames) {
        if (groupNames == null || captures == null) {
            return JSUndefined.INSTANCE;
        }

        JSObject groups = new JSObject();
        groups.setPrototype(null);

        int maxLength = Math.min(captures.length, groupNames.length);
        for (int i = 1; i < maxLength; i++) {
            String groupName = groupNames[i];
            if (groupName != null) {
                if (captures[i] != null) {
                    groups.defineProperty(null, PropertyKey.fromString(groupName), new JSString(captures[i]), PropertyDescriptor.DataState.All);
                } else if (!groups.hasOwnProperty(groupName)) {
                    groups.defineProperty(null, PropertyKey.fromString(groupName), JSUndefined.INSTANCE, PropertyDescriptor.DataState.All);
                }
            }
        }
        return groups;
    }

    private static String escapeRegExpSourcePattern(String source) {
        if (source.isEmpty()) {
            return "(?:)";
        }
        StringBuilder escaped = new StringBuilder(source.length() + 8);
        for (int index = 0; index < source.length(); index++) {
            char currentChar = source.charAt(index);
            switch (currentChar) {
                case '/' -> escaped.append("\\/");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\u2028' -> escaped.append("\\u2028");
                case '\u2029' -> escaped.append("\\u2029");
                default -> escaped.append(currentChar);
            }
        }
        return escaped.toString();
    }

    /**
     * RegExp.prototype.exec(str)
     * ES2020 21.2.5.2.1
     * Executes a search for a match in a string.
     */
    public static JSValue exec(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.exec called on non-RegExp");
        }

        JSString inputString = JSTypeConversions.toString(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String str = inputString.value();

        boolean global = regexp.isGlobal();
        boolean sticky = regexp.isSticky();

        JSValue lastIndexValue = regexp.get(context, PropertyKey.LAST_INDEX);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long lastIndexLong = JSTypeConversions.toLength(context, lastIndexValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        int lastIndex = 0;
        if (global || sticky) {
            if (lastIndexLong > str.length()) {
                setLastIndexOrThrow(context, regexp, JSNumber.of(0));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                return JSNull.INSTANCE;
            }
            lastIndex = (int) lastIndexLong;
        }

        RegExpEngine engine = regexp.getEngine();
        RegExpEngine.MatchResult result = engine.exec(str, lastIndex);

        if (result != null && result.matched()) {
            JSArray array = context.createJSArray();

            String[] captures = result.captures();
            for (int i = 0; i < captures.length; i++) {
                if (captures[i] != null) {
                    array.push(new JSString(captures[i]));
                } else {
                    array.push(JSUndefined.INSTANCE);
                }
            }

            int[][] indices = result.indices();
            if (indices != null && indices.length > 0) {
                array.defineProperty(null, PropertyKey.INDEX, JSNumber.of(indices[0][0]), PropertyDescriptor.DataState.All);
            }
            array.defineProperty(null, PropertyKey.INPUT, new JSString(str), PropertyDescriptor.DataState.All);
            array.defineProperty(null, PropertyKey.GROUPS,
                    createNamedGroupsValue(captures, regexp.getBytecode().groupNames()), PropertyDescriptor.DataState.All);
            if (regexp.hasIndices()) {
                array.defineProperty(null, PropertyKey.INDICES,
                        createIndicesValue(context, indices, regexp.getBytecode().groupNames()), PropertyDescriptor.DataState.All);
            }

            if (global || sticky) {
                if (indices != null && indices.length > 0) {
                    setLastIndexOrThrow(context, regexp, JSNumber.of(indices[0][1]));
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                }
            }

            return array;
        }

        if (global || sticky) {
            setLastIndexOrThrow(context, regexp, JSNumber.of(0));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        return JSNull.INSTANCE;
    }

    private static JSValue getBooleanFlagAccessorValue(
            JSContext context,
            JSValue thisArg,
            String errorName,
            java.util.function.Function<JSRegExp, Boolean> getter) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return JSUndefined.INSTANCE;
        }
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("RegExp.prototype." + errorName + " called on non-object");
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype." + errorName + " called on non-RegExp");
        }
        return JSBoolean.valueOf(getter.apply(regexp));
    }

    /**
     * get RegExp.prototype.dotAll
     * ES2020 21.2.5.6
     */
    public static JSValue getDotAll(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return JSUndefined.INSTANCE;
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.dotAll called on non-RegExp");
        }

        return JSBoolean.valueOf(regexp.isDotAll());
    }

    public static JSValue getDotAllAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return getBooleanFlagAccessorValue(context, thisArg, "dotAll", JSRegExp::isDotAll);
    }

    /**
     * get RegExp.prototype.flags
     * ES2020 21.2.5.3
     */
    public static JSValue getFlags(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return new JSString("");
        }
        if (!(thisArg instanceof JSRegExp regexpObject)) {
            return context.throwTypeError("RegExp.prototype.flags called on non-RegExp");
        }
        if (regexpObject.getPrototype() == null) {
            return new JSString(regexpObject.getFlags());
        }
        String flags = buildFlagsStringFromObject(context, regexpObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(flags != null ? flags : "");
    }

    public static JSValue getFlagsAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype.flags called on non-object");
        }
        if (isRegExpPrototypeObject(context, thisArg)) {
            return new JSString("");
        }
        String flags = buildFlagsStringFromObject(context, regexpObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(flags != null ? flags : "");
    }

    private static String getFlagsString(JSContext context, JSObject regexpObject) {
        JSValue flagsValue = regexpObject.get(context, PROPERTY_FLAGS);
        if (context.hasPendingException()) {
            return null;
        }
        JSString flagsString = JSTypeConversions.toString(context, flagsValue);
        if (context.hasPendingException()) {
            return null;
        }
        return flagsString.value();
    }

    /**
     * get RegExp.prototype.global
     * ES2020 21.2.5.4
     */
    public static JSValue getGlobal(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return JSUndefined.INSTANCE;
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isGlobal());
    }

    public static JSValue getGlobalAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return getBooleanFlagAccessorValue(context, thisArg, "global", JSRegExp::isGlobal);
    }

    /**
     * get RegExp.prototype.hasIndices
     * ES2022
     */
    public static JSValue getHasIndices(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return JSUndefined.INSTANCE;
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.hasIndices());
    }

    public static JSValue getHasIndicesAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return getBooleanFlagAccessorValue(context, thisArg, "hasIndices", JSRegExp::hasIndices);
    }

    /**
     * get RegExp.prototype.ignoreCase
     * ES2020 21.2.5.5
     */
    public static JSValue getIgnoreCase(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return JSUndefined.INSTANCE;
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isIgnoreCase());
    }

    public static JSValue getIgnoreCaseAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return getBooleanFlagAccessorValue(context, thisArg, "ignoreCase", JSRegExp::isIgnoreCase);
    }

    /**
     * get RegExp.prototype.multiline
     * ES2020 21.2.5.7
     */
    public static JSValue getMultiline(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return JSUndefined.INSTANCE;
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isMultiline());
    }

    public static JSValue getMultilineAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return getBooleanFlagAccessorValue(context, thisArg, "multiline", JSRegExp::isMultiline);
    }

    private static JSValue getRegExpSpeciesConstructor(JSContext context, JSObject regexpObject) {
        JSValue defaultConstructor = context.getGlobalObject().get(PropertyKey.fromString(JSRegExp.NAME));
        if (!(defaultConstructor instanceof JSFunction)) {
            return context.throwTypeError("RegExp constructor is not available");
        }
        JSValue constructorValue = regexpObject.get(context, PropertyKey.CONSTRUCTOR);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (constructorValue instanceof JSUndefined) {
            return defaultConstructor;
        }
        if (!(constructorValue instanceof JSObject constructorObject)) {
            return context.throwTypeError("constructor is not an object");
        }
        JSValue speciesValue = constructorObject.get(context, PropertyKey.SYMBOL_SPECIES);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (speciesValue instanceof JSUndefined || speciesValue instanceof JSNull) {
            return defaultConstructor;
        }
        if (!JSTypeChecking.isConstructor(speciesValue)) {
            return context.throwTypeError("Species is not a constructor");
        }
        return speciesValue;
    }

    /**
     * get RegExp.prototype.source
     * ES2020 21.2.5.10
     */
    public static JSValue getSource(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.source called on non-RegExp");
        }

        return new JSString(escapeRegExpSourcePattern(regexp.getPattern()));
    }

    public static JSValue getSourceAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("RegExp.prototype.source called on non-object");
        }
        if (isRegExpPrototypeObject(context, thisArg)) {
            return new JSString("(?:)");
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.source called on non-RegExp");
        }
        return new JSString(escapeRegExpSourcePattern(regexp.getPattern()));
    }

    /**
     * get RegExp.prototype.sticky
     * ES2020 21.2.5.12
     */
    public static JSValue getSticky(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return JSUndefined.INSTANCE;
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isSticky());
    }

    public static JSValue getStickyAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return getBooleanFlagAccessorValue(context, thisArg, "sticky", JSRegExp::isSticky);
    }

    /**
     * get RegExp.prototype.unicode
     * ES2020 21.2.5.15
     */
    public static JSValue getUnicode(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return JSUndefined.INSTANCE;
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isUnicode());
    }

    public static JSValue getUnicodeAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return getBooleanFlagAccessorValue(context, thisArg, "unicode", JSRegExp::isUnicode);
    }

    /**
     * get RegExp.prototype.unicodeSets
     * ES2024
     */
    public static JSValue getUnicodeSets(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return JSUndefined.INSTANCE;
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isUnicodeSets());
    }

    public static JSValue getUnicodeSetsAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return getBooleanFlagAccessorValue(context, thisArg, "unicodeSets", JSRegExp::isUnicodeSets);
    }

    private static boolean isRegExpPrototypeObject(JSContext context, JSValue value) {
        if (!(value instanceof JSObject)) {
            return false;
        }
        JSValue regExpConstructorValue = context.getGlobalObject().get(JSRegExp.NAME);
        if (!(regExpConstructorValue instanceof JSObject regExpConstructorObject)) {
            return false;
        }
        JSValue regExpPrototypeValue = regExpConstructorObject.get(PropertyKey.PROTOTYPE);
        return value == regExpPrototypeValue;
    }

    private static boolean isSameValuePositiveZero(JSValue value) {
        if (!(value instanceof JSNumber numberValue)) {
            return false;
        }
        double numericValue = numberValue.value();
        return Double.doubleToRawLongBits(numericValue) == Double.doubleToRawLongBits(0.0d);
    }

    private static JSValue regExpExec(JSContext context, JSObject regexpObject, JSString inputString) {
        JSValue execValue = regexpObject.get(context, PROPERTY_EXEC);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(execValue instanceof JSUndefined)) {
            if (!(execValue instanceof JSFunction execFunction)) {
                return context.throwTypeError("exec is not a function");
            }
            JSValue result = execFunction.call(context, regexpObject, new JSValue[]{inputString});
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(result instanceof JSObject) && !(result instanceof JSNull)) {
                return context.throwTypeError("RegExp exec method returned something other than an Object or null");
            }
            return result;
        }
        if (regexpObject instanceof JSRegExp) {
            return exec(context, regexpObject, new JSValue[]{inputString});
        }
        return context.throwTypeError("RegExp.prototype.exec called on incompatible receiver");
    }

    /**
     * %RegExpStringIteratorPrototype%.next()
     */
    public static JSValue regExpStringIteratorNext(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExpStringIterator regExpStringIterator)) {
            return context.throwTypeError("Method RegExp String Iterator.prototype.next called on incompatible receiver");
        }
        return regExpStringIterator.next(context);
    }

    private static boolean restoreLastIndex(JSContext context, JSObject regexpObject, JSValue previousLastIndex) {
        JSValue savedException = context.hasPendingException() ? context.getPendingException() : null;
        if (savedException != null) {
            context.clearPendingException();
        }
        setLastIndexOrThrow(context, regexpObject, previousLastIndex);
        if (context.hasPendingException()) {
            return false;
        }
        if (savedException != null) {
            context.setPendingException(savedException);
        }
        return true;
    }

    private static boolean sameValue(JSValue leftValue, JSValue rightValue) {
        if (leftValue == rightValue) {
            return true;
        }
        if (leftValue == null || rightValue == null) {
            return false;
        }
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            return Double.doubleToRawLongBits(leftNumber.value()) == Double.doubleToRawLongBits(rightNumber.value());
        }
        if (leftValue instanceof JSString leftString && rightValue instanceof JSString rightString) {
            return leftString.value().equals(rightString.value());
        }
        if (leftValue instanceof JSBoolean leftBoolean && rightValue instanceof JSBoolean rightBoolean) {
            return leftBoolean.value() == rightBoolean.value();
        }
        return false;
    }

    private static JSValue setLastIndexOrThrow(JSContext context, JSObject regexpObject, JSValue value) {
        boolean success = regexpObject.setWithResult(context, PropertyKey.LAST_INDEX, value);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!success) {
            return context.throwTypeError("Cannot assign to read only property 'lastIndex'");
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * RegExp.prototype[@@match](string)
     * ES2024 22.2.5.6
     */
    public static JSValue symbolMatch(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject rxObj)) {
            return context.throwTypeError("RegExp.prototype[@@match] called on non-object");
        }

        JSString str = JSTypeConversions.toString(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String flags = getFlagsString(context, rxObj);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (flags == null) {
            return JSUndefined.INSTANCE;
        }
        boolean global = flags.indexOf('g') >= 0;

        if (!global) {
            return regExpExec(context, rxObj, str);
        }

        boolean fullUnicode = flags.indexOf('u') >= 0 || flags.indexOf('v') >= 0;

        setLastIndexOrThrow(context, rxObj, JSNumber.of(0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSArray resultArray = context.createJSArray();
        int n = 0;
        String s = str.value();

        while (true) {
            JSValue result = regExpExec(context, rxObj, str);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (result instanceof JSNull || result instanceof JSUndefined) {
                if (n == 0) {
                    return JSNull.INSTANCE;
                }
                return resultArray;
            }
            if (!(result instanceof JSObject resultObj)) {
                return context.throwTypeError("RegExpExec must return an Object or null");
            }
            JSValue matchValue = resultObj.get(context, PropertyKey.fromIndex(0));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSString matchStr = JSTypeConversions.toString(context, matchValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            resultArray.push(matchStr);
            n++;

            if (matchStr.value().isEmpty()) {
                JSValue lastIndexValue = rxObj.get(context, PropertyKey.LAST_INDEX);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                long thisIndex = JSTypeConversions.toLength(context, lastIndexValue);
                long nextIndex = fullUnicode ? advanceStringIndexUnicode(s, (int) thisIndex) : thisIndex + 1;
                setLastIndexOrThrow(context, rxObj, JSNumber.of(nextIndex));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
        }
    }

    /**
     * RegExp.prototype[@@matchAll](string)
     * ES2024 22.2.6.9 (subset aligned with test262 semantics in current slice).
     */
    public static JSValue symbolMatchAll(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype[Symbol.matchAll] called on non-object");
        }

        JSString inputString = JSTypeConversions.toString(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String flags = getFlagsString(context, regexpObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (flags == null) {
            return JSUndefined.INSTANCE;
        }

        JSObject matcher = constructMatcherFromSpecies(context, regexpObject, flags);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (matcher == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue lastIndexValue = regexpObject.get(context, PropertyKey.LAST_INDEX);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long lastIndex = JSTypeConversions.toLength(context, lastIndexValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        setLastIndexOrThrow(context, matcher, JSNumber.of(lastIndex));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean global = flags.indexOf('g') >= 0;
        boolean fullUnicode = flags.indexOf('u') >= 0 || flags.indexOf('v') >= 0;
        return new JSRegExpStringIterator(context, matcher, inputString, global, fullUnicode);
    }

    /**
     * RegExp.prototype[@@replace](string, replaceValue)
     * ES2024 22.2.6.11 (subset sufficient for current test262 slice).
     */
    public static JSValue symbolReplace(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype[Symbol.replace] called on non-object");
        }

        JSString inputStringValue = JSTypeConversions.toString(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String inputString = inputStringValue.value();
        JSValue replaceValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        boolean functionalReplace = replaceValue instanceof JSFunction;
        if (!functionalReplace) {
            replaceValue = JSTypeConversions.toString(context, replaceValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            getFlagsString(context, regexpObject);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue globalValue = regexpObject.get(context, PROPERTY_GLOBAL);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean global = JSTypeConversions.toBoolean(globalValue).value();
        JSValue unicodeValue = regexpObject.get(context, PROPERTY_UNICODE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean fullUnicode = JSTypeConversions.toBoolean(unicodeValue).value();

        if (global) {
            setLastIndexOrThrow(context, regexpObject, JSNumber.of(0));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        java.util.ArrayList<JSObject> results = new java.util.ArrayList<>();
        while (true) {
            JSValue next = regExpExec(context, regexpObject, inputStringValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (next instanceof JSNull) {
                break;
            }
            if (!(next instanceof JSObject nextObject)) {
                return context.throwTypeError("RegExp exec method returned something other than an Object or null");
            }
            results.add(nextObject);
            if (!global) {
                break;
            }

            JSValue matchedValue = nextObject.get(context, PropertyKey.fromIndex(0));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSString matchedString = JSTypeConversions.toString(context, matchedValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (matchedString.value().isEmpty()) {
                JSValue lastIndexValue = regexpObject.get(context, PropertyKey.LAST_INDEX);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                long currentIndex = JSTypeConversions.toLength(context, lastIndexValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                long nextIndex = fullUnicode
                        ? advanceStringIndexUnicode(inputString, (int) Math.min(currentIndex, inputString.length()))
                        : currentIndex + 1;
                setLastIndexOrThrow(context, regexpObject, JSNumber.of(nextIndex));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
        }

        if (results.isEmpty()) {
            return inputStringValue;
        }

        StringBuilder accumulated = new StringBuilder(inputString.length() + 16);
        int nextSourcePosition = 0;
        for (JSObject resultObject : results) {
            JSValue matchedValue = resultObject.get(context, PropertyKey.fromIndex(0));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String matched = JSTypeConversions.toString(context, matchedValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSValue positionValue = resultObject.get(context, PropertyKey.INDEX);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int position = (int) JSTypeConversions.toInteger(context, positionValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            position = Math.max(0, Math.min(position, inputString.length()));
            int tailPosition = Math.max(0, Math.min(position + matched.length(), inputString.length()));

            JSValue lengthValue = resultObject.get(context, PropertyKey.LENGTH);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int resultLength = (int) Math.min(JSTypeConversions.toLength(context, lengthValue), Integer.MAX_VALUE);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String[] captures = new String[Math.max(1, resultLength)];
            captures[0] = matched;
            for (int i = 1; i < resultLength; i++) {
                JSValue captureValue = resultObject.get(context, PropertyKey.fromIndex(i));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (captureValue instanceof JSUndefined) {
                    captures[i] = null;
                } else {
                    captures[i] = JSTypeConversions.toString(context, captureValue).value();
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                }
            }

            JSValue groupsValue = resultObject.get(context, PropertyKey.GROUPS);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!functionalReplace && !(groupsValue instanceof JSUndefined)) {
                JSObject namedCapturesObject = JSTypeConversions.toObject(context, groupsValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (namedCapturesObject == null) {
                    return context.throwTypeError("Cannot convert undefined or null to object");
                }
            }

            String replacement = StringPrototype.applyRegExpReplacementWithNamedCapturesObject(
                    context,
                    replaceValue,
                    inputString,
                    position,
                    tailPosition,
                    captures,
                    groupsValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (position >= nextSourcePosition) {
                accumulated.append(inputString, nextSourcePosition, position);
                accumulated.append(replacement);
                nextSourcePosition = tailPosition;
            }
        }
        if (nextSourcePosition < inputString.length()) {
            accumulated.append(inputString.substring(nextSourcePosition));
        }
        return new JSString(accumulated.toString());
    }

    /**
     * RegExp.prototype[@@search](string)
     * ES2024 22.2.6.13
     */
    public static JSValue symbolSearch(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype[Symbol.search] called on non-object");
        }

        JSString inputString = JSTypeConversions.toString(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue previousLastIndex = regexpObject.get(context, PropertyKey.LAST_INDEX);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean restoreRequired = !isSameValuePositiveZero(previousLastIndex);
        if (restoreRequired) {
            setLastIndexOrThrow(context, regexpObject, JSNumber.of(0));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue resultValue = regExpExec(context, regexpObject, inputString);
        if (context.hasPendingException()) {
            if (restoreRequired) {
                restoreLastIndex(context, regexpObject, previousLastIndex);
            }
            return JSUndefined.INSTANCE;
        }

        JSValue returnValue;
        if (resultValue instanceof JSNull) {
            returnValue = JSNumber.of(-1);
        } else if (resultValue instanceof JSObject resultObject) {
            JSValue indexValue = resultObject.get(context, PropertyKey.INDEX);
            if (context.hasPendingException()) {
                if (restoreRequired) {
                    restoreLastIndex(context, regexpObject, previousLastIndex);
                }
                return JSUndefined.INSTANCE;
            }
            double index = JSTypeConversions.toInteger(context, indexValue);
            if (context.hasPendingException()) {
                if (restoreRequired) {
                    restoreLastIndex(context, regexpObject, previousLastIndex);
                }
                return JSUndefined.INSTANCE;
            }
            returnValue = JSNumber.of(index);
        } else {
            returnValue = context.throwTypeError("RegExp exec method returned something other than an Object or null");
            if (context.hasPendingException() && restoreRequired) {
                restoreLastIndex(context, regexpObject, previousLastIndex);
            }
            return JSUndefined.INSTANCE;
        }

        JSValue currentLastIndex = regexpObject.get(context, PropertyKey.LAST_INDEX);
        if (context.hasPendingException()) {
            if (restoreRequired) {
                restoreLastIndex(context, regexpObject, previousLastIndex);
            }
            return JSUndefined.INSTANCE;
        }
        if (!sameValue(currentLastIndex, previousLastIndex)) {
            if (!restoreLastIndex(context, regexpObject, previousLastIndex)) {
                return JSUndefined.INSTANCE;
            }
        }
        return returnValue;
    }

    /**
     * RegExp.prototype[@@split](string, limit)
     * ES2024 22.2.6.14
     */
    public static JSValue symbolSplit(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype[@@split] called on non-object");
        }

        String s = args.length > 0 ? JSTypeConversions.toString(context, args[0]).value() : "";
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String flags = getFlagsString(context, regexpObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (flags == null) {
            return JSUndefined.INSTANCE;
        }
        boolean unicodeMatching = flags.indexOf('u') >= 0 || flags.indexOf('v') >= 0;
        String newFlags = flags.indexOf('y') >= 0 ? flags : flags + "y";

        JSObject splitter = constructMatcherFromSpecies(context, regexpObject, newFlags);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (splitter == null) {
            return JSUndefined.INSTANCE;
        }

        long limit = args.length > 1 && !(args[1] instanceof JSUndefined) ? JSTypeConversions.toUint32(context, args[1]) : 0xFFFFFFFFL;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSArray result = context.createJSArray();
        int lengthA = 0;

        if (limit == 0) {
            return result;
        }

        int size = s.length();
        if (size == 0) {
            setLastIndexOrThrow(context, splitter, JSNumber.of(0));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue zValue = regExpExec(context, splitter, new JSString(s));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (zValue instanceof JSNull) {
                result.push(new JSString(s));
            }
            return result;
        }

        int p = 0;
        int q = p;

        while (q < size) {
            setLastIndexOrThrow(context, splitter, JSNumber.of(q));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue zValue = regExpExec(context, splitter, new JSString(s));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (zValue instanceof JSNull) {
                q = unicodeMatching ? advanceStringIndexUnicode(s, q) : q + 1;
                continue;
            }
            if (!(zValue instanceof JSObject zObject)) {
                return context.throwTypeError("RegExp exec method returned something other than an Object or null");
            }

            JSValue lastIndexValue = splitter.get(context, PropertyKey.LAST_INDEX);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            long eLong = JSTypeConversions.toLength(context, lastIndexValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int e = (int) Math.min(eLong, size);

            if (e == p) {
                q = unicodeMatching ? advanceStringIndexUnicode(s, q) : q + 1;
                continue;
            }

            JSValue matchIndexValue = zObject.get(context, PropertyKey.INDEX);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int zIndex = (int) JSTypeConversions.toInteger(context, matchIndexValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            zIndex = Math.max(0, Math.min(zIndex, s.length()));
            int splitIndex = Math.max(p, zIndex);
            result.push(new JSString(s.substring(p, splitIndex)));
            lengthA++;
            if (lengthA == limit) {
                return result;
            }

            JSValue lengthValue = zObject.get(context, PropertyKey.LENGTH);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            long captureLength = JSTypeConversions.toLength(context, lengthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            for (int i = 1; i < captureLength; i++) {
                JSValue captureValue = zObject.get(context, PropertyKey.fromIndex(i));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                result.push(captureValue);
                lengthA++;
                if (lengthA == limit) {
                    return result;
                }
            }

            p = e;
            q = p;
        }

        result.push(new JSString(s.substring(p)));
        return result;
    }

    /**
     * RegExp.prototype.test(str)
     * ES2020 21.2.5.17
     * Tests for a match in a string.
     */
    public static JSValue test(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype.test called on non-object");
        }
        JSString inputString = JSTypeConversions.toString(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue matchValue = regExpExec(context, regexpObject, inputString);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSBoolean.valueOf(!(matchValue instanceof JSNull));
    }

    /**
     * RegExp.prototype.toString()
     * ES2020 21.2.5.14
     */
    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.toString called on non-RegExp");
        }

        // Per ES spec, empty pattern should be "(?:)"
        String pattern = regexp.getPattern();
        if (pattern.isEmpty()) {
            pattern = "(?:)";
        }
        return new JSString("/" + pattern + "/" + regexp.getFlags());
    }

    private static final class JSRegExpStringIterator extends JSObject {
        private final boolean fullUnicode;
        private final boolean global;
        private final JSObject regexpObject;
        private final JSString stringValue;
        private boolean done;

        private JSRegExpStringIterator(
                JSContext context,
                JSObject regexpObject,
                JSString stringValue,
                boolean global,
                boolean fullUnicode) {
            super();
            this.regexpObject = regexpObject;
            this.stringValue = stringValue;
            this.global = global;
            this.fullUnicode = fullUnicode;
            this.done = false;

            JSObject prototype = context.getIteratorPrototype("RegExp String Iterator");
            if (prototype != null) {
                setPrototype(prototype);
            } else {
                context.transferPrototype(this, JSIterator.NAME);
            }
        }

        private JSValue next(JSContext context) {
            if (done) {
                return createIteratorResultObject(context, JSUndefined.INSTANCE, true);
            }

            JSValue matchValue = regExpExec(context, regexpObject, stringValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (matchValue instanceof JSNull) {
                done = true;
                return createIteratorResultObject(context, JSUndefined.INSTANCE, true);
            }

            if (!(matchValue instanceof JSObject matchObject)) {
                return context.throwTypeError("RegExp exec method returned something other than an Object or null");
            }

            if (!global) {
                done = true;
                return createIteratorResultObject(context, matchObject, false);
            }

            JSValue matchStringValue = matchObject.get(context, PropertyKey.fromString("0"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSString matchString = JSTypeConversions.toString(context, matchStringValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (matchString.value().isEmpty()) {
                JSValue lastIndexValue = regexpObject.get(context, PropertyKey.LAST_INDEX);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                long currentIndex = JSTypeConversions.toLength(context, lastIndexValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                long nextIndex;
                if (fullUnicode) {
                    int boundedIndex = (int) Math.min(currentIndex, stringValue.value().length());
                    nextIndex = advanceStringIndexUnicode(stringValue.value(), boundedIndex);
                } else {
                    nextIndex = currentIndex + 1;
                }
                setLastIndexOrThrow(context, regexpObject, JSNumber.of(nextIndex));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            return createIteratorResultObject(context, matchObject, false);
        }
    }
}
