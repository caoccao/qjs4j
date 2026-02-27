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
     * Check if a collation value is valid/supported.
     */
    private static final Set<String> VALID_COLLATION_TYPES = Set.of(
            "big5han", "compat", "dict", "direct", "ducet", "emoji", "eor",
            "gb2312", "phonebk", "phonetic", "pinyin", "reformed", "stroke",
            "trad", "unihan", "zhuyin"
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
        if (localeValue instanceof JSArray localeArray) {
            for (long i = 0; i < localeArray.getLength(); i++) {
                JSValue element = localeArray.get(i);
                // Per ECMA-402 §9.2.1 step 7.c.ii: element must be String or Object
                if (!(element instanceof JSString) && !(element instanceof JSObject)) {
                    context.throwTypeError(
                            JSTypeConversions.toString(context, element).value()
                                    + " is not a well-formed currency value");
                    return localeTags;
                }
                localeTags.add(JSTypeConversions.toString(context, element).value());
            }
        } else {
            localeTags.add(JSTypeConversions.toString(context, localeValue).value());
        }
        LinkedHashSet<String> canonicalLocales = new LinkedHashSet<>();
        for (String localeTag : localeTags) {
            canonicalLocales.add(parseLocaleTag(localeTag).toLanguageTag());
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
            if (calendar == null) {
                if (unicodeExtensions.containsKey("ca")) {
                    calendar = canonicalizeCalendar(unicodeExtensions.get("ca"));
                }
            }
            if (calendar == null) {
                calendar = "gregory";
            }

            if (numberingSystem == null && unicodeExtensions.containsKey("nu")) {
                String localeNumberingSystem = unicodeExtensions.get("nu").toLowerCase(Locale.ROOT);
                if (isSupportedDateTimeNumberingSystem(localeNumberingSystem)) {
                    numberingSystem = localeNumberingSystem;
                }
            }

            // Keep supported numbering system locale extension, but strip calendar extension.
            Locale resolvedLocale = stripCalendarUnicodeExtension(locale);

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
            String localeTag = JSTypeConversions.toString(context, args[0]).value();
            Locale locale = parseLocaleTag(localeTag);
            JSIntlLocale intlLocale = new JSIntlLocale(locale, locale.toLanguageTag());
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

            String style = normalizeOption(
                    getOptionString(context, optionsValue, "style"),
                    "decimal",
                    "decimal", "currency", "percent");
            String currency = getOptionString(context, optionsValue, "currency");

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
                    useSignificantDigits, maximumSignificantDigits);
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
            String style = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "style"),
                    "long",
                    "long", "short", "narrow");
            String numeric = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "numeric"),
                    "always",
                    "always", "auto");
            JSIntlRelativeTimeFormat relativeTimeFormat = new JSIntlRelativeTimeFormat(locale, style, numeric);
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
        resolvedOptions.set("locale", new JSString(dateTimeFormat.getLocale().toLanguageTag()));
        if (dateTimeFormat.getCalendar() != null) {
            resolvedOptions.set("calendar", new JSString(dateTimeFormat.getCalendar()));
        }
        if (dateTimeFormat.getNumberingSystem() != null) {
            resolvedOptions.set("numberingSystem", new JSString(dateTimeFormat.getNumberingSystem()));
        }
        resolvedOptions.set("timeZone", new JSString(dateTimeFormat.getTimeZone() != null
                ? dateTimeFormat.getTimeZone() : ZoneId.systemDefault().getId()));
        if (dateTimeFormat.getHourCycle() != null) {
            resolvedOptions.set("hourCycle", new JSString(dateTimeFormat.getHourCycle()));
        }
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
        if (dateTimeFormat.getDateStyle() != null) {
            resolvedOptions.set("dateStyle",
                    new JSString(dateTimeFormat.getDateStyle().name().toLowerCase(Locale.ROOT)));
        }
        if (dateTimeFormat.getTimeStyle() != null) {
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
            case "ja", "ko" -> "unihan".equals(collation);
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
        resolvedOptions.set("style", new JSString(numberFormat.getStyle()));
        if (numberFormat.getCurrency() != null) {
            resolvedOptions.set("currency", new JSString(numberFormat.getCurrency().toUpperCase(Locale.ROOT)));
        }
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

    private static Locale parseLocaleTag(String localeTag) {
        if (localeTag == null) {
            throw new IllegalArgumentException("Invalid language tag: null");
        }
        String normalizedTag = localeTag.strip();
        if (normalizedTag.isEmpty()) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag);
        }
        Locale locale = Locale.forLanguageTag(normalizedTag);
        if (locale.getLanguage().isEmpty() && !"und".equalsIgnoreCase(normalizedTag)
                && !normalizedTag.toLowerCase(Locale.ROOT).startsWith("und-")) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag);
        }
        return locale;
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
        return "latn".equals(numberingSystem)
                || "arab".equals(numberingSystem)
                || "deva".equals(numberingSystem)
                || "hanidec".equals(numberingSystem);
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
        // Check for offset timezone format
        if (timeZone.startsWith("+") || timeZone.startsWith("-")) {
            // Must be in format +HH:MM or +HHMM or +HH
            String normalized = timeZone;
            String offsetPart = normalized.substring(1);
            // Validate offset format
            if (offsetPart.contains(":")) {
                String[] hm = offsetPart.split(":");
                if (hm.length != 2) {
                    return null;
                }
                try {
                    int hours = Integer.parseInt(hm[0]);
                    int minutes = Integer.parseInt(hm[1]);
                    if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (offsetPart.length() == 4) {
                try {
                    int hours = Integer.parseInt(offsetPart.substring(0, 2));
                    int minutes = Integer.parseInt(offsetPart.substring(2, 4));
                    if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (offsetPart.length() == 2) {
                try {
                    int hours = Integer.parseInt(offsetPart);
                    if (hours < 0 || hours > 23) {
                        return null;
                    }
                    // Valid format like +05 → normalize to +05:00
                    return normalized.charAt(0) + String.format("%02d:00", hours);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
            // Validate via ZoneId
            try {
                ZoneId.of(normalized);
                return normalized;
            } catch (Exception e) {
                return null;
            }
        }
        // Named timezone - preserve as-is (don't canonicalize)
        try {
            ZoneId.of(timeZone);
            return timeZone;
        } catch (Exception e) {
            // Try case-insensitive lookup
            for (String available : ZoneId.getAvailableZoneIds()) {
                if (available.equalsIgnoreCase(timeZone)) {
                    return timeZone;
                }
            }
            return null;
        }
    }
}
