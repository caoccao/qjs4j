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

public record IsoGregorianYear(int gregorianYear, int marchDay, boolean leapYear) {
    private static final int[] PERSIAN_BREAKS = {
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
            1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394,
            2456, 3178
    };
    public static final int MAX_SUPPORTED_PERSIAN_YEAR = PERSIAN_BREAKS[PERSIAN_BREAKS.length - 1] - 1;
    public static final int MIN_SUPPORTED_PERSIAN_YEAR = PERSIAN_BREAKS[0] + 1;
    private static final int PERSIAN_FALLBACK_MAX_CORRECTION_DAYS = 62;
    private static final int PERSIAN_FALLBACK_MAX_YEAR = 275_139;
    private static final int PERSIAN_FALLBACK_MIN_CORRECTION_DAYS = -61;
    private static final int PERSIAN_FALLBACK_MIN_YEAR = -272_442;

    public static IsoGregorianYear createPersian(int persianYear) {
        if (persianYear <= PERSIAN_BREAKS[0] || persianYear >= PERSIAN_BREAKS[PERSIAN_BREAKS.length - 1]) {
            return null;
        }

        int gregorianYear = persianYear + 621;
        int leapCount = -14;
        int previousBreak = PERSIAN_BREAKS[0];
        int breakYear = 0;
        int jumpLength = 0;
        for (int breakIndex = 1; breakIndex < PERSIAN_BREAKS.length; breakIndex++) {
            breakYear = PERSIAN_BREAKS[breakIndex];
            jumpLength = breakYear - previousBreak;
            if (persianYear < breakYear) {
                break;
            }
            leapCount += (jumpLength / 33) * 8 + (jumpLength % 33) / 4;
            previousBreak = breakYear;
        }

        int yearsSinceBreak = persianYear - previousBreak;
        leapCount += (yearsSinceBreak / 33) * 8 + (yearsSinceBreak % 33 + 3) / 4;
        if (jumpLength % 33 == 4 && jumpLength - yearsSinceBreak == 4) {
            leapCount++;
        }

        int gregorianLeapCount = gregorianYear / 4 - ((gregorianYear / 100 + 1) * 3) / 4 - 150;
        int marchDay = 20 + leapCount - gregorianLeapCount;

        if (jumpLength - yearsSinceBreak < 6) {
            yearsSinceBreak = yearsSinceBreak - jumpLength + (jumpLength + 4) / 33 * 33;
        }
        int leapRemainder = ((yearsSinceBreak + 1) % 33 - 1) % 4;
        if (leapRemainder < 0) {
            leapRemainder += 4;
        }
        return new IsoGregorianYear(gregorianYear, marchDay, leapRemainder == 0);
    }

    public static boolean isPersianLeapYear(int persianYear) {
        IsoGregorianYear persianYearInfo = createPersian(persianYear);
        if (persianYearInfo != null) {
            return persianYearInfo.leapYear();
        }
        long currentYearStartEpochDay = persianCorrectedEpochDay(persianYear, 1, 1);
        long nextYearStartEpochDay = persianCorrectedEpochDay(persianYear + 1, 1, 1);
        return nextYearStartEpochDay - currentYearStartEpochDay == 366L;
    }

    public static long persianArithmeticEpochDay(int arithmeticPersianYear, int persianMonth, int dayOfMonth) {
        long yearsSinceEpochCycle = arithmeticPersianYear - (arithmeticPersianYear >= 0 ? 474L : 473L);
        long cycleYear = 474L + Math.floorMod(yearsSinceEpochCycle, 2820L);
        long dayOfYear = persianMonth <= 7
                ? (persianMonth - 1L) * 31L + dayOfMonth
                : (persianMonth - 1L) * 30L + dayOfMonth + 6L;
        long julianDay = dayOfYear
                + Math.floorDiv(cycleYear * 682L - 110L, 2816L)
                + (cycleYear - 1L) * 365L
                + Math.floorDiv(yearsSinceEpochCycle, 2820L) * 1_029_983L
                + 1_948_320L;
        return julianDay - 2_440_588L;
    }

    public static long persianCorrectedEpochDay(int persianYear, int persianMonth, int dayOfMonth) {
        return persianEpochDay(persianYear, persianMonth, dayOfMonth)
                + persianFallbackEpochDayCorrection(persianYear);
    }

    public static long persianEpochDay(int persianYear, int persianMonth, int dayOfMonth) {
        int arithmeticPersianYear = TemporalUtils.toArithmeticPersianYear(persianYear);
        return persianArithmeticEpochDay(arithmeticPersianYear, persianMonth, dayOfMonth);
    }

    private static long persianFallbackEpochDayCorrection(int persianYear) {
        if (persianYear <= PERSIAN_FALLBACK_MIN_YEAR) {
            return PERSIAN_FALLBACK_MIN_CORRECTION_DAYS;
        }
        if (persianYear >= PERSIAN_FALLBACK_MAX_YEAR) {
            return PERSIAN_FALLBACK_MAX_CORRECTION_DAYS;
        }
        double yearRange = PERSIAN_FALLBACK_MAX_YEAR - PERSIAN_FALLBACK_MIN_YEAR;
        double yearProgress = (double) (persianYear - PERSIAN_FALLBACK_MIN_YEAR) / yearRange;
        double correctionRange = PERSIAN_FALLBACK_MAX_CORRECTION_DAYS - PERSIAN_FALLBACK_MIN_CORRECTION_DAYS;
        double correction = PERSIAN_FALLBACK_MIN_CORRECTION_DAYS + yearProgress * correctionRange;
        return Math.round(correction);
    }
}
