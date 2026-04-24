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

import java.time.DateTimeException;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Calendar month slot container and cache.
 * <p>
 * The name follows the existing user request.
 */
public final class TemporalMonths extends ArrayList<IsoCalendarMonth> {
    private static final ConcurrentHashMap<Long, TemporalMonths> CACHE = new ConcurrentHashMap<>();
    private static final Queue<Long> CACHE_EVICTION_QUEUE = new ConcurrentLinkedQueue<>();
    private static final int CACHE_SIZE = 2_048;
    private final Map<String, IsoCalendarMonth> monthSlotsByCode;

    private TemporalMonths() {
        super();
        monthSlotsByCode = new HashMap<>();
    }

    private static TemporalMonths create(TemporalCalendarId calendarId, int calendarYear) {
        if (calendarId == TemporalCalendarId.HEBREW) {
            return createHebrew(calendarYear);
        }
        if (calendarId == TemporalCalendarId.CHINESE || calendarId == TemporalCalendarId.DANGI) {
            return createLunisolar(calendarId, calendarYear);
        }
        if (calendarId == TemporalCalendarId.COPTIC
                || calendarId == TemporalCalendarId.ETHIOPIC
                || calendarId == TemporalCalendarId.ETHIOAA) {
            int underlyingYear;
            if (calendarId == TemporalCalendarId.ETHIOAA) {
                underlyingYear = calendarYear - 5500;
            } else {
                underlyingYear = calendarYear;
            }
            TemporalMonths moths = new TemporalMonths();
            for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
                moths.addMonthSlot(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 30));
            }
            int monthThirteenDays;
            if (TemporalUtils.alexandrianLeapYear(underlyingYear)) {
                monthThirteenDays = 6;
            } else {
                monthThirteenDays = 5;
            }
            moths.addMonthSlot(new IsoCalendarMonth(13, false, "M13", monthThirteenDays));
            return moths;
        }
        if (calendarId == TemporalCalendarId.INDIAN) {
            return createIndian(calendarYear);
        }
        if (calendarId == TemporalCalendarId.PERSIAN) {
            return createPersian(calendarYear);
        }
        if (calendarId == TemporalCalendarId.ISLAMIC_CIVIL
                || calendarId == TemporalCalendarId.ISLAMIC_TBLA) {
            return createIslamic(calendarYear);
        }
        if (calendarId == TemporalCalendarId.ISLAMIC_UMALQURA) {
            return createUmalqura(calendarYear);
        }
        int isoYear = calendarYear;
        if (calendarId == TemporalCalendarId.BUDDHIST) {
            isoYear = calendarYear - 543;
        } else if (calendarId == TemporalCalendarId.ROC) {
            isoYear = calendarYear + 1911;
        }
        return createGregorianLike(isoYear);
    }

    private static TemporalMonths createGregorianLike(int isoYear) {
        TemporalMonths moths = new TemporalMonths();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            moths.addMonthSlot(new IsoCalendarMonth(
                    monthNumber,
                    false,
                    IsoMonth.toMonthCode(monthNumber),
                    IsoDate.daysInMonth(isoYear, monthNumber)));
        }
        return moths;
    }

    private static TemporalMonths createHebrew(int calendarYear) {
        boolean leapYear = TemporalUtils.isHebrewLeapYear(calendarYear);
        boolean longHeshvan = TemporalUtils.isHebrewYearLength(calendarYear) % 10L == 5L;
        boolean shortKislev = TemporalUtils.isHebrewYearLength(calendarYear) % 10L == 3L;
        TemporalMonths moths = new TemporalMonths();
        int monthNumberInYear = 1;
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M01", 30));
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M02", longHeshvan ? 30 : 29));
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M03", shortKislev ? 29 : 30));
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M04", 29));
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M05", 30));
        if (leapYear) {
            moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, true, "M05L", 30));
            moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M06", 29));
        } else {
            moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M06", 29));
        }
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M07", 30));
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M08", 29));
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M09", 30));
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M10", 29));
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, "M11", 30));
        moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear, false, "M12", 29));
        return moths;
    }

    private static TemporalMonths createIndian(int calendarYear) {
        TemporalMonths moths = new TemporalMonths();
        int gregorianYear = calendarYear + 78;
        boolean gregorianLeapYear = TemporalUtils.isLeapYear(gregorianYear);
        moths.addMonthSlot(new IsoCalendarMonth(1, false, "M01", gregorianLeapYear ? 31 : 30));
        for (int monthNumber = 2; monthNumber <= 6; monthNumber++) {
            moths.addMonthSlot(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 31));
        }
        for (int monthNumber = 7; monthNumber <= 12; monthNumber++) {
            moths.addMonthSlot(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 30));
        }
        return moths;
    }

    private static TemporalMonths createIslamic(int calendarYear) {
        TemporalMonths moths = new TemporalMonths();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            moths.addMonthSlot(new IsoCalendarMonth(
                    monthNumber,
                    false,
                    IsoMonth.toMonthCode(monthNumber),
                    TemporalUtils.islamicDaysInMonth(calendarYear, monthNumber)));
        }
        return moths;
    }

    private static TemporalMonths createLunisolar(TemporalCalendarId calendarId, int calendarYear) {
        if (calendarYear < 1900 || calendarYear > calendarId.getLunisolarMaxYear()) {
            TemporalMonths fallbackMoths = new TemporalMonths();
            for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
                fallbackMoths.addMonthSlot(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 30));
            }
            return fallbackMoths;
        }
        TemporalMonths moths = new TemporalMonths();
        int monthNumberInYear = 1;
        int leapMonth = calendarId.getLunisolarLeapMonth(calendarYear);
        for (int regularMonth = 1; regularMonth <= 12; regularMonth++) {
            int regularMonthLength = calendarId.getLunisolarMonthDays(calendarYear, regularMonth);
            String regularMonthCode = IsoMonth.toMonthCode(regularMonth);
            moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, false, regularMonthCode, regularMonthLength));
            if (leapMonth == regularMonth) {
                int leapMonthLength = calendarId.lunisolarLeapMonthDays(calendarYear);
                String leapMonthCode = regularMonthCode + "L";
                moths.addMonthSlot(new IsoCalendarMonth(monthNumberInYear++, true, leapMonthCode, leapMonthLength));
            }
        }
        return moths;
    }

    private static TemporalMonths createPersian(int calendarYear) {
        TemporalMonths moths = new TemporalMonths();
        for (int monthNumber = 1; monthNumber <= 6; monthNumber++) {
            moths.addMonthSlot(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 31));
        }
        for (int monthNumber = 7; monthNumber <= 11; monthNumber++) {
            moths.addMonthSlot(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 30));
        }
        moths.addMonthSlot(new IsoCalendarMonth(
                12,
                false,
                "M12",
                IsoGregorianYear.isPersianLeapYear(calendarYear) ? 30 : 29));
        return moths;
    }

    private static TemporalMonths createUmalqura(int calendarYear) {
        TemporalMonths moths = new TemporalMonths();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            int dayCount;
            try {
                HijrahDate monthStart = HijrahChronology.INSTANCE.date(calendarYear, monthNumber, 1);
                dayCount = monthStart.lengthOfMonth();
            } catch (DateTimeException dateTimeException) {
                dayCount = TemporalUtils.islamicDaysInMonth(calendarYear, monthNumber);
            }
            moths.addMonthSlot(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), dayCount));
        }
        return moths;
    }

    public static TemporalMonths get(TemporalCalendarId calendarId, int calendarYear) {
        long cacheKey = TemporalUtils.getCalendarYearCacheKey(calendarId, calendarYear);
        TemporalMonths cachedMoths = CACHE.get(cacheKey);
        if (cachedMoths != null) {
            return cachedMoths;
        }
        TemporalMonths createdMoths = create(calendarId, calendarYear);
        TemporalUtils.putBoundedMapEntry(
                CACHE,
                CACHE_EVICTION_QUEUE,
                cacheKey,
                createdMoths,
                CACHE_SIZE);
        TemporalMonths resolvedMoths = CACHE.get(cacheKey);
        if (resolvedMoths != null) {
            return resolvedMoths;
        }
        return createdMoths;
    }

    private void addMonthSlot(IsoCalendarMonth monthSlot) {
        add(monthSlot);
        monthSlotsByCode.put(monthSlot.monthCode(), monthSlot);
    }

    public IsoCalendarMonth getByMonthCode(String monthCode) {
        return monthSlotsByCode.get(monthCode);
    }

    public IsoCalendarMonth getByMonthNumber(int monthNumber) {
        if (monthNumber < 1 || monthNumber > size()) {
            return null;
        }
        return get(monthNumber - 1);
    }
}
