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

import com.caoccao.qjs4j.core.temporal.*;

import java.time.LocalDate;

/**
 * JSObject subclass representing a Temporal.PlainDate value.
 * Internal slots: [[ISODate]], [[Calendar]]
 */
public final class JSTemporalPlainDate extends JSObject {
    private final TemporalCalendarId calendarId;
    private final IsoDate isoDate;

    public JSTemporalPlainDate(JSContext context, IsoDate isoDate, TemporalCalendarId calendarId) {
        super(context);
        this.isoDate = isoDate;
        this.calendarId = calendarId;
    }

    public TemporalCalendarId getCalendarId() {
        return calendarId;
    }

    public IsoDate getIsoDate() {
        return isoDate;
    }

    public IsoCalendarDate toIsoCalendarDate() {
        return isoDate.toIsoCalendarDate(calendarId);
    }

    public TemporalEraYear toTemporalEraYear() {
        if (calendarId == TemporalCalendarId.ISO8601
                || calendarId == TemporalCalendarId.CHINESE
                || calendarId == TemporalCalendarId.DANGI) {
            return null;
        }

        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        int calendarYear = calendarDateFields.year();

        if (calendarId == TemporalCalendarId.GREGORY) {
            if (calendarYear <= 0) {
                return new TemporalEraYear(TemporalEra.BCE, 1 - calendarYear);
            } else {
                return new TemporalEraYear(TemporalEra.CE, calendarYear);
            }
        }

        if (calendarId == TemporalCalendarId.JAPANESE) {
            LocalDate date = LocalDate.of(isoDate.year(), isoDate.month(), isoDate.day());
            LocalDate reiwaStart = LocalDate.of(2019, 5, 1);
            LocalDate heiseiStart = LocalDate.of(1989, 1, 8);
            LocalDate showaStart = LocalDate.of(1926, 12, 25);
            LocalDate taishoStart = LocalDate.of(1912, 7, 30);
            LocalDate meijiStart = LocalDate.of(1873, 1, 1);

            if (!date.isBefore(reiwaStart)) {
                return new TemporalEraYear(TemporalEra.REIWA, isoDate.year() - 2018);
            } else if (!date.isBefore(heiseiStart)) {
                return new TemporalEraYear(TemporalEra.HEISEI, isoDate.year() - 1988);
            } else if (!date.isBefore(showaStart)) {
                return new TemporalEraYear(TemporalEra.SHOWA, isoDate.year() - 1925);
            } else if (!date.isBefore(taishoStart)) {
                return new TemporalEraYear(TemporalEra.TAISHO, isoDate.year() - 1911);
            } else if (!date.isBefore(meijiStart)) {
                return new TemporalEraYear(TemporalEra.MEIJI, isoDate.year() - 1867);
            } else if (isoDate.year() <= 0) {
                return new TemporalEraYear(TemporalEra.BCE, 1 - isoDate.year());
            } else {
                return new TemporalEraYear(TemporalEra.CE, isoDate.year());
            }
        }

        if (calendarId == TemporalCalendarId.ROC) {
            if (calendarYear >= 1) {
                return new TemporalEraYear(TemporalEra.ROC, calendarYear);
            } else {
                return new TemporalEraYear(TemporalEra.BROC, 1 - calendarYear);
            }
        }

        if (calendarId == TemporalCalendarId.BUDDHIST) {
            return new TemporalEraYear(TemporalEra.BE, calendarYear);
        } else if (calendarId == TemporalCalendarId.COPTIC) {
            return new TemporalEraYear(TemporalEra.AM, calendarYear);
        } else if (calendarId == TemporalCalendarId.ETHIOPIC) {
            if (calendarYear <= 0) {
                return new TemporalEraYear(TemporalEra.AA, 5500 + calendarYear);
            } else {
                return new TemporalEraYear(TemporalEra.AM, calendarYear);
            }
        } else if (calendarId == TemporalCalendarId.ETHIOAA) {
            return new TemporalEraYear(TemporalEra.AA, calendarYear);
        } else if (calendarId == TemporalCalendarId.HEBREW) {
            return new TemporalEraYear(TemporalEra.AM, calendarYear);
        } else if (calendarId == TemporalCalendarId.INDIAN) {
            return new TemporalEraYear(TemporalEra.SHAKA, calendarYear);
        } else if (calendarId == TemporalCalendarId.PERSIAN) {
            return new TemporalEraYear(TemporalEra.AP, calendarYear);
        } else if (calendarId == TemporalCalendarId.ISLAMIC_CIVIL
                || calendarId == TemporalCalendarId.ISLAMIC_TBLA
                || calendarId == TemporalCalendarId.ISLAMIC_UMALQURA) {
            if (calendarYear > 0) {
                return new TemporalEraYear(TemporalEra.AH, calendarYear);
            } else {
                return new TemporalEraYear(TemporalEra.BH, 1 - calendarYear);
            }
        }

        return null;
    }
}
