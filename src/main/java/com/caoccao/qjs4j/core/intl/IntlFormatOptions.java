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

package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.core.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Locale resolution and option access shared by Intl constructors.
 */
final class IntlFormatOptions {
    private static final String[] NUMERIC_WIDTHS = {"numeric", "2-digit"};

    /**
     * Supported numbering systems with simple digit mappings (per ECMA-402 Table 10). Used by
     * supportedValuesOf("numberingSystem") and validated in constructors.
     */
    static final Set<String> SUPPORTED_NUMBERING_SYSTEMS = Set.of("adlm", "ahom", "arab", "arabext", "bali", "beng",
            "bhks", "brah", "cakm", "cham", "deva", "diak", "fullwide", "gong", "gara", "gonm", "gujr", "gukh", "guru",
            "hanidec", "hmng", "hmnp", "java", "kali", "kawi", "khmr", "knda", "krai", "lana", "lanatham", "laoo",
            "latn", "lepc", "limb", "mathbold", "mathdbl", "mathmono", "mathsanb", "mathsans", "mlym", "modi", "mong",
            "mroo", "mtei", "mymr", "mymrepka", "mymrpao", "mymrshan", "mymrtlng", "nagm", "newa", "nkoo", "olck",
            "onao", "orya", "osma", "outlined", "rohg", "saur", "segment", "shrd", "sind", "sinh", "sora", "sund",
            "sunu", "takr", "talu", "tamldec", "telu", "thai", "tibt", "tirh", "tnsa", "tols", "vaii", "wara", "wcho");

    private static final String[] TEXT_WIDTHS = {"long", "short", "narrow"};

    /**
     * Pattern for Unicode Locale Identifier type: (3*8alphanum) *("-" (3*8alphanum))
     */
    static final Pattern UNICODE_TYPE_PATTERN = Pattern.compile("^[a-zA-Z0-9]{3,8}(-[a-zA-Z0-9]{3,8})*$");

    private IntlFormatOptions() {
    }

    static JSValue coerceOptionsObject(JSContext context, JSValue options) {
        if (options instanceof JSUndefined) {
            return emptyOptions(context);
        }
        if (options instanceof JSNull) {
            return context.throwTypeError("Cannot convert null to object");
        }
        JSObject object = JSTypeConversions.toObject(context, options);
        return object == null || context.hasPendingException() ? JSUndefined.INSTANCE : object;
    }

    static JSValue coerceOptionsWithLocaleMatcher(JSContext context, JSValue[] args) {
        JSValue options = coerceOptionsObject(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return options;
        }
        String matcher = readStringOption(context, options, "localeMatcher");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isValidLocaleMatcher(matcher)) {
            throw new IllegalArgumentException("Invalid option value: " + matcher);
        }
        return options;
    }

    static JSValue constructWithLocaleMatcher(JSContext context, JSObject prototype, JSValue[] args, String name,
            Factory factory) {
        try {
            ConstructorLocale constructor = ConstructorLocale.resolve(context, prototype, args, name);
            if (constructor == null) {
                return JSUndefined.INSTANCE;
            }
            JSValue options = coerceOptionsWithLocaleMatcher(context, args);
            if (context.hasPendingException() || !(options instanceof JSObject object)) {
                return options;
            }
            return factory.create(context, constructor, object);
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    static JSObject emptyOptions(JSContext context) {
        JSObject empty = context.createJSObject();
        empty.setPrototype(null);
        return empty;
    }

    static Integer getOptionIntegerOrUndefined(JSContext context, JSObject optionsObject, String key, int minimum,
            int maximum) {
        return getOptionIntegerOrUndefined(context, optionsObject, key, minimum, maximum, null);
    }

    static Integer getOptionIntegerOrUndefined(JSContext context, JSObject optionsObject, String key, int minimum,
            int maximum, String rangeError) {
        JSValue rawValue = optionsObject.get(PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        double numberValue = JSTypeConversions.toNumber(context, rawValue).value();
        if (context.hasPendingException()) {
            return null;
        }
        if (numberValue < minimum || numberValue > maximum || numberValue != (int) numberValue) {
            context.throwRangeError(rangeError != null
                    ? rangeError
                    : "Value " + numberValue + " out of range for Intl.NumberFormat options property " + key);
            return null;
        }
        return (int) numberValue;
    }

    /**
     * GetOptionsObject (ECMA-402 §9.2.11). Converts the options argument to an object per spec: - undefined → empty
     * object - object → return as-is - anything else (null, boolean, number, string, symbol, bigint) → TypeError
     */
    static JSValue getOptionsObject(JSContext context, JSValue options) {
        if (options instanceof JSUndefined || options == null) {
            // Create null-prototype object to avoid triggering Object.prototype getters
            return emptyOptions(context);
        }
        if (options instanceof JSObject) {
            return options;
        }
        context.throwTypeError("Cannot convert " + JSTypeConversions.toString(context, options).value() + " to object");
        return JSUndefined.INSTANCE;
    }

    static boolean isValidLocaleMatcher(String value) {
        return value == null || "lookup".equals(value) || "best fit".equals(value);
    }

    static JSValue localeMatcherError(JSContext context, JSValue options) {
        return localeMatcherError(context, options, null);
    }

    static JSValue localeMatcherError(JSContext context, JSValue options, String operation) {
        String matcher = readStringOption(context, options, "localeMatcher");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isValidLocaleMatcher(matcher)) {
            if (operation != null) {
                return context.throwRangeError(
                        "Value " + matcher + " out of range for " + operation + " options property localeMatcher");
            }
            return context.throwRangeError("localeMatcher must be \"lookup\" or \"best fit\"");
        }
        return null;
    }

    static String normalizedStringOption(JSContext context, JSValue options, String key, String fallback,
            String... allowed) {
        String value = readStringOption(context, options, key);
        return context.hasPendingException() ? null : normalizeOption(value, fallback, allowed);
    }

    static String normalizeOption(String rawValue, String defaultValue, String... allowedValues) {
        if (rawValue == null) {
            return defaultValue;
        }
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(rawValue)) {
                return allowedValue;
            }
        }
        throw new IllegalArgumentException("Invalid option value: " + rawValue);
    }

    static String readNumericWidth(JSContext context, JSObject options, String key) {
        return readValidatedOption(context, options, key, NUMERIC_WIDTHS);
    }

    /**
     * Read a string option from options object. Returns null if option is undefined.
     */
    static String readStringOption(JSContext context, JSValue options, String key) {
        if (!(options instanceof JSObject optionsObject) || context.hasPendingException()) {
            return null;
        }
        JSValue rawValue = optionsObject.get(PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        return JSTypeConversions.toString(context, rawValue).value();
    }

    static String readTextWidth(JSContext context, JSObject options, String key) {
        return readValidatedOption(context, options, key, TEXT_WIDTHS);
    }

    /**
     * Read a string option from options object, validate against allowed values. Returns null if option is undefined.
     * Throws RangeError for invalid values.
     */
    static String readValidatedOption(JSContext context, JSObject optionsObject, String key, String... allowedValues) {
        String stringValue = readStringOption(context, optionsObject, key);
        if (stringValue == null || context.hasPendingException()) {
            return null;
        }
        for (String allowed : allowedValues) {
            if (allowed.equals(stringValue)) {
                return stringValue;
            }
        }
        context.throwRangeError("Value " + stringValue + " out of range for property " + key);
        return null;
    }

    /**
     * Resolve the prototype for an Intl constructor per GetPrototypeFromConstructor. When called via Reflect.construct
     * with a newTarget from another realm whose prototype is not an object, falls back to that realm's
     * Intl.X.prototype.
     */
    static JSObject resolveIntlPrototype(JSContext context, JSObject defaultPrototype, String intlConstructorName) {
        JSValue newTarget = context.getNativeConstructorNewTarget();
        if (!(newTarget instanceof JSObject newTargetObject)) {
            return defaultPrototype;
        }
        JSValue newTargetProto = newTargetObject.get(PropertyKey.PROTOTYPE);
        if (context.hasPendingException()) {
            return null;
        }
        if (newTargetProto instanceof JSObject newTargetProtoObj) {
            return newTargetProtoObj;
        }
        // newTarget.prototype is not an object; fall back to realm's Intl.X.prototype
        JSContext functionRealm = context.getFunctionRealm(newTargetObject);
        if (context.hasPendingException()) {
            return null;
        }
        JSValue intlObj = functionRealm.getGlobalObject().get("Intl");
        if (intlObj instanceof JSObject intlObject) {
            JSValue ctor = intlObject.get(intlConstructorName);
            if (ctor instanceof JSObject ctorObj) {
                JSValue proto = ctorObj.get("prototype");
                if (proto instanceof JSObject protoObj) {
                    return protoObj;
                }
            }
        }
        return defaultPrototype;
    }

    static boolean validateUnicodeType(JSContext context, String key, String value) {
        if (value == null || UNICODE_TYPE_PATTERN.matcher(value).matches()) {
            return true;
        }
        context.throwRangeError("Invalid " + key + ": " + value);
        return false;
    }
    record ConstructorLocale(JSObject prototype, Locale locale) {
        static ConstructorLocale resolve(JSContext context, JSObject prototype, JSValue[] args, String name) {
            JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, name);
            if (context.hasPendingException()) {
                return null;
            }
            Locale locale = IntlBuiltins.resolveLocale(context, args, 0);
            return context.hasPendingException() ? null : new ConstructorLocale(resolvedPrototype, locale);
        }

        JSObject finish(JSObject instance) {
            if (prototype != null) {
                instance.setPrototype(prototype);
            }
            return instance;
        }
    }

    @FunctionalInterface
    interface Factory {
        JSValue create(JSContext context, ConstructorLocale constructor, JSObject options);
    }

    record NumberingLocale(Locale locale, String numberingSystem) {
        static NumberingLocale resolve(Locale locale, String option) {
            boolean hasExtensions = locale.hasExtensions();
            String extension = hasExtensions
                    ? IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag()).get("nu")
                    : null;
            if (extension != null) {
                extension = extension.toLowerCase(Locale.ROOT);
            }
            String system = "latn";
            if (option != null && SUPPORTED_NUMBERING_SYSTEMS.contains(option)) {
                system = option;
            } else if (extension != null && SUPPORTED_NUMBERING_SYSTEMS.contains(extension)) {
                system = extension;
            }
            Locale resolved = hasExtensions ? IntlLocaleTag.stripUnicodeExtensions(locale) : locale;
            if (system.equals(extension)) {
                resolved = new Locale.Builder().setLocale(resolved).setUnicodeLocaleKeyword("nu", system).build();
            }
            return new NumberingLocale(resolved, system);
        }
    }
}
