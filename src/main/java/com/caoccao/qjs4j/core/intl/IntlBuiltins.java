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

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of Intl object and Intl.* prototype methods.
 */
public abstract class IntlBuiltins {
    static final Set<String> SANCTIONED_SIMPLE_UNITS;

    private static final List<String> SORTED_SIMPLE_UNITS = List.of("acre", "bit", "byte", "celsius", "centimeter",
            "day", "degree", "fahrenheit", "fluid-ounce", "foot", "gallon", "gigabit", "gigabyte", "gram", "hectare",
            "hour", "inch", "kilobit", "kilobyte", "kilogram", "kilometer", "liter", "megabit", "megabyte", "meter",
            "microsecond", "mile", "mile-scandinavian", "milliliter", "millimeter", "millisecond", "minute", "month",
            "nanosecond", "ounce", "percent", "petabyte", "pound", "second", "stone", "terabit", "terabyte", "week",
            "yard", "year");
    /**
     * Supplementary valid IANA timezone IDs not present in ZoneId.getAvailableZoneIds(). These are real IANA TZDB
     * entries that some JDK versions omit.
     */
    private static final String[] SUPPLEMENTARY_TIMEZONE_IDS = {"EST", "MST", "HST", // IANA backward-compat Zone
                                                                                     // entries
            "GMT+0", "GMT-0", "GMT0", // IANA backward-compat Link entries
            "ROC", // Republic of China → Asia/Taipei
    };

    /**
     * Lazy-initialized map of lowercased timezone ID → canonical casing. Built from ZoneId.getAvailableZoneIds() plus
     * supplementary valid IANA timezone IDs that Java's ZoneId does not include.
     */
    private static volatile Map<String, String> TIMEZONE_LOOKUP_MAP;

    static {
        SANCTIONED_SIMPLE_UNITS = Set.copyOf(SORTED_SIMPLE_UNITS);
    }

    protected IntlBuiltins() {
    }

    static void appendMatchingExtension(StringBuilder tag, int baseLength, String key, String resolved,
            String requested) {
        if (resolved != null && resolved.equals(requested)) {
            tag.append(tag.length() == baseLength ? "-u-" : "-").append(key).append('-').append(resolved);
        }
    }

    /**
     * Canonicalize a calendar identifier per ECMA-402: - ASCII-only lowercase (not locale-sensitive) - Legacy alias
     * mapping (e.g., "islamicc" → "islamic-civil") - Deprecated calendar fallback ("islamic", "islamic-rgsa" →
     * "islamic-civil")
     */
    static String canonicalizeCalendar(String calendar) {
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
        // Deprecated calendars: implementation-defined fallback
        if ("islamic".equals(lowered) || "islamic-rgsa".equals(lowered)) {
            return "islamic-civil";
        }
        return IntlLocaleAliases.calendar(lowered);
    }

    static String canonicalizeLocaleTag(String localeTag) {
        return IntlLocaleTag.canonicalizeLocaleTag(localeTag);
    }

    public static JSValue collatorCompare(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlCollatorBuiltins.collatorCompare(context, thisArg, args);
    }

    public static JSValue collatorCompareGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlCollatorBuiltins.collatorCompareGetter(context, thisArg, args);
    }

    public static JSValue collatorResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlCollatorBuiltins.collatorResolvedOptions(context, thisArg, args);
    }

    public static JSValue createCollator(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlCollatorBuiltins.createCollator(context, prototype, args);
    }

    /**
     * Default entry: uses required="any", defaults="date" per Intl.DateTimeFormat constructor spec.
     */
    public static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        return createDateTimeFormat(context, prototype, args, "any", "date");
    }

    /**
     * Create DateTimeFormat with configurable ToDateTimeOptions behavior.
     *
     * @param required
     *            "date", "time", or "any" — which component types to check for needDefaults
     * @param defaults
     *            "date", "time", or "all" — which defaults to add when needDefaults is true
     */
    public static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args, String required,
            String defaults) {
        return IntlDateTimeFormatBuiltins.createDateTimeFormat(context, prototype, args, required, defaults);
    }

    /**
     * Intl.DisplayNames constructor factory. Per ECMA-402 §12.1.1: OrdinaryCreateFromConstructor (step 2) runs before
     * locale/options processing. Options are read in order: localeMatcher → style → type → fallback → languageDisplay
     */
    public static JSValue createDisplayNames(JSContext context, JSObject prototype, JSValue[] args) {
        IntlFormatOptions.ConstructorLocale constructor = IntlFormatOptions.ConstructorLocale.resolve(context,
                prototype, args, "DisplayNames");
        if (constructor == null) {
            return JSUndefined.INSTANCE;
        }

        // Step 4: Get options object (required, must be an object per GetOptionsObject)
        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
        if (optionsArg instanceof JSUndefined) {
            return context.throwTypeError("options must be an object");
        }
        if (!(optionsArg instanceof JSObject options)) {
            return context.throwTypeError(
                    "Cannot convert " + JSTypeConversions.toString(context, optionsArg).value() + " to object");
        }

        // localeMatcher
        JSValue matcherError = IntlFormatOptions.localeMatcherError(context, options);
        if (matcherError != null) {
            return matcherError;
        }

        // style (default: "long")
        String style = IntlFormatOptions.readStringOption(context, options, PropertyKey.STYLE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (style == null) {
            style = "long";
        } else if (!"narrow".equals(style) && !"short".equals(style) && !"long".equals(style)) {
            return context.throwRangeError("style must be \"narrow\", \"short\", or \"long\"");
        }

        // type (required — TypeError if undefined)
        String type = IntlFormatOptions.readStringOption(context, options, PropertyKey.TYPE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (type == null) {
            return context.throwTypeError("type is required");
        }
        if (!"language".equals(type) && !"region".equals(type) && !"script".equals(type) && !"currency".equals(type)
                && !"calendar".equals(type) && !"dateTimeField".equals(type)) {
            return context.throwRangeError(
                    "type must be \"language\", \"region\", \"script\", \"currency\", \"calendar\", or \"dateTimeField\"");
        }

        // fallback (default: "code")
        String fallback = IntlFormatOptions.readStringOption(context, options, PropertyKey.FALLBACK);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (fallback == null) {
            fallback = "code";
        } else if (!"code".equals(fallback) && !"none".equals(fallback)) {
            return context.throwRangeError("fallback must be \"code\" or \"none\"");
        }

        // languageDisplay (default: "dialect")
        String languageDisplay = IntlFormatOptions.readStringOption(context, options, PropertyKey.LANGUAGE_DISPLAY);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (languageDisplay == null) {
            languageDisplay = "dialect";
        } else if (!"dialect".equals(languageDisplay) && !"standard".equals(languageDisplay)) {
            return context.throwRangeError("languageDisplay must be \"dialect\" or \"standard\"");
        }

        // Create instance and set prototype
        JSIntlDisplayNames displayNames = new JSIntlDisplayNames(context, constructor.locale(), style, type, fallback,
                languageDisplay);
        return constructor.finish(displayNames);
    }

    /**
     * Intl.DurationFormat constructor.
     */
    public static JSValue createDurationFormat(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlDurationFormatBuiltins.createDurationFormat(context, prototype, args);
    }

    public static JSValue createListFormat(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlListFormatBuiltins.createListFormat(context, prototype, args);
    }

    public static JSValue createLocale(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlLocaleBuiltins.createLocale(context, prototype, args);
    }

    public static JSValue createNumberFormat(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlNumberFormatBuiltins.createNumberFormat(context, prototype, args);
    }

    static JSObject createPartObject(JSContext context, String type, String value) {
        JSObject part = context.createJSObject();
        part.set("type", new JSString(type));
        part.set("value", new JSString(value));
        return part;
    }

    static JSObject createPartObject(JSContext context, String type, String value, String source) {
        JSObject part = createPartObject(context, type, value);
        part.set("source", new JSString(source));
        return part;
    }

    public static JSValue createPluralRules(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlFormatOptions.constructWithLocaleMatcher(context, prototype, args, "PluralRules",
                IntlPluralRulesBuiltins::create);
    }

    public static JSValue createRelativeTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlFormatOptions.constructWithLocaleMatcher(context, prototype, args, "RelativeTimeFormat",
                IntlBuiltins::relativeTimeFormat);
    }

    public static JSValue createSegmenter(JSContext context, JSObject prototype, JSObject segmentsPrototype,
            JSValue[] args) {
        try {
            JSObject resolvedPrototype = IntlFormatOptions.resolveIntlPrototype(context, prototype, "Segmenter");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            Locale locale = Locale.getDefault();
            if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
                if (args[0] instanceof JSNull) {
                    return context.throwTypeError("Cannot convert null to object");
                }
                List<String> canonicalLocales = IntlLocaleList.canonicalizeLocaleList(context, args[0]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                for (String localeTag : canonicalLocales) {
                    Locale candidateLocale = IntlLocaleList.supportedLocale(localeTag);
                    if (candidateLocale != null) {
                        locale = candidateLocale;
                        break;
                    }
                }
            }

            JSValue options = IntlFormatOptions.coerceOptionsWithLocaleMatcher(context, args);
            if (context.hasPendingException() || !(options instanceof JSObject optionsObject)) {
                return options;
            }

            String granularity = IntlFormatOptions.normalizedStringOption(context, optionsObject,
                    PropertyKey.GRANULARITY, "grapheme", "grapheme", "word", "sentence");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSIntlSegmenter segmenter = new JSIntlSegmenter(context, locale, granularity, segmentsPrototype);
            if (resolvedPrototype != null) {
                segmenter.setPrototype(resolvedPrototype);
            }
            return segmenter;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue dateTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormat(context, thisArg, args);
    }

    /**
     * Getter for Intl.DateTimeFormat.prototype.format.
     */
    public static JSValue dateTimeFormatFormatGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormatGetter(context, thisArg, args);
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRange(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormatRange(context, thisArg, args);
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRangeToParts(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormatRangeToParts(context, thisArg, args);
    }

    /**
     * Intl.DateTimeFormat.prototype.formatToParts(date)
     */
    public static JSValue dateTimeFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormatToParts(context, thisArg, args);
    }

    public static JSValue dateTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatResolvedOptions(context, thisArg, args);
    }

    static void defineStringOption(JSObject options, String key, String value) {
        if (value != null) {
            options.createDataProperty(PropertyKey.fromString(key), new JSString(value));
        }
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
     * Intl.DisplayNames.prototype.resolvedOptions() Returns a new object with properties in the specified order.
     */
    public static JSValue displayNamesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDisplayNames displayNames)) {
            return context
                    .throwTypeError("Intl.DisplayNames.prototype.resolvedOptions called on incompatible receiver");
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

    /**
     * Intl.DurationFormat.prototype.format(duration)
     */
    public static JSValue durationFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDurationFormatBuiltins.durationFormatFormat(context, thisArg, args);
    }

    /**
     * Intl.DurationFormat.prototype.formatToParts(duration)
     */
    public static JSValue durationFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDurationFormatBuiltins.durationFormatFormatToParts(context, thisArg, args);
    }

    /**
     * Intl.DurationFormat.prototype.resolvedOptions()
     */
    public static JSValue durationFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDurationFormatBuiltins.durationFormatResolvedOptions(context, thisArg, args);
    }

    private static JSValue formatRelativeTime(JSContext context, JSValue thisArg, JSValue[] args, boolean parts) {
        String method = parts ? "formatToParts" : "format";
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context
                    .throwTypeError("Intl.RelativeTimeFormat.prototype." + method + " called on incompatible receiver");
        }
        if (args.length < 2) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype." + method + " requires value and unit");
        }

        double value = JSTypeConversions.toNumber(context, args[0]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String unitValue = JSTypeConversions.toString(context, args[1]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        try {
            return parts
                    ? relativeTimeFormat.formatToParts(context, value, unitValue)
                    : new JSString(relativeTimeFormat.format(value, unitValue));
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue getCanonicalLocales(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleList.getCanonicalLocales(context, thisArg, args);
    }

    /**
     * Get the default hourCycle for a locale by inspecting the locale's time format pattern.
     */
    static String getLocaleDefaultHourCycle(Locale locale) {
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

    static Map<String, String> getTimezoneLookupMap() {
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
     * Check if a calendar name is supported by the Java platform.
     */
    static boolean isSupportedCalendar(String calendar) {
        if (calendar == null) {
            return false;
        }
        // Known supported calendars
        return switch (calendar) {
            case "buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic", "gregory", "hebrew", "indian",
                    "islamic-civil", "islamic-tbla", "islamic-umalqura", "iso8601", "japanese", "persian", "roc" ->
                true;
            default -> false;
        };
    }

    public static JSValue listFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlListFormatBuiltins.listFormatFormat(context, thisArg, args);
    }

    public static JSValue listFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlListFormatBuiltins.listFormatFormatToParts(context, thisArg, args);
    }

    public static JSValue listFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlListFormatBuiltins.listFormatResolvedOptions(context, thisArg, args);
    }

    public static JSValue localeGetBaseName(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "baseName", JSIntlLocale::getBaseName);
    }

    public static JSValue localeGetCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "calendar", JSIntlLocale::getCalendar);
    }

    public static JSValue localeGetCalendars(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetCalendars(context, thisArg, args);
    }

    public static JSValue localeGetCaseFirst(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "caseFirst", JSIntlLocale::getCaseFirst);
    }

    public static JSValue localeGetCollation(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "collation", JSIntlLocale::getCollation);
    }

    public static JSValue localeGetCollations(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetCollations(context, thisArg, args);
    }

    public static JSValue localeGetFirstDayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "firstDayOfWeek", JSIntlLocale::getFirstDayOfWeek);
    }

    public static JSValue localeGetHourCycle(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "hourCycle", JSIntlLocale::getHourCycle);
    }

    public static JSValue localeGetHourCycles(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetHourCycles(context, thisArg, args);
    }

    public static JSValue localeGetLanguage(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "language", JSIntlLocale::getLanguage);
    }

    public static JSValue localeGetNumberingSystem(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "numberingSystem", JSIntlLocale::getNumberingSystem);
    }

    public static JSValue localeGetNumberingSystems(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetNumberingSystems(context, thisArg, args);
    }

    public static JSValue localeGetNumeric(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetNumeric(context, thisArg, args);
    }

    public static JSValue localeGetRegion(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "region", locale -> nullIfEmpty(locale.getRegion()));
    }

    public static JSValue localeGetScript(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "script", locale -> nullIfEmpty(locale.getScript()));
    }

    public static JSValue localeGetTextInfo(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetTextInfo(context, thisArg, args);
    }

    public static JSValue localeGetTimeZones(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetTimeZones(context, thisArg, args);
    }

    public static JSValue localeGetVariants(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "variants",
                locale -> nullIfEmpty(locale.getVariant()));
    }

    public static JSValue localeGetWeekInfo(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetWeekInfo(context, thisArg, args);
    }

    public static JSValue localeMaximize(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.changeLikelySubtags(context, thisArg, true);
    }

    public static JSValue localeMinimize(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.changeLikelySubtags(context, thisArg, false);
    }

    public static JSValue localeToString(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "toString", JSIntlLocale::getTag);
    }

    static String nullIfEmpty(String value) {
        return (value != null && !value.isEmpty()) ? value : null;
    }

    public static JSValue numberFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormat(context, thisArg, args);
    }

    public static JSValue numberFormatFormatGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormatGetter(context, thisArg, args);
    }

    public static JSValue numberFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormatRange(context, thisArg, args);
    }

    public static JSValue numberFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormatRangeToParts(context, thisArg, args);
    }

    public static JSValue numberFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormatToParts(context, thisArg, args);
    }

    public static JSValue numberFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatResolvedOptions(context, thisArg, args);
    }

    public static JSValue pluralRulesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlPluralRulesBuiltins.pluralRulesResolvedOptions(context, thisArg, args);
    }

    public static JSValue pluralRulesSelect(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlPluralRulesBuiltins.pluralRulesSelect(context, thisArg, args);
    }

    public static JSValue pluralRulesSelectRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlPluralRulesBuiltins.pluralRulesSelectRange(context, thisArg, args);
    }

    private static JSValue relativeTimeFormat(JSContext context, IntlFormatOptions.ConstructorLocale constructor,
            JSObject optionsObject) {
        String numberingSystemOption = IntlFormatOptions.readStringOption(context, optionsObject,
                PropertyKey.NUMBERING_SYSTEM);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (numberingSystemOption != null) {
            numberingSystemOption = numberingSystemOption.toLowerCase(Locale.ROOT);
            if (!IntlFormatOptions.UNICODE_TYPE_PATTERN.matcher(numberingSystemOption).matches()) {
                return context.throwRangeError("Invalid numberingSystem: " + numberingSystemOption);
            }
        }

        String style = IntlFormatOptions.normalizedStringOption(context, optionsObject, PropertyKey.STYLE, "long",
                "long", "short", "narrow");
        String numeric = IntlFormatOptions.normalizedStringOption(context, optionsObject, PropertyKey.NUMERIC, "always",
                "always", "auto");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        IntlFormatOptions.NumberingLocale numbering = IntlFormatOptions.NumberingLocale.resolve(constructor.locale(),
                numberingSystemOption);
        JSIntlRelativeTimeFormat relativeTimeFormat = new JSIntlRelativeTimeFormat(context, numbering.locale(), style,
                numeric, numbering.numberingSystem());
        return constructor.finish(relativeTimeFormat);
    }

    public static JSValue relativeTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatRelativeTime(context, thisArg, args, false);
    }

    public static JSValue relativeTimeFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatRelativeTime(context, thisArg, args, true);
    }

    public static JSValue relativeTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError(
                    "Intl.RelativeTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.createDataProperty(PropertyKey.LOCALE,
                new JSString(relativeTimeFormat.getLocale().toLanguageTag()));
        resolvedOptions.createDataProperty(PropertyKey.STYLE, new JSString(relativeTimeFormat.getStyle()));
        resolvedOptions.createDataProperty(PropertyKey.NUMERIC, new JSString(relativeTimeFormat.getNumeric()));
        resolvedOptions.createDataProperty(PropertyKey.NUMBERING_SYSTEM,
                new JSString(relativeTimeFormat.getNumberingSystem()));
        return resolvedOptions;
    }

    public static Locale resolveLocale(JSContext context, JSValue[] args, int index) {
        return IntlLocaleList.resolveLocale(context, args, index);
    }

    public static JSValue segmenterResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlSegmenter segmenter)) {
            return context.throwTypeError("Intl.Segmenter.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.createDataProperty(PropertyKey.LOCALE, new JSString(segmenter.getLocale().toLanguageTag()));
        resolvedOptions.createDataProperty(PropertyKey.GRANULARITY, new JSString(segmenter.getGranularity()));
        return resolvedOptions;
    }

    public static JSValue segmenterSegment(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlSegmenter segmenter)) {
            return context.throwTypeError("Intl.Segmenter.prototype.segment called on incompatible receiver");
        }
        JSValue stringValue = JSValue.argumentOrUndefined(args, 0);
        JSString text = JSTypeConversions.toString(context, stringValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return segmenter.createSegments(text.value());
    }

    public static JSValue segmentsContaining(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlSegments segments)) {
            return context.throwTypeError("%Segments.prototype%.containing called on incompatible receiver");
        }
        JSValue indexValue = JSValue.argumentOrUndefined(args, 0);
        return segments.containing(context, indexValue);
    }

    public static JSValue segmentsIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlSegments segments)) {
            return context.throwTypeError("%Segments.prototype%[@@iterator] called on incompatible receiver");
        }
        return segments.createIterator(context);
    }

    public static JSValue supportedLocalesOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleList.supportedLocalesOf(context, thisArg, args);
    }

    /**
     * Intl.supportedValuesOf(key) Returns a sorted array of supported unique values for the given key. See ECMA-402
     * §8.3.2
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
                values = new ArrayList<>(List.of("buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic",
                        "gregory", "hebrew", "indian", "islamic-civil", "islamic-tbla", "islamic-umalqura", "iso8601",
                        "japanese", "persian", "roc"));
            }
            case "collation" -> {
                // Per ECMA-402: must NOT include "standard" or "search"
                // "compat" not accepted by any major Collator implementation
                values = new ArrayList<>(List.of("big5han", "dict", "direct", "ducet", "emoji", "eor", "gb2312",
                        "phonebk", "phonetic", "pinyin", "reformed", "searchjl", "stroke", "trad", "unihan", "zhuyin"));
            }
            case "currency" -> {
                Set<Currency> currencies = Currency.getAvailableCurrencies();
                values = currencies.stream().map(Currency::getCurrencyCode).sorted().collect(Collectors.toList());
            }
            case "numberingSystem" -> {
                values = new ArrayList<>(IntlFormatOptions.SUPPORTED_NUMBERING_SYSTEMS);
                Collections.sort(values);
            }
            case "timeZone" -> {
                Set<String> zoneIds = ZoneId.getAvailableZoneIds();
                // IANA canonical timezone names: include Etc/GMT+N, Etc/GMT-N, UTC
                // Exclude SystemV/, EST/MST/HST (deprecated short names), and non-canonical aliases
                Set<String> nonCanonical = new HashSet<>(Set.of("EST", "MST", "HST", "GMT0", "Etc/GMT0",
                        "Etc/Greenwich", "Etc/UCT", "Etc/Universal", "Etc/Zulu", "UCT", "GMT-0", "GMT+0", "Universal",
                        "Zulu", "Greenwich", "Etc/GMT-0", "Etc/GMT+0", "Etc/GMT", "Etc/UTC", "GMT", "US/Alaska",
                        "US/Aleutian", "US/Arizona", "US/Central", "US/East-Indiana", "US/Eastern", "US/Hawaii",
                        "US/Indiana-Starke", "US/Michigan", "US/Mountain", "US/Pacific", "US/Samoa", "Canada/Atlantic",
                        "Canada/Central", "Canada/Eastern", "Canada/Mountain", "Canada/Newfoundland", "Canada/Pacific",
                        "Canada/Saskatchewan", "Canada/Yukon", "Australia/ACT", "Australia/Canberra", "Australia/LHI",
                        "Australia/NSW", "Australia/North", "Australia/Queensland", "Australia/South",
                        "Australia/Tasmania", "Australia/Victoria", "Australia/West", "Australia/Yancowinna",
                        "Brazil/Acre", "Brazil/DeNoronha", "Brazil/East", "Brazil/West", "Chile/Continental",
                        "Chile/EasterIsland", "Mexico/BajaNorte", "Mexico/BajaSur", "Mexico/General", "NZ", "NZ-CHAT",
                        "Navajo", "PRC", "ROC", "ROK", "W-SU", "Cuba", "Egypt", "Eire", "Hongkong", "Iceland", "Iran",
                        "Israel", "Jamaica", "Japan", "Kwajalein", "Libya", "Poland", "Portugal", "Singapore", "Turkey",
                        "America/Atka", "America/Knox_IN", "Asia/Ashkhabad", "Asia/Calcutta", "Asia/Dacca",
                        "Asia/Istanbul", "Asia/Macao", "Asia/Thimbu", "Asia/Ujung_Pandang", "Asia/Ulan_Bator",
                        "Atlantic/Jan_Mayen", "Pacific/Truk"));
                Collections.addAll(nonCanonical, "Europe/Nicosia", "Asia/Ashkhabad", "Asia/Calcutta", "Asia/Choibalsan",
                        "Asia/Chongqing", "Asia/Chungking", "Asia/Dacca", "Asia/Harbin", "Asia/Istanbul",
                        "Asia/Kashgar", "Asia/Katmandu", "Asia/Macao", "Asia/Rangoon", "Asia/Saigon", "Asia/Tel_Aviv",
                        "Asia/Thimbu", "Asia/Ujung_Pandang", "Asia/Ulan_Bator", "Africa/Asmera", "Africa/Timbuktu",
                        "Antarctica/South_Pole", "Australia/ACT", "Australia/Canberra", "Australia/Currie",
                        "Australia/LHI", "Australia/NSW", "Australia/North", "Australia/Queensland", "Australia/South",
                        "Australia/Tasmania", "Australia/Victoria", "Australia/West", "Australia/Yancowinna",
                        "Pacific/Enderbury", "Pacific/Johnston", "Pacific/Ponape", "Pacific/Samoa", "Pacific/Truk",
                        "Pacific/Yap", "Europe/Belfast", "Europe/Kiev", "Europe/Tiraspol", "Europe/Uzhgorod",
                        "Europe/Zaporozhye", "America/Argentina/ComodRivadavia", "America/Atka", "America/Buenos_Aires",
                        "America/Catamarca", "America/Coral_Harbour", "America/Cordoba", "America/Ensenada",
                        "America/Fort_Wayne", "America/Godthab", "America/Indianapolis", "America/Jujuy",
                        "America/Knox_IN", "America/Louisville", "America/Mendoza", "America/Montreal",
                        "America/Nipigon", "America/Pangnirtung", "America/Porto_Acre", "America/Rainy_River",
                        "America/Rosario", "America/Santa_Isabel", "America/Shiprock", "America/Thunder_Bay",
                        "America/Virgin", "America/Yellowknife", "US/Alaska", "US/Aleutian", "US/Arizona", "US/Central",
                        "US/East-Indiana", "US/Eastern", "US/Hawaii", "US/Indiana-Starke", "US/Michigan", "US/Mountain",
                        "US/Pacific", "US/Samoa", "Atlantic/Faeroe", "Atlantic/Jan_Mayen", "Brazil/Acre",
                        "Brazil/DeNoronha", "Brazil/East", "Brazil/West", "CET", "CST6CDT", "Canada/Atlantic",
                        "Canada/Central", "Canada/Eastern", "Canada/Mountain", "Canada/Newfoundland", "Canada/Pacific",
                        "Canada/Saskatchewan", "Canada/Yukon", "Chile/Continental", "Chile/EasterIsland", "Cuba", "EET",
                        "EST", "EST5EDT", "Egypt", "Eire", "Etc/GMT", "Etc/GMT+0", "Etc/GMT-0", "Etc/GMT0",
                        "Etc/Greenwich", "Etc/UCT", "Etc/UTC", "Etc/Universal", "Etc/Zulu", "GB", "GB-Eire", "GMT",
                        "GMT+0", "GMT-0", "GMT0", "Greenwich", "HST", "Hongkong", "Iceland", "Iran", "Israel",
                        "Jamaica", "Japan", "Kwajalein", "Libya", "MET", "MST", "MST7MDT", "Mexico/BajaNorte",
                        "Mexico/BajaSur", "Mexico/General", "NZ", "NZ-CHAT", "Navajo", "PRC", "PST8PDT", "Poland",
                        "Portugal", "ROC", "ROK", "Singapore", "Turkey", "UCT", "Universal", "W-SU", "WET", "Zulu");
                values = zoneIds.stream()
                        .filter(id -> !id.startsWith("SystemV/") && !nonCanonical.contains(id) && !id.equals("EST5EDT")
                                && !id.equals("CST6CDT") && !id.equals("MST7MDT") && !id.equals("PST8PDT"))
                        .sorted().collect(Collectors.toList());
            }
            case "unit" -> values = SORTED_SIMPLE_UNITS;
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
}
