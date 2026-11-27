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

package com.caoccao.qjs4j.builtins.text;

import com.caoccao.qjs4j.core.*;
/** Shared GetSubstitution and replacer callback handling for string and RegExp matches. */
public final class StringReplacement {
    private StringReplacement() {
    }

    /**
     * Append a capture to a substitution, refusing to pass the string length limit. "$1" repeated in a replacement
     * multiplies a capture's length: GetSubstitution used to build such a string until the JVM ran out of memory.
     *
     * @return false with a pending RangeError when the result would be too long
     */
    private static boolean appendCapture(JSContext context, StringResultBuilder builder, String capture) {
        String text = capture != null ? capture : "";
        if (builder.exceedsMaxLength(text.length())) {
            return false;
        }
        builder.append(text);
        return true;
    }

    private static JSValue[] callbackArguments(JSContext context, String input, int matchStart, String[] captures,
            JSValue namedCapturesValue) {
        boolean hasNamedCaptures = !(namedCapturesValue instanceof JSUndefined);
        int callbackArgCount = captures.length + 2 + (hasNamedCaptures ? 1 : 0);
        JSValue[] callbackArgs = new JSValue[callbackArgCount];
        for (int captureIndex = 0; captureIndex < captures.length; captureIndex++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            String captureValue = captures[captureIndex];
            callbackArgs[captureIndex] = captureValue != null ? new JSString(captureValue) : JSUndefined.INSTANCE;
        }
        callbackArgs[captures.length] = JSNumber.of(matchStart);
        callbackArgs[captures.length + 1] = new JSString(input);
        if (hasNamedCaptures) {
            callbackArgs[captures.length + 2] = namedCapturesValue;
        }
        return callbackArgs;
    }

    /**
     * GetSubstitution: expand the replacement template's tokens. Named-group property access and conversion stay lazy
     * and in template order.
     *
     * @param captures
     *            the match followed by its captures, a missing capture being null
     * @param namedCapturesValue
     *            the match result's groups, or null when there are none
     * @return the substitution, or null with a pending exception
     */
    private static JSString expand(JSContext context, String replacementTemplate, String input, int matchStart,
            int matchEnd, String[] captures, JSValue namedCapturesValue) {
        // RegExp.prototype[Symbol.replace] has already refused null groups (ToObject), so only undefined means none.
        boolean hasNamedCaptures = namedCapturesValue != null && !(namedCapturesValue instanceof JSUndefined);
        StringResultBuilder resultBuilder = new StringResultBuilder(context);
        String prefix = null;
        String suffix = null;
        int replacementIndex = 0;
        while (replacementIndex < replacementTemplate.length()) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            char currentChar = replacementTemplate.charAt(replacementIndex);
            if (currentChar != '$') {
                // Preserve literal runs as a single leaf. Per-character appends rebuild short rope leaves for every
                // match and can allocate gigabytes of temporary strings before the result's length check fires.
                int nextToken = replacementTemplate.indexOf('$', replacementIndex + 1);
                int literalEnd = nextToken >= 0 ? nextToken : replacementTemplate.length();
                resultBuilder.append(replacementTemplate, replacementIndex, literalEnd);
                replacementIndex = literalEnd;
                continue;
            }
            if (replacementIndex + 1 >= replacementTemplate.length()) {
                resultBuilder.append(currentChar);
                replacementIndex++;
                continue;
            }
            char nextChar = replacementTemplate.charAt(replacementIndex + 1);
            if (nextChar == '$') {
                resultBuilder.append('$');
                replacementIndex += 2;
                continue;
            }
            if (nextChar == '&') {
                if (resultBuilder.exceedsMaxLength(captures[0].length())) {
                    return null;
                }
                resultBuilder.append(captures[0]);
                replacementIndex += 2;
                continue;
            }
            if (nextChar == '`') {
                if (resultBuilder.exceedsMaxLength(matchStart)) {
                    return null;
                }
                if (prefix == null) {
                    prefix = input.substring(0, matchStart);
                }
                resultBuilder.append(prefix);
                replacementIndex += 2;
                continue;
            }
            if (nextChar == '\'') {
                if (resultBuilder.exceedsMaxLength(input.length() - matchEnd)) {
                    return null;
                }
                if (suffix == null) {
                    suffix = input.substring(matchEnd);
                }
                resultBuilder.append(suffix);
                replacementIndex += 2;
                continue;
            }
            if (nextChar == '<') {
                if (!hasNamedCaptures) {
                    // Without named captures, consume only "$<" so later substitutions still run.
                    resultBuilder.append("$<");
                    replacementIndex += 2;
                    continue;
                }
                int closeIndex = replacementTemplate.indexOf('>', replacementIndex + 2);
                if (closeIndex >= 0) {
                    String groupName = replacementTemplate.substring(replacementIndex + 2, closeIndex);
                    JSObject namedCapturesObject = JSTypeConversions.toObject(context, namedCapturesValue);
                    JSValue groupValue = namedCapturesObject.get(PropertyKey.fromString(groupName));
                    if (context.hasPendingException()) {
                        return null;
                    }
                    if (!(groupValue instanceof JSUndefined)) {
                        String replacement = JSTypeConversions.toString(context, groupValue).value();
                        if (context.hasPendingException() || resultBuilder.exceedsMaxLength(replacement.length())) {
                            return null;
                        }
                        resultBuilder.append(replacement);
                    }
                    replacementIndex = closeIndex + 1;
                    continue;
                }
            }
            if (nextChar >= '0' && nextChar <= '9') {
                int captureIndex = nextChar - '0';
                int consumed = 2;
                if (replacementIndex + 2 < replacementTemplate.length()) {
                    char digit = replacementTemplate.charAt(replacementIndex + 2);
                    if (digit >= '0' && digit <= '9') {
                        int pair = captureIndex * 10 + digit - '0';
                        if (pair > 0 && pair < captures.length) {
                            captureIndex = pair;
                            consumed++;
                        }
                    }
                }
                if (captureIndex > 0 && captureIndex < captures.length) {
                    if (!appendCapture(context, resultBuilder, captures[captureIndex])) {
                        return null;
                    }
                    replacementIndex += consumed;
                    continue;
                }
            }
            resultBuilder.append('$');
            replacementIndex++;
        }
        return resultBuilder.build();
    }

    public static JSString forRegExpMatch(JSContext context, JSValue replaceValue, String input, int matchStart,
            int matchEnd, String[] captures, JSValue namedCapturesValue) {
        if (JSTypeChecking.isCallable(replaceValue)) {
            JSValue[] callbackArgs = callbackArguments(context, input, matchStart, captures, namedCapturesValue);
            if (context.hasPendingException()) {
                return null;
            }
            JSValue callbackResult = JSCall.call(context, replaceValue, JSUndefined.INSTANCE, callbackArgs);
            if (context.hasPendingException()) {
                return null;
            }
            return JSTypeConversions.toString(context, callbackResult);
        }
        return expand(context, ((JSString) replaceValue).value(), input, matchStart, matchEnd, captures,
                namedCapturesValue);
    }

    static JSString forStringMatch(JSContext context, String search, String input, int index, JSValue replaceValue,
            String substitute) {
        if (substitute != null) {
            if (substitute.indexOf('$') < 0) {
                return new JSString(substitute);
            }
            return expand(context, substitute, input, index, index + search.length(), new String[]{search}, null);
        }
        JSValue value = JSCall.call(context, replaceValue, JSUndefined.INSTANCE,
                new JSValue[]{new JSString(search), JSNumber.of(index), new JSString(input)});
        return context.hasPendingException() ? null : JSTypeConversions.toString(context, value);
    }

}
