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

package com.caoccao.qjs4j.builtins.regexp;

import com.caoccao.qjs4j.builtins.text.StringReplacement;
import com.caoccao.qjs4j.builtins.text.StringResultBuilder;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.iterator.JSIterator;
import com.caoccao.qjs4j.core.regexp.JSRegExp;
import java.util.ArrayList;
import java.util.List;

/** String matching, searching, splitting and substitution through the RegExp execution protocol. */
final class RegExpStringOperations {
    private RegExpStringOperations() {
    }

    private static boolean advanceAfterEmptyMatch(JSContext context, JSObject regexp, JSString match, String input,
            boolean fullUnicode) {
        if (!match.value().isEmpty()) {
            return true;
        }
        long index = readLastIndex(context, regexp);
        if (context.hasPendingException()) {
            return false;
        }
        long next = fullUnicode && index < input.length() ? advanceStringIndexUnicode(input, (int) index) : index + 1;
        setLastIndexOrThrow(context, regexp, JSNumber.of(next));
        return !context.hasPendingException();
    }

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

    // @@match returns the matched strings, while @@replace retains the objects for later capture reads.
    private static boolean collectGlobalMatches(JSContext context, JSObject regexp, JSString input, boolean unicode,
            JSArray strings, List<JSObject> objects) {
        JSObject match;
        while ((match = nextMatch(context, regexp, input)) != null) {
            JSString text = matchedString(context, match);
            if (context.hasPendingException()) {
                return false;
            }
            if (strings != null) {
                strings.push(text);
            } else {
                objects.add(match);
            }
            if (!advanceAfterEmptyMatch(context, regexp, text, input.value(), unicode)) {
                return false;
            }
        }
        return !context.hasPendingException();
    }

    private static JSObject constructWithSpecies(JSContext context, JSValue constructorValue, JSObject regexpObject,
            String flags) {
        JSArray argsArray = context.createJSArray();
        argsArray.push(regexpObject);
        argsArray.push(new JSString(flags));
        JSValue constructed = JSReflectObject.construct(context, JSUndefined.INSTANCE,
                new JSValue[]{constructorValue, argsArray});
        if (context.hasPendingException()) {
            return null;
        }
        if (!(constructed instanceof JSObject constructedObject)) {
            return context.rejectTypeError("RegExp species constructor did not return an object");
        }
        return constructedObject;
    }

    private static JSObject createIteratorResultObject(JSContext context, JSValue value, boolean done) {
        JSObject resultObject = context.createJSObject();
        resultObject.defineProperty(PropertyKey.VALUE, value, PropertyDescriptor.DataState.All);
        resultObject.defineProperty(PropertyKey.DONE, JSBoolean.valueOf(done), PropertyDescriptor.DataState.All);
        return resultObject;
    }

    private static String getFlagsString(JSContext context, JSObject regexpObject) {
        JSValue flagsValue = regexpObject.get(RegExpPrototype.PROPERTY_FLAGS);
        if (context.hasPendingException()) {
            return null;
        }
        JSString flagsString = JSTypeConversions.toString(context, flagsValue);
        if (context.hasPendingException()) {
            return null;
        }
        return flagsString.value();
    }

    private static JSValue getRegExpSpeciesConstructor(JSContext context, JSObject regexpObject) {
        JSValue defaultConstructor = context.getCachedRegExpConstructor();
        if (!(defaultConstructor instanceof JSFunction)) {
            return context.throwTypeError("RegExp constructor is not available");
        }
        JSValue constructorValue = regexpObject.get(PropertyKey.CONSTRUCTOR);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (constructorValue instanceof JSUndefined) {
            return defaultConstructor;
        }
        if (!(constructorValue instanceof JSObject constructorObject)) {
            return context.throwTypeError("constructor is not an object");
        }
        JSValue speciesValue = constructorObject.get(PropertyKey.SYMBOL_SPECIES);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (JSTypeChecking.isNullish(speciesValue)) {
            return defaultConstructor;
        }
        if (!JSTypeChecking.isConstructor(speciesValue)) {
            return context.throwTypeError("Species is not a constructor");
        }
        return speciesValue;
    }

    private static boolean isSameValuePositiveZero(JSValue value) {
        if (!(value instanceof JSNumber numberValue)) {
            return false;
        }
        double numericValue = numberValue.value();
        return Double.doubleToRawLongBits(numericValue) == Double.doubleToRawLongBits(0.0d);
    }

    private static JSString matchedString(JSContext context, JSObject match) {
        JSValue matched = match.get(PropertyKey.ZERO);
        return context.hasPendingException() ? null : JSTypeConversions.toString(context, matched);
    }

    /** A null match signals exhaustion or a pending exception, which the caller checks before using the result. */
    private static JSObject nextMatch(JSContext context, JSObject regexp, JSString input) {
        JSValue result = regExpExec(context, regexp, input);
        return !context.hasPendingException() && result instanceof JSObject match ? match : null;
    }

    static long readLastIndex(JSContext context, JSObject regexp) {
        JSValue value = regexp.get(PropertyKey.LAST_INDEX);
        return context.hasPendingException() ? 0 : JSTypeConversions.toLength(context, value);
    }

    private static JSValue regExpExec(JSContext context, JSObject regexpObject, JSString inputString) {
        // ES2024 22.2.5.2 RegExpExec
        // Step 3: Let exec be ? Get(R, "exec").
        JSValue execValue = regexpObject.get(PropertyKey.EXEC);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        // Step 4: If IsCallable(exec) is true, call it
        if (execValue instanceof JSFunction execFunction) {
            JSValue result = execFunction.call(context, regexpObject, new JSValue[]{inputString});
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(result instanceof JSObject) && !(result instanceof JSNull)) {
                return context.throwTypeError("RegExp exec method returned something other than an Object or null");
            }
            return result;
        }
        // Step 5-6: If exec is not callable, fall back to RegExpBuiltinExec
        if (regexpObject instanceof JSRegExp) {
            return RegExpPrototype.exec(context, regexpObject, new JSValue[]{inputString});
        }
        return context.throwTypeError("RegExp.prototype.exec called on incompatible receiver");
    }

    static JSValue regExpStringIteratorNext(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExpStringIterator regExpStringIterator)) {
            return context
                    .throwTypeError("Method RegExp String Iterator.prototype.next called on incompatible receiver");
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

    static JSValue setLastIndexOrThrow(JSContext context, JSObject regexpObject, JSValue value) {
        boolean success = regexpObject.setWithResult(PropertyKey.LAST_INDEX, value);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!success) {
            return context.throwTypeError("Cannot assign to read only property 'lastIndex'");
        }
        return JSUndefined.INSTANCE;
    }

    static JSValue symbolMatch(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject rxObj)) {
            return context.throwTypeError("RegExp.prototype[@@match] called on non-object");
        }

        JSString str = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String flags = getFlagsString(context, rxObj);
        if (flags == null) {
            return JSUndefined.INSTANCE;
        }
        if (flags.indexOf('g') < 0) {
            return regExpExec(context, rxObj, str);
        }
        setLastIndexOrThrow(context, rxObj, JSNumber.of(0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSArray matches = context.createJSArray();
        if (!collectGlobalMatches(context, rxObj, str, usesUnicode(flags), matches, null)) {
            return JSUndefined.INSTANCE;
        }
        return matches.getLength() == 0 ? JSNull.INSTANCE : matches;
    }

    static JSValue symbolMatchAll(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype[Symbol.matchAll] called on non-object");
        }

        JSString inputString = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        SpeciesMatcher species = SpeciesMatcher.create(context, regexpObject, false);
        if (species == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject matcher = species.matcher();
        String flags = species.flags();

        long lastIndex = readLastIndex(context, regexpObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        setLastIndexOrThrow(context, matcher, JSNumber.of(lastIndex));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean global = flags.indexOf('g') >= 0;
        boolean fullUnicode = usesUnicode(flags);
        return new JSRegExpStringIterator(context, matcher, inputString, global, fullUnicode);
    }

    static JSValue symbolReplace(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype[Symbol.replace] called on non-object");
        }

        JSString inputStringValue = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String inputString = inputStringValue.value();
        JSValue replaceValue = JSValue.argumentOrUndefined(args, 1);

        boolean functionalReplace = JSTypeChecking.isCallable(replaceValue);
        if (!functionalReplace) {
            replaceValue = JSTypeConversions.toString(context, replaceValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        // ES2024 22.2.5.11 step 6-8: Read flags string, parse global and unicode from it
        String flags = getFlagsString(context, regexpObject);
        if (flags == null) {
            return JSUndefined.INSTANCE;
        }
        boolean global = flags.indexOf('g') >= 0;
        boolean fullUnicode = usesUnicode(flags);

        if (global) {
            setLastIndexOrThrow(context, regexpObject, JSNumber.of(0));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        ArrayList<JSObject> results = new ArrayList<>();
        if (global) {
            if (!collectGlobalMatches(context, regexpObject, inputStringValue, fullUnicode, null, results)) {
                return JSUndefined.INSTANCE;
            }
        } else {
            JSObject match = nextMatch(context, regexpObject, inputStringValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (match != null) {
                results.add(match);
            }
        }

        if (results.isEmpty()) {
            return inputStringValue;
        }

        StringResultBuilder accumulated = new StringResultBuilder(context);
        int nextSourcePosition = 0;
        for (JSObject resultObject : results) {
            // ES2024 22.2.5.11 step 14a: nCaptures = ToLength(Get(result, "length"))
            JSValue lengthValue = resultObject.get(PropertyKey.LENGTH);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int resultLength = (int) Math.min(JSTypeConversions.toLength(context, lengthValue), Integer.MAX_VALUE);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // ES2024 22.2.5.11 step 14c: matched = ToString(Get(result, "0"))
            JSString matchedValue = matchedString(context, resultObject);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String matched = matchedValue.value();

            // ES2024 22.2.5.11 step 14d: position = ToIntegerOrInfinity(Get(result, "index"))
            JSValue positionValue = resultObject.get(PropertyKey.INDEX);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int position = (int) JSTypeConversions.toInteger(context, positionValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            position = Math.max(0, Math.min(position, inputString.length()));
            int tailPosition = Math.max(0, Math.min(position + matched.length(), inputString.length()));

            // ES2024 22.2.5.11 step 14f-g: captures
            String[] captures = new String[Math.max(1, resultLength)];
            captures[0] = matched;
            for (int i = 1; i < resultLength; i++) {
                JSValue captureValue = resultObject.get(PropertyKey.fromIndex(i));
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

            // ES2024 22.2.5.11 step 14h: namedCaptures = Get(result, "groups")
            JSValue groupsValue = resultObject.get(PropertyKey.GROUPS);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!functionalReplace && groupsValue instanceof JSNull) {
                // ToObject(namedCaptures) fails only for null; undefined is skipped.
                return context.throwTypeError("Cannot convert undefined or null to object");
            }

            JSString replacement = StringReplacement.forRegExpMatch(context, replaceValue, inputString, position,
                    tailPosition, captures, groupsValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (position >= nextSourcePosition) {
                // The result is bounded like every other string, so an oversized replacement is a RangeError rather
                // than the OutOfMemoryError this used to catch and report as a catchable InternalError.
                if (accumulated.exceedsMaxLength((long) position - nextSourcePosition + replacement.length())) {
                    return context.getPendingException();
                }
                accumulated.append(inputString, nextSourcePosition, position);
                accumulated.append(replacement);
                nextSourcePosition = tailPosition;
            }
        }
        if (accumulated.exceedsMaxLength(inputString.length() - nextSourcePosition)) {
            return context.getPendingException();
        }
        if (nextSourcePosition < inputString.length()) {
            accumulated.append(inputString.substring(nextSourcePosition));
        }
        return accumulated.build();
    }

    static JSValue symbolSearch(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype[Symbol.search] called on non-object");
        }

        JSString inputString = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue previousLastIndex = regexpObject.get(PropertyKey.LAST_INDEX);
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

        JSObject resultObject = nextMatch(context, regexpObject, inputString);
        if (context.hasPendingException()) {
            if (restoreRequired) {
                restoreLastIndex(context, regexpObject, previousLastIndex);
            }
            return JSUndefined.INSTANCE;
        }

        // ES2024 22.2.5.10 steps 8-9: Restore lastIndex BEFORE reading result properties
        JSValue currentLastIndex = regexpObject.get(PropertyKey.LAST_INDEX);
        if (context.hasPendingException()) {
            if (restoreRequired) {
                restoreLastIndex(context, regexpObject, previousLastIndex);
            }
            return JSUndefined.INSTANCE;
        }
        if (!JSObject.sameValue(currentLastIndex, previousLastIndex)) {
            if (!restoreLastIndex(context, regexpObject, previousLastIndex)) {
                return JSUndefined.INSTANCE;
            }
        }

        // ES2024 22.2.5.10 step 10-11: Read result index
        if (resultObject == null) {
            return JSNumber.of(-1);
        } else {
            JSValue indexValue = resultObject.get(PropertyKey.INDEX);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            double index = JSTypeConversions.toInteger(context, indexValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return JSNumber.of(index);
        }
    }

    static JSValue symbolSplit(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype[@@split] called on non-object");
        }

        JSString input = args.length > 0 ? JSTypeConversions.toString(context, args[0]) : JSString.EMPTY;
        String s = input.value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        SpeciesMatcher species = SpeciesMatcher.create(context, regexpObject, true);
        if (species == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject splitter = species.matcher();
        boolean unicodeMatching = usesUnicode(species.flags());

        long limit = args.length > 1 && !(args[1] instanceof JSUndefined)
                ? JSTypeConversions.toUint32(context, args[1])
                : 0xFFFFFFFFL;
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
            // ES2024 22.2.6.14 step 12: no setLastIndex for empty string case.
            JSObject zObject = nextMatch(context, splitter, input);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (zObject == null) {
                result.push(input);
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
            JSObject zObject = nextMatch(context, splitter, input);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (zObject == null) {
                q = unicodeMatching ? advanceStringIndexUnicode(s, q) : q + 1;
                continue;
            }

            long eLong = readLastIndex(context, splitter);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int e = (int) Math.min(eLong, size);

            if (e == p) {
                q = unicodeMatching ? advanceStringIndexUnicode(s, q) : q + 1;
                continue;
            }

            // ES2024 22.2.6.14 step 13.c.iii.4.a: T = substring(S, p, q).
            result.push(new JSString(s.substring(p, q)));
            lengthA++;
            if (lengthA == limit) {
                return result;
            }

            JSValue lengthValue = zObject.get(PropertyKey.LENGTH);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            long captureLength = JSTypeConversions.toLength(context, lengthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            for (int i = 1; i < captureLength; i++) {
                JSValue captureValue = zObject.get(PropertyKey.fromIndex(i));
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

    static JSValue test(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype.test called on non-object");
        }
        JSString inputString = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSObject matchObject = nextMatch(context, regexpObject, inputString);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSBoolean.valueOf(matchObject != null);
    }

    private static boolean usesUnicode(String flags) {
        return flags.indexOf('u') >= 0 || flags.indexOf('v') >= 0;
    }

    private static final class JSRegExpStringIterator extends JSObject {
        private boolean done;
        private final boolean fullUnicode;
        private final boolean global;
        private final JSObject regexpObject;
        private final JSString stringValue;

        private JSRegExpStringIterator(JSContext context, JSObject regexpObject, JSString stringValue, boolean global,
                boolean fullUnicode) {
            super(context);
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

            JSObject matchObject = nextMatch(context, regexpObject, stringValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (matchObject == null) {
                done = true;
                return createIteratorResultObject(context, JSUndefined.INSTANCE, true);
            }

            if (!global) {
                done = true;
                return createIteratorResultObject(context, matchObject, false);
            }

            JSString matchString = matchedString(context, matchObject);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (!advanceAfterEmptyMatch(context, regexpObject, matchString, stringValue.value(), fullUnicode)) {
                return JSUndefined.INSTANCE;
            }

            return createIteratorResultObject(context, matchObject, false);
        }
    }

    private record SpeciesMatcher(JSObject matcher, String flags) {
        static SpeciesMatcher create(JSContext context, JSObject regexp, boolean sticky) {
            // Both algorithms resolve the species before reading the flags.
            JSValue constructor = getRegExpSpeciesConstructor(context, regexp);
            if (context.hasPendingException()) {
                return null;
            }
            String flags = getFlagsString(context, regexp);
            if (flags == null) {
                return null;
            }
            String matcherFlags = sticky && flags.indexOf('y') < 0 ? flags + "y" : flags;
            JSObject matcher = constructWithSpecies(context, constructor, regexp, matcherFlags);
            return matcher == null ? null : new SpeciesMatcher(matcher, flags);
        }
    }
}
