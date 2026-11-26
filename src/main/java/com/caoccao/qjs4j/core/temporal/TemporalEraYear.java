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

public record TemporalEraYear(TemporalEra era, Integer eraYear) {

    private static final IsoDate MEIJI_START = new IsoDate(1873, 1, 1);
    private static final IsoDate TAISHO_START = new IsoDate(1912, 7, 30);
    private static final IsoDate SHOWA_START = new IsoDate(1926, 12, 25);
    private static final IsoDate HEISEI_START = new IsoDate(1989, 1, 8);
    private static final IsoDate REIWA_START = new IsoDate(2019, 5, 1);

    public static TemporalEraYear fromDate(IsoDate date, TemporalCalendarId calendar, int year) {
        return switch (calendar) {
            case GREGORY -> signed(year, TemporalEra.CE, TemporalEra.BCE);
            case JAPANESE -> japanese(date);
            case ROC -> signed(year, TemporalEra.ROC, TemporalEra.BROC);
            case BUDDHIST -> new TemporalEraYear(TemporalEra.BE, year);
            case COPTIC, HEBREW -> new TemporalEraYear(TemporalEra.AM, year);
            case ETHIOPIC -> year <= 0
                    ? new TemporalEraYear(TemporalEra.AA, 5500 + year)
                    : new TemporalEraYear(TemporalEra.AM, year);
            case ETHIOAA -> new TemporalEraYear(TemporalEra.AA, year);
            case INDIAN -> new TemporalEraYear(TemporalEra.SHAKA, year);
            case PERSIAN -> new TemporalEraYear(TemporalEra.AP, year);
            case ISLAMIC_CIVIL, ISLAMIC_TBLA, ISLAMIC_UMALQURA -> signed(year, TemporalEra.AH, TemporalEra.BH);
            default -> null;
        };
    }

    private static TemporalEraYear signed(int year, TemporalEra positive, TemporalEra negative) {
        return year > 0 ? new TemporalEraYear(positive, year) : new TemporalEraYear(negative, 1 - year);
    }

    private static TemporalEraYear japanese(IsoDate date) {
        TemporalEra era;
        if (date.compareTo(REIWA_START) >= 0) {
            era = TemporalEra.REIWA;
        } else if (date.compareTo(HEISEI_START) >= 0) {
            era = TemporalEra.HEISEI;
        } else if (date.compareTo(SHOWA_START) >= 0) {
            era = TemporalEra.SHOWA;
        } else if (date.compareTo(TAISHO_START) >= 0) {
            era = TemporalEra.TAISHO;
        } else if (date.compareTo(MEIJI_START) >= 0) {
            era = TemporalEra.MEIJI;
        } else {
            return signed(date.year(), TemporalEra.CE, TemporalEra.BCE);
        }
        return new TemporalEraYear(era, date.year() - era.getEraYearByJapaneseEraYear(0));
    }
}
