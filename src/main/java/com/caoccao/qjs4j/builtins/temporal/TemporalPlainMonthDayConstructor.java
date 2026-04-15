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

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.IsoDate;
import com.caoccao.qjs4j.core.temporal.TemporalCalendarMath;
import com.caoccao.qjs4j.core.temporal.TemporalParser;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of Temporal.PlainMonthDay constructor and static methods.
 */
public final class TemporalPlainMonthDayConstructor {
    private static final int DEFAULT_REFERENCE_ISO_YEAR = 1972;
    private static final int MAX_REFERENCE_ISO_YEAR = 2050;
    private static final int MIN_REFERENCE_ISO_YEAR = 1900;
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Map<MonthDayLookupKey, IsoDate>>>
            REFERENCE_LOOKUP_BY_CALENDAR_AND_ISO_YEAR = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ReferenceLookupKey, IsoDate> REFERENCE_LOOKUP_HIT_CACHE = new ConcurrentHashMap<>();
    private static final Set<ReferenceLookupKey> REFERENCE_LOOKUP_MISS_CACHE = ConcurrentHashMap.newKeySet();

    private TemporalPlainMonthDayConstructor() {
    }

    private static Map<MonthDayLookupKey, IsoDate> buildReferenceLookupForIsoYear(
            String calendarId,
            int isoYear) {
        Map<MonthDayLookupKey, IsoDate> latestIsoDateByMonthDay = new HashMap<>();
        for (int isoMonth = 1; isoMonth <= 12; isoMonth++) {
            int daysInMonth = IsoDate.daysInMonth(isoYear, isoMonth);
            for (int isoDay = 1; isoDay <= daysInMonth; isoDay++) {
                IsoDate isoDate = new IsoDate(isoYear, isoMonth, isoDay);
                TemporalCalendarMath.CalendarDateFields calendarDateFields =
                        TemporalCalendarMath.isoDateToCalendarDate(isoDate, calendarId);
                MonthDayLookupKey monthDayLookupKey = new MonthDayLookupKey(
                        calendarDateFields.monthCode(),
                        calendarDateFields.day());
                latestIsoDateByMonthDay.put(monthDayLookupKey, isoDate);
            }
        }
        return Map.copyOf(latestIsoDateByMonthDay);
    }

    /**
     * Temporal.PlainMonthDay(isoMonth, isoDay, calendar?, referenceISOYear?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainYearMonth.");
            return JSUndefined.INSTANCE;
        }

        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String calendarId = "iso8601";
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        int referenceYear = DEFAULT_REFERENCE_ISO_YEAR;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            referenceYear = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (!IsoDate.isValidIsoDate(referenceYear, isoMonth, isoDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "PlainMonthDay");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainMonthDay(context, new IsoDate(referenceYear, isoMonth, isoDay), calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainMonthDay createPlainMonthDay(JSContext context, IsoDate isoDate, String calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainMonthDay");
        return createPlainMonthDay(context, isoDate, calendarId, prototype);
    }

    static JSTemporalPlainMonthDay createPlainMonthDay(JSContext context, IsoDate isoDate, String calendarId, JSObject prototype) {
        JSTemporalPlainMonthDay plainMonthDay = new JSTemporalPlainMonthDay(context, isoDate, calendarId);
        if (prototype != null) {
            plainMonthDay.setPrototype(prototype);
        }
        return plainMonthDay;
    }

    static JSTemporalPlainMonthDay createPlainMonthDayFromCalendarMonthDay(
            JSContext context,
            String calendarId,
            String monthCode,
            int dayOfMonth,
            String overflow) {
        IsoDate referenceIsoDate = resolveReferenceIsoDate(context, calendarId, monthCode, dayOfMonth, overflow);
        if (context.hasPendingException() || referenceIsoDate == null) {
            return null;
        }
        return createPlainMonthDay(context, referenceIsoDate, calendarId);
    }

    private static IsoDate findReferenceIsoDateExact(String calendarId, String monthCode, int dayOfMonth) {
        ReferenceLookupKey referenceLookupKey = new ReferenceLookupKey(calendarId, monthCode, dayOfMonth);
        IsoDate cachedReferenceIsoDate = REFERENCE_LOOKUP_HIT_CACHE.get(referenceLookupKey);
        if (cachedReferenceIsoDate != null) {
            return cachedReferenceIsoDate;
        }
        if (REFERENCE_LOOKUP_MISS_CACHE.contains(referenceLookupKey)) {
            return null;
        }

        IsoDate resolvedReferenceIsoDate = findReferenceIsoDateExactUncached(calendarId, monthCode, dayOfMonth);
        if (resolvedReferenceIsoDate == null) {
            REFERENCE_LOOKUP_MISS_CACHE.add(referenceLookupKey);
            return null;
        }
        REFERENCE_LOOKUP_HIT_CACHE.put(referenceLookupKey, resolvedReferenceIsoDate);
        return resolvedReferenceIsoDate;
    }

    private static IsoDate findReferenceIsoDateExactUncached(String calendarId, String monthCode, int dayOfMonth) {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            return null;
        }

        for (int isoYear = DEFAULT_REFERENCE_ISO_YEAR; isoYear >= MIN_REFERENCE_ISO_YEAR; isoYear--) {
            Map<MonthDayLookupKey, IsoDate> referenceLookupForIsoYear = findReferenceLookupForIsoYear(calendarId, isoYear);
            IsoDate candidateIsoDate = findReferenceIsoDateFromReferenceLookup(
                    referenceLookupForIsoYear,
                    monthCode,
                    dayOfMonth);
            if (candidateIsoDate != null) {
                return candidateIsoDate;
            }
        }

        for (int isoYear = DEFAULT_REFERENCE_ISO_YEAR + 1; isoYear <= MAX_REFERENCE_ISO_YEAR; isoYear++) {
            Map<MonthDayLookupKey, IsoDate> referenceLookupForIsoYear = findReferenceLookupForIsoYear(calendarId, isoYear);
            IsoDate candidateIsoDate = findReferenceIsoDateFromReferenceLookup(
                    referenceLookupForIsoYear,
                    monthCode,
                    dayOfMonth);
            if (candidateIsoDate != null) {
                return candidateIsoDate;
            }
        }
        return null;
    }

    private static IsoDate findReferenceIsoDateFromReferenceLookup(
            Map<MonthDayLookupKey, IsoDate> referenceLookupForIsoYear,
            String monthCode,
            int dayOfMonth) {
        MonthDayLookupKey monthDayLookupKey = new MonthDayLookupKey(monthCode, dayOfMonth);
        return referenceLookupForIsoYear.get(monthDayLookupKey);
    }

    private static Map<MonthDayLookupKey, IsoDate> findReferenceLookupForIsoYear(
            String calendarId,
            int isoYear) {
        ConcurrentHashMap<Integer, Map<MonthDayLookupKey, IsoDate>> referenceLookupByIsoYear =
                REFERENCE_LOOKUP_BY_CALENDAR_AND_ISO_YEAR.computeIfAbsent(
                        calendarId,
                        key -> new ConcurrentHashMap<>());
        return referenceLookupByIsoYear.computeIfAbsent(
                isoYear,
                key -> buildReferenceLookupForIsoYear(calendarId, isoYear));
    }

    private static String formatMonthCode(ParsedMonthCode parsedMonthCode) {
        String formattedMonthCode = TemporalUtils.monthCode(parsedMonthCode.month());
        if (parsedMonthCode.leapMonth()) {
            formattedMonthCode = formattedMonthCode + "L";
        }
        return formattedMonthCode;
    }

    /**
     * Temporal.PlainMonthDay.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalMonthDay(context, item, options);
    }

    private static boolean isChineseOrDangiCalendar(String calendarId) {
        return "chinese".equals(calendarId) || "dangi".equals(calendarId);
    }

    private static boolean isIsoLeapYearWithoutRangeCheck(int year) {
        if (Math.floorMod(year, 4) != 0) {
            return false;
        }
        if (Math.floorMod(year, 100) != 0) {
            return true;
        }
        return Math.floorMod(year, 400) == 0;
    }

    private static int isoDaysInMonthWithoutRangeCheck(int year, int month) {
        if (month == 2) {
            if (isIsoLeapYearWithoutRangeCheck(year)) {
                return 29;
            }
            return 28;
        }
        if (month == 4 || month == 6 || month == 9 || month == 11) {
            return 30;
        }
        return 31;
    }

    static JSValue monthDayFromFields(JSContext context, JSObject fields, JSValue options) {
        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String calendarId = "iso8601";
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalUtils.toTemporalCalendarWithISODefault(context, calendarValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue dayValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDay = !(dayValue instanceof JSUndefined) && dayValue != null;
        int dayOfMonth = Integer.MIN_VALUE;
        if (hasDay) {
            dayOfMonth = TemporalUtils.toIntegerThrowOnInfinity(context, dayValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonth = !(monthValue instanceof JSUndefined) && monthValue != null;
        int month = Integer.MIN_VALUE;
        if (hasMonth) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCode = !(monthCodeValue instanceof JSUndefined) && monthCodeValue != null;
        String monthCode = null;
        if (hasMonthCode) {
            monthCode = JSTypeConversions.toString(context, monthCodeValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        int year = Integer.MIN_VALUE;

        boolean calendarSupportsEras = TemporalFieldResolver.calendarUsesEras(calendarId);
        if (calendarSupportsEras) {
            JSValue eraValue = fields.get(PropertyKey.fromString("era"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            boolean hasEra = !(eraValue instanceof JSUndefined) && eraValue != null;
            String era = null;
            if (hasEra) {
                era = JSTypeConversions.toString(context, eraValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            JSValue eraYearValue = fields.get(PropertyKey.fromString("eraYear"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            boolean hasEraYear = !(eraYearValue instanceof JSUndefined) && eraYearValue != null;
            Integer eraYear = null;
            if (hasEraYear) {
                eraYear = TemporalUtils.toIntegerThrowOnInfinity(context, eraYearValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            if (hasEra != hasEraYear) {
                context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            if (!hasYear && hasEra && hasEraYear) {
                String canonicalEra = TemporalFieldResolver.canonicalizeEraForCalendar(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                year = TemporalFieldResolver.yearFromEraAndEraYear(calendarId, canonicalEra, eraYear);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                hasYear = true;
            } else if (hasEra && hasEraYear) {
                String canonicalEra = TemporalFieldResolver.canonicalizeEraForCalendar(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int expectedYear = TemporalFieldResolver.yearFromEraAndEraYear(calendarId, canonicalEra, eraYear);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (year != expectedYear) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
            }
        }

        if (!hasDay) {
            context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        if (!hasMonth && !hasMonthCode) {
            context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        if (!hasYear && hasMonth && !"iso8601".equals(calendarId)) {
            context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        ParsedMonthCode parsedMonthCode = null;
        if (hasMonthCode) {
            parsedMonthCode = parseMonthCodeSyntaxForMonthDayFrom(context, monthCode);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (hasYear) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String resolvedMonthCode;
        int resolvedDay;
        if (hasYear || hasMonth) {
            if ("iso8601".equals(calendarId)) {
                ResolvedMonthDay resolvedIsoMonthDay = resolveIsoMonthDay(
                        context,
                        hasYear ? year : DEFAULT_REFERENCE_ISO_YEAR,
                        hasMonth ? month : null,
                        parsedMonthCode,
                        dayOfMonth,
                        overflow);
                if (context.hasPendingException() || resolvedIsoMonthDay == null) {
                    return JSUndefined.INSTANCE;
                }
                resolvedMonthCode = resolvedIsoMonthDay.monthCode();
                resolvedDay = resolvedIsoMonthDay.dayOfMonth();
                JSTemporalPlainMonthDay resolvedMonthDay = createPlainMonthDayFromCalendarMonthDay(
                        context,
                        calendarId,
                        resolvedMonthCode,
                        resolvedDay,
                        overflow);
                if (context.hasPendingException() || resolvedMonthDay == null) {
                    return JSUndefined.INSTANCE;
                }
                return resolvedMonthDay;
            }

            int resolutionYear = hasYear ? year : DEFAULT_REFERENCE_ISO_YEAR;
            Integer monthFromProperty = null;
            if (hasMonth) {
                monthFromProperty = month;
            }
            String monthCodeFromProperty = null;
            if (parsedMonthCode != null) {
                monthCodeFromProperty = formatMonthCode(parsedMonthCode);
            }
            IsoDate resolvedIsoDate = TemporalCalendarMath.calendarDateToIsoDate(
                    context,
                    calendarId,
                    resolutionYear,
                    monthFromProperty,
                    monthCodeFromProperty,
                    dayOfMonth,
                    overflow);
            if (context.hasPendingException() || resolvedIsoDate == null) {
                return JSUndefined.INSTANCE;
            }
            TemporalCalendarMath.CalendarDateFields resolvedCalendarDateFields =
                    TemporalCalendarMath.isoDateToCalendarDate(resolvedIsoDate, calendarId);
            resolvedMonthCode = resolvedCalendarDateFields.monthCode();
            resolvedDay = resolvedCalendarDateFields.day();
        } else {
            resolvedMonthCode = formatMonthCode(parsedMonthCode);
            resolvedDay = dayOfMonth;
        }

        JSTemporalPlainMonthDay resolvedMonthDay = createPlainMonthDayFromCalendarMonthDay(
                context,
                calendarId,
                resolvedMonthCode,
                resolvedDay,
                overflow);
        if (context.hasPendingException() || resolvedMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        return resolvedMonthDay;
    }

    static JSValue monthDayFromString(JSContext context, String input) {
        String calendar = "iso8601";
        String calendarAnnotation = TemporalUtils.firstCalendarAnnotation(input);
        if (calendarAnnotation != null) {
            calendar = TemporalUtils.validateCalendar(context, new JSString(calendarAnnotation));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        IsoDate parsedDate = TemporalParser.parseDateString(context, input);
        if (parsedDate != null && !context.hasPendingException()) {
            if ("iso8601".equals(calendar)) {
                return createPlainMonthDay(
                        context,
                        new IsoDate(DEFAULT_REFERENCE_ISO_YEAR, parsedDate.month(), parsedDate.day()),
                        calendar);
            }
            TemporalCalendarMath.CalendarDateFields calendarDateFields =
                    TemporalCalendarMath.isoDateToCalendarDate(parsedDate, calendar);
            JSTemporalPlainMonthDay plainMonthDay = createPlainMonthDayFromCalendarMonthDay(
                    context,
                    calendar,
                    calendarDateFields.monthCode(),
                    calendarDateFields.day(),
                    "constrain");
            if (context.hasPendingException() || plainMonthDay == null) {
                return JSUndefined.INSTANCE;
            }
            return plainMonthDay;
        }
        context.clearPendingException();

        IsoDate parsedMonthDay = TemporalParser.parseMonthDayString(context, input);
        if (parsedMonthDay == null) {
            return JSUndefined.INSTANCE;
        }

        if (!"iso8601".equals(calendar)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return createPlainMonthDay(context, parsedMonthDay, calendar);
    }

    private static ParsedMonthCode parseMonthCodeSyntaxForMonthDayFrom(JSContext context, String monthCode) {
        TemporalFieldResolver.ParsedMonthCode parsedMonthCode = TemporalFieldResolver.parseMonthCodeSyntax(
                context,
                monthCode,
                "Temporal error: Invalid ISO date.");
        if (parsedMonthCode == null) {
            return null;
        }
        return new ParsedMonthCode(parsedMonthCode.month(), parsedMonthCode.leapMonth());
    }

    private static ResolvedMonthDay resolveIsoMonthDay(
            JSContext context,
            int year,
            Integer monthFromProperty,
            ParsedMonthCode monthCodeFromProperty,
            int dayOfMonth,
            String overflow) {
        if (dayOfMonth < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        Integer resolvedMonth = monthFromProperty;
        if (monthCodeFromProperty != null) {
            if (monthCodeFromProperty.month() < 1 || monthCodeFromProperty.month() > 12 || monthCodeFromProperty.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (resolvedMonth != null && resolvedMonth != monthCodeFromProperty.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (resolvedMonth == null) {
                resolvedMonth = monthCodeFromProperty.month();
            }
        }
        if (resolvedMonth == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int constrainedMonth = resolvedMonth;
        if ("reject".equals(overflow)) {
            if (constrainedMonth < 1 || constrainedMonth > 12) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else {
            if (constrainedMonth < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (constrainedMonth > 12) {
                constrainedMonth = 12;
            }
        }

        int maximumDay = isoDaysInMonthWithoutRangeCheck(year, constrainedMonth);
        int constrainedDay = dayOfMonth;
        if ("reject".equals(overflow)) {
            if (constrainedDay > maximumDay) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else if (constrainedDay > maximumDay) {
            constrainedDay = maximumDay;
        }
        return new ResolvedMonthDay(TemporalUtils.monthCode(constrainedMonth), constrainedDay);
    }

    static IsoDate resolveReferenceIsoDate(
            JSContext context,
            String calendarId,
            String monthCode,
            int dayOfMonth,
            String overflow) {
        if (dayOfMonth < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        ParsedMonthCode parsedMonthCode = parseMonthCodeSyntaxForMonthDayFrom(context, monthCode);
        if (context.hasPendingException() || parsedMonthCode == null) {
            return null;
        }

        String normalizedMonthCode = formatMonthCode(parsedMonthCode);
        int searchDay = dayOfMonth;

        if (isChineseOrDangiCalendar(calendarId) && parsedMonthCode.leapMonth()) {
            if ("reject".equals(overflow)) {
                IsoDate exactLeapDate = findReferenceIsoDateExact(calendarId, normalizedMonthCode, searchDay);
                if (exactLeapDate == null) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                return exactLeapDate;
            }

            int constrainedLeapDay = Math.min(searchDay, 30);
            IsoDate exactLeapDate = findReferenceIsoDateExact(calendarId, normalizedMonthCode, constrainedLeapDay);
            if (exactLeapDate != null) {
                return exactLeapDate;
            }

            normalizedMonthCode = TemporalUtils.monthCode(parsedMonthCode.month());
            searchDay = constrainedLeapDay;
        }

        if ("reject".equals(overflow)) {
            IsoDate exactReferenceDate = findReferenceIsoDateExact(calendarId, normalizedMonthCode, searchDay);
            if (exactReferenceDate == null) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            return exactReferenceDate;
        }

        int constrainedSearchDay = Math.min(searchDay, 31);
        for (int candidateDay = constrainedSearchDay; candidateDay >= 1; candidateDay--) {
            IsoDate candidateReferenceDate = findReferenceIsoDateExact(calendarId, normalizedMonthCode, candidateDay);
            if (candidateReferenceDate != null) {
                return candidateReferenceDate;
            }
        }

        context.throwRangeError("Temporal error: Invalid ISO date.");
        return null;
    }

    public static JSValue toTemporalMonthDay(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainMonthDay plainMonthDay) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainMonthDay(context, plainMonthDay.getIsoDate(), plainMonthDay.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            JSObject plainMonthDayPrototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainMonthDay");
            if (itemObj == plainMonthDayPrototype) {
                context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            return monthDayFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            JSValue result = monthDayFromString(context, itemStr.value());
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return result;
        }
        context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainMonthDay toTemporalMonthDayObject(JSContext context, JSValue item) {
        JSValue result = toTemporalMonthDay(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalPlainMonthDay) result;
    }

    private record MonthDayLookupKey(String monthCode, int dayOfMonth) {
    }

    private record ParsedMonthCode(int month, boolean leapMonth) {
    }

    private record ReferenceLookupKey(String calendarId, String monthCode, int dayOfMonth) {
    }

    private record ResolvedMonthDay(String monthCode, int dayOfMonth) {
    }
}
