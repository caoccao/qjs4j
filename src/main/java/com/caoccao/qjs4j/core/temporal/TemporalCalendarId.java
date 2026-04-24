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

package com.caoccao.qjs4j.core.temporal;

import com.caoccao.qjs4j.core.*;

import java.time.DateTimeException;
import java.time.chrono.HijrahChronology;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public enum TemporalCalendarId {
    BUDDHIST("buddhist", true),
    CHINESE("chinese", false),
    COPTIC("coptic", true),
    DANGI("dangi", false),
    ETHIOAA("ethioaa", true),
    ETHIOPIC("ethiopic", true),
    GREGORY("gregory", true),
    HEBREW("hebrew", true),
    INDIAN("indian", true),
    ISLAMIC_CIVIL("islamic-civil", true),
    ISLAMIC_TBLA("islamic-tbla", true),
    ISLAMIC_UMALQURA("islamic-umalqura", true),
    ISO8601("iso8601", false),
    JAPANESE("japanese", true),
    PERSIAN("persian", true),
    ROC("roc", true);

    private static final int[] CHINESE_LUNAR_YEAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ae0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63
    };
    private static final int[] CHINESE_LUNAR_YEAR_INFO_EXTENSION_2051_TO_2100 = {
            0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, 0x092e0,
            0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, 0x092d0,
            0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, 0x0b273,
            0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, 0x0e968,
            0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, 0x0d520
    };
    private static final int[] DANGI_LUNAR_YEAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x0da95, 0x0b550, 0x056a0, 0x0ada2, 0x095d0, 0x04bb7,
            0x049b0, 0x0a4b0, 0x0b4b5, 0x06a90, 0x0ad40, 0x0bb54, 0x02b60, 0x095b0, 0x05372, 0x04970,
            0x06566, 0x0e4a0, 0x0ea50, 0x16a95, 0x05b50, 0x02b60, 0x18ae3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b690, 0x056d0, 0x125b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0d557,
            0x0b4a0, 0x0b550, 0x15555, 0x04db0, 0x025b0, 0x18573, 0x052b0, 0x0a9b8, 0x06950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05270, 0x07263, 0x0d950, 0x06b57, 0x056a0,
            0x09ad0, 0x04dd5, 0x04ae0, 0x0a4e0, 0x0d4d4, 0x0d250, 0x0d598, 0x0b540, 0x0d6a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a9b4, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0b756, 0x02b60, 0x095b0,
            0x04b75, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06d98, 0x05ad0, 0x02b60, 0x096e5, 0x092e0,
            0x0c960, 0x0e954, 0x0d4a0, 0x0da50, 0x07552, 0x056c0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x1b4a3, 0x0b550, 0x055d9, 0x04ba0, 0x0a5b0, 0x09575, 0x052b0, 0x0a950,
            0x0b954, 0x06aa0, 0x0ad50, 0x06b52, 0x04b60, 0x0a6e6, 0x0a570, 0x05270, 0x06a65, 0x0d930,
            0x05aa0, 0x0b6a3, 0x096d0, 0x04afb, 0x04ae0, 0x0a4d0, 0x1d0d6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b6a0, 0x096d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0b250, 0x1b255, 0x06d40, 0x0ada0,
            0x18b63
    };
    private static final int DEFAULT_REFERENCE_ISO_YEAR = 1972;
    private static final int MAX_REFERENCE_ISO_DATE_CACHE_SIZE = 4_096;
    private static final int MAX_REFERENCE_ISO_YEAR = 2050;
    private static final int MIN_REFERENCE_ISO_YEAR = 1900;
    private static final ConcurrentHashMap<TemporalReferenceIsoDateLookupKey, IsoDate> REFERENCE_ISO_DATE_HIT_CACHE =
            new ConcurrentHashMap<>();
    private static final Set<TemporalReferenceIsoDateLookupKey> REFERENCE_ISO_DATE_MISS_CACHE =
            ConcurrentHashMap.newKeySet();
    private static final int YEAR_MONTH_BOUNDARY_SEARCH_RADIUS_DAYS = 400;

    private final boolean era;
    private final String identifier;

    TemporalCalendarId(String identifier, boolean era) {
        this.era = era;
        this.identifier = identifier;
    }

    private static boolean canParseCalendarLikeString(JSContext context, String calendarLikeText) {
        JSValue originalPendingException = context.getPendingException();
        String calendarLikeBaseText = calendarLikeText;
        int annotationStart = calendarLikeBaseText.indexOf('[');
        if (annotationStart >= 0) {
            calendarLikeBaseText = calendarLikeBaseText.substring(0, annotationStart);
        }

        if (IsoDate.parseDateString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (IsoCalendarDateTime.parseDateTimeString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (IsoDate.parseYearMonthString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (IsoDate.parseMonthDayString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (IsoTime.parseTimeString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (calendarLikeBaseText.length() == 5
                && Character.isDigit(calendarLikeBaseText.charAt(0))
                && Character.isDigit(calendarLikeBaseText.charAt(1))
                && calendarLikeBaseText.charAt(2) == '-'
                && Character.isDigit(calendarLikeBaseText.charAt(3))
                && Character.isDigit(calendarLikeBaseText.charAt(4))
                && IsoDate.parseMonthDayString(context, "--" + calendarLikeBaseText) != null
                && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (originalPendingException != null) {
            context.setPendingException(originalPendingException);
        }
        return false;
    }

    public static TemporalCalendarId createFromCalendarString(JSContext context, JSValue calendarValue) {
        if (calendarValue instanceof JSUndefined || calendarValue == null) {
            return TemporalCalendarId.ISO8601;
        }
        if (!(calendarValue instanceof JSString calendarString)) {
            context.throwTypeError("Temporal error: Calendar must be string.");
            return null;
        }
        TemporalCalendarId canonicalCalendarId = TemporalCalendarId.fromIdentifier(calendarString.value());
        if (canonicalCalendarId == null) {
            context.throwRangeError("Temporal error: Invalid calendar.");
            return null;
        }
        return canonicalCalendarId;
    }

    public static TemporalCalendarId createFromCalendarValue(JSContext context, JSValue calendarValue) {
        if (calendarValue instanceof JSUndefined || calendarValue == null) {
            return TemporalCalendarId.ISO8601;
        }
        if (calendarValue instanceof JSTemporalPlainDate temporalPlainDate) {
            return temporalPlainDate.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalPlainDateTime temporalPlainDateTime) {
            return temporalPlainDateTime.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalPlainMonthDay temporalPlainMonthDay) {
            return temporalPlainMonthDay.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalPlainYearMonth temporalPlainYearMonth) {
            return temporalPlainYearMonth.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalZonedDateTime temporalZonedDateTime) {
            return temporalZonedDateTime.getCalendarId();
        }
        if (!(calendarValue instanceof JSString calendarString)) {
            context.throwTypeError("Temporal error: Calendar must be string.");
            return null;
        }

        TemporalCalendarId canonicalCalendarId = TemporalCalendarId.fromIdentifier(calendarString.value());
        if (canonicalCalendarId != null) {
            return canonicalCalendarId;
        }

        String calendarStringValue = calendarString.value();
        if (!canParseCalendarLikeString(context, calendarStringValue)) {
            context.throwRangeError("Temporal error: Invalid calendar.");
            return null;
        }

        String firstCalendarAnnotation = TemporalUtils.firstCalendarAnnotation(calendarStringValue);
        if (firstCalendarAnnotation != null) {
            TemporalCalendarId validatedCalendar = TemporalCalendarId.createFromCalendarString(
                    context, new JSString(firstCalendarAnnotation));
            if (context.hasPendingException()) {
                return null;
            }
            return validatedCalendar;
        }
        return TemporalCalendarId.ISO8601;
    }

    private static IsoDate findClosestBoundaryIsoDate(
            TemporalCalendarId calendarId,
            int targetYear,
            String targetMonthCode,
            long boundaryEpochDay) {
        for (int offset = 0; offset <= YEAR_MONTH_BOUNDARY_SEARCH_RADIUS_DAYS; offset++) {
            long[] candidateEpochDays;
            if (offset == 0) {
                candidateEpochDays = new long[]{boundaryEpochDay};
            } else {
                candidateEpochDays = new long[]{boundaryEpochDay - offset, boundaryEpochDay + offset};
            }
            for (long candidateEpochDay : candidateEpochDays) {
                IsoDate candidateIsoDate = IsoDate.createFromEpochDay(candidateEpochDay);
                IsoCalendarDate candidateCalendarDateFields = candidateIsoDate.toIsoCalendarDate(calendarId);
                if (candidateCalendarDateFields.year() == targetYear
                        && targetMonthCode.equals(candidateCalendarDateFields.monthCode())) {
                    return candidateIsoDate;
                }
            }
        }
        return null;
    }

    public static TemporalCalendarId fromIdentifier(String calendarIdentifier) {
        if (calendarIdentifier == null) {
            return null;
        }
        return switch (calendarIdentifier.toLowerCase(Locale.ROOT)) {
            case "buddhist" -> BUDDHIST;
            case "chinese" -> CHINESE;
            case "coptic" -> COPTIC;
            case "dangi" -> DANGI;
            case "ethioaa", "ethiopic-amete-alem" -> ETHIOAA;
            case "ethiopic" -> ETHIOPIC;
            case "gregory", "gregorian" -> GREGORY;
            case "hebrew" -> HEBREW;
            case "indian" -> INDIAN;
            case "islamic-civil", "islamicc" -> ISLAMIC_CIVIL;
            case "islamic-tbla" -> ISLAMIC_TBLA;
            case "islamic-umalqura" -> ISLAMIC_UMALQURA;
            case "iso8601" -> ISO8601;
            case "japanese" -> JAPANESE;
            case "persian" -> PERSIAN;
            case "roc" -> ROC;
            default -> null;
        };
    }

    private static <Key, Value> void putBoundedMapEntry(
            ConcurrentHashMap<Key, Value> cache,
            Key key,
            Value value,
            int maximumSize) {
        if (cache.size() >= maximumSize) {
            Iterator<Key> cacheIterator = cache.keySet().iterator();
            if (cacheIterator.hasNext()) {
                cache.remove(cacheIterator.next());
            }
        }
        cache.put(key, value);
    }

    private static <Value> void putBoundedSetEntry(
            Set<Value> cache,
            Value value,
            int maximumSize) {
        if (cache.size() >= maximumSize) {
            Iterator<Value> cacheIterator = cache.iterator();
            if (cacheIterator.hasNext()) {
                cache.remove(cacheIterator.next());
            }
        }
        cache.add(value);
    }

    private static int umalquraDaysInMonth(int islamicYear, int islamicMonth) {
        try {
            return HijrahChronology.INSTANCE.date(islamicYear, islamicMonth, 1).lengthOfMonth();
        } catch (DateTimeException dateTimeException) {
            return TemporalUtils.islamicDaysInMonth(islamicYear, islamicMonth);
        }
    }

    public boolean calendarYearHasMonthCode(int calendarYear, String monthCode) {
        if (monthCode == null) {
            return false;
        }
        for (IsoCalendarMonth monthSlot : TemporalCalendarMath.getMonthSlots(this, calendarYear)) {
            if (monthSlot.monthCode().equals(monthCode)) {
                return true;
            }
        }
        return false;
    }

    public String constrainMonthCode(int calendarYear, String monthCode) {
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
        if (monthCodeData == null || !monthCodeData.leapMonth()) {
            return monthCode;
        }
        if (calendarYearHasMonthCode(calendarYear, monthCode)) {
            return monthCode;
        }
        String fallbackMonthCode = TemporalCalendarMath.resolveFallbackMonthCodeForMissingLeapMonth(this, monthCode);
        if (fallbackMonthCode != null) {
            return fallbackMonthCode;
        }
        return monthCode;
    }

    public IsoDate findBoundaryIsoDateForYearMonth(int targetYear, String targetMonthCode) {
        IsoDate minimumBoundaryIsoDate = findClosestBoundaryIsoDate(
                this,
                targetYear,
                targetMonthCode,
                TemporalConstants.MIN_SUPPORTED_EPOCH_DAY);
        if (minimumBoundaryIsoDate != null) {
            return minimumBoundaryIsoDate;
        } else {
            return findClosestBoundaryIsoDate(
                    this,
                    targetYear,
                    targetMonthCode,
                    TemporalConstants.MAX_SUPPORTED_EPOCH_DAY);
        }
    }

    IsoCalendarMonth findMonthSlotByCode(int calendarYear, String monthCode) {
        for (IsoCalendarMonth monthSlot : TemporalCalendarMath.getMonthSlots(this, calendarYear)) {
            if (monthSlot.monthCode().equals(monthCode)) {
                return monthSlot;
            }
        }
        return null;
    }

    IsoCalendarMonth findMonthSlotByNumber(int calendarYear, int monthNumber) {
        for (IsoCalendarMonth monthSlot : TemporalCalendarMath.getMonthSlots(this, calendarYear)) {
            if (monthSlot.monthNumber() == monthNumber) {
                return monthSlot;
            }
        }
        return null;
    }

    IsoDate findReferenceIsoDateExact(String monthCode, int dayOfMonth) {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            return null;
        }
        TemporalReferenceIsoDateLookupKey referenceIsoDateLookupKey = new TemporalReferenceIsoDateLookupKey(
                this,
                monthCode,
                dayOfMonth);
        IsoDate cachedReferenceIsoDate = REFERENCE_ISO_DATE_HIT_CACHE.get(referenceIsoDateLookupKey);
        if (cachedReferenceIsoDate != null) {
            return cachedReferenceIsoDate;
        }
        if (REFERENCE_ISO_DATE_MISS_CACHE.contains(referenceIsoDateLookupKey)) {
            return null;
        }

        IsoDate resolvedReferenceIsoDate = findReferenceIsoDateExactUncached(monthCode, dayOfMonth);
        if (resolvedReferenceIsoDate == null) {
            putBoundedSetEntry(
                    REFERENCE_ISO_DATE_MISS_CACHE,
                    referenceIsoDateLookupKey,
                    MAX_REFERENCE_ISO_DATE_CACHE_SIZE);
            return null;
        } else {
            putBoundedMapEntry(
                    REFERENCE_ISO_DATE_HIT_CACHE,
                    referenceIsoDateLookupKey,
                    resolvedReferenceIsoDate,
                    MAX_REFERENCE_ISO_DATE_CACHE_SIZE);
            return resolvedReferenceIsoDate;
        }
    }

    private IsoDate findReferenceIsoDateExactUncached(String monthCode, int dayOfMonth) {
        for (int isoYear = DEFAULT_REFERENCE_ISO_YEAR; isoYear >= MIN_REFERENCE_ISO_YEAR; isoYear--) {
            IsoDate candidateReferenceIsoDate = findReferenceIsoDateForIsoYear(
                    isoYear,
                    monthCode,
                    dayOfMonth);
            if (candidateReferenceIsoDate != null) {
                return candidateReferenceIsoDate;
            }
        }
        for (int isoYear = DEFAULT_REFERENCE_ISO_YEAR + 1; isoYear <= MAX_REFERENCE_ISO_YEAR; isoYear++) {
            IsoDate candidateReferenceIsoDate = findReferenceIsoDateForIsoYear(
                    isoYear,
                    monthCode,
                    dayOfMonth);
            if (candidateReferenceIsoDate != null) {
                return candidateReferenceIsoDate;
            }
        }
        return null;
    }

    private IsoDate findReferenceIsoDateForIsoYear(
            int isoYear,
            String monthCode,
            int dayOfMonth) {
        IsoDate latestMatchedReferenceIsoDate = null;
        for (int isoMonth = 1; isoMonth <= 12; isoMonth++) {
            int daysInIsoMonth = IsoDate.daysInMonth(isoYear, isoMonth);
            for (int isoDay = 1; isoDay <= daysInIsoMonth; isoDay++) {
                IsoDate candidateIsoDate = new IsoDate(isoYear, isoMonth, isoDay);
                IsoCalendarDate calendarDateFields = candidateIsoDate.toIsoCalendarDate(this);
                if (dayOfMonth == calendarDateFields.day()
                        && monthCode.equals(calendarDateFields.monthCode())) {
                    latestMatchedReferenceIsoDate = candidateIsoDate;
                }
            }
        }
        return latestMatchedReferenceIsoDate;
    }

    public int getEraYearFromEra(TemporalEra era, int eraYear) {
        switch (this) {
            case GREGORY -> {
                if (era == TemporalEra.BCE) {
                    return 1 - eraYear;
                } else {
                    return eraYear;
                }
            }
            case JAPANESE -> {
                return era.getEraYearByJapaneseEraYear(eraYear);
            }
            case ROC -> {
                if (era == TemporalEra.BROC) {
                    return 1 - eraYear;
                } else {
                    return eraYear;
                }
            }
            case ETHIOPIC -> {
                if (era == TemporalEra.AA) {
                    return eraYear - 5500;
                } else {
                    return eraYear;
                }
            }
            case ISLAMIC_CIVIL, ISLAMIC_TBLA, ISLAMIC_UMALQURA -> {
                if (era == TemporalEra.BH) {
                    return 1 - eraYear;
                } else {
                    return eraYear;
                }
            }
            default -> {
                return eraYear;
            }
        }
    }

    int getLunisolarLeapMonth(int calendarYear) {
        Integer yearInfo = getLunisolarYearInfo(calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        return yearInfo & 0x0F;
    }

    int getLunisolarMaxYear() {
        if (this == DANGI) {
            return 1900 + DANGI_LUNAR_YEAR_INFO.length - 1;
        }
        return 2100;
    }

    int getLunisolarMonthDays(int calendarYear, int calendarMonth) {
        Integer yearInfo = getLunisolarYearInfo(calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        int monthMask = 0x10000 >> calendarMonth;
        return (yearInfo & monthMask) != 0 ? 30 : 29;
    }

    int getLunisolarYearDays(int calendarYear) {
        int totalDays = 0;
        for (int monthIndex = 1; monthIndex <= 12; monthIndex++) {
            int monthDays = getLunisolarMonthDays(calendarYear, monthIndex);
            if (monthDays == 0) {
                return 0;
            }
            totalDays += monthDays;
        }
        totalDays += lunisolarLeapMonthDays(calendarYear);
        return totalDays;
    }

    private Integer getLunisolarYearInfo(int calendarYear) {
        if (this == DANGI) {
            if (calendarYear < 1900 || calendarYear > 1900 + DANGI_LUNAR_YEAR_INFO.length - 1) {
                return null;
            }
            return DANGI_LUNAR_YEAR_INFO[calendarYear - 1900];
        }
        if (calendarYear >= 1900 && calendarYear <= 1900 + CHINESE_LUNAR_YEAR_INFO.length - 1) {
            return CHINESE_LUNAR_YEAR_INFO[calendarYear - 1900];
        }
        if (calendarYear >= 2051 && calendarYear <= 2100) {
            return CHINESE_LUNAR_YEAR_INFO_EXTENSION_2051_TO_2100[calendarYear - 2051];
        }
        return null;
    }

    public boolean hasEra() {
        return era;
    }

    public String identifier() {
        return identifier;
    }

    public boolean isCalendarLeapYear(int calendarYear) {
        return switch (this) {
            case ISO8601, GREGORY, JAPANESE -> TemporalCalendarMath.isLeapYear(calendarYear);
            case BUDDHIST -> TemporalCalendarMath.isLeapYear(calendarYear - 543);
            case ROC -> TemporalCalendarMath.isLeapYear(calendarYear + 1911);
            case COPTIC, ETHIOPIC -> TemporalUtils.alexandrianLeapYear(calendarYear);
            case ETHIOAA -> TemporalUtils.alexandrianLeapYear(calendarYear - 5500);
            case HEBREW -> TemporalCalendarMath.isHebrewLeapYear(calendarYear);
            case INDIAN -> TemporalCalendarMath.isLeapYear(calendarYear + 78);
            case ISLAMIC_CIVIL -> TemporalUtils.islamicDaysInMonth(calendarYear, 12) == 30;
            case ISLAMIC_TBLA -> TemporalUtils.islamicDaysInMonth(calendarYear, 12) == 30;
            case ISLAMIC_UMALQURA -> TemporalConstants.UMALQURA_KNOWN_LEAP_YEARS_1390_TO_1469.contains(calendarYear)
                    || (calendarYear < 1390 || calendarYear > 1469)
                    && umalquraDaysInMonth(calendarYear, 12) == 30;
            case PERSIAN -> IsoGregorianYear.isPersianLeapYear(calendarYear);
            case CHINESE, DANGI -> calendarYear >= 1900
                    && calendarYear <= getLunisolarMaxYear()
                    && getLunisolarLeapMonth(calendarYear) != 0;
        };
    }

    public boolean isChineseOrDangiCalendar() {
        return this == CHINESE || this == DANGI;
    }

    int lunisolarLeapMonthDays(int calendarYear) {
        int leapMonth = getLunisolarLeapMonth(calendarYear);
        if (leapMonth == 0) {
            return 0;
        }
        Integer yearInfo = getLunisolarYearInfo(calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        return (yearInfo & 0x10000) != 0 ? 30 : 29;
    }

    public TemporalEra toTemporalEra(String eraIdentifier) {
        if (eraIdentifier == null) {
            return null;
        }
        String normalizedEraIdentifier = eraIdentifier.toLowerCase(Locale.ROOT);
        return switch (this) {
            case GREGORY -> switch (normalizedEraIdentifier) {
                case "ce", "ad" -> TemporalEra.CE;
                case "bce", "bc" -> TemporalEra.BCE;
                default -> null;
            };
            case JAPANESE -> switch (normalizedEraIdentifier) {
                case "ce", "ad" -> TemporalEra.CE;
                case "bce", "bc" -> TemporalEra.BCE;
                case "meiji" -> TemporalEra.MEIJI;
                case "taisho" -> TemporalEra.TAISHO;
                case "showa" -> TemporalEra.SHOWA;
                case "heisei" -> TemporalEra.HEISEI;
                case "reiwa" -> TemporalEra.REIWA;
                default -> null;
            };
            case ROC -> switch (normalizedEraIdentifier) {
                case "roc", "minguo" -> TemporalEra.ROC;
                case "broc", "before-roc" -> TemporalEra.BROC;
                default -> null;
            };
            case BUDDHIST -> "be".equals(normalizedEraIdentifier) ? TemporalEra.BE : null;
            case COPTIC, HEBREW -> "am".equals(normalizedEraIdentifier) ? TemporalEra.AM : null;
            case ETHIOAA -> "aa".equals(normalizedEraIdentifier) ? TemporalEra.AA : null;
            case ETHIOPIC -> switch (normalizedEraIdentifier) {
                case "aa" -> TemporalEra.AA;
                case "am" -> TemporalEra.AM;
                default -> null;
            };
            case INDIAN -> switch (normalizedEraIdentifier) {
                case "shaka", "saka" -> TemporalEra.SHAKA;
                default -> null;
            };
            case ISLAMIC_CIVIL, ISLAMIC_TBLA, ISLAMIC_UMALQURA -> switch (normalizedEraIdentifier) {
                case "ah" -> TemporalEra.AH;
                case "bh" -> TemporalEra.BH;
                default -> null;
            };
            case PERSIAN -> "ap".equals(normalizedEraIdentifier) ? TemporalEra.AP : null;
            default -> null;
        };
    }
}
