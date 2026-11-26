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

import com.caoccao.qjs4j.core.temporal.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.IsoDate;
import com.caoccao.qjs4j.core.temporal.IsoDateTime;

import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.FormatStyle;
import java.util.*;

/**
 * ECMA-402 DateTimeFormat initialization, Temporal conversion and range formatting.
 */
final class IntlDateTimeFormatBuiltins {
    private static final String[] FORMAT_STYLE_VALUES = {"short", "medium", "long", "full"};

    private static final BigInteger NANOS_PER_MILLISECOND = BigInteger.valueOf(1_000_000L);

    private static final String[] NUMERIC_WIDTHS = {"numeric", "2-digit"};

    private static final String[] TEXT_WIDTHS = {"long", "short", "narrow"};

    private static final IsoDate UNIX_EPOCH_DATE = new IsoDate(1970, 1, 1);

    private IntlDateTimeFormatBuiltins() {
    }

    /**
     * Build resolved locale tag according to ECMA-402 ResolveLocale rules. Unicode extension keys are included only if:
     * - The extension value matched the resolved value AND - No option explicitly overrode it
     */
    private static Locale buildResolvedLocale(Locale locale, Map<String, String> unicodeExtensions, String calendar,
            String hourCycle, Boolean hour12, String numberingSystem) {
        Locale base = IntlLocaleTag.stripUnicodeExtensions(locale);
        StringBuilder tag = new StringBuilder(base.toLanguageTag());
        int baseLength = tag.length();
        JSIntlObject.appendMatchingExtension(tag, baseLength, "ca", calendar,
                JSIntlObject.canonicalizeCalendar(unicodeExtensions.get("ca")));
        if (hour12 == null) {
            JSIntlObject.appendMatchingExtension(tag, baseLength, "hc", hourCycle, unicodeExtensions.get("hc"));
        }
        String nu = unicodeExtensions.get("nu");
        JSIntlObject.appendMatchingExtension(tag, baseLength, "nu", numberingSystem,
                nu == null ? null : nu.toLowerCase(Locale.ROOT));
        return tag.length() == baseLength ? base : Locale.forLanguageTag(tag.toString());
    }

    static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args, String required,
            String defaults) {
        try {
            Locale locale = JSIntlObject.resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSValue rawOptions = JSValue.argumentOrUndefined(args, 1);
            boolean optionsIsUndefined = rawOptions instanceof JSUndefined;

            // Convert options to Object per spec: undefined → null-prototype object, null → TypeError
            // Per ToDateTimeOptions: undefined → OrdinaryObjectCreate(null) to avoid Object.prototype pollution
            JSObject optionsObject;
            if (optionsIsUndefined) {
                optionsObject = IntlFormatOptions.emptyOptions(context);
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
            String localeMatcher = IntlFormatOptions.readStringOption(context, optionsObject, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!IntlFormatOptions.isValidLocaleMatcher(localeMatcher)) {
                return context.throwRangeError("Invalid option value " + localeMatcher + " for property localeMatcher");
            }

            // 2. calendar
            String calendar = IntlFormatOptions.readStringOption(context, optionsObject, "calendar");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!IntlFormatOptions.validateUnicodeType(context, "calendar", calendar)) {
                return context.getPendingException();
            }
            if (calendar != null) {
                calendar = JSIntlObject.canonicalizeCalendar(calendar);
            }

            // 3. numberingSystem
            String numberingSystem = IntlFormatOptions.readStringOption(context, optionsObject, "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!IntlFormatOptions.validateUnicodeType(context, "numberingSystem", numberingSystem)) {
                return context.getPendingException();
            }

            // 4. hour12
            JSValue hour12Value = optionsObject.get(PropertyKey.fromString("hour12"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // 5. hourCycle
            String hourCycle = IntlFormatOptions.readStringOption(context, optionsObject, "hourCycle");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (hourCycle != null && !"h11".equals(hourCycle) && !"h12".equals(hourCycle) && !"h23".equals(hourCycle)
                    && !"h24".equals(hourCycle)) {
                return context.throwRangeError("Invalid hourCycle: " + hourCycle);
            }

            // 6. timeZone
            String timeZone = IntlFormatOptions.readStringOption(context, optionsObject, "timeZone");
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
            String weekdayOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "weekday",
                    TEXT_WIDTHS);
            String eraOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "era", TEXT_WIDTHS);
            String yearOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "year", NUMERIC_WIDTHS);
            String monthOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "month", "numeric",
                    "2-digit", "long", "short", "narrow");
            String dayOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "day", NUMERIC_WIDTHS);
            String dayPeriodOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "dayPeriod",
                    TEXT_WIDTHS);
            String hourOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "hour", NUMERIC_WIDTHS);
            String minuteOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "minute",
                    NUMERIC_WIDTHS);
            String secondOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "second",
                    NUMERIC_WIDTHS);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // 16. fractionalSecondDigits (GetNumberOption: ToNumber, range check, then floor)
            Integer fractionalSecondDigits = null;
            JSValue fsdValue = optionsObject.get(PropertyKey.fromString("fractionalSecondDigits"));
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
            String timeZoneNameOption = IntlFormatOptions.readValidatedOption(context, optionsObject, "timeZoneName",
                    "long", "short", "longOffset", "shortOffset", "longGeneric", "shortGeneric");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // 18. formatMatcher
            String formatMatcher = IntlFormatOptions.readStringOption(context, optionsObject, "formatMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (formatMatcher != null && !"basic".equals(formatMatcher) && !"best fit".equals(formatMatcher)) {
                return context.throwRangeError("Invalid formatMatcher: " + formatMatcher);
            }

            // 19-20. dateStyle and timeStyle
            String dateStyleStr = IntlFormatOptions.readValidatedOption(context, optionsObject, "dateStyle",
                    FORMAT_STYLE_VALUES);
            String timeStyleStr = IntlFormatOptions.readValidatedOption(context, optionsObject, "timeStyle",
                    FORMAT_STYLE_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            FormatStyle dateStyle = parseFormatStyle(dateStyleStr);
            FormatStyle timeStyle = parseFormatStyle(timeStyleStr);

            // Check dateStyle/timeStyle conflict with explicit components
            if (dateStyle != null || timeStyle != null) {
                if (weekdayOption != null || eraOption != null || yearOption != null || monthOption != null
                        || dayOption != null || dayPeriodOption != null || hourOption != null || minuteOption != null
                        || secondOption != null || fractionalSecondDigits != null || timeZoneNameOption != null) {
                    return context.throwTypeError(
                            "dateStyle and timeStyle may not be used with other DateTimeFormat options");
                }
            }

            // Apply defaults per ECMA-402 ToDateTimeOptions(options, required, defaults):
            // 'required' determines which component types to check for needDefaults.
            // 'defaults' determines which defaults to add when needDefaults is true.
            boolean hasDefaultDateComponents = false;
            boolean hasDefaultTimeComponents = false;
            if (dateStyle == null && timeStyle == null) {
                boolean hasDateComponent = weekdayOption != null || yearOption != null || monthOption != null
                        || dayOption != null;
                boolean hasTimeComponent = dayPeriodOption != null || hourOption != null || minuteOption != null
                        || secondOption != null || fractionalSecondDigits != null;
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
                        hasDefaultDateComponents = true;
                    }
                    if ("time".equals(defaults) || "all".equals(defaults)) {
                        hourOption = "numeric";
                        minuteOption = "numeric";
                        secondOption = "numeric";
                        hasDefaultTimeComponents = true;
                    }
                }
            }

            // Resolve calendar from locale unicode extension if not specified in options
            Map<String, String> unicodeExtensions = IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag());
            // If option value is not a supported calendar, treat as if not specified
            if (calendar != null && !JSIntlObject.isSupportedCalendar(calendar)) {
                calendar = null;
            }
            if (calendar == null) {
                if (unicodeExtensions.containsKey("ca")) {
                    String extensionCalendar = JSIntlObject.canonicalizeCalendar(unicodeExtensions.get("ca"));
                    if (JSIntlObject.isSupportedCalendar(extensionCalendar)) {
                        calendar = extensionCalendar;
                    }
                }
            }
            if (calendar == null) {
                calendar = "gregory";
            }

            // Resolve numberingSystem
            // If option value is not supported, treat as if not specified
            if (numberingSystem != null && !isSupportedDateTimeNumberingSystem(numberingSystem)) {
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
            Locale resolvedLocale = buildResolvedLocale(locale, unicodeExtensions, calendar, hourCycle, hour12,
                    numberingSystem);

            // Finalize hourCycle for the instance:
            // - If no hour component, set to null (step 32 of CreateDateTimeFormat)
            // - If still null (no option/extension/hour12), use locale default
            String hourCycleForInstant = hourCycle;
            boolean hasHourComponent = hourOption != null || timeStyle != null;
            if (!hasHourComponent) {
                hourCycle = null;
            } else if (hourCycle == null) {
                hourCycle = JSIntlObject.getLocaleDefaultHourCycle(locale);
                hourCycleForInstant = hourCycle;
            }

            JSIntlDateTimeFormat dateTimeFormat = new JSIntlDateTimeFormat(context, resolvedLocale, dateStyle,
                    timeStyle, calendar, numberingSystem, timeZone, hourCycle, hourCycleForInstant, weekdayOption,
                    eraOption, yearOption, monthOption, dayOption, dayPeriodOption, hourOption, minuteOption,
                    secondOption, fractionalSecondDigits, timeZoneNameOption, hasDefaultDateComponents,
                    hasDefaultTimeComponents);
            JSObject resolvedDtfPrototype = IntlFormatOptions.resolveIntlPrototype(context, prototype,
                    "DateTimeFormat");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            dateTimeFormat.setPrototype(resolvedDtfPrototype);
            return dateTimeFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    private static JSIntlDateTimeFormat createTemporalDateTimeFormat(JSContext context,
            JSIntlDateTimeFormat baseDateTimeFormat, DateTimeFormattableKind formattableKind) {
        boolean instant = formattableKind == DateTimeFormattableKind.INSTANT;
        boolean fullDate = instant || formattableKind == DateTimeFormattableKind.PLAIN_DATE
                || formattableKind == DateTimeFormattableKind.PLAIN_DATE_TIME;
        boolean supportsYear = fullDate || formattableKind == DateTimeFormattableKind.PLAIN_YEAR_MONTH;
        boolean supportsDay = fullDate || formattableKind == DateTimeFormattableKind.PLAIN_MONTH_DAY;
        boolean supportsMonth = supportsYear || supportsDay;
        boolean supportsTime = instant || formattableKind == DateTimeFormattableKind.PLAIN_DATE_TIME
                || formattableKind == DateTimeFormattableKind.PLAIN_TIME;

        FormatStyle dateStyle = baseDateTimeFormat.getDateStyle();
        FormatStyle timeStyle = baseDateTimeFormat.getTimeStyle();
        String weekdayOption = baseDateTimeFormat.getWeekdayOption();
        String eraOption = baseDateTimeFormat.getEraOption();
        String yearOption = baseDateTimeFormat.getYearOption();
        String monthOption = baseDateTimeFormat.getMonthOption();
        String dayOption = baseDateTimeFormat.getDayOption();
        String dayPeriodOption = baseDateTimeFormat.getDayPeriodOption();
        String hourOption = baseDateTimeFormat.getHourOption();
        String minuteOption = baseDateTimeFormat.getMinuteOption();
        String secondOption = baseDateTimeFormat.getSecondOption();
        Integer fractionalSecondDigits = baseDateTimeFormat.getFractionalSecondDigits();

        if (baseDateTimeFormat.hasDefaultDateComponents()) {
            yearOption = null;
            monthOption = null;
            dayOption = null;
        }
        if (baseDateTimeFormat.hasDefaultTimeComponents()) {
            hourOption = null;
            minuteOption = null;
            secondOption = null;
        }

        if ((formattableKind == DateTimeFormattableKind.PLAIN_YEAR_MONTH
                || formattableKind == DateTimeFormattableKind.PLAIN_MONTH_DAY) && dateStyle != null) {
            DateStyleOptionFields dateStyleOptionFields = toDateStyleOptionFields(dateStyle);
            if (weekdayOption == null) {
                weekdayOption = dateStyleOptionFields.weekdayOption();
            }
            if (yearOption == null) {
                yearOption = dateStyleOptionFields.yearOption();
            }
            if (monthOption == null) {
                monthOption = dateStyleOptionFields.monthOption();
            }
            if (dayOption == null) {
                dayOption = dateStyleOptionFields.dayOption();
            }
            dateStyle = null;
        }

        boolean hasTime = timeStyle != null || dayPeriodOption != null || hourOption != null || minuteOption != null
                || secondOption != null || fractionalSecondDigits != null;
        // An era by itself does not trigger the incompatible-options error.
        boolean requestedComponents = hasTime || dateStyle != null || weekdayOption != null || yearOption != null
                || monthOption != null || dayOption != null;

        if (!supportsTime) {
            timeStyle = null;
            dayPeriodOption = null;
            hourOption = null;
            minuteOption = null;
            secondOption = null;
            fractionalSecondDigits = null;
            hasTime = false;
        }
        if (!supportsMonth) {
            dateStyle = null;
            monthOption = null;
        }
        if (!fullDate) {
            weekdayOption = null;
        }
        if (!supportsYear) {
            eraOption = null;
            yearOption = null;
        }
        if (!supportsDay) {
            dayOption = null;
        }

        boolean hasDate = dateStyle != null || weekdayOption != null || yearOption != null || monthOption != null
                || dayOption != null;
        if (!hasDate && !hasTime) {
            if (requestedComponents && eraOption == null) {
                context.throwTypeError("Invalid date/time formatting options");
                return null;
            }
            if (supportsYear) {
                yearOption = "numeric";
            }
            if (supportsMonth) {
                monthOption = "numeric";
            }
            if (supportsDay) {
                dayOption = "numeric";
            }
            if (supportsTime) {
                hourOption = "numeric";
                minuteOption = "numeric";
                secondOption = "numeric";
                hasTime = true;
            }
        }
        String effectiveHourCycle = instant || hasTime
                ? resolveHourCycle(baseDateTimeFormat)
                : baseDateTimeFormat.getHourCycle();

        return new JSIntlDateTimeFormat(context, baseDateTimeFormat.getLocale(), dateStyle, timeStyle,
                baseDateTimeFormat.getCalendar(), baseDateTimeFormat.getNumberingSystem(),
                instant ? baseDateTimeFormat.getTimeZone() : "UTC", effectiveHourCycle,
                instant ? effectiveHourCycle : baseDateTimeFormat.getHourCycleForInstant(), weekdayOption, eraOption,
                yearOption, monthOption, dayOption, dayPeriodOption, hourOption, minuteOption, secondOption,
                fractionalSecondDigits, instant ? baseDateTimeFormat.getTimeZoneNameOption() : null, false, false);
    }

    private static JSValue datePartsToJSArray(JSContext context, List<JSIntlDateTimeFormat.DatePart> parts) {
        JSArray array = context.createJSArray();
        for (JSIntlDateTimeFormat.DatePart part : parts) {
            JSObject partObj = JSIntlObject.createPartObject(context, part.type(), part.value());
            array.push(partObj);
        }
        return array;
    }

    static JSValue dateTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDateTime(context, thisArg, args, false);
    }

    static JSValue dateTimeFormatFormatGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.format called on incompatible receiver");
        }
        JSFunction cachedBoundFormatFunction = dateTimeFormat.getBoundFormatFunction();
        if (cachedBoundFormatFunction != null) {
            return cachedBoundFormatFunction;
        }
        JSNativeFunction boundFormatFunction = new JSNativeFunction(context, "", 1, (childContext, thisValue,
                formatArgs) -> dateTimeFormatFormat(childContext, dateTimeFormat, formatArgs));
        context.transferPrototype(boundFormatFunction, JSFunction.NAME);
        dateTimeFormat.setBoundFormatFunction(boundFormatFunction);
        return boundFormatFunction;
    }

    static JSValue dateTimeFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDateRange(context, thisArg, args, false);
    }

    static JSValue dateTimeFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDateRange(context, thisArg, args, true);
    }

    static JSValue dateTimeFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDateTime(context, thisArg, args, true);
    }

    static JSValue dateTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context
                    .throwTypeError("Intl.DateTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();

        // Property order per ECMA-402: locale, calendar, numberingSystem, timeZone,
        // hourCycle, hour12, weekday, era, year, month, day, dayPeriod,
        // hour, minute, second, fractionalSecondDigits, timeZoneName, dateStyle, timeStyle
        JSIntlObject.createDataProperty(resolvedOptions, "locale",
                new JSString(dateTimeFormat.getLocale().toLanguageTag()));
        JSIntlObject.defineStringOption(resolvedOptions, "calendar", dateTimeFormat.getCalendar());
        if (dateTimeFormat.getNumberingSystem() != null) {
            JSIntlObject.createDataProperty(resolvedOptions, "numberingSystem",
                    new JSString(dateTimeFormat.getNumberingSystem()));
        } else {
            JSIntlObject.createDataProperty(resolvedOptions, "numberingSystem", new JSString("latn"));
        }
        JSIntlObject.createDataProperty(resolvedOptions, "timeZone", new JSString(
                dateTimeFormat.getTimeZone() != null ? dateTimeFormat.getTimeZone() : ZoneId.systemDefault().getId()));

        // hourCycle and hour12: only present when hour component is in use
        String hourCycle = dateTimeFormat.getHourCycle();
        if (hourCycle != null) {
            JSIntlObject.createDataProperty(resolvedOptions, "hourCycle", new JSString(hourCycle));
            boolean isHour12 = "h11".equals(hourCycle) || "h12".equals(hourCycle);
            JSIntlObject.createDataProperty(resolvedOptions, "hour12", isHour12 ? JSBoolean.TRUE : JSBoolean.FALSE);
        }

        // When dateStyle or timeStyle is set, individual component options are NOT reported
        boolean hasDateStyle = dateTimeFormat.getDateStyle() != null;
        boolean hasTimeStyle = dateTimeFormat.getTimeStyle() != null;

        if (!hasDateStyle && !hasTimeStyle) {
            // Component options only when not using styles
            JSIntlObject.defineStringOption(resolvedOptions, "weekday", dateTimeFormat.getWeekdayOption());
            JSIntlObject.defineStringOption(resolvedOptions, "era", dateTimeFormat.getEraOption());
            JSIntlObject.defineStringOption(resolvedOptions, "year", dateTimeFormat.getYearOption());
            JSIntlObject.defineStringOption(resolvedOptions, "month", dateTimeFormat.getMonthOption());
            JSIntlObject.defineStringOption(resolvedOptions, "day", dateTimeFormat.getDayOption());
            JSIntlObject.defineStringOption(resolvedOptions, "dayPeriod", dateTimeFormat.getDayPeriodOption());
            JSIntlObject.defineStringOption(resolvedOptions, "hour", dateTimeFormat.getHourOption());
            JSIntlObject.defineStringOption(resolvedOptions, "minute", dateTimeFormat.getMinuteOption());
            JSIntlObject.defineStringOption(resolvedOptions, "second", dateTimeFormat.getSecondOption());
            if (dateTimeFormat.getFractionalSecondDigits() != null) {
                JSIntlObject.createDataProperty(resolvedOptions, "fractionalSecondDigits",
                        JSNumber.of(dateTimeFormat.getFractionalSecondDigits()));
            }
            JSIntlObject.defineStringOption(resolvedOptions, "timeZoneName", dateTimeFormat.getTimeZoneNameOption());
        }
        if (hasDateStyle) {
            JSIntlObject.createDataProperty(resolvedOptions, "dateStyle",
                    new JSString(dateTimeFormat.getDateStyle().name().toLowerCase(Locale.ROOT)));
        }
        if (hasTimeStyle) {
            JSIntlObject.createDataProperty(resolvedOptions, "timeStyle",
                    new JSString(dateTimeFormat.getTimeStyle().name().toLowerCase(Locale.ROOT)));
        }
        return resolvedOptions;
    }

    private static boolean differsOnlyInTimeParts(List<JSIntlDateTimeFormat.DatePart> startParts,
            List<JSIntlDateTimeFormat.DatePart> endParts) {
        if (startParts.size() != endParts.size()) {
            return false;
        }
        boolean hasDifference = false;
        boolean hasHourDifference = false;
        for (int index = 0; index < startParts.size(); index++) {
            JSIntlDateTimeFormat.DatePart startPart = startParts.get(index);
            JSIntlDateTimeFormat.DatePart endPart = endParts.get(index);
            if (startPart.equals(endPart)) {
                continue;
            }
            hasDifference = true;
            String type = startPart.type();
            if (!"hour".equals(type) && !"minute".equals(type) && !"second".equals(type)
                    && !"fractionalSecond".equals(type) && !"dayPeriod".equals(type)) {
                return false;
            }
            if ("hour".equals(type)) {
                hasHourDifference = true;
            }
        }
        return hasDifference && hasHourDifference;
    }

    private static JSValue formatDateRange(JSContext context, JSValue thisArg, JSValue[] args, boolean toParts) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            String method = toParts ? "formatRangeToParts" : "formatRange";
            return context
                    .throwTypeError("Intl.DateTimeFormat.prototype." + method + " called on incompatible receiver");
        }
        JSValue startArg = JSValue.argumentOrUndefined(args, 0);
        JSValue endArg = JSValue.argumentOrUndefined(args, 1);
        if (startArg instanceof JSUndefined || endArg instanceof JSUndefined) {
            return context.throwTypeError("start date or end date is undefined");
        }
        DateTimeFormattable startFormattable = toDateTimeFormattable(context, startArg, false);
        if (startFormattable == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DateTimeFormattable endFormattable = toDateTimeFormattable(context, endArg, false);
        if (endFormattable == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (startFormattable.kind() != endFormattable.kind()) {
            return context.throwTypeError("Invalid range");
        }
        if (startFormattable.calendarId() != null && endFormattable.calendarId() != null
                && !startFormattable.calendarId().equals(endFormattable.calendarId())) {
            return context.throwRangeError("Invalid date/time value");
        }
        JSIntlDateTimeFormat effectiveDateTimeFormat = getEffectiveDateTimeFormat(context, dateTimeFormat,
                startFormattable.kind());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isTemporalCalendarCompatibleWithDateTimeFormat(context, startFormattable, effectiveDateTimeFormat)
                || !isTemporalCalendarCompatibleWithDateTimeFormat(context, endFormattable, effectiveDateTimeFormat)) {
            return JSUndefined.INSTANCE;
        }
        if (!isFormattableEpochMillisValid(context, startFormattable)
                || !isFormattableEpochMillisValid(context, endFormattable)) {
            return JSUndefined.INSTANCE;
        }
        double startDate = startFormattable.epochMillis();
        double endDate = endFormattable.epochMillis();

        if (!toParts && Double.compare(startDate, endDate) == 0) {
            return new JSString(effectiveDateTimeFormat.format(startDate));
        }

        List<JSIntlDateTimeFormat.DatePart> startParts = effectiveDateTimeFormat.formatToPartsList(startDate);
        List<JSIntlDateTimeFormat.DatePart> endParts = effectiveDateTimeFormat.formatToPartsList(endDate);

        if (toParts) {
            JSArray result = context.createJSArray();
            writeDateRange(effectiveDateTimeFormat, startParts, endParts,
                    (type, value, source) -> result.push(JSIntlObject.createPartObject(context, type, value, source)));
            return result;
        }
        StringBuilder text = new StringBuilder();
        writeDateRange(effectiveDateTimeFormat, startParts, endParts, (type, value, source) -> text.append(value));
        return new JSString(text.toString());
    }

    private static JSValue formatDateTime(JSContext context, JSValue thisArg, JSValue[] args, boolean toParts) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError(toParts
                    ? "Intl.DateTimeFormat.prototype.formatToParts called on incompatible receiver"
                    : "Intl.DateTimeFormat.prototype.format called on incompatible receiver");
        }
        DateTimeFormattable formattable = toDateTimeFormattable(context, JSValue.argumentOrUndefined(args, 0), true);
        if (formattable == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSIntlDateTimeFormat effectiveDateTimeFormat = getEffectiveDateTimeFormat(context, dateTimeFormat,
                formattable.kind());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isTemporalCalendarCompatibleWithDateTimeFormat(context, formattable, effectiveDateTimeFormat)) {
            return JSUndefined.INSTANCE;
        }
        if (!isFormattableEpochMillisValid(context, formattable)) {
            return JSUndefined.INSTANCE;
        }
        if (toParts) {
            return datePartsToJSArray(context, effectiveDateTimeFormat.formatToPartsList(formattable.epochMillis()));
        }
        return new JSString(effectiveDateTimeFormat.format(formattable.epochMillis()));
    }

    private static DateTimeFormattable fromPlainDateTime(JSContext context, DateTimeFormattableKind kind,
            IsoDateTime dateTime, TemporalCalendarId calendar, String errorMessage) {
        try {
            double millis = dateTime.toLocalDateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
            return new DateTimeFormattable(kind, millis, calendar);
        } catch (DateTimeException e) {
            context.throwRangeError(errorMessage);
            return null;
        }
    }

    private static JSIntlDateTimeFormat getEffectiveDateTimeFormat(JSContext context,
            JSIntlDateTimeFormat baseDateTimeFormat, DateTimeFormattableKind formattableKind) {
        return switch (formattableKind) {
            case NUMBER -> baseDateTimeFormat;
            case ZONED_DATE_TIME -> {
                context.throwTypeError("Invalid date/time value");
                yield null;
            }
            default -> createTemporalDateTimeFormat(context, baseDateTimeFormat, formattableKind);
        };
    }

    private static boolean isFormattableEpochMillisValid(JSContext context, DateTimeFormattable formattable) {
        if (!Double.isFinite(formattable.epochMillis())) {
            context.throwRangeError("Invalid time value");
            return false;
        }
        if (formattable.kind() == DateTimeFormattableKind.NUMBER && Math.abs(formattable.epochMillis()) > 8.64e15) {
            context.throwRangeError("Invalid time value");
            return false;
        }
        return true;
    }

    private static boolean isSupportedDateTimeNumberingSystem(String numberingSystem) {
        if (numberingSystem == null) {
            return false;
        }
        return IntlFormatOptions.SUPPORTED_NUMBERING_SYSTEMS.contains(numberingSystem);
    }

    private static boolean isTemporalCalendarCompatibleWithDateTimeFormat(JSContext context,
            DateTimeFormattable formattable, JSIntlDateTimeFormat dateTimeFormat) {
        TemporalCalendarId temporalCalendarId = formattable.calendarId();
        if (temporalCalendarId == null) {
            return true;
        }
        String canonicalTemporalCalendarId = JSIntlObject.canonicalizeCalendar(temporalCalendarId.identifier());
        if ("iso8601".equals(canonicalTemporalCalendarId)) {
            return true;
        }
        String formatterCalendarId = dateTimeFormat.getCalendar();
        if (formatterCalendarId == null || formatterCalendarId.isEmpty()) {
            return true;
        }
        String canonicalFormatterCalendarId = JSIntlObject.canonicalizeCalendar(formatterCalendarId);
        if (!canonicalTemporalCalendarId.equals(canonicalFormatterCalendarId)) {
            context.throwRangeError("Invalid date/time value");
            return false;
        }
        return true;
    }

    /**
     * Normalize an offset timezone string to the canonical form: +HH:MM or -HH:MM. -00 and -00:00 normalize to +00:00.
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
            int hourTens = hm[0].charAt(0) - '0';
            int hourOnes = hm[0].charAt(1) - '0';
            int minuteTens = hm[1].charAt(0) - '0';
            int minuteOnes = hm[1].charAt(1) - '0';
            if (hourTens < 0 || hourTens > 9 || hourOnes < 0 || hourOnes > 9 || minuteTens < 0 || minuteTens > 9
                    || minuteOnes < 0 || minuteOnes > 9) {
                return null;
            }
            hours = hourTens * 10 + hourOnes;
            minutes = minuteTens * 10 + minuteOnes;
        } else if (offsetPart.length() == 4) {
            int h0 = offsetPart.charAt(0) - '0';
            int h1 = offsetPart.charAt(1) - '0';
            int m0 = offsetPart.charAt(2) - '0';
            int m1 = offsetPart.charAt(3) - '0';
            if (h0 < 0 || h0 > 9 || h1 < 0 || h1 > 9 || m0 < 0 || m0 > 9 || m1 < 0 || m1 > 9) {
                return null;
            }
            hours = h0 * 10 + h1;
            minutes = m0 * 10 + m1;
        } else if (offsetPart.length() == 2) {
            int h0 = offsetPart.charAt(0) - '0';
            int h1 = offsetPart.charAt(1) - '0';
            if (h0 < 0 || h0 > 9 || h1 < 0 || h1 > 9) {
                return null;
            }
            hours = h0 * 10 + h1;
            minutes = 0;
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

    private static String resolveHourCycle(JSIntlDateTimeFormat formatter) {
        String cycle = formatter.getHourCycle();
        if (cycle != null) {
            return cycle;
        }
        cycle = formatter.getHourCycleForInstant();
        if (cycle != null) {
            return cycle;
        }
        Locale locale = formatter.getLocale();
        cycle = IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag()).get("hc");
        return "h11".equals(cycle) || "h12".equals(cycle) || "h23".equals(cycle) || "h24".equals(cycle)
                ? cycle
                : JSIntlObject.getLocaleDefaultHourCycle(locale);
    }

    private static DateStyleOptionFields toDateStyleOptionFields(FormatStyle dateStyle) {
        return switch (dateStyle) {
            case FULL -> new DateStyleOptionFields("long", "numeric", "long", "numeric");
            case LONG -> new DateStyleOptionFields(null, "numeric", "long", "numeric");
            case MEDIUM -> new DateStyleOptionFields(null, "numeric", "short", "numeric");
            case SHORT -> new DateStyleOptionFields(null, "2-digit", "numeric", "numeric");
        };
    }

    private static DateTimeFormattable toDateTimeFormattable(JSContext context, JSValue value,
            boolean useCurrentTimeForUndefined) {
        if (useCurrentTimeForUndefined && value instanceof JSUndefined) {
            return new DateTimeFormattable(DateTimeFormattableKind.NUMBER, System.currentTimeMillis(), null);
        }

        if (value instanceof JSDate jsDate) {
            return new DateTimeFormattable(DateTimeFormattableKind.NUMBER, jsDate.getTimeValue(), null);
        }
        if (value instanceof JSTemporalInstant temporalInstant) {
            return new DateTimeFormattable(DateTimeFormattableKind.INSTANT,
                    toEpochMillisFromEpochNanoseconds(temporalInstant.getEpochNanoseconds()), null);
        }
        if (value instanceof JSTemporalPlainDate temporalPlainDate) {
            return fromPlainDateTime(context, DateTimeFormattableKind.PLAIN_DATE,
                    temporalPlainDate.getIsoDate().atMidnight(), temporalPlainDate.getCalendarId(),
                    "Invalid Temporal.PlainDate value");
        }
        if (value instanceof JSTemporalPlainDateTime temporalPlainDateTime) {
            return fromPlainDateTime(context, DateTimeFormattableKind.PLAIN_DATE_TIME,
                    temporalPlainDateTime.getIsoDateTime(), temporalPlainDateTime.getCalendarId(),
                    "Invalid Temporal.PlainDateTime value");
        }
        if (value instanceof JSTemporalPlainMonthDay temporalPlainMonthDay) {
            return fromPlainDateTime(context, DateTimeFormattableKind.PLAIN_MONTH_DAY,
                    temporalPlainMonthDay.getIsoDate().atMidnight(), temporalPlainMonthDay.getCalendarId(),
                    "Invalid Temporal.PlainMonthDay value");
        }
        if (value instanceof JSTemporalPlainTime temporalPlainTime) {
            return fromPlainDateTime(context, DateTimeFormattableKind.PLAIN_TIME,
                    UNIX_EPOCH_DATE.atTime(temporalPlainTime.getIsoTime()), null, "Invalid Temporal.PlainTime value");
        }
        if (value instanceof JSTemporalPlainYearMonth temporalPlainYearMonth) {
            return fromPlainDateTime(context, DateTimeFormattableKind.PLAIN_YEAR_MONTH,
                    temporalPlainYearMonth.getIsoDate().atMidnight(), temporalPlainYearMonth.getCalendarId(),
                    "Invalid Temporal.PlainYearMonth value");
        }
        if (value instanceof JSTemporalZonedDateTime temporalZonedDateTime) {
            return new DateTimeFormattable(DateTimeFormattableKind.ZONED_DATE_TIME,
                    toEpochMillisFromEpochNanoseconds(temporalZonedDateTime.getEpochNanoseconds()),
                    temporalZonedDateTime.getCalendarId());
        }

        double epochMillis = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return null;
        }
        return new DateTimeFormattable(DateTimeFormattableKind.NUMBER, epochMillis, null);
    }

    private static double toEpochMillisFromEpochNanoseconds(BigInteger epochNanoseconds) {
        BigInteger[] quotientAndRemainder = epochNanoseconds.divideAndRemainder(NANOS_PER_MILLISECOND);
        BigInteger epochMilliseconds = quotientAndRemainder[0];
        if (epochNanoseconds.signum() < 0 && quotientAndRemainder[1].signum() != 0) {
            epochMilliseconds = epochMilliseconds.subtract(BigInteger.ONE);
        }
        return epochMilliseconds.doubleValue();
    }

    /**
     * Validate and canonicalize a time zone string. Returns the canonical time zone name, or null if invalid.
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
        String canonical = JSIntlObject.getTimezoneLookupMap().get(timeZone.toLowerCase(Locale.ROOT));
        return canonical;
    }

    private static void writeDateRange(JSIntlDateTimeFormat format, List<JSIntlDateTimeFormat.DatePart> start,
            List<JSIntlDateTimeFormat.DatePart> end, DateRangeOutput output) {
        int first = 0;
        int startLimit = start.size();
        int endLimit = end.size();
        if (startLimit == endLimit) {
            while (first < startLimit && start.get(first).equals(end.get(first))) {
                first++;
            }
            if (first == startLimit) {
                output.append(start, 0, first, "shared");
                return;
            }
            boolean sameYear = true;
            for (int i = first; i < startLimit; i++) {
                if ("year".equals(start.get(i).type()) && !start.get(i).equals(end.get(i))) {
                    sameYear = false;
                    break;
                }
            }
            if ((format.hasTextMonth() && sameYear) || differsOnlyInTimeParts(start, end)) {
                while (startLimit > first && start.get(startLimit - 1).equals(end.get(endLimit - 1))) {
                    startLimit--;
                    endLimit--;
                }
            } else {
                first = 0;
            }
        }
        output.append(start, 0, first, "shared");
        output.append(start, first, startLimit, "startRange");
        output.append("literal", " \u2013 ", "shared");
        output.append(end, first, endLimit, "endRange");
        output.append(start, startLimit, start.size(), "shared");
    }

    private interface DateRangeOutput {
        default void append(List<JSIntlDateTimeFormat.DatePart> parts, int start, int end, String source) {
            for (int i = start; i < end; i++) {
                JSIntlDateTimeFormat.DatePart part = parts.get(i);
                append(part.type(), part.value(), source);
            }
        }

        void append(String type, String value, String source);
    }

    private record DateStyleOptionFields(String weekdayOption, String yearOption, String monthOption,
            String dayOption) {
    }

    private record DateTimeFormattable(DateTimeFormattableKind kind, double epochMillis,
            TemporalCalendarId calendarId) {
    }

    private enum DateTimeFormattableKind {
        INSTANT, NUMBER, PLAIN_DATE, PLAIN_DATE_TIME, PLAIN_MONTH_DAY, PLAIN_TIME, PLAIN_YEAR_MONTH, ZONED_DATE_TIME
    }
}
