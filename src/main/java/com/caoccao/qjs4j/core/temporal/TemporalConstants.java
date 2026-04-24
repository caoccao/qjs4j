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

import java.math.BigInteger;
import java.util.Set;

/**
 * Centralized constants for the Temporal implementation.
 * Eliminates duplication of nanosecond conversion factors, epoch day boundaries,
 * calendar epoch offsets, and solar day subdivisions across temporal files.
 */
public final class TemporalConstants {

    // ── Nanosecond conversion factors (BigInteger) ──────────────────────────

    public static final BigInteger BI_DAY_NANOSECONDS = BigInteger.valueOf(86_400_000_000_000L);
    public static final BigInteger BI_HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    public static final BigInteger BI_MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    public static final BigInteger BI_MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    public static final BigInteger BI_MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    public static final BigInteger BI_SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);
    /**
     * Alias: 1_000_000_000 as BigInteger, used for epoch-second ↔ nanosecond conversion.
     */
    public static final BigInteger BI_BILLION = BI_SECOND_NANOSECONDS;
    public static final BigInteger BI_WEEK_NANOSECONDS = BI_DAY_NANOSECONDS.multiply(BigInteger.valueOf(7L));

    // ── Nanosecond conversion factors (long, for fast-path arithmetic) ──────
    public static final long CALENDAR_UNIT_MAX = 4_294_967_295L;
    public static final long COPTIC_EPOCH_DAY_OFFSET = -615_558L;
    public static final long DAY_NANOSECONDS = 86_400_000_000_000L;
    public static final long ETHIOPIC_EPOCH_DAY_OFFSET = -716_367L;
    public static final long HEBREW_EPOCH_DAY_OFFSET = -2_092_591L;
    public static final long HOUR_NANOSECONDS = 3_600_000_000_000L;

    // ── Solar day subdivisions (for Instant rounding) ───────────────────────
    public static final long ISLAMIC_CIVIL_EPOCH_DAY_OFFSET = -492_148L;
    public static final long ISLAMIC_TBLA_EPOCH_DAY_OFFSET = -492_149L;
    public static final int[] LUNISOLAR_MONTH_LENGTHS_YEAR_1899 = {
            30, 29, 30, 30, 29, 30, 29, 30, 29, 30, 29, 30
    };
    public static final BigInteger MAX_ABSOLUTE_TIME_NANOSECONDS =
            BigInteger.valueOf(9_007_199_254_740_992L).multiply(BI_SECOND_NANOSECONDS).subtract(BigInteger.ONE);
    public static final long MAX_ROUNDING_INCREMENT = 1_000_000_000L;
    public static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();

    // ── Epoch day boundaries ────────────────────────────────────────────────
    public static final long MICROSECOND_NANOSECONDS = 1_000L;
    public static final long MILLISECOND_NANOSECONDS = 1_000_000L;

    // ── Maximum rounding increment ──────────────────────────────────────────
    public static final long MINUTE_NANOSECONDS = 60_000_000_000L;

    // ── Calendar epoch day offsets ──────────────────────────────────────────
    public static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();
    public static final long SECOND_NANOSECONDS = 1_000_000_000L;
    public static final long SOLAR_DAY_HOURS = 24L;
    public static final long SOLAR_DAY_MICROSECONDS = 86_400_000_000L;
    public static final long SOLAR_DAY_MILLISECONDS = 86_400_000L;

    // ── Lunisolar reference data ────────────────────────────────────────────
    public static final long SOLAR_DAY_MINUTES = 1_440L;
    public static final long SOLAR_DAY_NANOSECONDS = 86_400_000_000_000L;

    // ── Absolute time duration limit ────────────────────────────────────────
    public static final long SOLAR_DAY_SECONDS = 86_400L;
    public static final Set<Integer> UMALQURA_KNOWN_LEAP_YEARS_1390_TO_1469 = Set.of(
            1390, 1392, 1397, 1399, 1403, 1405, 1406, 1411, 1412, 1414,
            1418, 1420, 1425, 1426, 1428, 1433, 1435, 1439, 1441, 1443,
            1447, 1448, 1451, 1454, 1455, 1457, 1462, 1463, 1467, 1469);

    private TemporalConstants() {
    }
}
