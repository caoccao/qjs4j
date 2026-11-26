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

    private void addMonth(int regularMonth, boolean leap, int days) {
        String code = IsoMonth.toMonthCode(regularMonth);
        addMonthSlot(new IsoCalendarMonth(size() + 1, leap, leap ? code + "L" : code, days));
    }

    private void addMonthSlot(IsoCalendarMonth monthSlot) {
        add(monthSlot);
        monthSlotsByCode.put(monthSlot.monthCode(), monthSlot);
    }

    private void addRegularMonths(int count, int days) {
        for (int i = 0; i < count; i++) {
            addMonth(size() + 1, false, days);
        }
    }

    IsoCalendarDate dateAtDayOffset(int year, long dayOffset) {
        for (int index = 0; index < size(); index++) {
            IsoCalendarMonth month = get(index);
            if (dayOffset < month.daysInMonth()) {
                return new IsoCalendarDate(year, month.monthNumber(), month.monthCode(), (int) dayOffset + 1);
            }
            dayOffset -= month.daysInMonth();
        }
        IsoCalendarMonth last = get(size() - 1);
        return new IsoCalendarDate(year, last.monthNumber(), last.monthCode(), last.daysInMonth());
    }

    int daysBeforeMonth(int monthNumber) {
        int days = 0;
        for (int index = 0; index < monthNumber - 1; index++) {
            days += get(index).daysInMonth();
        }
        return days;
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

    private static TemporalMonths create(TemporalCalendarId calendar, int year) {
        return switch (calendar) {
            case HEBREW -> createHebrew(year);
            case CHINESE, DANGI -> createLunisolar(calendar, year);
            case COPTIC, ETHIOPIC -> createAlexandrian(year);
            case ETHIOAA -> createAlexandrian(year - 5500);
            case INDIAN -> createIndian(year);
            case PERSIAN -> createPersian(year);
            case ISLAMIC_CIVIL, ISLAMIC_TBLA, ISLAMIC_UMALQURA -> createIslamic(calendar, year);
            case BUDDHIST -> createGregorianLike(year - 543);
            case ROC -> createGregorianLike(year + 1911);
            default -> createGregorianLike(year);
        };
    }

    private static TemporalMonths createAlexandrian(int year) {
        TemporalMonths months = new TemporalMonths();
        months.addRegularMonths(12, 30);
        months.addMonth(13, false, TemporalUtils.alexandrianLeapYear(year) ? 6 : 5);
        return months;
    }

    private static TemporalMonths createGregorianLike(int year) {
        TemporalMonths months = new TemporalMonths();
        for (int month = 1; month <= 12; month++) {
            months.addMonth(month, false, IsoDate.daysInMonth(year, month));
        }
        return months;
    }

    private static TemporalMonths createHebrew(int year) {
        long yearLength = TemporalUtils.isHebrewYearLength(year);
        TemporalMonths months = new TemporalMonths();
        months.addMonth(1, false, 30);
        months.addMonth(2, false, yearLength % 10 == 5 ? 30 : 29);
        months.addMonth(3, false, yearLength % 10 == 3 ? 29 : 30);
        months.addMonth(4, false, 29);
        months.addMonth(5, false, 30);
        if (TemporalUtils.isHebrewLeapYear(year)) {
            months.addMonth(5, true, 30);
        }
        for (int month = 6; month <= 12; month++) {
            months.addMonth(month, false, month % 2 == 0 ? 29 : 30);
        }
        return months;
    }

    private static TemporalMonths createIndian(int year) {
        TemporalMonths months = new TemporalMonths();
        months.addMonth(1, false, TemporalUtils.isLeapYear(year + 78) ? 31 : 30);
        months.addRegularMonths(5, 31);
        months.addRegularMonths(6, 30);
        return months;
    }

    private static TemporalMonths createIslamic(TemporalCalendarId calendar, int year) {
        TemporalMonths months = new TemporalMonths();
        for (int month = 1; month <= 12; month++) {
            int days = calendar == TemporalCalendarId.ISLAMIC_UMALQURA
                    ? TemporalCalendarId.umalquraDaysInMonth(year, month)
                    : TemporalUtils.islamicDaysInMonth(year, month);
            months.addMonth(month, false, days);
        }
        return months;
    }

    private static TemporalMonths createLunisolar(TemporalCalendarId calendar, int year) {
        TemporalMonths months = new TemporalMonths();
        if (year < 1900 || year > calendar.getLunisolarMaxYear()) {
            months.addRegularMonths(12, 30);
        } else {
            int leap = calendar.getLunisolarLeapMonth(year);
            for (int month = 1; month <= 12; month++) {
                months.addMonth(month, false, calendar.getLunisolarMonthDays(year, month));
                if (month == leap) {
                    months.addMonth(month, true, calendar.lunisolarLeapMonthDays(year));
                }
            }
        }
        return months;
    }

    private static TemporalMonths createPersian(int year) {
        TemporalMonths months = new TemporalMonths();
        months.addRegularMonths(6, 31);
        months.addRegularMonths(5, 30);
        months.addMonth(12, false, IsoGregorianYear.isPersianLeapYear(year) ? 30 : 29);
        return months;
    }

    public static TemporalMonths get(TemporalCalendarId calendarId, int calendarYear) {
        long cacheKey = TemporalUtils.getCalendarYearCacheKey(calendarId, calendarYear);
        TemporalMonths cachedMoths = CACHE.get(cacheKey);
        if (cachedMoths != null) {
            return cachedMoths;
        }
        TemporalMonths createdMoths = create(calendarId, calendarYear);
        TemporalUtils.putBoundedMapEntry(CACHE, CACHE_EVICTION_QUEUE, cacheKey, createdMoths, CACHE_SIZE);
        TemporalMonths resolvedMoths = CACHE.get(cacheKey);
        if (resolvedMoths != null) {
            return resolvedMoths;
        }
        return createdMoths;
    }
}
