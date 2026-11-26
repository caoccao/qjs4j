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
import com.caoccao.qjs4j.core.temporal.*;

/** Already-coerced fields supplied to Temporal's calendar date merge. */
record TemporalPartialDate(Integer day, Integer month, String monthCode, Integer year, String era, Integer eraYear) {
    boolean isEmpty() {
        return day == null && month == null && monthCode == null && year == null && era == null && eraYear == null;
    }
    JSValue merge(JSContext context, JSTemporalPlainDate original, JSValue options) {
        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        IsoDate merged = mergeDate(context, original.getIsoDate(), original.getCalendarId(), overflow);
        return merged == null
                ? JSUndefined.INSTANCE
                : JSTemporalPlainDate.create(context, merged, original.getCalendarId());
    }

    private TemporalPartialDate mergeFields(IsoDate original, TemporalCalendarId calendar) {
        IsoCalendarDate fields = original.toIsoCalendarDate(calendar);
        boolean keepMonth = month == null && monthCode == null;
        boolean keepYear = year == null && era == null && eraYear == null;
        TemporalEraYear originalEra = keepYear && calendar.hasEra()
                ? TemporalEraYear.fromDate(original, calendar, fields.year())
                : null;
        Integer mergedMonth = keepMonth && calendar == TemporalCalendarId.ISO8601
                ? Integer.valueOf(fields.month())
                : month;
        return new TemporalPartialDate(day == null ? Integer.valueOf(fields.day()) : day, mergedMonth,
                keepMonth ? fields.monthCode() : monthCode, keepYear ? Integer.valueOf(fields.year()) : year,
                originalEra == null ? era : originalEra.era().identifier(),
                originalEra == null ? eraYear : originalEra.eraYear());
    }

    private Integer resolveYear(JSContext context, TemporalCalendarId calendar, String missingEraMessage) {
        return TemporalUtils.resolveEraYear(context, calendar, year, era, eraYear, missingEraMessage);
    }

    JSValue mergeYearMonth(JSContext context, JSTemporalPlainYearMonth original, String overflow) {
        TemporalCalendarId calendar = original.getCalendarId();
        TemporalPartialDate merged = mergeFields(original.getIsoDate(), calendar);
        IsoMonth code = merged.monthCode == null
                ? null
                : IsoMonth.parseByMonthCode(context, merged.monthCode, "Temporal error: Month code out of range.");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer resolvedYear = merged.resolveYear(context, calendar,
                "Temporal error: year argument must be an object.");
        if (resolvedYear == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalCalendarFields.yearMonth(context, calendar, resolvedYear, merged.month, code, overflow);
    }

    IsoDate mergeDate(JSContext context, IsoDate original, TemporalCalendarId calendar, String overflow) {
        if (isEmpty()) {
            return original;
        }
        TemporalPartialDate merged = mergeFields(original, calendar);
        String code = merged.monthCode;
        if (year != null && month == null && monthCode == null && !"reject".equals(overflow)) {
            String constrained = calendar.constrainMonthCode(year, code);
            if (constrained != null) {
                code = constrained;
            }
        }
        IsoMonth parsedCode = code == null
                ? null
                : IsoMonth.parseByMonthCode(context, code, "Temporal error: Month code out of range.");
        if (context.hasPendingException()) {
            return null;
        }
        Integer resolvedYear = merged.resolveYear(context, calendar,
                "Temporal error: Date argument must be object or string.");
        if (resolvedYear == null) {
            return null;
        }
        return IsoDate.calendarDateToIsoDate(context, calendar, resolvedYear, merged.month,
                parsedCode == null ? null : parsedCode.monthCode(), merged.day, overflow);
    }
}
