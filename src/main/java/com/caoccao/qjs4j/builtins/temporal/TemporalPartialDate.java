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
    JSValue merge(JSContext context, JSTemporalPlainDate plainDate, JSValue optionsValue) {
        TemporalCalendarId calendarId = plainDate.getCalendarId();
        IsoCalendarDate calendarFields = plainDate.toIsoCalendarDate();
        IsoDate calendarDate = calendarFields.toIsoDate();
        if (context.hasPendingException() || calendarDate == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarMonthCode = calendarFields.monthCode();
        if (context.hasPendingException() || calendarMonthCode == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraYear eraFields = plainDate.toTemporalEraYear();

        JSObject mergedFieldsObject = mergeFields(context, calendarId, calendarDate, calendarMonthCode, eraFields,
                true);

        String overflow = TemporalUtils.getOverflowOption(context, optionsValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!"reject".equals(overflow) && year != null && month == null && monthCode == null) {
            JSValue mergedMonthCodeValue = mergedFieldsObject.get(PropertyKey.fromString("monthCode"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (mergedMonthCodeValue instanceof JSString mergedMonthCodeString) {
                String constrainedMonthCode = calendarId.constrainMonthCode(year, mergedMonthCodeString.value());
                if (constrainedMonthCode != null && !constrainedMonthCode.equals(mergedMonthCodeString.value())) {
                    mergedFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(constrainedMonthCode));
                }
            }
        }
        JSObject normalizedOptionsObject = TemporalUtils.overflowOptions(context, overflow);
        JSValue mergedDateValue = TemporalPlainDateConstructor.dateFromFields(context, mergedFieldsObject,
                normalizedOptionsObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(mergedDateValue instanceof JSTemporalPlainDate mergedDate)) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDate.create(context, mergedDate.getIsoDate(), calendarId);
    }

    JSObject mergeFields(JSContext context, TemporalCalendarId calendarId, IsoDate calendarDate,
            String calendarMonthCode, TemporalEraYear eraFields, boolean includeDay) {
        JSObject mergedFieldsObject = new JSObject(context);
        mergedFieldsObject.set(PropertyKey.fromString("calendar"), new JSString(calendarId.identifier()));
        if (includeDay) {
            mergedFieldsObject.set(PropertyKey.fromString("day"), JSNumber.of(calendarDate.day()));
        }
        if (calendarId == TemporalCalendarId.ISO8601) {
            mergedFieldsObject.set(PropertyKey.fromString("month"), JSNumber.of(calendarDate.month()));
        }
        mergedFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(calendarMonthCode));
        mergedFieldsObject.set(PropertyKey.fromString("year"), JSNumber.of(calendarDate.year()));
        if (eraFields != null) {
            mergedFieldsObject.set(PropertyKey.fromString("era"), new JSString(eraFields.era().identifier()));
            if (eraFields.eraYear() != null) {
                mergedFieldsObject.set(PropertyKey.fromString("eraYear"), JSNumber.of(eraFields.eraYear()));
            }
        }

        if (year != null) {
            mergedFieldsObject.delete(PropertyKey.fromString("era"));
            mergedFieldsObject.delete(PropertyKey.fromString("eraYear"));
        }
        if (era != null || eraYear != null) {
            mergedFieldsObject.delete(PropertyKey.fromString("year"));
            mergedFieldsObject.delete(PropertyKey.fromString("era"));
            mergedFieldsObject.delete(PropertyKey.fromString("eraYear"));
        }
        if (month != null) {
            mergedFieldsObject.delete(PropertyKey.fromString("monthCode"));
        }
        if (monthCode != null) {
            mergedFieldsObject.delete(PropertyKey.fromString("month"));
        }

        if (day != null) {
            mergedFieldsObject.set(PropertyKey.fromString("day"), JSNumber.of(day));
        }
        if (month != null) {
            mergedFieldsObject.set(PropertyKey.fromString("month"), JSNumber.of(month));
        }
        if (monthCode != null) {
            mergedFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(monthCode));
        }
        if (year != null) {
            mergedFieldsObject.set(PropertyKey.fromString("year"), JSNumber.of(year));
        }
        if (era != null) {
            mergedFieldsObject.set(PropertyKey.fromString("era"), new JSString(era));
        }
        if (eraYear != null) {
            mergedFieldsObject.set(PropertyKey.fromString("eraYear"), JSNumber.of(eraYear));
        }

        return mergedFieldsObject;
    }

    IsoDate mergeDate(JSContext context, IsoDate original, TemporalCalendarId calendar, String overflow) {
        if (isEmpty()) {
            return original;
        }
        JSTemporalPlainDate date = JSTemporalPlainDate.create(context, original, calendar);
        if (context.hasPendingException()) {
            return null;
        }
        JSValue merged = merge(context, date, TemporalUtils.overflowOptions(context, overflow));
        if (context.hasPendingException()) {
            return null;
        }
        if (merged instanceof JSTemporalPlainDate result) {
            return result.getIsoDate();
        }
        context.throwTypeError("Temporal error: Date argument must be object or string.");
        return null;
    }
}
