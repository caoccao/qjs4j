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

import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

import java.time.ZoneId;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of Intl object and Intl.* prototype methods.
 */
public final class JSIntlObject {
    private static final Set<String> AVAILABLE_LOCALE_LANGUAGES;
    private static final String[] FORMAT_STYLE_VALUES = {"short", "medium", "long", "full"};
    /**
     * Pattern for Unicode Locale Identifier type: (3*8alphanum) *("-" (3*8alphanum))
     */
    private static final Pattern UNICODE_TYPE_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]{3,8}(-[a-zA-Z0-9]{3,8})*$");

    /**
     * Supported numbering systems with simple digit mappings (per ECMA-402 Table 10).
     * Used by supportedValuesOf("numberingSystem") and validated in constructors.
     */
    private static final Set<String> SUPPORTED_NUMBERING_SYSTEMS = Set.of(
            "adlm", "ahom", "arab", "arabext", "bali", "beng", "bhks",
            "brah", "cakm", "cham", "deva", "diak", "fullwide", "gong",
            "gonm", "gujr", "guru", "hanidec", "hmng", "hmnp", "java",
            "kali", "kawi", "khmr", "knda", "lana", "lanatham", "laoo", "latn",
            "lepc", "limb", "mathbold", "mathdbl", "mathmono", "mathsanb",
            "mathsans", "mlym", "modi", "mong", "mroo", "mtei", "mymr",
            "mymrshan", "mymrtlng", "nagm", "newa", "nkoo", "olck", "orya",
            "osma", "rohg", "saur", "segment", "shrd", "sind", "sinh",
            "sora", "sund", "takr", "talu", "tamldec", "tnsa", "telu", "thai",
            "tibt", "tirh", "vaii", "wara", "wcho"
    );
    /**
     * Check if a collation value is valid/supported.
     */
    private static final Set<String> VALID_COLLATION_TYPES = Set.of(
            "big5han", "compat", "dict", "direct", "ducet", "emoji", "eor",
            "gb2312", "phonebk", "phonetic", "pinyin", "reformed", "searchjl",
            "stroke", "trad", "unihan", "zhuyin"
    );

    static {
        AVAILABLE_LOCALE_LANGUAGES = Arrays.stream(Locale.getAvailableLocales())
                .map(Locale::toLanguageTag)
                .filter(tag -> !tag.isEmpty() && !"und".equals(tag))
                .map(tag -> {
                    int dashIndex = tag.indexOf('-');
                    return dashIndex > 0 ? tag.substring(0, dashIndex).toLowerCase(Locale.ROOT) : tag.toLowerCase(Locale.ROOT);
                })
                .collect(Collectors.toSet());
    }

    /**
     * Build resolved locale tag including applied unicode extensions.
     * Extension keys are only included when the resolved value matches the
     * original unicode extension value. Sorted alphabetically per BCP 47.
     */
    private static String buildResolvedLocaleTag(Locale baseLocale, String collation, boolean numeric,
                                                  String caseFirst,
                                                  Map<String, String> unicodeExtensions) {
        String baseTag = stripUnicodeExtensions(baseLocale).toLanguageTag();
        TreeMap<String, String> extensions = new TreeMap<>();

        // Include co extension only if resolved collation matches original extension
        if (unicodeExtensions.containsKey("co")) {
            String extCollation = unicodeExtensions.get("co");
            if (collation != null && collation.equals(extCollation)) {
                extensions.put("co", collation);
            }
        }

        // Include kf extension only if resolved caseFirst matches original extension
        if (unicodeExtensions.containsKey("kf")) {
            String extCaseFirst = unicodeExtensions.get("kf");
            if (caseFirst != null && caseFirst.equals(extCaseFirst)) {
                extensions.put("kf", caseFirst);
            }
        }

        // Include kn extension only if resolved numeric matches original extension
        if (unicodeExtensions.containsKey("kn")) {
            String extKnValue = unicodeExtensions.get("kn");
            boolean extNumeric = !"false".equals(extKnValue);
            if (numeric == extNumeric && numeric) {
                extensions.put("kn", null); // canonical form: kn without value
            }
        }

        if (extensions.isEmpty()) {
            return baseTag;
        }
        StringBuilder builder = new StringBuilder(baseTag);
        builder.append("-u");
        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            builder.append("-").append(entry.getKey());
            if (entry.getValue() != null) {
                builder.append("-").append(entry.getValue());
            }
        }
        return builder.toString();
    }

    private static List<String> canonicalizeLocaleList(JSContext context, JSValue localeValue) {
        List<String> localeTags = new ArrayList<>();
        if (localeValue == null || localeValue instanceof JSUndefined) {
            return localeTags;
        }
        if (localeValue instanceof JSNull) {
            context.throwTypeError("Cannot convert null to object");
            return localeTags;
        }
        // Per ECMA-402 §9.2.1 CanonicalizeLocaleList:
        // Step 3: If Type(locales) is String, wrap in array
        if (localeValue instanceof JSString) {
            localeTags.add(((JSString) localeValue).value());
        } else {
            // Step 4: Let O be ToObject(locales)
            JSObject localeObject;
            if (localeValue instanceof JSObject obj) {
                localeObject = obj;
            } else {
                localeObject = JSTypeConversions.toObject(context, localeValue);
                if (context.hasPendingException()) {
                    return localeTags;
                }
            }
            // Step 5: Let len be ToLength(Get(O, "length"))
            JSValue lengthValue = localeObject.get(context, PropertyKey.fromString("length"));
            if (context.hasPendingException()) {
                return localeTags;
            }
            long length;
            if (lengthValue == null || lengthValue instanceof JSUndefined) {
                length = 0;
            } else {
                double lenNum = JSTypeConversions.toNumber(context, lengthValue).value();
                if (context.hasPendingException()) {
                    return localeTags;
                }
                if (Double.isNaN(lenNum) || lenNum < 0) {
                    length = 0;
                } else {
                    length = (long) Math.min(lenNum, (double) Long.MAX_VALUE);
                }
            }
            // Step 6: Iterate from 0 to len
            for (long i = 0; i < length; i++) {
                PropertyKey pk = PropertyKey.fromString(String.valueOf(i));
                // Step 7b: Let kPresent be HasProperty(O, Pk)
                boolean kPresent;
                try {
                    kPresent = localeObject.has(pk);
                } catch (JSException e) {
                    // Proxy has trap may throw
                    return localeTags;
                }
                if (context.hasPendingException()) {
                    return localeTags;
                }
                if (!kPresent) {
                    continue;
                }
                JSValue element = localeObject.get(context, pk);
                if (context.hasPendingException()) {
                    return localeTags;
                }
                if (element == null || element instanceof JSUndefined) {
                    context.throwTypeError("undefined is not a well-formed locale value");
                    return localeTags;
                }
                if (!(element instanceof JSString) && !(element instanceof JSObject)) {
                    context.throwTypeError(
                            JSTypeConversions.toString(context, element).value()
                                    + " is not a well-formed currency value");
                    return localeTags;
                }
                // Per ECMA-402, if element has [[InitializedLocale]], use its [[Locale]] tag
                if (element instanceof JSIntlLocale intlLocale) {
                    localeTags.add(intlLocale.getTag());
                } else {
                    localeTags.add(JSTypeConversions.toString(context, element).value());
                }
                if (context.hasPendingException()) {
                    return localeTags;
                }
            }
        }
        LinkedHashSet<String> canonicalLocales = new LinkedHashSet<>();
        for (String localeTag : localeTags) {
            try {
                canonicalLocales.add(canonicalizeLocaleTag(localeTag));
            } catch (IllegalArgumentException e) {
                context.throwRangeError("Invalid language tag: " + localeTag);
                return new ArrayList<>();
            }
        }
        return new ArrayList<>(canonicalLocales);
    }

    public static JSValue collatorCompare(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlCollator collator)) {
            return context.throwTypeError("Intl.Collator.prototype.compare called on incompatible receiver");
        }
        String left = args.length > 0 ? JSTypeConversions.toString(context, args[0]).value() : "undefined";
        String right = args.length > 1 ? JSTypeConversions.toString(context, args[1]).value() : "undefined";
        return JSNumber.of(collator.compare(left, right));
    }

    /**
     * Getter for Intl.Collator.prototype.compare.
     * Per ECMA-402 §10.3.3, this is an accessor property that returns a bound compare function.
     */
    public static JSValue collatorCompareGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlCollator collator)) {
            return context.throwTypeError("Intl.Collator.prototype.compare called on incompatible receiver");
        }
        // Return the cached bound compare function, creating it on first access
        JSFunction cached = collator.getBoundCompareFunction();
        if (cached != null) {
            return cached;
        }
        JSNativeFunction boundCompare = new JSNativeFunction("", 2,
                (ctx, thisVal, compareArgs) -> collatorCompare(ctx, collator, compareArgs));
        context.transferPrototype(boundCompare, JSFunction.NAME);
        collator.setBoundCompareFunction(boundCompare);
        return boundCompare;
    }

    public static JSValue collatorResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlCollator collator)) {
            return context.throwTypeError("Intl.Collator.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(collator.getResolvedLocaleTag()));
        resolvedOptions.set("usage", new JSString(collator.getUsage()));
        resolvedOptions.set("sensitivity", new JSString(collator.getSensitivity()));
        resolvedOptions.set("ignorePunctuation", JSBoolean.valueOf(collator.getIgnorePunctuation()));
        resolvedOptions.set("collation", new JSString(collator.getCollation()));
        resolvedOptions.set("numeric", JSBoolean.valueOf(collator.getNumeric()));
        resolvedOptions.set("caseFirst", new JSString(collator.getCaseFirst()));
        return resolvedOptions;
    }

    public static JSValue createCollator(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

            // Parse unicode extension keys from locale tag (e.g. en-u-kn-true-co-phonebk)
            Map<String, String> unicodeExtensions = parseUnicodeExtensions(locale.toLanguageTag());

            // Read options in ECMA-402 spec order

            // 1. usage
            String usage = getOptionStringChecked(context, optionsValue, "usage");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            usage = normalizeOption(usage, "sort", "sort", "search");

            // 2. localeMatcher
            String localeMatcher = getOptionStringChecked(context, optionsValue, "localeMatcher");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("Invalid option value " + localeMatcher + " for property localeMatcher");
            }

            // 3. collation: resolve from option and/or extension per ECMA-402
            String optionCollation = getOptionStringChecked(context, optionsValue, "collation");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            String extensionCollation = unicodeExtensions.get("co");
            String collation;
            if (optionCollation != null) {
                if (isValidCollation(optionCollation) && isSupportedCollation(locale.getLanguage(), optionCollation)) {
                    collation = optionCollation;
                } else if (extensionCollation != null && isValidCollation(extensionCollation)
                        && isSupportedCollation(locale.getLanguage(), extensionCollation)) {
                    collation = extensionCollation;
                } else {
                    collation = "default";
                }
            } else if (extensionCollation != null && isValidCollation(extensionCollation)
                    && isSupportedCollation(locale.getLanguage(), extensionCollation)) {
                collation = extensionCollation;
            } else {
                collation = "default";
            }

            // 4. numeric: option overrides extension key "kn"
            boolean numericSet = false;
            boolean numeric = false;
            if (optionsValue instanceof JSObject optionsObject) {
                JSValue numericValue = optionsObject.get(context, PropertyKey.fromString("numeric"));
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (numericValue != null && !numericValue.isNullOrUndefined()) {
                    numeric = JSTypeConversions.toBoolean(numericValue).value();
                    numericSet = true;
                }
            }
            if (!numericSet && unicodeExtensions.containsKey("kn")) {
                String extNumeric = unicodeExtensions.get("kn");
                numeric = !"false".equals(extNumeric);
            }

            // 5. caseFirst: option overrides extension key "kf"
            String caseFirst = getOptionStringChecked(context, optionsValue, "caseFirst");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (caseFirst != null && !"upper".equals(caseFirst) && !"lower".equals(caseFirst) && !"false".equals(caseFirst)) {
                return context.throwRangeError("Invalid option value " + caseFirst + " for property caseFirst");
            }
            if (caseFirst == null && unicodeExtensions.containsKey("kf")) {
                String extCaseFirst = unicodeExtensions.get("kf");
                if ("upper".equals(extCaseFirst) || "lower".equals(extCaseFirst) || "false".equals(extCaseFirst)) {
                    caseFirst = extCaseFirst;
                }
            }
            if (caseFirst == null) {
                caseFirst = "false";
            }

            // 6. sensitivity - default depends on usage per ECMA-402 Table 4
            String sensitivity = getOptionStringChecked(context, optionsValue, "sensitivity");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            String sensitivityDefault = "search".equals(usage) ? "base" : "variant";
            sensitivity = normalizeOption(sensitivity, sensitivityDefault, "base", "accent", "case", "variant");

            // 7. ignorePunctuation - Thai locale defaults to true per ECMA-402
            boolean ignorePunctuation = "th".equals(locale.getLanguage());
            if (optionsValue instanceof JSObject optionsObject2) {
                JSValue ipValue = optionsObject2.get(context, PropertyKey.fromString("ignorePunctuation"));
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (ipValue != null && !ipValue.isNullOrUndefined()) {
                    ignorePunctuation = JSTypeConversions.toBoolean(ipValue).value();
                }
            }

            // Build resolved locale with applied unicode extensions
            Locale resolvedLocale = stripUnicodeExtensions(locale);
            String resolvedLocaleTag = buildResolvedLocaleTag(locale, collation, numeric, caseFirst,
                    unicodeExtensions);

            JSIntlCollator collator = new JSIntlCollator(resolvedLocale, sensitivity, usage, collation,
                    numeric, caseFirst, ignorePunctuation, resolvedLocaleTag);
            JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "Collator");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            collator.setPrototype(resolvedPrototype);
            return collator;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    /**
     * Default entry: uses required="any", defaults="date" per Intl.DateTimeFormat constructor spec.
     */
    public static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        return createDateTimeFormat(context, prototype, args, "any", "date");
    }

    /**
     * Create DateTimeFormat with configurable ToDateTimeOptions behavior.
     * @param required "date", "time", or "any" — which component types to check for needDefaults
     * @param defaults "date", "time", or "all" — which defaults to add when needDefaults is true
     */
    public static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args, String required, String defaults) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSValue rawOptions = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
            boolean optionsIsUndefined = rawOptions instanceof JSUndefined;

            // Convert options to Object per spec: undefined → null-prototype object, null → TypeError
            // Per ToDateTimeOptions: undefined → OrdinaryObjectCreate(null) to avoid Object.prototype pollution
            JSObject optionsObject;
            if (optionsIsUndefined) {
                optionsObject = context.createJSObject();
                optionsObject.setPrototype(null);
            } else if (rawOptions instanceof JSNull) {
                return context.throwTypeError("Cannot convert null to object");
            } else if (rawOptions instanceof JSObject obj) {
                optionsObject = obj;
            } else {
                // Primitive → ToObject (wraps as a wrapper object with no own properties)
                optionsObject = context.createJSObject();
            }

            // Read ALL options in ECMA-402 CreateDateTimeFormat spec order,
            // triggering getters and propagating exceptions.

            // 1. localeMatcher
            String localeMatcher = readStringOption(context, optionsObject, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("Invalid option value " + localeMatcher + " for property localeMatcher");
            }

            // 2. calendar
            String calendar = readStringOption(context, optionsObject, "calendar");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (calendar != null && !UNICODE_TYPE_PATTERN.matcher(calendar).matches()) {
                return context.throwRangeError("Invalid calendar: " + calendar);
            }
            if (calendar != null) {
                calendar = canonicalizeCalendar(calendar);
            }

            // 3. numberingSystem
            String numberingSystem = readStringOption(context, optionsObject, "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (numberingSystem != null && !UNICODE_TYPE_PATTERN.matcher(numberingSystem).matches()) {
                return context.throwRangeError("Invalid numberingSystem: " + numberingSystem);
            }

            // 4. hour12
            JSValue hour12Value = optionsObject.get(context, PropertyKey.fromString("hour12"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // 5. hourCycle
            String hourCycle = readStringOption(context, optionsObject, "hourCycle");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (hourCycle != null && !"h11".equals(hourCycle) && !"h12".equals(hourCycle)
                    && !"h23".equals(hourCycle) && !"h24".equals(hourCycle)) {
                return context.throwRangeError("Invalid hourCycle: " + hourCycle);
            }

            // 6. timeZone
            String timeZone = readStringOption(context, optionsObject, "timeZone");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (timeZone != null) {
                timeZone = validateAndCanonicalizeTimeZone(timeZone);
                if (timeZone == null) {
                    return context.throwRangeError("Invalid time zone");
                }
            } else {
                timeZone = ZoneId.systemDefault().getId();
            }

            // 7-15. Component options
            String weekdayOption = readValidatedOption(context, optionsObject, "weekday", "long", "short", "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String eraOption = readValidatedOption(context, optionsObject, "era", "long", "short", "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String yearOption = readValidatedOption(context, optionsObject, "year", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String monthOption = readValidatedOption(context, optionsObject, "month",
                    "numeric", "2-digit", "long", "short", "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String dayOption = readValidatedOption(context, optionsObject, "day", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String dayPeriodOption = readValidatedOption(context, optionsObject, "dayPeriod", "long", "short", "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String hourOption = readValidatedOption(context, optionsObject, "hour", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String minuteOption = readValidatedOption(context, optionsObject, "minute", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String secondOption = readValidatedOption(context, optionsObject, "second", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // 16. fractionalSecondDigits (GetNumberOption: ToNumber, range check, then floor)
            Integer fractionalSecondDigits = null;
            JSValue fsdValue = optionsObject.get(context, PropertyKey.fromString("fractionalSecondDigits"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (fsdValue != null && !(fsdValue instanceof JSUndefined)) {
                double fsdDouble = JSTypeConversions.toNumber(context, fsdValue).value();
                // DefaultNumberOption: check NaN and range BEFORE flooring
                if (Double.isNaN(fsdDouble) || fsdDouble < 1 || fsdDouble > 3) {
                    return context.throwRangeError("fractionalSecondDigits value is out of range.");
                }
                fractionalSecondDigits = (int) Math.floor(fsdDouble);
            }

            // 17. timeZoneName
            String timeZoneNameOption = readValidatedOption(context, optionsObject, "timeZoneName",
                    "long", "short", "longOffset", "shortOffset", "longGeneric", "shortGeneric");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // 18. formatMatcher
            String formatMatcher = readStringOption(context, optionsObject, "formatMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (formatMatcher != null && !"basic".equals(formatMatcher) && !"best fit".equals(formatMatcher)) {
                return context.throwRangeError("Invalid formatMatcher: " + formatMatcher);
            }

            // 19-20. dateStyle and timeStyle
            String dateStyleStr = readValidatedOption(context, optionsObject, "dateStyle",
                    "full", "long", "medium", "short");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String timeStyleStr = readValidatedOption(context, optionsObject, "timeStyle",
                    "full", "long", "medium", "short");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            FormatStyle dateStyle = parseFormatStyle(dateStyleStr);
            FormatStyle timeStyle = parseFormatStyle(timeStyleStr);

            // Check dateStyle/timeStyle conflict with explicit components
            if (dateStyle != null || timeStyle != null) {
                if (weekdayOption != null || eraOption != null || yearOption != null
                        || monthOption != null || dayOption != null || dayPeriodOption != null
                        || hourOption != null || minuteOption != null || secondOption != null
                        || fractionalSecondDigits != null || timeZoneNameOption != null) {
                    return context.throwTypeError("dateStyle and timeStyle may not be used with other DateTimeFormat options");
                }
            }

            // Apply defaults per ECMA-402 ToDateTimeOptions(options, required, defaults):
            // 'required' determines which component types to check for needDefaults.
            // 'defaults' determines which defaults to add when needDefaults is true.
            if (dateStyle == null && timeStyle == null) {
                boolean hasDateComponent = weekdayOption != null || eraOption != null
                        || yearOption != null || monthOption != null || dayOption != null;
                boolean hasTimeComponent = dayPeriodOption != null || hourOption != null
                        || minuteOption != null || secondOption != null
                        || fractionalSecondDigits != null || timeZoneNameOption != null;
                boolean needDefaults;
                if ("date".equals(required)) {
                    needDefaults = !hasDateComponent;
                } else if ("time".equals(required)) {
                    needDefaults = !hasTimeComponent;
                } else {
                    // "any"
                    needDefaults = !hasDateComponent && !hasTimeComponent;
                }
                if (needDefaults) {
                    if ("date".equals(defaults) || "all".equals(defaults)) {
                        yearOption = "numeric";
                        monthOption = "numeric";
                        dayOption = "numeric";
                    }
                    if ("time".equals(defaults) || "all".equals(defaults)) {
                        hourOption = "numeric";
                        minuteOption = "numeric";
                        secondOption = "numeric";
                    }
                }
            }

            // Resolve calendar from locale unicode extension if not specified in options
            Map<String, String> unicodeExtensions = parseUnicodeExtensions(locale.toLanguageTag());
            String calendarFromOption = calendar;
            // If option value is not a supported calendar, treat as if not specified
            if (calendarFromOption != null && !isSupportedCalendar(calendarFromOption)) {
                calendarFromOption = null;
                calendar = null;
            }
            if (calendar == null) {
                if (unicodeExtensions.containsKey("ca")) {
                    String extensionCalendar = canonicalizeCalendar(unicodeExtensions.get("ca"));
                    if (isSupportedCalendar(extensionCalendar)) {
                        calendar = extensionCalendar;
                    }
                }
            }
            if (calendar == null) {
                calendar = "gregory";
            }

            // Resolve numberingSystem
            String numberingSystemFromOption = numberingSystem;
            // If option value is not supported, treat as if not specified
            if (numberingSystemFromOption != null && !isSupportedDateTimeNumberingSystem(numberingSystemFromOption)) {
                numberingSystemFromOption = null;
                numberingSystem = null;
            }
            if (numberingSystem == null && unicodeExtensions.containsKey("nu")) {
                String localeNumberingSystem = unicodeExtensions.get("nu").toLowerCase(Locale.ROOT);
                if (isSupportedDateTimeNumberingSystem(localeNumberingSystem)) {
                    numberingSystem = localeNumberingSystem;
                }
            }

            // Resolve hourCycle per ECMA-402:
            // 1. If hour12 is not undefined, it takes precedence (sets hourCycle to null, resolved later)
            // 2. Else if hourCycle option is set, use it
            // 3. Else if locale has -u-hc- extension, use it
            // 4. Else use locale default
            Boolean hour12 = null;
            if (!(hour12Value instanceof JSUndefined) && hour12Value != null) {
                hour12 = JSTypeConversions.toBoolean(hour12Value).value();
                // hour12 takes precedence over hourCycle
                hourCycle = null;
            }

            String hourCycleFromOption = hourCycle;
            String hourCycleFromExtension = unicodeExtensions.containsKey("hc") ? unicodeExtensions.get("hc") : null;

            if (hourCycle == null && hour12 == null && hourCycleFromExtension != null) {
                if ("h11".equals(hourCycleFromExtension) || "h12".equals(hourCycleFromExtension)
                        || "h23".equals(hourCycleFromExtension) || "h24".equals(hourCycleFromExtension)) {
                    hourCycle = hourCycleFromExtension;
                }
            }

            // If hour12 is set, derive hourCycle from locale default
            if (hour12 != null) {
                if (hour12) {
                    // Prefer h12 for most locales, h11 for ja
                    String lang = locale.getLanguage();
                    hourCycle = "ja".equals(lang) ? "h11" : "h12";
                } else {
                    hourCycle = "h23";
                }
            }

            // Resolve the locale tag BEFORE modifying hourCycle for no-hour-component.
            // Per spec, ResolveLocale (step 9) runs before [[HourCycle]] nullification (step 32).
            // The pre-nullification hourCycle value determines which extensions appear in the locale tag.
            Locale resolvedLocale = buildResolvedLocale(locale, unicodeExtensions,
                    calendarFromOption, calendar,
                    hourCycleFromOption, hourCycle, hour12,
                    numberingSystemFromOption, numberingSystem);

            // Finalize hourCycle for the instance:
            // - If no hour component, set to null (step 32 of CreateDateTimeFormat)
            // - If still null (no option/extension/hour12), use locale default
            boolean hasHourComponent = hourOption != null || timeStyle != null;
            if (!hasHourComponent) {
                hourCycle = null;
            } else if (hourCycle == null) {
                hourCycle = getLocaleDefaultHourCycle(locale);
            }

            JSIntlDateTimeFormat dateTimeFormat = new JSIntlDateTimeFormat(
                    resolvedLocale, dateStyle, timeStyle, calendar, numberingSystem, timeZone,
                    hourCycle, weekdayOption, eraOption, yearOption, monthOption, dayOption,
                    dayPeriodOption, hourOption, minuteOption, secondOption, fractionalSecondDigits,
                    timeZoneNameOption);
            JSObject resolvedDtfPrototype = resolveIntlPrototype(context, prototype, "DateTimeFormat");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            dateTimeFormat.setPrototype(resolvedDtfPrototype);
            return dateTimeFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createListFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String style = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "style"),
                    "long",
                    "long", "short", "narrow");
            String type = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "type"),
                    "conjunction",
                    "conjunction", "disjunction", "unit");
            JSIntlListFormat listFormat = new JSIntlListFormat(locale, style, type);
            listFormat.setPrototype(prototype);
            return listFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createLocale(JSContext context, JSObject prototype, JSValue[] args) {
        if (args.length == 0 || args[0].isNullOrUndefined()) {
            return context.throwTypeError("Intl.Locale requires a locale argument");
        }
        try {
            String localeTag;
            if (args[0] instanceof JSIntlLocale inputLocale) {
                localeTag = inputLocale.getTag();
            } else {
                localeTag = JSTypeConversions.toString(context, args[0]).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            // Parse options (second argument)
            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

            // Read option values before any tag modification
            String calendarOpt = getOptionString(context, optionsValue, "calendar");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String caseFirstOpt = getOptionString(context, optionsValue, "caseFirst");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String collationOpt = getOptionString(context, optionsValue, "collation");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String hourCycleOpt = getOptionString(context, optionsValue, "hourCycle");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String numberingSystemOpt = getOptionString(context, optionsValue, "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String numericStr = getOptionString(context, optionsValue, "numeric");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String languageOpt = getOptionString(context, optionsValue, "language");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String scriptOpt = getOptionString(context, optionsValue, "script");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String regionOpt = getOptionString(context, optionsValue, "region");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            Locale locale = parseLocaleTag(localeTag);

            // Apply language/script/region overrides from options
            Locale.Builder builder = new Locale.Builder().setLocale(locale);
            if (languageOpt != null && !languageOpt.isEmpty()) {
                builder.setLanguage(languageOpt);
            }
            if (scriptOpt != null && !scriptOpt.isEmpty()) {
                builder.setScript(scriptOpt);
            }
            if (regionOpt != null && !regionOpt.isEmpty()) {
                builder.setRegion(regionOpt);
            }

            // Parse existing unicode extensions from locale tag
            Map<String, String> existingExtensions = parseUnicodeExtensions(locale.toLanguageTag());

            // Apply unicode extension options (options override tag extensions)
            String calendar = calendarOpt != null ? calendarOpt : existingExtensions.get("ca");
            String caseFirst = caseFirstOpt != null ? caseFirstOpt : existingExtensions.get("kf");
            String collation = collationOpt != null ? collationOpt : existingExtensions.get("co");
            String hourCycle = hourCycleOpt != null ? hourCycleOpt : existingExtensions.get("hc");
            String numberingSystem = numberingSystemOpt != null ? numberingSystemOpt : existingExtensions.get("nu");
            boolean numericSet = false;
            boolean numeric = false;
            if (numericStr != null) {
                numeric = "true".equals(numericStr);
                numericSet = true;
            } else {
                String knValue = existingExtensions.get("kn");
                if (knValue != null) {
                    numeric = "true".equals(knValue) || knValue.isEmpty();
                    numericSet = true;
                }
            }

            // Build unicode extension string
            StringBuilder uExt = new StringBuilder();
            if (calendar != null) {
                uExt.append("-ca-").append(calendar);
            }
            if (collation != null) {
                uExt.append("-co-").append(collation);
            }
            if (hourCycle != null) {
                uExt.append("-hc-").append(hourCycle);
            }
            if (caseFirst != null) {
                uExt.append("-kf-").append(caseFirst);
            }
            if (numericSet) {
                uExt.append("-kn-").append(numeric ? "true" : "false");
            }
            if (numberingSystem != null) {
                uExt.append("-nu-").append(numberingSystem);
            }
            if (!uExt.isEmpty()) {
                builder.setExtension('u', uExt.substring(1)); // remove leading '-'
            }

            locale = builder.build();
            String tag = canonicalizeLocaleTag(locale.toLanguageTag());

            JSIntlLocale intlLocale = new JSIntlLocale(locale, tag,
                    calendar, caseFirst, collation, hourCycle, numberingSystem,
                    numeric, numericSet);
            intlLocale.setPrototype(prototype);
            return intlLocale;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createNumberFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

            // Validate localeMatcher option
            String localeMatcher = getOptionStringChecked(context, optionsValue, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("Value " + localeMatcher + " out of range for Intl.NumberFormat options property localeMatcher");
            }

            String style = getOptionString(context, optionsValue, "style");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            // Accept "unit" and other styles
            if (style == null || style.isBlank()) {
                style = "decimal";
            } else if (!"decimal".equals(style) && !"currency".equals(style) && !"percent".equals(style) && !"unit".equals(style)) {
                return context.throwRangeError("Invalid option value: " + style);
            }

            String currency = getOptionString(context, optionsValue, "currency");

            // Parse unit and unitDisplay for unit style
            String unit = getOptionString(context, optionsValue, "unit");
            if ("unit".equals(style) && (unit == null || unit.isBlank())) {
                return context.throwRangeError("Unit is required with unit style");
            }
            String unitDisplay = getOptionString(context, optionsValue, "unitDisplay");
            if (unitDisplay == null) {
                unitDisplay = "short";
            }

            // Parse signDisplay
            String signDisplay = getOptionString(context, optionsValue, "signDisplay");
            if (signDisplay == null) {
                signDisplay = "auto";
            }

            // Parse roundingMode
            String roundingMode = getOptionString(context, optionsValue, "roundingMode");
            if (roundingMode == null) {
                roundingMode = "halfExpand";
            }

            // Parse numberingSystem
            String numberingSystem = getOptionString(context, optionsValue, "numberingSystem");
            if (numberingSystem != null && !SUPPORTED_NUMBERING_SYSTEMS.contains(numberingSystem)) {
                numberingSystem = null;
            }

            // Validate currency: must be 3 ASCII letter ISO 4217 code
            if (currency != null) {
                if (currency.length() != 3 || !currency.chars().allMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) {
                    return context.throwRangeError("Invalid currency code: " + currency);
                }
            }
            if ("currency".equals(style) && currency == null) {
                throw new IllegalArgumentException("Currency code is required with currency style");
            }

            // Parse useGrouping option
            boolean useGrouping = true;
            if (optionsValue instanceof JSObject optionsObject) {
                JSValue useGroupingValue = optionsObject.get(context, PropertyKey.fromString("useGrouping"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (useGroupingValue != null && !(useGroupingValue instanceof JSUndefined)) {
                    useGrouping = JSTypeConversions.toBoolean(useGroupingValue).value();
                }
            }

            // Parse minimumIntegerDigits
            int minimumIntegerDigits = 1;
            if (optionsValue instanceof JSObject optionsObject) {
                JSValue midValue = optionsObject.get(context, PropertyKey.fromString("minimumIntegerDigits"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (midValue != null && !(midValue instanceof JSUndefined)) {
                    double midDouble = JSTypeConversions.toNumber(context, midValue).value();
                    if (Double.isNaN(midDouble) || midDouble < 1 || midDouble > 21) {
                        return context.throwRangeError("minimumIntegerDigits value is out of range");
                    }
                    minimumIntegerDigits = (int) midDouble;
                }
            }

            // Parse minimumFractionDigits
            int minimumFractionDigits = -1;
            if (optionsValue instanceof JSObject optionsObject) {
                JSValue mfdValue = optionsObject.get(context, PropertyKey.fromString("minimumFractionDigits"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (mfdValue != null && !(mfdValue instanceof JSUndefined)) {
                    double mfdDouble = JSTypeConversions.toNumber(context, mfdValue).value();
                    if (Double.isNaN(mfdDouble) || mfdDouble < 0 || mfdDouble > 100) {
                        return context.throwRangeError("minimumFractionDigits value is out of range");
                    }
                    minimumFractionDigits = (int) mfdDouble;
                }
            }

            // Parse maximumFractionDigits
            int maximumFractionDigits = -1;
            if (optionsValue instanceof JSObject optionsObject) {
                JSValue maxFdValue = optionsObject.get(context, PropertyKey.fromString("maximumFractionDigits"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (maxFdValue != null && !(maxFdValue instanceof JSUndefined)) {
                    double maxFdDouble = JSTypeConversions.toNumber(context, maxFdValue).value();
                    if (Double.isNaN(maxFdDouble) || maxFdDouble < 0 || maxFdDouble > 100) {
                        return context.throwRangeError("maximumFractionDigits value is out of range");
                    }
                    maximumFractionDigits = (int) maxFdDouble;
                }
            }

            // Parse maximumSignificantDigits
            boolean useSignificantDigits = false;
            int maximumSignificantDigits = 0;
            if (optionsValue instanceof JSObject optionsObject) {
                JSValue msdValue = optionsObject.get(context, PropertyKey.fromString("maximumSignificantDigits"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (msdValue != null && !(msdValue instanceof JSUndefined)) {
                    double msdDouble = JSTypeConversions.toNumber(context, msdValue).value();
                    if (Double.isNaN(msdDouble) || Double.isInfinite(msdDouble) || msdDouble < 1 || msdDouble > 21) {
                        return context.throwRangeError("maximumSignificantDigits value is out of range");
                    }
                    maximumSignificantDigits = (int) msdDouble;
                    useSignificantDigits = true;
                }
            }

            JSIntlNumberFormat numberFormat = new JSIntlNumberFormat(locale, style, currency,
                    useGrouping, minimumIntegerDigits, minimumFractionDigits, maximumFractionDigits,
                    useSignificantDigits, maximumSignificantDigits,
                    unit, unitDisplay, signDisplay, roundingMode, numberingSystem);
            numberFormat.setPrototype(prototype);
            return numberFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createPluralRules(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String type = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "type"),
                    "cardinal",
                    "cardinal", "ordinal");
            JSIntlPluralRules pluralRules = new JSIntlPluralRules(locale, type);
            pluralRules.setPrototype(prototype);
            return pluralRules;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createRelativeTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
            String style = normalizeOption(
                    getOptionString(context, optionsValue, "style"),
                    "long",
                    "long", "short", "narrow");
            String numeric = normalizeOption(
                    getOptionString(context, optionsValue, "numeric"),
                    "always",
                    "always", "auto");
            String numberingSystem = getOptionString(context, optionsValue, "numberingSystem");
            if (numberingSystem != null && !SUPPORTED_NUMBERING_SYSTEMS.contains(numberingSystem)) {
                numberingSystem = null;
            }
            JSIntlRelativeTimeFormat relativeTimeFormat = new JSIntlRelativeTimeFormat(
                    locale, style, numeric, numberingSystem);
            relativeTimeFormat.setPrototype(prototype);
            return relativeTimeFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue dateTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.format called on incompatible receiver");
        }
        double epochMillis = toDateTimeEpochMillis(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE, true);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(dateTimeFormat.format(epochMillis));
    }

    /**
     * Getter for Intl.DateTimeFormat.prototype.format.
     */
    public static JSValue dateTimeFormatFormatGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.format called on incompatible receiver");
        }
        JSFunction cachedBoundFormatFunction = dateTimeFormat.getBoundFormatFunction();
        if (cachedBoundFormatFunction != null) {
            return cachedBoundFormatFunction;
        }
        JSNativeFunction boundFormatFunction = new JSNativeFunction("", 1,
                (childContext, thisValue, formatArgs) -> dateTimeFormatFormat(childContext, dateTimeFormat, formatArgs));
        context.transferPrototype(boundFormatFunction, JSFunction.NAME);
        dateTimeFormat.setBoundFormatFunction(boundFormatFunction);
        return boundFormatFunction;
    }

    /**
     * Intl.DateTimeFormat.prototype.formatToParts(date)
     */
    public static JSValue dateTimeFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.formatToParts called on incompatible receiver");
        }
        double dateValue = toDateTimeEpochMillis(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE, true);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        List<JSIntlDateTimeFormat.DatePart> partsList = dateTimeFormat.formatToPartsList(dateValue);
        return datePartsToJSArray(context, partsList);
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRange(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.formatRange called on incompatible receiver");
        }
        JSValue startArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue endArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (startArg instanceof JSUndefined || endArg instanceof JSUndefined) {
            return context.throwTypeError("start date or end date is undefined");
        }
        double startDate = toDateTimeEpochMillis(context, startArg, false);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        double endDate = toDateTimeEpochMillis(context, endArg, false);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (Double.compare(startDate, endDate) == 0) {
            return new JSString(dateTimeFormat.format(startDate));
        }

        List<JSIntlDateTimeFormat.DatePart> startParts = dateTimeFormat.formatToPartsList(startDate);
        List<JSIntlDateTimeFormat.DatePart> endParts = dateTimeFormat.formatToPartsList(endDate);

        StringBuilder resultBuilder = new StringBuilder();
        if (startParts.equals(endParts)) {
            for (JSIntlDateTimeFormat.DatePart startPart : startParts) {
                resultBuilder.append(startPart.value());
            }
            return new JSString(resultBuilder.toString());
        }

        boolean isYearSame = true;
        int comparablePartCount = Math.min(startParts.size(), endParts.size());
        for (int index = 0; index < comparablePartCount; index++) {
            JSIntlDateTimeFormat.DatePart startPart = startParts.get(index);
            JSIntlDateTimeFormat.DatePart endPart = endParts.get(index);
            if ("year".equals(startPart.type()) && !startPart.equals(endPart)) {
                isYearSame = false;
                break;
            }
        }
        boolean canCollapse = dateTimeFormat.hasTextMonth() && isYearSame && startParts.size() == endParts.size();
        if (canCollapse) {
            int firstDifferenceIndex = -1;
            int lastDifferenceIndex = -1;
            for (int index = 0; index < startParts.size(); index++) {
                if (!startParts.get(index).equals(endParts.get(index))) {
                    if (firstDifferenceIndex < 0) {
                        firstDifferenceIndex = index;
                    }
                    lastDifferenceIndex = index;
                }
            }
            if (firstDifferenceIndex >= 0) {
                for (int index = 0; index < firstDifferenceIndex; index++) {
                    resultBuilder.append(startParts.get(index).value());
                }
                for (int index = firstDifferenceIndex; index <= lastDifferenceIndex; index++) {
                    resultBuilder.append(startParts.get(index).value());
                }
                resultBuilder.append(" \u2013 ");
                for (int index = firstDifferenceIndex; index <= lastDifferenceIndex; index++) {
                    resultBuilder.append(endParts.get(index).value());
                }
                for (int index = lastDifferenceIndex + 1; index < startParts.size(); index++) {
                    resultBuilder.append(startParts.get(index).value());
                }
                return new JSString(resultBuilder.toString());
            }
        }

        for (JSIntlDateTimeFormat.DatePart startPart : startParts) {
            resultBuilder.append(startPart.value());
        }
        resultBuilder.append(" \u2013 ");
        for (JSIntlDateTimeFormat.DatePart endPart : endParts) {
            resultBuilder.append(endPart.value());
        }
        return new JSString(resultBuilder.toString());
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRangeToParts(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.formatRangeToParts called on incompatible receiver");
        }

        // Per ECMA-402: If startDate is undefined or endDate is undefined, throw TypeError
        JSValue startArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue endArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (startArg instanceof JSUndefined || endArg instanceof JSUndefined) {
            return context.throwTypeError("start date or end date is undefined");
        }

        double startDate = toDateTimeEpochMillis(context, startArg, false);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        double endDate = toDateTimeEpochMillis(context, endArg, false);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        List<JSIntlDateTimeFormat.DatePart> startParts = dateTimeFormat.formatToPartsList(startDate);
        List<JSIntlDateTimeFormat.DatePart> endParts = dateTimeFormat.formatToPartsList(endDate);

        JSArray result = context.createJSArray();
        // Check if dates are practically equal (all parts identical)
        if (startParts.equals(endParts)) {
            for (JSIntlDateTimeFormat.DatePart part : startParts) {
                result.push(createPartObject(context, part.type(), part.value(), "shared"));
            }
            return result;
        }

        // Determine the range of differing parts for collapsed formatting.
        // For text-based formats (short/long month), collapse shared parts
        // but only when the year values are the same (per CLDR range patterns).
        boolean yearSame = true;
        for (int i = 0; i < startParts.size(); i++) {
            if ("year".equals(startParts.get(i).type()) && !startParts.get(i).equals(endParts.get(i))) {
                yearSame = false;
                break;
            }
        }
        boolean canCollapse = dateTimeFormat.hasTextMonth() && yearSame;
        if (canCollapse && startParts.size() == endParts.size()) {
            int firstDiff = -1;
            int lastDiff = -1;
            for (int i = 0; i < startParts.size(); i++) {
                if (!startParts.get(i).equals(endParts.get(i))) {
                    if (firstDiff < 0) {
                        firstDiff = i;
                    }
                    lastDiff = i;
                }
            }
            if (firstDiff >= 0) {
                // Shared prefix
                for (int i = 0; i < firstDiff; i++) {
                    result.push(createPartObject(context, startParts.get(i).type(),
                            startParts.get(i).value(), "shared"));
                }
                // Start range parts
                for (int i = firstDiff; i <= lastDiff; i++) {
                    result.push(createPartObject(context, startParts.get(i).type(),
                            startParts.get(i).value(), "startRange"));
                }
                // Range separator
                result.push(createPartObject(context, "literal", " \u2013 ", "shared"));
                // End range parts
                for (int i = firstDiff; i <= lastDiff; i++) {
                    result.push(createPartObject(context, endParts.get(i).type(),
                            endParts.get(i).value(), "endRange"));
                }
                // Shared suffix
                for (int i = lastDiff + 1; i < startParts.size(); i++) {
                    result.push(createPartObject(context, startParts.get(i).type(),
                            startParts.get(i).value(), "shared"));
                }
                return result;
            }
        }

        // Full range: all start parts as startRange, separator, all end parts as endRange
        for (JSIntlDateTimeFormat.DatePart part : startParts) {
            result.push(createPartObject(context, part.type(), part.value(), "startRange"));
        }
        result.push(createPartObject(context, "literal", " \u2013 ", "shared"));
        for (JSIntlDateTimeFormat.DatePart part : endParts) {
            result.push(createPartObject(context, part.type(), part.value(), "endRange"));
        }
        return result;
    }

    private static double toDateTimeEpochMillis(JSContext context, JSValue value, boolean useCurrentTimeForUndefined) {
        if (useCurrentTimeForUndefined && value instanceof JSUndefined) {
            return System.currentTimeMillis();
        }

        double epochMillis;
        if (value instanceof JSDate jsDate) {
            epochMillis = jsDate.getTimeValue();
        } else {
            epochMillis = JSTypeConversions.toNumber(context, value).value();
            if (context.hasPendingException()) {
                return Double.NaN;
            }
        }

        if (!Double.isFinite(epochMillis) || Math.abs(epochMillis) > 8.64e15) {
            context.throwRangeError("Invalid time value");
            return Double.NaN;
        }
        return epochMillis;
    }

    private static JSObject createPartObject(JSContext context, String type, String value, String source) {
        JSObject part = context.createJSObject();
        part.set("type", new JSString(type));
        part.set("value", new JSString(value));
        part.set("source", new JSString(source));
        return part;
    }

    private static JSValue datePartsToJSArray(JSContext context, List<JSIntlDateTimeFormat.DatePart> parts) {
        JSArray array = context.createJSArray();
        for (JSIntlDateTimeFormat.DatePart part : parts) {
            JSObject partObj = context.createJSObject();
            partObj.set("type", new JSString(part.type()));
            partObj.set("value", new JSString(part.value()));
            array.push(partObj);
        }
        return array;
    }

    public static JSValue dateTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();

        // Property order per ECMA-402: locale, calendar, numberingSystem, timeZone,
        // hourCycle, hour12, weekday, era, year, month, day, dayPeriod,
        // hour, minute, second, fractionalSecondDigits, timeZoneName, dateStyle, timeStyle
        resolvedOptions.set("locale", new JSString(dateTimeFormat.getLocale().toLanguageTag()));
        if (dateTimeFormat.getCalendar() != null) {
            resolvedOptions.set("calendar", new JSString(dateTimeFormat.getCalendar()));
        }
        if (dateTimeFormat.getNumberingSystem() != null) {
            resolvedOptions.set("numberingSystem", new JSString(dateTimeFormat.getNumberingSystem()));
        } else {
            resolvedOptions.set("numberingSystem", new JSString("latn"));
        }
        resolvedOptions.set("timeZone", new JSString(dateTimeFormat.getTimeZone() != null
                ? dateTimeFormat.getTimeZone() : ZoneId.systemDefault().getId()));

        // hourCycle and hour12: only present when hour component is in use
        String hourCycle = dateTimeFormat.getHourCycle();
        if (hourCycle != null) {
            resolvedOptions.set("hourCycle", new JSString(hourCycle));
            boolean isHour12 = "h11".equals(hourCycle) || "h12".equals(hourCycle);
            resolvedOptions.set("hour12", isHour12 ? JSBoolean.TRUE : JSBoolean.FALSE);
        }

        // When dateStyle or timeStyle is set, individual component options are NOT reported
        boolean hasDateStyle = dateTimeFormat.getDateStyle() != null;
        boolean hasTimeStyle = dateTimeFormat.getTimeStyle() != null;

        if (!hasDateStyle && !hasTimeStyle) {
            // Component options only when not using styles
            if (dateTimeFormat.getWeekdayOption() != null) {
                resolvedOptions.set("weekday", new JSString(dateTimeFormat.getWeekdayOption()));
            }
            if (dateTimeFormat.getEraOption() != null) {
                resolvedOptions.set("era", new JSString(dateTimeFormat.getEraOption()));
            }
            if (dateTimeFormat.getYearOption() != null) {
                resolvedOptions.set("year", new JSString(dateTimeFormat.getYearOption()));
            }
            if (dateTimeFormat.getMonthOption() != null) {
                resolvedOptions.set("month", new JSString(dateTimeFormat.getMonthOption()));
            }
            if (dateTimeFormat.getDayOption() != null) {
                resolvedOptions.set("day", new JSString(dateTimeFormat.getDayOption()));
            }
            if (dateTimeFormat.getDayPeriodOption() != null) {
                resolvedOptions.set("dayPeriod", new JSString(dateTimeFormat.getDayPeriodOption()));
            }
            if (dateTimeFormat.getHourOption() != null) {
                resolvedOptions.set("hour", new JSString(dateTimeFormat.getHourOption()));
            }
            if (dateTimeFormat.getMinuteOption() != null) {
                resolvedOptions.set("minute", new JSString(dateTimeFormat.getMinuteOption()));
            }
            if (dateTimeFormat.getSecondOption() != null) {
                resolvedOptions.set("second", new JSString(dateTimeFormat.getSecondOption()));
            }
            if (dateTimeFormat.getFractionalSecondDigits() != null) {
                resolvedOptions.set("fractionalSecondDigits", JSNumber.of(dateTimeFormat.getFractionalSecondDigits()));
            }
            if (dateTimeFormat.getTimeZoneNameOption() != null) {
                resolvedOptions.set("timeZoneName", new JSString(dateTimeFormat.getTimeZoneNameOption()));
            }
        }
        if (hasDateStyle) {
            resolvedOptions.set("dateStyle",
                    new JSString(dateTimeFormat.getDateStyle().name().toLowerCase(Locale.ROOT)));
        }
        if (hasTimeStyle) {
            resolvedOptions.set("timeStyle",
                    new JSString(dateTimeFormat.getTimeStyle().name().toLowerCase(Locale.ROOT)));
        }
        return resolvedOptions;
    }

    public static JSValue getCanonicalLocales(JSContext context, JSValue thisArg, JSValue[] args) {
        try {
            List<String> localeList = canonicalizeLocaleList(
                    context,
                    args.length > 0 ? args[0] : JSUndefined.INSTANCE);
            JSArray localesArray = context.createJSArray();
            for (String localeTag : localeList) {
                localesArray.push(new JSString(localeTag));
            }
            return localesArray;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    /**
     * Intl.supportedValuesOf(key)
     * Returns a sorted array of supported unique values for the given key.
     * See ECMA-402 §8.3.2
     */
    public static JSValue supportedValuesOf_Intl(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwRangeError("invalid key: undefined");
        }
        String key = JSTypeConversions.toString(context, args[0]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        List<String> values;
        switch (key) {
            case "calendar" -> {
                // Per ECMA-402 and calendars-required-by-intl-era-monthcode test
                // "islamic" and "islamic-rgsa" are excluded because DateTimeFormat
                // resolves them to "islamic-civil" (they are not canonical calendar IDs)
                values = new ArrayList<>(List.of(
                        "buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic",
                        "gregory", "hebrew", "indian", "islamic-civil",
                        "islamic-tbla", "islamic-umalqura", "iso8601",
                        "japanese", "persian", "roc"
                ));
            }
            case "collation" -> {
                // Per ECMA-402: must NOT include "standard" or "search"
                // "compat" not accepted by any major Collator implementation
                values = new ArrayList<>(List.of(
                        "big5han", "dict", "direct", "ducet", "emoji", "eor",
                        "gb2312", "phonebk", "phonetic", "pinyin", "reformed",
                        "searchjl", "stroke", "trad", "unihan", "zhuyin"
                ));
            }
            case "currency" -> {
                Set<Currency> currencies = Currency.getAvailableCurrencies();
                values = currencies.stream()
                        .map(Currency::getCurrencyCode)
                        .sorted()
                        .collect(Collectors.toList());
            }
            case "numberingSystem" -> {
                values = new ArrayList<>(SUPPORTED_NUMBERING_SYSTEMS);
                Collections.sort(values);
            }
            case "timeZone" -> {
                Set<String> zoneIds = ZoneId.getAvailableZoneIds();
                // IANA canonical timezone names: include Etc/GMT+N, Etc/GMT-N, UTC
                // Exclude SystemV/, EST/MST/HST (deprecated short names), and non-canonical aliases
                Set<String> nonCanonical = Set.of(
                        "EST", "MST", "HST", "GMT0", "Etc/GMT0", "Etc/Greenwich",
                        "Etc/UCT", "Etc/Universal", "Etc/Zulu", "UCT", "GMT-0",
                        "GMT+0", "Universal", "Zulu", "Greenwich", "Etc/GMT-0", "Etc/GMT+0",
                        "Etc/GMT", "Etc/UTC", "GMT",
                        "US/Alaska", "US/Aleutian", "US/Arizona", "US/Central",
                        "US/East-Indiana", "US/Eastern", "US/Hawaii", "US/Indiana-Starke",
                        "US/Michigan", "US/Mountain", "US/Pacific", "US/Samoa",
                        "Canada/Atlantic", "Canada/Central", "Canada/Eastern",
                        "Canada/Mountain", "Canada/Newfoundland", "Canada/Pacific",
                        "Canada/Saskatchewan", "Canada/Yukon",
                        "Australia/ACT", "Australia/Canberra", "Australia/LHI",
                        "Australia/NSW", "Australia/North", "Australia/Queensland",
                        "Australia/South", "Australia/Tasmania", "Australia/Victoria",
                        "Australia/West", "Australia/Yancowinna",
                        "Brazil/Acre", "Brazil/DeNoronha", "Brazil/East", "Brazil/West",
                        "Chile/Continental", "Chile/EasterIsland",
                        "Mexico/BajaNorte", "Mexico/BajaSur", "Mexico/General",
                        "NZ", "NZ-CHAT", "Navajo", "PRC", "ROC", "ROK", "W-SU",
                        "Cuba", "Egypt", "Eire", "Hongkong", "Iceland", "Iran", "Israel",
                        "Jamaica", "Japan", "Kwajalein", "Libya", "Poland", "Portugal",
                        "Singapore", "Turkey"
                );
                values = zoneIds.stream()
                        .filter(id -> !id.startsWith("SystemV/")
                                && !nonCanonical.contains(id)
                                && !id.equals("EST5EDT") && !id.equals("CST6CDT")
                                && !id.equals("MST7MDT") && !id.equals("PST8PDT"))
                        .sorted()
                        .collect(Collectors.toList());
            }
            case "unit" -> {
                values = new ArrayList<>(List.of(
                        "acre", "bit", "byte", "celsius", "centimeter", "day",
                        "degree", "fahrenheit", "fluid-ounce", "foot", "gallon",
                        "gigabit", "gigabyte", "gram", "hectare", "hour", "inch",
                        "kilobit", "kilobyte", "kilogram", "kilometer", "liter",
                        "megabit", "megabyte", "meter", "microsecond", "mile",
                        "mile-scandinavian", "milliliter", "millimeter", "millisecond",
                        "minute", "month", "nanosecond", "ounce", "percent", "petabyte",
                        "pound", "second", "stone", "terabit", "terabyte", "week",
                        "yard", "year"
                ));
            }
            default -> {
                return context.throwRangeError("Invalid key : " + key);
            }
        }
        JSArray result = context.createJSArray();
        for (String value : values) {
            result.push(new JSString(value));
        }
        return result;
    }

    /**
     * Intl.DisplayNames constructor factory.
     * Per ECMA-402 §12.1.1: OrdinaryCreateFromConstructor (step 2) runs before
     * locale/options processing. Options are read in order:
     * localeMatcher → style → type → fallback → languageDisplay
     */
    public static JSValue createDisplayNames(JSContext context, JSObject prototype, JSValue[] args) {
        // Step 2: OrdinaryCreateFromConstructor — resolve prototype from NewTarget FIRST
        JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "DisplayNames");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Step 3: CanonicalizeLocaleList
        Locale locale = resolveLocale(context, args, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Step 4: Get options object (required, must be an object per GetOptionsObject)
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (optionsArg instanceof JSUndefined) {
            return context.throwTypeError("options must be an object");
        }
        if (!(optionsArg instanceof JSObject options)) {
            return context.throwTypeError("Cannot convert " + JSTypeConversions.toString(context, optionsArg).value() + " to object");
        }

        // localeMatcher
        String localeMatcher = getOptionStringChecked(context, options, "localeMatcher");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (localeMatcher != null) {
            if (!"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("localeMatcher must be \"lookup\" or \"best fit\"");
            }
        }

        // style (default: "long")
        String style = getOptionStringChecked(context, options, "style");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (style == null) {
            style = "long";
        } else if (!"narrow".equals(style) && !"short".equals(style) && !"long".equals(style)) {
            return context.throwRangeError("style must be \"narrow\", \"short\", or \"long\"");
        }

        // type (required — TypeError if undefined)
        String type = getOptionStringChecked(context, options, "type");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (type == null) {
            return context.throwTypeError("type is required");
        }
        if (!"language".equals(type) && !"region".equals(type)
                && !"script".equals(type) && !"currency".equals(type)
                && !"calendar".equals(type) && !"dateTimeField".equals(type)) {
            return context.throwRangeError("type must be \"language\", \"region\", \"script\", \"currency\", \"calendar\", or \"dateTimeField\"");
        }

        // fallback (default: "code")
        String fallback = getOptionStringChecked(context, options, "fallback");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (fallback == null) {
            fallback = "code";
        } else if (!"code".equals(fallback) && !"none".equals(fallback)) {
            return context.throwRangeError("fallback must be \"code\" or \"none\"");
        }

        // languageDisplay (default: "dialect")
        String languageDisplay = getOptionStringChecked(context, options, "languageDisplay");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (languageDisplay == null) {
            languageDisplay = "dialect";
        } else if (!"dialect".equals(languageDisplay) && !"standard".equals(languageDisplay)) {
            return context.throwRangeError("languageDisplay must be \"dialect\" or \"standard\"");
        }

        // Create instance and set prototype
        JSIntlDisplayNames displayNames = new JSIntlDisplayNames(locale, style, type, fallback, languageDisplay);
        if (resolvedPrototype != null) {
            displayNames.setPrototype(resolvedPrototype);
        }
        return displayNames;
    }

    /**
     * Intl.DisplayNames.prototype.of(code)
     */
    public static JSValue displayNamesOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDisplayNames displayNames)) {
            return context.throwTypeError("Intl.DisplayNames.prototype.of called on incompatible receiver");
        }
        if (args.length == 0) {
            return context.throwRangeError("invalid code for DisplayNames: undefined");
        }
        String code = JSTypeConversions.toString(context, args[0]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue result = displayNames.of(context, code);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return result;
    }

    /**
     * Intl.DisplayNames.prototype.resolvedOptions()
     * Returns a new object with properties in the specified order.
     */
    public static JSValue displayNamesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDisplayNames displayNames)) {
            return context.throwTypeError("Intl.DisplayNames.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject result = context.createJSObject();
        result.set("locale", new JSString(displayNames.getLocale().toLanguageTag()));
        result.set("style", new JSString(displayNames.getStyle()));
        result.set("type", new JSString(displayNames.getType()));
        result.set("fallback", new JSString(displayNames.getFallback()));
        if ("language".equals(displayNames.getType())) {
            result.set("languageDisplay", new JSString(displayNames.getLanguageDisplay()));
        }
        return result;
    }

    // =========================================================================
    // Intl.NumberFormat.prototype.formatToParts
    // =========================================================================

    public static JSValue numberFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.formatToParts called on incompatible receiver");
        }
        if (args.length == 0) {
            return numberFormat.formatToParts(context, Double.NaN);
        }
        JSValue arg = args[0];
        if (arg instanceof JSString str) {
            return numberFormat.formatToParts(context, str.value());
        }
        double numValue = JSTypeConversions.toNumber(context, arg).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return numberFormat.formatToParts(context, numValue);
    }

    // =========================================================================
    // Intl.ListFormat.prototype.formatToParts
    // =========================================================================

    public static JSValue listFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.formatToParts called on incompatible receiver");
        }
        List<String> values = new ArrayList<>();
        if (args.length > 0 && args[0] instanceof JSArray arr) {
            for (int i = 0; i < arr.getLength(); i++) {
                JSValue item = arr.get(context, PropertyKey.fromIndex(i));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                values.add(JSTypeConversions.toString(context, item).value());
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
        }
        List<JSIntlDurationFormat.ListFormatPart> parts = JSIntlDurationFormat.formatListToParts(listFormat, values);
        JSArray result = context.createJSArray();
        for (int i = 0; i < parts.size(); i++) {
            JSIntlDurationFormat.ListFormatPart part = parts.get(i);
            JSObject partObj = context.createJSObject();
            partObj.set("type", new JSString(part.type()));
            partObj.set("value", new JSString(part.value()));
            result.set(context, i, partObj);
        }
        return result;
    }

    // =========================================================================
    // Intl.DurationFormat
    // =========================================================================

    /**
     * Intl.DurationFormat constructor.
     */
    public static JSValue createDurationFormat(JSContext context, JSObject prototype, JSValue[] args) {
        JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "DurationFormat");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Resolve locale
        Locale locale = resolveLocale(context, args, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Get options object (optional, defaults to undefined)
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSObject options = null;
        if (optionsArg instanceof JSObject opt) {
            options = opt;
        } else if (!(optionsArg instanceof JSUndefined)) {
            return context.throwTypeError("Options is not an object");
        }

        // Read localeMatcher (not in resolvedOptions, but read from options)
        if (options != null) {
            String localeMatcher = getOptionStringChecked(context, options, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (localeMatcher != null) {
                if (!"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                    return context.throwRangeError("localeMatcher must be \"lookup\" or \"best fit\"");
                }
            }
        }

        // Read numberingSystem from options
        String numberingSystemOption = options != null ? getOptionStringChecked(context, options, "numberingSystem") : null;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (numberingSystemOption != null && !UNICODE_TYPE_PATTERN.matcher(numberingSystemOption).matches()) {
            return context.throwRangeError("Invalid numberingSystem: " + numberingSystemOption);
        }

        // Parse unicode extension "nu" from locale
        Map<String, String> unicodeExtensions = parseUnicodeExtensions(locale.toLanguageTag());
        String extensionNu = unicodeExtensions.get("nu");

        // Resolve numberingSystem: options > unicode extension > default
        String numberingSystem;
        boolean useExtensionInLocale = false;
        if (numberingSystemOption != null && SUPPORTED_NUMBERING_SYSTEMS.contains(numberingSystemOption)) {
            numberingSystem = numberingSystemOption;
            // Extension is reflected in locale only if it matches the option
            if (numberingSystem.equals(extensionNu)) {
                useExtensionInLocale = true;
            }
        } else if (extensionNu != null && SUPPORTED_NUMBERING_SYSTEMS.contains(extensionNu)) {
            numberingSystem = extensionNu;
            useExtensionInLocale = true;
        } else {
            numberingSystem = "latn";
        }

        // Build resolved locale: strip existing unicode extensions and re-add if needed
        Locale strippedLocale = stripUnicodeExtensions(locale);
        if (useExtensionInLocale) {
            locale = new Locale.Builder().setLocale(strippedLocale)
                    .setUnicodeLocaleKeyword("nu", numberingSystem).build();
        } else {
            locale = strippedLocale;
        }

        // Read style (default "short")
        String style = options != null ? getOptionStringChecked(context, options, "style") : null;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (style == null) {
            style = "short";
        } else if (!"long".equals(style) && !"short".equals(style) && !"narrow".equals(style) && !"digital".equals(style)) {
            return context.throwRangeError("Value " + style + " out of range for Intl.DurationFormat options property style");
        }

        // Process unit options
        String prevStyle = null;
        String[] unitStyles = new String[10];
        String[] unitDisplays = new String[10];

        for (int i = 0; i < JSIntlDurationFormat.UNIT_NAMES.length; i++) {
            String unitName = JSIntlDurationFormat.UNIT_NAMES[i];

            // Read unit style from options
            String unitStyle = options != null ? getOptionStringChecked(context, options, unitName) : null;
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Track whether the unit style was explicitly provided
            boolean unitStyleExplicit = (unitStyle != null);

            // Read unit display from options
            String display = options != null ? getOptionStringChecked(context, options, unitName + "Display") : null;
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Get valid styles for this unit
            String[] validStyles = JSIntlDurationFormat.getValidStylesForUnit(i);

            // Validate unit style
            if (unitStyle != null) {
                boolean valid = false;
                for (String vs : validStyles) {
                    if (vs.equals(unitStyle)) {
                        valid = true;
                        break;
                    }
                }
                if (!valid) {
                    return context.throwRangeError("Value " + unitStyle + " out of range for Intl.DurationFormat options property " + unitName);
                }
            }

            // Apply defaults if unit style not specified
            if (unitStyle == null) {
                if ("digital".equals(style)) {
                    unitStyle = JSIntlDurationFormat.getDigitalDefault(i);
                } else if (prevStyle != null && ("numeric".equals(prevStyle) || "2-digit".equals(prevStyle))) {
                    unitStyle = "numeric";
                } else {
                    unitStyle = style;
                    // For date units with digital style base, ensure the style is valid
                    if ("digital".equals(unitStyle)) {
                        unitStyle = "short"; // fallback for date units
                    }
                }
            }

            // Style conflict check (spec step 6/9)
            if (prevStyle != null && ("numeric".equals(prevStyle) || "2-digit".equals(prevStyle))) {
                if (!"numeric".equals(unitStyle) && !"2-digit".equals(unitStyle)) {
                    return context.throwRangeError("Cannot use style \"" + unitStyle + "\" for " + unitName +
                            " when previous unit uses \"" + prevStyle + "\" style");
                }
                // Upgrade minutes/seconds to 2-digit
                if ("minutes".equals(unitName) || "seconds".equals(unitName)) {
                    unitStyle = "2-digit";
                }
            }

            // Display default: "always" when unit style was explicitly set in options,
            // "auto" when unit style was defaulted from the base style or digital defaults
            if (display == null) {
                if (unitStyleExplicit && !"digital".equals(style)) {
                    display = "always";
                } else {
                    display = "auto";
                }
            } else if (!"auto".equals(display) && !"always".equals(display)) {
                return context.throwRangeError("Value " + display + " out of range for Intl.DurationFormat options property " + unitName + "Display");
            }

            unitStyles[i] = unitStyle;
            unitDisplays[i] = display;

            // Update prevStyle - only propagate for time/subsecond units
            prevStyle = unitStyle;
        }

        // In digital mode, when hoursDisplay was explicitly set in options,
        // set minutesDisplay and secondsDisplay to "always" if they weren't
        // explicitly set. This ensures minutes:seconds pair is always shown
        // in digital format even when hours is hidden (hoursDisplay="auto").
        if ("digital".equals(style) && options != null) {
            JSValue hoursDisplayVal = options.get(context, PropertyKey.fromString("hoursDisplay"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            boolean hoursDisplayExplicit = hoursDisplayVal != null && !(hoursDisplayVal instanceof JSUndefined);
            if (hoursDisplayExplicit) {
                JSValue minutesDisplayVal = options.get(context, PropertyKey.fromString("minutesDisplay"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                boolean minutesDisplayExplicit = minutesDisplayVal != null && !(minutesDisplayVal instanceof JSUndefined);
                if (!minutesDisplayExplicit) {
                    unitDisplays[5] = "always"; // minutes
                }
                JSValue secondsDisplayVal = options.get(context, PropertyKey.fromString("secondsDisplay"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                boolean secondsDisplayExplicit = secondsDisplayVal != null && !(secondsDisplayVal instanceof JSUndefined);
                if (!secondsDisplayExplicit) {
                    unitDisplays[6] = "always"; // seconds
                }
            }
        }

        // Read fractionalDigits
        Integer fractionalDigits = null;
        if (options != null) {
            JSValue fdValue = options.get(context, PropertyKey.fromString("fractionalDigits"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (fdValue != null && !(fdValue instanceof JSUndefined)) {
                double fdDouble = JSTypeConversions.toNumber(context, fdValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int fd = (int) fdDouble;
                if (Double.isNaN(fdDouble) || fd < 0 || fd > 9 || fdDouble != fd) {
                    return context.throwRangeError("fractionalDigits value is out of range");
                }
                fractionalDigits = fd;
            }
        }

        JSIntlDurationFormat durationFormat = new JSIntlDurationFormat(locale, numberingSystem, style,
                unitStyles, unitDisplays, fractionalDigits);
        if (resolvedPrototype != null) {
            durationFormat.setPrototype(resolvedPrototype);
        }
        return durationFormat;
    }

    /**
     * Intl.DurationFormat.prototype.format(duration)
     */
    public static JSValue durationFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDurationFormat durationFormat)) {
            return context.throwTypeError("Intl.DurationFormat.prototype.format called on incompatible receiver");
        }
        if (args.length == 0) {
            return context.throwTypeError("Invalid duration value");
        }

        Map<String, Double> durationRecord = JSIntlDurationFormat.toDurationRecord(context, args[0]);
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        String result = durationFormat.formatDuration(durationRecord);
        return new JSString(result);
    }

    /**
     * Intl.DurationFormat.prototype.formatToParts(duration)
     */
    public static JSValue durationFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDurationFormat durationFormat)) {
            return context.throwTypeError("Intl.DurationFormat.prototype.formatToParts called on incompatible receiver");
        }
        if (args.length == 0) {
            return context.throwTypeError("Invalid duration value");
        }

        Map<String, Double> durationRecord = JSIntlDurationFormat.toDurationRecord(context, args[0]);
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        List<JSIntlDurationFormat.FormatPart> parts = durationFormat.partitionDurationFormatPattern(durationRecord);
        JSArray result = context.createJSArray();
        for (int i = 0; i < parts.size(); i++) {
            JSIntlDurationFormat.FormatPart part = parts.get(i);
            JSObject partObj = context.createJSObject();
            partObj.set("type", new JSString(part.type()));
            partObj.set("value", new JSString(part.value()));
            if (part.unit() != null) {
                partObj.set("unit", new JSString(part.unit()));
            }
            result.set(context, i, partObj);
        }
        return result;
    }

    /**
     * Intl.DurationFormat.prototype.resolvedOptions()
     */
    public static JSValue durationFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDurationFormat df)) {
            return context.throwTypeError("Intl.DurationFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject result = context.createJSObject();
        result.set("locale", new JSString(df.getLocale().toLanguageTag()));
        result.set("numberingSystem", new JSString(df.getNumberingSystem()));
        result.set("style", new JSString(df.getStyle()));

        // Add unit styles and displays in table order
        for (int i = 0; i < JSIntlDurationFormat.UNIT_NAMES.length; i++) {
            String unitName = JSIntlDurationFormat.UNIT_NAMES[i];
            result.set(unitName, new JSString(df.getUnitStyle(i)));
            result.set(unitName + "Display", new JSString(df.getUnitDisplay(i)));
        }

        // Add fractionalDigits only if explicitly set
        if (df.getFractionalDigits() != null) {
            result.set("fractionalDigits", new JSNumber(df.getFractionalDigits()));
        }

        return result;
    }

    private static String getOptionString(JSContext context, JSValue optionsValue, String key) {
        if (!(optionsValue instanceof JSObject optionsObject)) {
            return null;
        }
        JSValue rawValue = optionsObject.get(context, PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        return JSTypeConversions.toString(context, rawValue).value();
    }

    /**
     * Like getOptionString but checks for pending exceptions after property access.
     * Returns null if an exception occurred (caller must check context.hasPendingException()).
     */
    private static String getOptionStringChecked(JSContext context, JSValue optionsValue, String key) {
        if (!(optionsValue instanceof JSObject optionsObject)) {
            return null;
        }
        JSValue rawValue = optionsObject.get(context, PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        return JSTypeConversions.toString(context, rawValue).value();
    }

    /**
     * Check if a collation type is supported for a given locale language.
     * Based on CLDR data for common locale-specific collation types.
     */
    private static boolean isSupportedCollation(String language, String collation) {
        if (collation == null || "default".equals(collation)) {
            return true;
        }
        return switch (language) {
            case "de" -> "phonebk".equals(collation) || "eor".equals(collation);
            case "es" -> "trad".equals(collation) || "eor".equals(collation);
            case "zh" -> "pinyin".equals(collation) || "stroke".equals(collation)
                    || "zhuyin".equals(collation) || "big5han".equals(collation)
                    || "gb2312".equals(collation) || "unihan".equals(collation);
            case "ja", "ko" -> "unihan".equals(collation) || "searchjl".equals(collation);
            case "si" -> "dict".equals(collation);
            case "sv" -> "reformed".equals(collation);
            case "en" -> "ducet".equals(collation) || "emoji".equals(collation) || "eor".equals(collation);
            case "ln" -> "phonetic".equals(collation);
            case "hi" -> "direct".equals(collation);
            default -> false;
        };
    }

    private static boolean isValidCollation(String collation) {
        if (collation == null || collation.isEmpty()) {
            return false;
        }
        return VALID_COLLATION_TYPES.contains(collation);
    }

    /**
     * Canonicalize a calendar identifier per ECMA-402:
     * - ASCII-only lowercase (not locale-sensitive)
     * - Legacy alias mapping (e.g., "islamicc" → "islamic-civil")
     * - Deprecated calendar fallback ("islamic", "islamic-rgsa" → "islamic-civil")
     */
    private static String canonicalizeCalendar(String calendar) {
        if (calendar == null) {
            return null;
        }
        // ASCII-only lowercase
        StringBuilder sb = new StringBuilder(calendar.length());
        for (int i = 0; i < calendar.length(); i++) {
            char c = calendar.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + 32));
            } else {
                sb.append(c);
            }
        }
        String lowered = sb.toString();
        // Legacy alias
        if ("islamicc".equals(lowered)) {
            return "islamic-civil";
        }
        if ("ethiopic-amete-alem".equals(lowered)) {
            return "ethioaa";
        }
        // Deprecated calendars: implementation-defined fallback
        if ("islamic".equals(lowered) || "islamic-rgsa".equals(lowered)) {
            return "islamic-civil";
        }
        return lowered;
    }

    public static JSValue listFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.format called on incompatible receiver");
        }
        if (args.length == 0 || !(args[0] instanceof JSArray listArray)) {
            return context.throwTypeError("Intl.ListFormat.prototype.format requires an array");
        }
        List<String> values = new ArrayList<>();
        for (long i = 0; i < listArray.getLength(); i++) {
            values.add(JSTypeConversions.toString(context, listArray.get(i)).value());
        }
        return new JSString(listFormat.format(values));
    }

    public static JSValue listFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(listFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("style", new JSString(listFormat.getStyle()));
        resolvedOptions.set("type", new JSString(listFormat.getType()));
        return resolvedOptions;
    }

    public static JSValue localeGetCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.calendar called on incompatible receiver");
        }
        String calendar = intlLocale.getCalendar();
        return calendar != null ? new JSString(calendar) : JSUndefined.INSTANCE;
    }

    public static JSValue localeGetCaseFirst(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.caseFirst called on incompatible receiver");
        }
        String caseFirst = intlLocale.getCaseFirst();
        return caseFirst != null ? new JSString(caseFirst) : JSUndefined.INSTANCE;
    }

    public static JSValue localeGetCollation(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.collation called on incompatible receiver");
        }
        String collation = intlLocale.getCollation();
        return collation != null ? new JSString(collation) : JSUndefined.INSTANCE;
    }

    public static JSValue localeGetHourCycle(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.hourCycle called on incompatible receiver");
        }
        String hourCycle = intlLocale.getHourCycle();
        return hourCycle != null ? new JSString(hourCycle) : JSUndefined.INSTANCE;
    }

    public static JSValue localeGetNumberingSystem(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.numberingSystem called on incompatible receiver");
        }
        String numberingSystem = intlLocale.getNumberingSystem();
        return numberingSystem != null ? new JSString(numberingSystem) : JSUndefined.INSTANCE;
    }

    public static JSValue localeGetNumeric(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.numeric called on incompatible receiver");
        }
        return intlLocale.isNumericSet() ? JSBoolean.valueOf(intlLocale.getNumeric()) : JSBoolean.FALSE;
    }

    public static JSValue localeGetBaseName(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.baseName called on incompatible receiver");
        }
        return new JSString(intlLocale.getBaseName());
    }

    public static JSValue localeGetLanguage(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.language called on incompatible receiver");
        }
        return new JSString(intlLocale.getLanguage());
    }

    public static JSValue localeGetRegion(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.region called on incompatible receiver");
        }
        return intlLocale.getRegion().isEmpty() ? JSUndefined.INSTANCE : new JSString(intlLocale.getRegion());
    }

    public static JSValue localeGetScript(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.script called on incompatible receiver");
        }
        return intlLocale.getScript().isEmpty() ? JSUndefined.INSTANCE : new JSString(intlLocale.getScript());
    }

    public static JSValue localeToString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.toString called on incompatible receiver");
        }
        return new JSString(intlLocale.getTag());
    }

    /**
     * Resolve the prototype for an Intl constructor per GetPrototypeFromConstructor.
     * When called via Reflect.construct with a newTarget from another realm whose
     * prototype is not an object, falls back to that realm's Intl.X.prototype.
     */
    private static JSObject resolveIntlPrototype(JSContext context, JSObject defaultPrototype, String intlConstructorName) {
        JSValue newTarget = context.getNativeConstructorNewTarget();
        if (!(newTarget instanceof JSObject newTargetObject)) {
            return defaultPrototype;
        }
        JSValue newTargetProto = newTargetObject.get(context, PropertyKey.PROTOTYPE);
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

    private static String normalizeOption(String rawValue, String defaultValue, String... allowedValues) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(rawValue)) {
                return allowedValue;
            }
        }
        throw new IllegalArgumentException("Invalid option value: " + rawValue);
    }

    public static JSValue numberFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.format called on incompatible receiver");
        }
        if (args.length > 0 && args[0] instanceof JSBigInt bigInt) {
            return new JSString(numberFormat.format(bigInt.value()));
        }
        double value = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : 0;
        return new JSString(numberFormat.format(value));
    }

    public static JSValue numberFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(numberFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("numberingSystem", new JSString(
                numberFormat.getNumberingSystem() != null ? numberFormat.getNumberingSystem() : "latn"));
        resolvedOptions.set("style", new JSString(numberFormat.getStyle()));
        if ("currency".equals(numberFormat.getStyle()) && numberFormat.getCurrency() != null) {
            resolvedOptions.set("currency", new JSString(numberFormat.getCurrency().toUpperCase(Locale.ROOT)));
        }
        if ("unit".equals(numberFormat.getStyle()) && numberFormat.getUnit() != null) {
            resolvedOptions.set("unit", new JSString(numberFormat.getUnit()));
            resolvedOptions.set("unitDisplay", new JSString(
                    numberFormat.getUnitDisplay() != null ? numberFormat.getUnitDisplay() : "short"));
        }
        resolvedOptions.set("minimumIntegerDigits", JSNumber.of(numberFormat.getMinimumIntegerDigits()));
        resolvedOptions.set("useGrouping", JSBoolean.valueOf(numberFormat.getUseGrouping()));
        resolvedOptions.set("signDisplay", new JSString(numberFormat.getSignDisplay()));
        resolvedOptions.set("roundingMode", new JSString(numberFormat.getRoundingMode()));
        return resolvedOptions;
    }

    private static FormatStyle parseFormatStyle(String rawStyle) {
        if (rawStyle == null) {
            return null;
        }
        return switch (rawStyle) {
            case "short" -> FormatStyle.SHORT;
            case "medium" -> FormatStyle.MEDIUM;
            case "long" -> FormatStyle.LONG;
            case "full" -> FormatStyle.FULL;
            default -> null;
        };
    }

    // ---- CLDR Language Alias Data ----
    // Maps: old language code → new language code
    // Some entries have additional script/region that are added only if not already present
    private static final Map<String, String> LANGUAGE_ALIASES = new HashMap<>();
    // Maps: old language → script to add if not already present
    private static final Map<String, String> LANGUAGE_SCRIPT_ADDITIONS = new HashMap<>();
    // Maps: old language → region to add if not already present
    private static final Map<String, String> LANGUAGE_REGION_ADDITIONS = new HashMap<>();

    static {
        // 3-letter to 2-letter language aliases
        LANGUAGE_ALIASES.put("aar", "aa"); LANGUAGE_ALIASES.put("abk", "ab"); LANGUAGE_ALIASES.put("afr", "af");
        LANGUAGE_ALIASES.put("aka", "ak"); LANGUAGE_ALIASES.put("amh", "am"); LANGUAGE_ALIASES.put("ara", "ar");
        LANGUAGE_ALIASES.put("arg", "an"); LANGUAGE_ALIASES.put("asm", "as"); LANGUAGE_ALIASES.put("ava", "av");
        LANGUAGE_ALIASES.put("ave", "ae"); LANGUAGE_ALIASES.put("aym", "ay"); LANGUAGE_ALIASES.put("aze", "az");
        LANGUAGE_ALIASES.put("bak", "ba"); LANGUAGE_ALIASES.put("bam", "bm"); LANGUAGE_ALIASES.put("bel", "be");
        LANGUAGE_ALIASES.put("ben", "bn"); LANGUAGE_ALIASES.put("bis", "bi"); LANGUAGE_ALIASES.put("bod", "bo");
        LANGUAGE_ALIASES.put("bos", "bs"); LANGUAGE_ALIASES.put("bre", "br"); LANGUAGE_ALIASES.put("bul", "bg");
        LANGUAGE_ALIASES.put("cat", "ca"); LANGUAGE_ALIASES.put("ces", "cs"); LANGUAGE_ALIASES.put("cha", "ch");
        LANGUAGE_ALIASES.put("che", "ce"); LANGUAGE_ALIASES.put("chu", "cu"); LANGUAGE_ALIASES.put("chv", "cv");
        LANGUAGE_ALIASES.put("cor", "kw"); LANGUAGE_ALIASES.put("cos", "co"); LANGUAGE_ALIASES.put("cre", "cr");
        LANGUAGE_ALIASES.put("cym", "cy"); LANGUAGE_ALIASES.put("dan", "da"); LANGUAGE_ALIASES.put("deu", "de");
        LANGUAGE_ALIASES.put("div", "dv"); LANGUAGE_ALIASES.put("dzo", "dz"); LANGUAGE_ALIASES.put("ell", "el");
        LANGUAGE_ALIASES.put("eng", "en"); LANGUAGE_ALIASES.put("epo", "eo"); LANGUAGE_ALIASES.put("est", "et");
        LANGUAGE_ALIASES.put("eus", "eu"); LANGUAGE_ALIASES.put("ewe", "ee"); LANGUAGE_ALIASES.put("fao", "fo");
        LANGUAGE_ALIASES.put("fas", "fa"); LANGUAGE_ALIASES.put("fij", "fj"); LANGUAGE_ALIASES.put("fin", "fi");
        LANGUAGE_ALIASES.put("fra", "fr"); LANGUAGE_ALIASES.put("fry", "fy"); LANGUAGE_ALIASES.put("ful", "ff");
        LANGUAGE_ALIASES.put("gla", "gd"); LANGUAGE_ALIASES.put("gle", "ga"); LANGUAGE_ALIASES.put("glg", "gl");
        LANGUAGE_ALIASES.put("glv", "gv"); LANGUAGE_ALIASES.put("grn", "gn"); LANGUAGE_ALIASES.put("guj", "gu");
        LANGUAGE_ALIASES.put("hat", "ht"); LANGUAGE_ALIASES.put("hau", "ha"); LANGUAGE_ALIASES.put("hbs", "sr");
        LANGUAGE_ALIASES.put("heb", "he"); LANGUAGE_ALIASES.put("her", "hz"); LANGUAGE_ALIASES.put("hin", "hi");
        LANGUAGE_ALIASES.put("hmo", "ho"); LANGUAGE_ALIASES.put("hrv", "hr"); LANGUAGE_ALIASES.put("hun", "hu");
        LANGUAGE_ALIASES.put("hye", "hy"); LANGUAGE_ALIASES.put("ibo", "ig"); LANGUAGE_ALIASES.put("ido", "io");
        LANGUAGE_ALIASES.put("iii", "ii"); LANGUAGE_ALIASES.put("iku", "iu"); LANGUAGE_ALIASES.put("ile", "ie");
        LANGUAGE_ALIASES.put("ina", "ia"); LANGUAGE_ALIASES.put("ind", "id"); LANGUAGE_ALIASES.put("ipk", "ik");
        LANGUAGE_ALIASES.put("isl", "is"); LANGUAGE_ALIASES.put("ita", "it"); LANGUAGE_ALIASES.put("jav", "jv");
        LANGUAGE_ALIASES.put("jpn", "ja"); LANGUAGE_ALIASES.put("kal", "kl"); LANGUAGE_ALIASES.put("kan", "kn");
        LANGUAGE_ALIASES.put("kas", "ks"); LANGUAGE_ALIASES.put("kat", "ka"); LANGUAGE_ALIASES.put("kau", "kr");
        LANGUAGE_ALIASES.put("kaz", "kk"); LANGUAGE_ALIASES.put("khm", "km"); LANGUAGE_ALIASES.put("kik", "ki");
        LANGUAGE_ALIASES.put("kin", "rw"); LANGUAGE_ALIASES.put("kir", "ky"); LANGUAGE_ALIASES.put("kom", "kv");
        LANGUAGE_ALIASES.put("kon", "kg"); LANGUAGE_ALIASES.put("kor", "ko"); LANGUAGE_ALIASES.put("kua", "kj");
        LANGUAGE_ALIASES.put("kur", "ku"); LANGUAGE_ALIASES.put("lao", "lo"); LANGUAGE_ALIASES.put("lat", "la");
        LANGUAGE_ALIASES.put("lav", "lv"); LANGUAGE_ALIASES.put("lim", "li"); LANGUAGE_ALIASES.put("lin", "ln");
        LANGUAGE_ALIASES.put("lit", "lt"); LANGUAGE_ALIASES.put("ltz", "lb"); LANGUAGE_ALIASES.put("lub", "lu");
        LANGUAGE_ALIASES.put("lug", "lg"); LANGUAGE_ALIASES.put("mah", "mh"); LANGUAGE_ALIASES.put("mal", "ml");
        LANGUAGE_ALIASES.put("mar", "mr"); LANGUAGE_ALIASES.put("mkd", "mk"); LANGUAGE_ALIASES.put("mlg", "mg");
        LANGUAGE_ALIASES.put("mlt", "mt"); LANGUAGE_ALIASES.put("mon", "mn"); LANGUAGE_ALIASES.put("mri", "mi");
        LANGUAGE_ALIASES.put("msa", "ms"); LANGUAGE_ALIASES.put("mya", "my"); LANGUAGE_ALIASES.put("nau", "na");
        LANGUAGE_ALIASES.put("nav", "nv"); LANGUAGE_ALIASES.put("nbl", "nr"); LANGUAGE_ALIASES.put("nde", "nd");
        LANGUAGE_ALIASES.put("ndo", "ng"); LANGUAGE_ALIASES.put("nep", "ne"); LANGUAGE_ALIASES.put("nld", "nl");
        LANGUAGE_ALIASES.put("nno", "nn"); LANGUAGE_ALIASES.put("nob", "nb"); LANGUAGE_ALIASES.put("nor", "no");
        LANGUAGE_ALIASES.put("nya", "ny"); LANGUAGE_ALIASES.put("oci", "oc"); LANGUAGE_ALIASES.put("oji", "oj");
        LANGUAGE_ALIASES.put("ori", "or"); LANGUAGE_ALIASES.put("orm", "om"); LANGUAGE_ALIASES.put("oss", "os");
        LANGUAGE_ALIASES.put("pan", "pa"); LANGUAGE_ALIASES.put("pli", "pi"); LANGUAGE_ALIASES.put("pol", "pl");
        LANGUAGE_ALIASES.put("por", "pt"); LANGUAGE_ALIASES.put("pus", "ps"); LANGUAGE_ALIASES.put("que", "qu");
        LANGUAGE_ALIASES.put("roh", "rm"); LANGUAGE_ALIASES.put("ron", "ro"); LANGUAGE_ALIASES.put("run", "rn");
        LANGUAGE_ALIASES.put("rus", "ru"); LANGUAGE_ALIASES.put("sag", "sg"); LANGUAGE_ALIASES.put("san", "sa");
        LANGUAGE_ALIASES.put("sin", "si"); LANGUAGE_ALIASES.put("slk", "sk"); LANGUAGE_ALIASES.put("slv", "sl");
        LANGUAGE_ALIASES.put("sme", "se"); LANGUAGE_ALIASES.put("smo", "sm"); LANGUAGE_ALIASES.put("sna", "sn");
        LANGUAGE_ALIASES.put("snd", "sd"); LANGUAGE_ALIASES.put("som", "so"); LANGUAGE_ALIASES.put("sot", "st");
        LANGUAGE_ALIASES.put("spa", "es"); LANGUAGE_ALIASES.put("sqi", "sq"); LANGUAGE_ALIASES.put("srd", "sc");
        LANGUAGE_ALIASES.put("srp", "sr"); LANGUAGE_ALIASES.put("ssw", "ss"); LANGUAGE_ALIASES.put("sun", "su");
        LANGUAGE_ALIASES.put("swa", "sw"); LANGUAGE_ALIASES.put("swe", "sv"); LANGUAGE_ALIASES.put("tah", "ty");
        LANGUAGE_ALIASES.put("tam", "ta"); LANGUAGE_ALIASES.put("tat", "tt"); LANGUAGE_ALIASES.put("tel", "te");
        LANGUAGE_ALIASES.put("tgk", "tg"); LANGUAGE_ALIASES.put("tgl", "tl"); LANGUAGE_ALIASES.put("tha", "th");
        LANGUAGE_ALIASES.put("tir", "ti"); LANGUAGE_ALIASES.put("ton", "to"); LANGUAGE_ALIASES.put("tsn", "tn");
        LANGUAGE_ALIASES.put("tso", "ts"); LANGUAGE_ALIASES.put("tuk", "tk"); LANGUAGE_ALIASES.put("tur", "tr");
        LANGUAGE_ALIASES.put("twi", "tw"); LANGUAGE_ALIASES.put("uig", "ug"); LANGUAGE_ALIASES.put("ukr", "uk");
        LANGUAGE_ALIASES.put("urd", "ur"); LANGUAGE_ALIASES.put("uzb", "uz"); LANGUAGE_ALIASES.put("ven", "ve");
        LANGUAGE_ALIASES.put("vie", "vi"); LANGUAGE_ALIASES.put("vol", "vo"); LANGUAGE_ALIASES.put("wln", "wa");
        LANGUAGE_ALIASES.put("wol", "wo"); LANGUAGE_ALIASES.put("xho", "xh"); LANGUAGE_ALIASES.put("yid", "yi");
        LANGUAGE_ALIASES.put("yor", "yo"); LANGUAGE_ALIASES.put("zha", "za"); LANGUAGE_ALIASES.put("zho", "zh");
        LANGUAGE_ALIASES.put("zul", "zu");

        // Deprecated 2-letter codes
        LANGUAGE_ALIASES.put("mo", "ro");
        LANGUAGE_ALIASES.put("jw", "jv");
        LANGUAGE_ALIASES.put("tl", "fil");

        // Complex language aliases (language maps to different language + script)
        LANGUAGE_ALIASES.put("cmn", "zh");
        LANGUAGE_ALIASES.put("sh", "sr");
        LANGUAGE_SCRIPT_ADDITIONS.put("sh", "Latn");
        LANGUAGE_ALIASES.put("cnr", "sr");
        LANGUAGE_REGION_ADDITIONS.put("cnr", "ME");

        // Sign language aliases
        LANGUAGE_ALIASES.put("sgn-gr", "gss");
        LANGUAGE_ALIASES.put("sgn-be-fr", "sfb");
        LANGUAGE_ALIASES.put("sgn-be-nl", "vgt");
        LANGUAGE_ALIASES.put("sgn-ch-de", "sgg");
    }

    // ---- Region Alias Data ----
    // Maps: old region → {language/script → new region}
    // Default replacement is first, specific language/script replacements follow
    private static final Map<String, String> REGION_ALIASES = new HashMap<>();
    private static final Map<String, Map<String, String>> REGION_ALIASES_BY_LANG = new HashMap<>();

    static {
        REGION_ALIASES.put("DD", "DE"); REGION_ALIASES.put("YD", "YE");
        REGION_ALIASES.put("AN", "CW"); REGION_ALIASES.put("BU", "MM");
        REGION_ALIASES.put("CS", "RS"); REGION_ALIASES.put("TP", "TL");
        REGION_ALIASES.put("YU", "RS"); REGION_ALIASES.put("ZR", "CD");
        REGION_ALIASES.put("200", "CZ"); REGION_ALIASES.put("530", "CW");
        REGION_ALIASES.put("532", "CW"); REGION_ALIASES.put("536", "SA");
        REGION_ALIASES.put("582", "FM"); REGION_ALIASES.put("736", "SD");
        REGION_ALIASES.put("890", "RS");

        // SU (Soviet Union) and 810 → default RU, but hy→AM, ka→GE, etc.
        REGION_ALIASES.put("SU", "RU");
        REGION_ALIASES.put("810", "RU");
        Map<String, String> suByLang = new HashMap<>();
        suByLang.put("hy", "AM"); suByLang.put("ka", "GE"); suByLang.put("az", "AZ");
        suByLang.put("be", "BY"); suByLang.put("et", "EE"); suByLang.put("lv", "LV");
        suByLang.put("lt", "LT"); suByLang.put("tk", "TM"); suByLang.put("uk", "UA");
        suByLang.put("uz", "UZ"); suByLang.put("kk", "KZ"); suByLang.put("ky", "KG");
        suByLang.put("tg", "TJ"); suByLang.put("mn", "MN");
        REGION_ALIASES_BY_LANG.put("SU", suByLang);
        REGION_ALIASES_BY_LANG.put("810", suByLang);

        // NT (Neutral Zone) → SA (for az)
        REGION_ALIASES.put("NT", "SA");
        Map<String, String> ntByLang = new HashMap<>();
        ntByLang.put("az", "SA");
        REGION_ALIASES_BY_LANG.put("NT", ntByLang);
    }

    // Script-based region resolution for SU/810
    private static final Map<String, String> REGION_BY_SCRIPT = new HashMap<>();
    static {
        REGION_BY_SCRIPT.put("Armn", "AM"); REGION_BY_SCRIPT.put("Geor", "GE");
    }

    // ---- Variant Alias Data ----
    private static final Map<String, String> VARIANT_ALIASES = new HashMap<>();
    private static final Map<String, String> VARIANT_TO_LANGUAGE = new HashMap<>();
    static {
        VARIANT_ALIASES.put("heploc", "alalc97");
        // When heploc is replaced by alalc97, hepburn should also be removed
        // (heploc was a sub-variant of hepburn)
        // hy-arevela → hy (remove variant, language stays hy)
        VARIANT_TO_LANGUAGE.put("arevela", "hy");
        // hy-arevmda → hyw (remove variant, change language to hyw)
        VARIANT_TO_LANGUAGE.put("arevmda", "hyw");
    }

    // ---- Unicode Extension Value Aliases ----
    private static final Map<String, Map<String, String>> UNICODE_EXT_VALUE_ALIASES = new HashMap<>();
    static {
        // ca (calendar)
        Map<String, String> calendarAliases = new HashMap<>();
        calendarAliases.put("ethiopic-amete-alem", "ethioaa");
        calendarAliases.put("islamicc", "islamic-civil");
        UNICODE_EXT_VALUE_ALIASES.put("ca", calendarAliases);

        // ks (collation strength)
        Map<String, String> colStrengthAliases = new HashMap<>();
        colStrengthAliases.put("primary", "level1");
        colStrengthAliases.put("secondary", "level2");
        colStrengthAliases.put("tertiary", "level3");
        colStrengthAliases.put("quaternary", "level4");
        colStrengthAliases.put("identical", "level5");
        UNICODE_EXT_VALUE_ALIASES.put("ks", colStrengthAliases);

        // ms (measurement system)
        Map<String, String> msAliases = new HashMap<>();
        msAliases.put("imperial", "uksystem");
        UNICODE_EXT_VALUE_ALIASES.put("ms", msAliases);
    }

    // Unicode extension keys where "yes" → "true" (which means omit the value)
    private static final Set<String> UNICODE_EXT_BOOLEAN_KEYS = Set.of("kb", "kc", "kh", "kk", "kn");

    // ---- Subdivision Aliases ----
    private static final Map<String, String> SUBDIVISION_ALIASES = new HashMap<>();
    static {
        SUBDIVISION_ALIASES.put("no23", "no50");
        SUBDIVISION_ALIASES.put("cn11", "cnbj");
        SUBDIVISION_ALIASES.put("cz10a", "cz110");
        SUBDIVISION_ALIASES.put("fra", "frges");
        SUBDIVISION_ALIASES.put("frg", "frges");
        SUBDIVISION_ALIASES.put("lud", "lucl");
    }

    // ---- Timezone Aliases ----
    private static final Map<String, String> TIMEZONE_ALIASES = new HashMap<>();
    static {
        TIMEZONE_ALIASES.put("cnckg", "cnsha");
        TIMEZONE_ALIASES.put("eire", "iedub");
        TIMEZONE_ALIASES.put("est", "papty");
        TIMEZONE_ALIASES.put("gmt0", "gmt");
        TIMEZONE_ALIASES.put("uct", "utc");
        TIMEZONE_ALIASES.put("zulu", "utc");
    }

    // ---- Transform Extension tvalue Aliases ----
    private static final Map<String, Map<String, String>> TVALUE_ALIASES = new HashMap<>();
    static {
        Map<String, String> m0Aliases = new HashMap<>();
        m0Aliases.put("names", "prprname");
        TVALUE_ALIASES.put("m0", m0Aliases);
    }

    /**
     * Canonicalize a locale tag string per ECMA-402 CanonicalizeUnicodeLocaleId.
     * Applies CLDR language/region/variant aliases, sorts variants, and canonicalizes extensions.
     */
    static String canonicalizeLocaleTag(String localeTag) {
        // First, let Java parse and normalize basic structure (case normalization, grandfathered tag mapping)
        Locale locale = parseLocaleTag(localeTag);
        String javaTag = locale.toLanguageTag();

        // Strip private-use suffixes that Java adds for grandfathered tags
        // e.g. "xtg-x-cel-gaulish" → "xtg", "en-GB-x-oed" → needs special handling
        javaTag = stripJavaGrandfatheredPrivateUse(localeTag, javaTag);

        // Now parse and canonicalize the tag further
        return applyCLDRCanonicalization(javaTag);
    }

    /**
     * Fix Java's handling of grandfathered tags that add unnecessary -x- private use.
     */
    private static String stripJavaGrandfatheredPrivateUse(String originalTag, String javaTag) {
        String lowerOriginal = originalTag.toLowerCase(Locale.ROOT);
        // cel-gaulish → xtg (Java gives xtg-x-cel-gaulish)
        if (lowerOriginal.equals("cel-gaulish") && javaTag.contains("-x-")) {
            return javaTag.substring(0, javaTag.indexOf("-x-"));
        }
        // zh-min → nan (Java gives nan-x-zh-min)
        if (lowerOriginal.equals("zh-min") && javaTag.contains("-x-")) {
            return javaTag.substring(0, javaTag.indexOf("-x-"));
        }
        // zh-guoyu → zh (Java gives cmn which still needs aliasing)
        if (lowerOriginal.equals("zh-guoyu")) {
            return "zh";
        }
        return javaTag;
    }

    /**
     * Apply CLDR canonicalization to a tag that has already been through Java's Locale processing.
     */
    private static String applyCLDRCanonicalization(String tag) {
        // Parse the tag into components
        List<String> parts = new ArrayList<>(Arrays.asList(tag.split("-")));
        if (parts.isEmpty()) {
            return tag;
        }

        // Find boundary indices for different subtag types
        String language = parts.get(0).toLowerCase(Locale.ROOT);
        int idx = 1;
        String script = "";
        String region = "";
        List<String> variants = new ArrayList<>();
        Map<Character, String> extensions = new LinkedHashMap<>();

        // Check for sign language prefix mappings first (sgn-XX → replacement)
        String signKey = language;
        if (idx < parts.size()) {
            signKey += "-" + parts.get(idx).toLowerCase(Locale.ROOT);
            if (idx + 1 < parts.size()) {
                String threePartKey = signKey + "-" + parts.get(idx + 1).toLowerCase(Locale.ROOT);
                if (LANGUAGE_ALIASES.containsKey(threePartKey)) {
                    return LANGUAGE_ALIASES.get(threePartKey);
                }
            }
            if (LANGUAGE_ALIASES.containsKey(signKey) && language.equals("sgn")) {
                return LANGUAGE_ALIASES.get(signKey);
            }
        }

        // Apply language alias
        String newLanguage = LANGUAGE_ALIASES.get(language);
        if (newLanguage != null) {
            language = newLanguage;
        }

        // Parse script (4 alpha letters, title case after Java processing)
        if (idx < parts.size() && parts.get(idx).length() == 4 && parts.get(idx).chars().allMatch(Character::isLetter)) {
            script = parts.get(idx);
            idx++;
        }

        // For complex language aliases: add script if not already present
        String scriptAddition = LANGUAGE_SCRIPT_ADDITIONS.get(parts.get(0).toLowerCase(Locale.ROOT));
        if (scriptAddition != null && script.isEmpty()) {
            script = scriptAddition;
        }

        // Parse region (2 alpha or 3 digits)
        if (idx < parts.size()) {
            String p = parts.get(idx);
            if ((p.length() == 2 && p.chars().allMatch(Character::isLetter)) ||
                    (p.length() == 3 && p.chars().allMatch(Character::isDigit))) {
                region = p.toUpperCase(Locale.ROOT);
                idx++;
            }
        }

        // For complex language aliases: add region if not already present
        String regionAddition = LANGUAGE_REGION_ADDITIONS.get(parts.get(0).toLowerCase(Locale.ROOT));
        if (regionAddition != null && region.isEmpty()) {
            region = regionAddition;
        }

        // Apply region alias
        if (!region.isEmpty()) {
            String regionUpper = region.toUpperCase(Locale.ROOT);
            if (REGION_ALIASES.containsKey(regionUpper)) {
                // Check language/script-specific replacements
                Map<String, String> byLang = REGION_ALIASES_BY_LANG.get(regionUpper);
                if (byLang != null && byLang.containsKey(language)) {
                    region = byLang.get(language);
                } else if (byLang != null && !script.isEmpty() && REGION_BY_SCRIPT.containsKey(script)) {
                    region = REGION_BY_SCRIPT.get(script);
                } else {
                    region = REGION_ALIASES.get(regionUpper);
                }
            }
        }

        // Parse variants
        while (idx < parts.size()) {
            String p = parts.get(idx);
            if (p.length() == 1) {
                break; // Extension singleton
            }
            boolean isVariant = (p.length() >= 5 && p.length() <= 8) ||
                    (p.length() == 4 && Character.isDigit(p.charAt(0)));
            if (!isVariant) {
                break;
            }
            String lowerVariant = p.toLowerCase(Locale.ROOT);
            // Check variant-to-language mappings (e.g., hy-arevela → hy, hy-arevmda → hyw)
            if (VARIANT_TO_LANGUAGE.containsKey(lowerVariant)) {
                language = VARIANT_TO_LANGUAGE.get(lowerVariant);
                // Don't add this variant
            } else {
                // Apply variant alias
                String aliased = VARIANT_ALIASES.getOrDefault(lowerVariant, lowerVariant);
                variants.add(aliased);
            }
            idx++;
        }

        // When heploc → alalc97, remove the companion hepburn variant
        if (variants.contains("alalc97")) {
            variants.remove("hepburn");
        }

        // Sort variants alphabetically per CLDR canonicalization
        variants.sort(String.CASE_INSENSITIVE_ORDER);

        // Parse extensions
        while (idx < parts.size()) {
            String p = parts.get(idx);
            if (p.length() == 1 && !p.equalsIgnoreCase("x")) {
                char singleton = p.toLowerCase(Locale.ROOT).charAt(0);
                StringBuilder extValue = new StringBuilder();
                idx++;
                while (idx < parts.size() && parts.get(idx).length() > 1) {
                    if (!extValue.isEmpty()) {
                        extValue.append("-");
                    }
                    extValue.append(parts.get(idx).toLowerCase(Locale.ROOT));
                    idx++;
                }
                extensions.put(singleton, extValue.toString());
            } else if (p.equalsIgnoreCase("x")) {
                // Private use
                StringBuilder privateUse = new StringBuilder("x");
                idx++;
                while (idx < parts.size()) {
                    privateUse.append("-").append(parts.get(idx));
                    idx++;
                }
                extensions.put('x', privateUse.toString());
            } else {
                idx++;
            }
        }

        // Canonicalize unicode extension (u)
        if (extensions.containsKey('u')) {
            extensions.put('u', canonicalizeUnicodeExtension(extensions.get('u')));
        }

        // Canonicalize transform extension (t)
        if (extensions.containsKey('t')) {
            extensions.put('t', canonicalizeTransformExtension(extensions.get('t')));
        }

        // Reconstruct the tag
        StringBuilder result = new StringBuilder(language);
        if (!script.isEmpty()) {
            // Title case for script
            result.append("-").append(Character.toUpperCase(script.charAt(0)))
                    .append(script.substring(1).toLowerCase(Locale.ROOT));
        }
        if (!region.isEmpty()) {
            result.append("-").append(region.toUpperCase(Locale.ROOT));
        }
        for (String variant : variants) {
            result.append("-").append(variant);
        }
        // Extensions in singleton order
        List<Character> sortedSingletons = new ArrayList<>(extensions.keySet());
        sortedSingletons.sort(Character::compare);
        for (char singleton : sortedSingletons) {
            String extValue = extensions.get(singleton);
            if (singleton == 'x') {
                result.append("-").append(extValue);
            } else {
                result.append("-").append(singleton);
                if (!extValue.isEmpty()) {
                    result.append("-").append(extValue);
                }
            }
        }

        return result.toString();
    }

    /**
     * Canonicalize a Unicode extension value string (the part after "u-").
     * Sort key-value pairs by key, apply value aliases, handle boolean keys.
     */
    private static String canonicalizeUnicodeExtension(String extValue) {
        if (extValue == null || extValue.isEmpty()) {
            return extValue;
        }
        String[] parts = extValue.split("-");
        List<String> attributes = new ArrayList<>();
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        String currentKey = null;
        List<String> currentValues = new ArrayList<>();

        for (String part : parts) {
            if (part.length() == 2 && Character.isLetter(part.charAt(0))) {
                // New keyword key
                if (currentKey != null) {
                    keywords.put(currentKey, new ArrayList<>(currentValues));
                    currentValues.clear();
                }
                currentKey = part;
            } else if (currentKey != null) {
                currentValues.add(part);
            } else {
                // Attribute (before any key)
                attributes.add(part);
            }
        }
        if (currentKey != null) {
            keywords.put(currentKey, new ArrayList<>(currentValues));
        }

        // Sort keywords by key
        Map<String, List<String>> sortedKeywords = new TreeMap<>(keywords);

        // Apply value aliases and boolean canonicalization
        for (Map.Entry<String, List<String>> entry : sortedKeywords.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            String joinedValue = String.join("-", values);

            // Apply value aliases
            Map<String, String> valueAliases = UNICODE_EXT_VALUE_ALIASES.get(key);
            if (valueAliases != null && valueAliases.containsKey(joinedValue)) {
                String replacement = valueAliases.get(joinedValue);
                entry.setValue(replacement.isEmpty() ? List.of() : List.of(replacement.split("-")));
            }

            // Handle "rg" and "sd" subdivision aliases
            if (("rg".equals(key) || "sd".equals(key)) && !values.isEmpty()) {
                String sdValue = String.join("-", values);
                if (SUBDIVISION_ALIASES.containsKey(sdValue)) {
                    entry.setValue(List.of(SUBDIVISION_ALIASES.get(sdValue)));
                }
            }

            // Handle "tz" timezone aliases
            if ("tz".equals(key) && !values.isEmpty()) {
                String tzValue = String.join("-", values);
                if (TIMEZONE_ALIASES.containsKey(tzValue)) {
                    entry.setValue(List.of(TIMEZONE_ALIASES.get(tzValue)));
                }
            }

            // Handle boolean keys where "yes" → "true" → omit value
            if (UNICODE_EXT_BOOLEAN_KEYS.contains(key)) {
                String val = String.join("-", entry.getValue());
                if ("yes".equals(val) || "true".equals(val)) {
                    entry.setValue(List.of()); // Empty value (meaning "true", the default)
                }
            }
        }

        // Reconstruct
        StringBuilder result = new StringBuilder();
        for (String attr : attributes) {
            if (!result.isEmpty()) {
                result.append("-");
            }
            result.append(attr);
        }
        for (Map.Entry<String, List<String>> entry : sortedKeywords.entrySet()) {
            if (!result.isEmpty()) {
                result.append("-");
            }
            result.append(entry.getKey());
            List<String> values = entry.getValue();
            if (!values.isEmpty()) {
                result.append("-").append(String.join("-", values));
            }
        }
        return result.toString();
    }

    /**
     * Canonicalize a Transform extension value string (the part after "t-").
     * Sort tfield keys, sort tlang variants, canonicalize tlang, replace deprecated tvalues.
     */
    private static String canonicalizeTransformExtension(String extValue) {
        if (extValue == null || extValue.isEmpty()) {
            return extValue;
        }
        String[] parts = extValue.split("-");
        int idx = 0;

        // Parse tlang (optional) - starts with a language subtag
        String tlang = null;
        StringBuilder tlangBuilder = new StringBuilder();
        if (idx < parts.length && parts[idx].length() >= 2 && parts[idx].chars().allMatch(Character::isLetter)) {
            // This is a language subtag
            tlangBuilder.append(parts[idx]);
            idx++;
            // Parse optional script, region, variants
            List<String> tlangVariants = new ArrayList<>();
            String tlangScript = "";
            String tlangRegion = "";

            // Script
            if (idx < parts.length && parts[idx].length() == 4 && parts[idx].chars().allMatch(Character::isLetter)) {
                tlangScript = parts[idx];
                idx++;
            }
            // Region
            if (idx < parts.length) {
                String p = parts[idx];
                if ((p.length() == 2 && p.chars().allMatch(Character::isLetter)) ||
                        (p.length() == 3 && p.chars().allMatch(Character::isDigit))) {
                    tlangRegion = p;
                    idx++;
                }
            }
            // Variants
            while (idx < parts.length && parts[idx].length() > 1 &&
                    !isTransformFieldKey(parts[idx])) {
                boolean isVariant = (parts[idx].length() >= 5 && parts[idx].length() <= 8) ||
                        (parts[idx].length() == 4 && Character.isDigit(parts[idx].charAt(0)));
                if (!isVariant) {
                    break;
                }
                tlangVariants.add(parts[idx].toLowerCase(Locale.ROOT));
                idx++;
            }

            // Sort tlang variants
            tlangVariants.sort(String.CASE_INSENSITIVE_ORDER);

            // Canonicalize tlang language (apply aliases like iw→he)
            String tlangLang = tlangBuilder.toString().toLowerCase(Locale.ROOT);
            // Java's legacy code mapping
            if ("iw".equals(tlangLang)) {
                tlangLang = "he";
            } else if ("in".equals(tlangLang)) {
                tlangLang = "id";
            } else if ("ji".equals(tlangLang)) {
                tlangLang = "yi";
            }
            // CLDR language aliases
            String aliased = LANGUAGE_ALIASES.get(tlangLang);
            if (aliased != null) {
                tlangLang = aliased;
            }

            StringBuilder tlangResult = new StringBuilder(tlangLang);
            if (!tlangScript.isEmpty()) {
                tlangResult.append("-").append(tlangScript.toLowerCase(Locale.ROOT));
            }
            if (!tlangRegion.isEmpty()) {
                tlangResult.append("-").append(tlangRegion.toLowerCase(Locale.ROOT));
            }
            for (String v : tlangVariants) {
                tlangResult.append("-").append(v);
            }
            tlang = tlangResult.toString();
        }

        // Parse tfields (key-value pairs where key is 2 chars starting with letter/digit)
        Map<String, List<String>> tfields = new TreeMap<>(); // TreeMap for sorted order
        while (idx < parts.length) {
            if (isTransformFieldKey(parts[idx])) {
                String key = parts[idx].toLowerCase(Locale.ROOT);
                idx++;
                List<String> values = new ArrayList<>();
                while (idx < parts.length && !isTransformFieldKey(parts[idx])) {
                    values.add(parts[idx].toLowerCase(Locale.ROOT));
                    idx++;
                }
                // Apply tvalue aliases
                Map<String, String> aliases = TVALUE_ALIASES.get(key);
                if (aliases != null) {
                    String joinedValue = String.join("-", values);
                    if (aliases.containsKey(joinedValue)) {
                        String replacement = aliases.get(joinedValue);
                        values = new ArrayList<>(Arrays.asList(replacement.split("-")));
                    }
                }
                tfields.put(key, values);
            } else {
                idx++;
            }
        }

        // Reconstruct
        StringBuilder result = new StringBuilder();
        if (tlang != null) {
            result.append(tlang);
        }
        for (Map.Entry<String, List<String>> entry : tfields.entrySet()) {
            if (!result.isEmpty()) {
                result.append("-");
            }
            result.append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                result.append("-").append(String.join("-", entry.getValue()));
            }
        }
        return result.toString();
    }

    /**
     * Check if a string is a transform extension field key (2 chars: letter/digit + letter/digit).
     */
    private static boolean isTransformFieldKey(String s) {
        if (s.length() != 2) {
            return false;
        }
        char c0 = s.charAt(0);
        char c1 = s.charAt(1);
        // BCP 47 / UTS 35: tkey = alpha digit
        return Character.isLetter(c0) && Character.isDigit(c1);
    }

    private static Locale parseLocaleTag(String localeTag) {
        if (localeTag == null) {
            throw new IllegalArgumentException("Invalid language tag: null");
        }
        // Tags with leading/trailing whitespace or internal whitespace are invalid
        if (localeTag.isEmpty() || !localeTag.equals(localeTag.strip())) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag);
        }
        String normalizedTag = localeTag;
        // Additional BCP 47 structural validation that Java's Locale.Builder doesn't enforce
        validateBcp47Structure(normalizedTag);
        try {
            return new Locale.Builder().setLanguageTag(normalizedTag).build();
        } catch (java.util.IllformedLocaleException e) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag, e);
        }
    }

    /**
     * Validate BCP 47 structural constraints not enforced by Java's Locale.Builder.
     * - 4+ letter primary language subtags cannot be followed by extlang subtags
     * - Variant subtags must not be duplicated
     * - Extension singletons must not be duplicated
     */
    private static void validateBcp47Structure(String tag) {
        String[] parts = tag.split("-");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid language tag: " + tag);
        }

        String primaryLanguage = parts[0];
        // Primary language must be all ASCII alpha
        if (!primaryLanguage.matches("[a-zA-Z]+")) {
            throw new IllegalArgumentException("Invalid language tag: " + tag);
        }

        // Per UTS 35: language = 2-3 alpha or 5-8 alpha
        // 1-letter (i, x) and 4-letter primary subtags are invalid
        int langLen = primaryLanguage.length();
        if (langLen < 2 || langLen == 4 || langLen > 8) {
            throw new IllegalArgumentException("Invalid language tag: " + tag);
        }

        // Parse subtags in strict positional order: script?, region?, variants*, extensions*, privateuse?
        int idx = 1;
        boolean scriptSeen = false;
        boolean regionSeen = false;
        Set<String> variants = new HashSet<>();
        Set<Character> extensionSingletons = new HashSet<>();

        // Optional script (4 alpha)
        if (idx < parts.length) {
            String p = parts[idx];
            if (p.length() == 4 && p.chars().allMatch(c -> Character.isLetter((char) c))) {
                scriptSeen = true;
                idx++;
            }
        }

        // Optional region (2 alpha or 3 digit)
        if (idx < parts.length) {
            String p = parts[idx];
            if ((p.length() == 2 && p.chars().allMatch(c -> Character.isLetter((char) c))) ||
                    (p.length() == 3 && p.chars().allMatch(c -> Character.isDigit((char) c)))) {
                regionSeen = true;
                idx++;
            }
        }

        // Variants (5-8 alphanum or 4 starting with digit)
        while (idx < parts.length) {
            String p = parts[idx].toLowerCase(Locale.ROOT);
            boolean isVariant = (p.length() >= 5 && p.length() <= 8) ||
                    (p.length() == 4 && Character.isDigit(p.charAt(0)));
            if (!isVariant) {
                break;
            }
            if (!variants.add(p)) {
                throw new IllegalArgumentException(
                        "Invalid language tag: duplicate variant '" + p + "': " + tag);
            }
            idx++;
        }

        // Extensions and private use
        boolean inPrivateUse = false;
        char currentSingleton = 0;
        while (idx < parts.length) {
            String p = parts[idx];
            String pLower = p.toLowerCase(Locale.ROOT);

            if (inPrivateUse) {
                idx++;
                continue;
            }

            // Private use prefix
            if ("x".equalsIgnoreCase(p)) {
                inPrivateUse = true;
                idx++;
                continue;
            }

            // Extension singleton
            if (p.length() == 1) {
                char singleton = pLower.charAt(0);
                if (!Character.isLetterOrDigit(singleton)) {
                    throw new IllegalArgumentException("Invalid language tag: " + tag);
                }
                if (!extensionSingletons.add(singleton)) {
                    throw new IllegalArgumentException(
                            "Invalid language tag: duplicate extension singleton '" + singleton + "': " + tag);
                }
                currentSingleton = singleton;
                idx++;
                // Must have at least one extension subtag
                boolean hasValue = false;
                while (idx < parts.length && parts[idx].length() > 1) {
                    String extSubtag = parts[idx].toLowerCase(Locale.ROOT);
                    // Validate Unicode extension keys (u-extension)
                    if (currentSingleton == 'u' && extSubtag.length() == 2) {
                        char c1 = extSubtag.charAt(1);
                        if (Character.isDigit(c1)) {
                            throw new IllegalArgumentException(
                                    "Invalid language tag: Unicode extension key second char must be letter: " + tag);
                        }
                    }
                    hasValue = true;
                    idx++;
                }
                if (!hasValue) {
                    throw new IllegalArgumentException(
                            "Invalid language tag: extension singleton without value: " + tag);
                }
                continue;
            }

            // If we get here, the subtag doesn't fit any valid position
            throw new IllegalArgumentException("Invalid language tag: " + tag);
        }

        // Validate transform extension structure
        if (extensionSingletons.contains('t')) {
            validateTransformExtension(tag, parts);
        }
    }

    /**
     * Validate the structure of a transform extension (t-extension) in a BCP 47 tag.
     * Per UTS 35: t-extension = "t" ("-" tlang)? ("-" tfield)*
     * tlang = unicode_language_id (2-3 or 5-8 alpha language, optional script/region/variants)
     * tfield = tkey tvalue+ (tkey = 2 chars alphanum+alpha, tvalue = 3-8 alphanum)
     */
    private static void validateTransformExtension(String tag, String[] allParts) {
        // Find the t-extension start
        int tStart = -1;
        for (int i = 0; i < allParts.length; i++) {
            if (allParts[i].length() == 1 && allParts[i].equalsIgnoreCase("t")) {
                tStart = i + 1;
                break;
            }
        }
        if (tStart < 0 || tStart >= allParts.length) {
            throw new IllegalArgumentException("Invalid language tag: empty t-extension: " + tag);
        }

        // Find the end of the t-extension (next singleton or end)
        int tEnd = allParts.length;
        for (int i = tStart; i < allParts.length; i++) {
            if (allParts[i].length() == 1) {
                tEnd = i;
                break;
            }
        }

        if (tStart == tEnd) {
            throw new IllegalArgumentException("Invalid language tag: empty t-extension: " + tag);
        }

        // Collect t-extension subtags
        List<String> parts = new ArrayList<>();
        for (int i = tStart; i < tEnd; i++) {
            parts.add(allParts[i].toLowerCase(Locale.ROOT));
        }

        int idx = 0;
        String first = parts.get(0);

        // Determine if first subtag is a tlang language
        // Language: 2-3 alpha or 5-8 alpha. NOT 4 alpha (that would be invalid) or 1, 9+ alpha.
        boolean hasTlang = false;
        if (first.chars().allMatch(Character::isLetter)) {
            if (first.length() == 4) {
                throw new IllegalArgumentException("Invalid language tag: invalid tlang in t-extension: " + tag);
            }
            if (first.length() >= 9) {
                throw new IllegalArgumentException("Invalid language tag: invalid tlang in t-extension: " + tag);
            }
            if ((first.length() >= 2 && first.length() <= 3) || (first.length() >= 5 && first.length() <= 8)) {
                hasTlang = true;
            }
        }

        if (hasTlang) {
            idx++; // consume language

            // Check for invalid extlang (3 alpha after language) — not allowed in unicode_language_id
            if (idx < parts.size()) {
                String next = parts.get(idx);
                if (next.length() == 3 && next.chars().allMatch(Character::isLetter)) {
                    throw new IllegalArgumentException(
                            "Invalid language tag: extlang not allowed in tlang: " + tag);
                }
            }

            // Optional script (4 alpha)
            if (idx < parts.size()) {
                String next = parts.get(idx);
                if (next.length() == 4 && next.chars().allMatch(Character::isLetter)) {
                    idx++;
                    // Check duplicate script
                    if (idx < parts.size()) {
                        String afterScript = parts.get(idx);
                        if (afterScript.length() == 4 && afterScript.chars().allMatch(Character::isLetter)) {
                            throw new IllegalArgumentException(
                                    "Invalid language tag: duplicate script in tlang: " + tag);
                        }
                    }
                }
            }

            // Optional region (2 alpha or 3 digit)
            if (idx < parts.size()) {
                String next = parts.get(idx);
                if ((next.length() == 2 && next.chars().allMatch(Character::isLetter)) ||
                        (next.length() == 3 && next.chars().allMatch(Character::isDigit))) {
                    idx++;
                }
            }

            // Optional variants (5-8 alphanum or 4 starting with digit)
            while (idx < parts.size()) {
                String next = parts.get(idx);
                boolean isVariant = (next.length() >= 5 && next.length() <= 8) ||
                        (next.length() == 4 && Character.isDigit(next.charAt(0)));
                if (!isVariant) {
                    break;
                }
                idx++;
            }
        }

        // Validate tfields
        while (idx < parts.size()) {
            String field = parts.get(idx);
            if (field.length() == 2 && Character.isLetter(field.charAt(0)) && Character.isDigit(field.charAt(1))) {
                idx++;
                boolean hasValue = false;
                while (idx < parts.size()) {
                    String val = parts.get(idx);
                    if (val.length() == 2 && Character.isLetter(val.charAt(0)) && Character.isDigit(val.charAt(1))) {
                        break; // Next tfield key
                    }
                    if (val.length() < 3 || val.length() > 8) {
                        throw new IllegalArgumentException("Invalid language tag: invalid tvalue: " + tag);
                    }
                    hasValue = true;
                    idx++;
                }
                if (!hasValue) {
                    throw new IllegalArgumentException("Invalid language tag: tfield key without value: " + tag);
                }
            } else {
                throw new IllegalArgumentException("Invalid language tag: unexpected subtag in t-extension: " + tag);
            }
        }
    }

    /**
     * Parse Unicode extension keys from a BCP 47 locale tag.
     */
    private static Map<String, String> parseUnicodeExtensions(String localeTag) {
        Map<String, String> extensions = new HashMap<>();
        if (localeTag == null) {
            return extensions;
        }
        String lowerTag = localeTag.toLowerCase(Locale.ROOT);
        int uIndex = -1;
        String[] parts = lowerTag.split("-");
        for (int i = 0; i < parts.length; i++) {
            if ("x".equals(parts[i]) && i > 0) {
                break;
            }
            if ("u".equals(parts[i]) && i > 0) {
                uIndex = i;
                break;
            }
        }
        if (uIndex < 0) {
            return extensions;
        }
        String currentKey = null;
        for (int i = uIndex + 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() == 1) {
                break;
            }
            if (part.length() == 2) {
                if (currentKey != null && !extensions.containsKey(currentKey)) {
                    extensions.put(currentKey, "true");
                }
                currentKey = part;
            } else if (currentKey != null) {
                extensions.put(currentKey, part);
                currentKey = null;
            }
        }
        if (currentKey != null && !extensions.containsKey(currentKey)) {
            extensions.put(currentKey, "true");
        }
        return extensions;
    }

    public static JSValue pluralRulesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(pluralRules.getLocale().toLanguageTag()));
        resolvedOptions.set("type", new JSString(pluralRules.getType()));
        JSArray categories = context.createJSArray();
        if ("ordinal".equals(pluralRules.getType())) {
            categories.push(new JSString("one"));
            categories.push(new JSString("two"));
            categories.push(new JSString("few"));
            categories.push(new JSString("other"));
        } else {
            categories.push(new JSString("one"));
            categories.push(new JSString("other"));
        }
        resolvedOptions.set("pluralCategories", categories);
        return resolvedOptions;
    }

    public static JSValue pluralRulesSelect(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.select called on incompatible receiver");
        }
        double value = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : Double.NaN;
        return new JSString(pluralRules.select(value));
    }

    /**
     * Read a string option from options object, validate against allowed values.
     * Returns null if option is undefined. Throws RangeError for invalid values.
     */
    private static String readValidatedOption(JSContext context, JSObject optionsObject,
                                               String key, String... allowedValues) {
        JSValue rawValue = optionsObject.get(context, PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        String stringValue = JSTypeConversions.toString(context, rawValue).value();
        for (String allowed : allowedValues) {
            if (allowed.equals(stringValue)) {
                return stringValue;
            }
        }
        context.throwRangeError("Value " + stringValue + " out of range for property " + key);
        return null;
    }

    /**
     * Read a string option from options object. Returns null if option is undefined.
     */
    private static String readStringOption(JSContext context, JSObject optionsObject, String key) {
        JSValue rawValue = optionsObject.get(context, PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        return JSTypeConversions.toString(context, rawValue).value();
    }

    public static JSValue relativeTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.format called on incompatible receiver");
        }
        if (args.length < 2) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.format requires value and unit");
        }
        try {
            double value = JSTypeConversions.toNumber(context, args[0]).value();
            String unit = JSTypeConversions.toString(context, args[1]).value();
            return new JSString(relativeTimeFormat.format(value, unit));
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue relativeTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(relativeTimeFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("style", new JSString(relativeTimeFormat.getStyle()));
        resolvedOptions.set("numeric", new JSString(relativeTimeFormat.getNumeric()));
        resolvedOptions.set("numberingSystem", new JSString(relativeTimeFormat.getNumberingSystem()));
        return resolvedOptions;
    }

    private static Locale resolveLocale(JSContext context, JSValue[] args, int index) {
        if (index >= args.length || args[index].isUndefined()) {
            return Locale.getDefault();
        }
        // Per ECMA-402 CanonicalizeLocaleList: null → TypeError
        if (args[index] instanceof JSNull) {
            context.throwTypeError("Cannot convert null to object");
            return null;
        }
        List<String> canonicalLocales = canonicalizeLocaleList(context, args[index]);
        if (context.hasPendingException()) {
            return null;
        }
        if (canonicalLocales.isEmpty()) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(canonicalLocales.get(0));
    }

    /**
     * Check if a calendar name is supported by the Java platform.
     */
    private static boolean isSupportedCalendar(String calendar) {
        if (calendar == null) {
            return false;
        }
        // Known supported calendars
        return switch (calendar) {
            case "buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic",
                 "gregory", "hebrew", "indian", "islamic-civil", "islamic-tbla",
                 "islamic-umalqura", "iso8601", "japanese", "persian", "roc" -> true;
            default -> false;
        };
    }

    /**
     * Get the default hourCycle for a locale by inspecting the locale's time format pattern.
     */
    private static String getLocaleDefaultHourCycle(Locale locale) {
        java.text.DateFormat df = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale);
        if (df instanceof java.text.SimpleDateFormat sdf) {
            String pattern = sdf.toPattern();
            if (pattern.contains("K")) {
                return "h11"; // 0-11 hour (ja, ko)
            }
            if (pattern.contains("h")) {
                return "h12"; // 1-12 hour (en, es)
            }
            if (pattern.contains("H")) {
                return "h23"; // 0-23 hour (fr, de)
            }
            if (pattern.contains("k")) {
                return "h24"; // 1-24 hour
            }
        }
        return "h23"; // Default fallback
    }

    /**
     * Build resolved locale tag according to ECMA-402 ResolveLocale rules.
     * Unicode extension keys are included only if:
     * - The extension value matched the resolved value AND
     * - No option explicitly overrode it
     */
    private static Locale buildResolvedLocale(Locale locale, Map<String, String> unicodeExtensions,
                                              String calendarFromOption, String resolvedCalendar,
                                              String hourCycleFromOption, String resolvedHourCycle,
                                              Boolean hour12,
                                              String numberingSystemFromOption, String resolvedNumberingSystem) {
        // Start from base locale without unicode extensions
        Locale baseLocale = stripUnicodeExtensions(locale);
        String baseTag = baseLocale.toLanguageTag();

        StringBuilder extensionBuilder = new StringBuilder();

        // Calendar extension: include if extension value == resolved AND no option override
        String extensionCalendar = unicodeExtensions.get("ca");
        if (extensionCalendar != null && calendarFromOption == null) {
            String canonicalizedExtCal = canonicalizeCalendar(extensionCalendar);
            if (canonicalizedExtCal != null && canonicalizedExtCal.equals(resolvedCalendar)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("ca-").append(resolvedCalendar);
            }
        } else if (extensionCalendar != null && calendarFromOption != null) {
            // Option overrides extension: include only if they match
            String canonicalizedExtCal = canonicalizeCalendar(extensionCalendar);
            if (canonicalizedExtCal != null && canonicalizedExtCal.equals(resolvedCalendar)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("ca-").append(resolvedCalendar);
            }
        }

        // Hour cycle extension: include if extension value == resolved AND no option/hour12 override
        String extensionHourCycle = unicodeExtensions.get("hc");
        if (extensionHourCycle != null && hourCycleFromOption == null && hour12 == null) {
            if (extensionHourCycle.equals(resolvedHourCycle)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("hc-").append(resolvedHourCycle);
            }
        } else if (extensionHourCycle != null && hourCycleFromOption != null && hour12 == null) {
            // hourCycle option overrides: include only if they match
            if (extensionHourCycle.equals(resolvedHourCycle)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("hc-").append(resolvedHourCycle);
            }
        }
        // If hour12 was set, don't include hc extension (hour12 clears hc)

        // Numbering system extension: include if extension value == resolved AND no option override
        String extensionNu = unicodeExtensions.get("nu");
        if (extensionNu != null && numberingSystemFromOption == null) {
            if (extensionNu.toLowerCase(Locale.ROOT).equals(resolvedNumberingSystem)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("nu-").append(resolvedNumberingSystem);
            }
        } else if (extensionNu != null && numberingSystemFromOption != null) {
            if (extensionNu.toLowerCase(Locale.ROOT).equals(resolvedNumberingSystem)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("nu-").append(resolvedNumberingSystem);
            }
        }

        if (extensionBuilder.length() > 0) {
            return Locale.forLanguageTag(baseTag + "-u-" + extensionBuilder);
        }
        return baseLocale;
    }

    /**
     * Strip unicode extension subtags from a locale tag.
     */
    private static Locale stripUnicodeExtensions(Locale locale) {
        String tag = locale.toLanguageTag();
        String lowerTag = tag.toLowerCase(Locale.ROOT);
        int xIndex = lowerTag.indexOf("-x-");
        int uIndex = lowerTag.indexOf("-u-");
        if (uIndex < 0 || (xIndex >= 0 && xIndex < uIndex)) {
            return locale;
        }
        String[] parts = tag.split("-");
        StringBuilder builder = new StringBuilder();
        boolean inUnicodeExt = false;
        boolean inPrivateUse = false;
        for (String part : parts) {
            if (!inPrivateUse && part.length() == 1 && "x".equalsIgnoreCase(part)) {
                inPrivateUse = true;
                if (!builder.isEmpty()) {
                    builder.append("-");
                }
                builder.append(part);
                continue;
            }
            if (inPrivateUse) {
                if (!builder.isEmpty()) {
                    builder.append("-");
                }
                builder.append(part);
                continue;
            }
            if (part.length() == 1 && "u".equalsIgnoreCase(part)) {
                inUnicodeExt = true;
                continue;
            }
            if (inUnicodeExt) {
                if (part.length() == 1) {
                    inUnicodeExt = false;
                    if (!builder.isEmpty()) {
                        builder.append("-");
                    }
                    builder.append(part);
                }
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("-");
            }
            builder.append(part);
        }
        String strippedTag = builder.toString();
        if (strippedTag.isEmpty()) {
            return locale;
        }
        return Locale.forLanguageTag(strippedTag);
    }

    private static Locale stripCalendarUnicodeExtension(Locale locale) {
        String languageTag = locale.toLanguageTag();
        String[] parts = languageTag.split("-");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if ("u".equalsIgnoreCase(part)) {
                if (builder.length() > 0) {
                    builder.append('-');
                }
                builder.append(part);
                index++;
                while (index < parts.length) {
                    String key = parts[index];
                    if (key.length() != 2) {
                        index++;
                        continue;
                    }

                    if ("ca".equalsIgnoreCase(key)) {
                        index++;
                        while (index < parts.length && parts[index].length() > 2) {
                            index++;
                        }
                        continue;
                    }

                    boolean keepKey = false;
                    if ("hc".equalsIgnoreCase(key)) {
                        keepKey = true;
                    } else if ("nu".equalsIgnoreCase(key)) {
                        int valueIndex = index + 1;
                        if (valueIndex < parts.length && parts[valueIndex].length() > 2) {
                            String value = parts[valueIndex].toLowerCase(Locale.ROOT);
                            keepKey = isSupportedDateTimeNumberingSystem(value);
                        }
                    }

                    if (keepKey) {
                        builder.append('-').append(key);
                    }
                    index++;
                    while (index < parts.length && parts[index].length() > 2) {
                        if (keepKey) {
                            builder.append('-').append(parts[index]);
                        }
                        index++;
                    }
                }
                String filteredTag = builder.toString();
                if (filteredTag.endsWith("-u")) {
                    filteredTag = filteredTag.substring(0, filteredTag.length() - 2);
                    if (filteredTag.endsWith("-")) {
                        filteredTag = filteredTag.substring(0, filteredTag.length() - 1);
                    }
                }
                if (filteredTag.isEmpty()) {
                    return locale;
                }
                return Locale.forLanguageTag(filteredTag);
            }
            if (builder.length() > 0) {
                builder.append('-');
            }
            builder.append(part);
        }
        return locale;
    }

    private static boolean isSupportedDateTimeNumberingSystem(String numberingSystem) {
        if (numberingSystem == null) {
            return false;
        }
        return SUPPORTED_NUMBERING_SYSTEMS.contains(numberingSystem);
    }

    public static JSValue supportedLocalesOf(JSContext context, JSValue thisArg, JSValue[] args) {
        try {
            JSArray localesArray = context.createJSArray();
            if (args.length > 0) {
                for (String localeTag : canonicalizeLocaleList(context, args[0])) {
                    Locale locale = Locale.forLanguageTag(localeTag);
                    String language = locale.getLanguage().toLowerCase(Locale.ROOT);
                    if (!language.isEmpty() && AVAILABLE_LOCALE_LANGUAGES.contains(language)) {
                        localesArray.push(new JSString(localeTag));
                    }
                }
            }
            return localesArray;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    /**
     * Lazy-initialized map of lowercased timezone ID → canonical casing.
     * Built from ZoneId.getAvailableZoneIds() plus supplementary valid IANA
     * timezone IDs that Java's ZoneId does not include.
     */
    private static volatile Map<String, String> TIMEZONE_LOOKUP_MAP;

    /**
     * Supplementary valid IANA timezone IDs not present in ZoneId.getAvailableZoneIds().
     * These are real IANA TZDB entries that some JDK versions omit.
     */
    private static final String[] SUPPLEMENTARY_TIMEZONE_IDS = {
            "EST", "MST", "HST",         // IANA backward-compat Zone entries
            "GMT+0", "GMT-0", "GMT0",     // IANA backward-compat Link entries
            "ROC",                         // Republic of China → Asia/Taipei
    };

    private static Map<String, String> getTimezoneLookupMap() {
        if (TIMEZONE_LOOKUP_MAP == null) {
            synchronized (JSIntlObject.class) {
                if (TIMEZONE_LOOKUP_MAP == null) {
                    Map<String, String> map = new HashMap<>();
                    // Add all ZoneId timezone IDs
                    for (String id : ZoneId.getAvailableZoneIds()) {
                        map.putIfAbsent(id.toLowerCase(Locale.ROOT), id);
                    }
                    // Add supplementary valid IANA IDs
                    for (String id : SUPPLEMENTARY_TIMEZONE_IDS) {
                        map.putIfAbsent(id.toLowerCase(Locale.ROOT), id);
                    }
                    TIMEZONE_LOOKUP_MAP = map;
                }
            }
        }
        return TIMEZONE_LOOKUP_MAP;
    }

    /**
     * Validate and canonicalize a time zone string.
     * Returns the canonical time zone name, or null if invalid.
     */
    private static String validateAndCanonicalizeTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isEmpty()) {
            return null;
        }
        // Reject U+2212 MINUS SIGN per ECMA-402: only ASCII hyphen-minus is valid
        if (timeZone.indexOf('\u2212') >= 0) {
            return null;
        }
        // Reject non-ASCII letters
        for (int i = 0; i < timeZone.length(); i++) {
            char c = timeZone.charAt(i);
            if (c > 127 && Character.isLetter(c)) {
                return null;
            }
        }
        // Check for offset timezone format
        if (timeZone.startsWith("+") || timeZone.startsWith("-")) {
            return normalizeOffsetTimeZone(timeZone);
        }
        // Named timezone - case-insensitive lookup via the comprehensive map
        String canonical = getTimezoneLookupMap().get(timeZone.toLowerCase(Locale.ROOT));
        if (canonical != null) {
            return canonical;
        }
        return null;
    }

    /**
     * Normalize an offset timezone string to the canonical form: +HH:MM or -HH:MM.
     * -00 and -00:00 normalize to +00:00.
     */
    private static String normalizeOffsetTimeZone(String timeZone) {
        char sign = timeZone.charAt(0);
        String offsetPart = timeZone.substring(1);

        // Reject any non-digit, non-colon characters (e.g., '.', ',')
        for (int i = 0; i < offsetPart.length(); i++) {
            char c = offsetPart.charAt(i);
            if (c != ':' && (c < '0' || c > '9')) {
                return null;
            }
        }

        int hours;
        int minutes;

        if (offsetPart.contains(":")) {
            String[] hm = offsetPart.split(":", -1);
            if (hm.length != 2) {
                return null;
            }
            // Both parts must be exactly 2 digits
            if (hm[0].length() != 2 || hm[1].length() != 2) {
                return null;
            }
            try {
                hours = Integer.parseInt(hm[0]);
                minutes = Integer.parseInt(hm[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (offsetPart.length() == 4) {
            try {
                hours = Integer.parseInt(offsetPart.substring(0, 2));
                minutes = Integer.parseInt(offsetPart.substring(2, 4));
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (offsetPart.length() == 2) {
            try {
                hours = Integer.parseInt(offsetPart);
                minutes = 0;
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }

        if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
            return null;
        }

        // Normalize -00:00 to +00:00
        if (hours == 0 && minutes == 0) {
            sign = '+';
        }

        return String.format("%c%02d:%02d", sign, hours, minutes);
    }
}
