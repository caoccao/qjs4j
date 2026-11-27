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
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.IsoCalendarDate;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;

/** Intl.DateTimeFormat setup shared by the Temporal toLocaleString methods. */
public final class TemporalLocaleFormat {
    private static final String[] DATE_TIME_COMPONENTS = {"dateStyle", "timeStyle", "weekday", "year", "month", "day",
            "dayPeriod", "hour", "minute", "second", "fractionalSecondDigits"};

    private TemporalLocaleFormat() {
    }

    public static JSValue create(JSContext context, JSValue[] args, String excludedStyle) {
        JSValue locales = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        if (options instanceof JSObject object) {
            JSValue style = object.get(PropertyKey.fromString(excludedStyle));
            if (context.hasPendingException()) {
                return null;
            }
            if (style != null && !(style instanceof JSUndefined)) {
                context.throwTypeError("Invalid date/time formatting options");
                return null;
            }
        }
        JSValue formatter = JSIntlObject.createDateTimeFormat(context, null, new JSValue[]{locales, options});
        return context.hasPendingException() ? null : formatter;
    }

    private static JSObject createDateTimeFormatOptionsForZonedDateTime(JSContext context, JSValue options,
            String timeZoneId) {
        JSObject dateTimeFormatOptions = context.createJSObject();
        dateTimeFormatOptions.setPrototype(null);

        if (!(options instanceof JSUndefined)) {
            JSValue optionsObjectValue = JSTypeConversions.toObject(context, options);
            if (context.hasPendingException() || !(optionsObjectValue instanceof JSObject optionsObject)) {
                return null;
            }

            JSValue timeZoneOption = optionsObject.get(PropertyKey.fromString("timeZone"));
            if (context.hasPendingException()) {
                return null;
            }
            if (!(timeZoneOption instanceof JSUndefined) && timeZoneOption != null) {
                context.throwTypeError("Temporal error: timeZone option is not allowed.");
                return null;
            }

            PropertyKey[] ownPropertyKeys = optionsObject.ownPropertyKeys();
            for (PropertyKey ownPropertyKey : ownPropertyKeys) {
                PropertyDescriptor ownPropertyDescriptor = optionsObject.getOwnPropertyDescriptor(ownPropertyKey);
                if (ownPropertyDescriptor != null && ownPropertyDescriptor.isEnumerable()) {
                    JSValue ownPropertyValue = optionsObject.get(ownPropertyKey);
                    if (context.hasPendingException()) {
                        return null;
                    }
                    dateTimeFormatOptions.set(ownPropertyKey, ownPropertyValue);
                }
            }
        }

        boolean shouldApplyDefaultComponents = shouldApplyZonedDateTimeDefaultComponents(context,
                dateTimeFormatOptions);
        if (context.hasPendingException()) {
            return null;
        }
        if (shouldApplyDefaultComponents) {
            JSValue timeZoneNameOption = dateTimeFormatOptions.get(PropertyKey.fromString("timeZoneName"));
            if (context.hasPendingException()) {
                return null;
            }
            if (timeZoneNameOption instanceof JSUndefined || timeZoneNameOption == null) {
                String defaultTimeZoneNameOption = TemporalUtils.isOffsetTimeZoneIdentifier(timeZoneId)
                        ? "shortOffset"
                        : "short";
                dateTimeFormatOptions.set(PropertyKey.fromString("timeZoneName"),
                        new JSString(defaultTimeZoneNameOption));
            }
        }

        dateTimeFormatOptions.set(PropertyKey.fromString("timeZone"), new JSString(timeZoneId));
        return dateTimeFormatOptions;
    }

    public static JSValue format(JSContext context, JSTemporalZonedDateTime zonedDateTime, JSValue[] args) {
        JSValue locales = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        JSObject dateTimeFormatOptions = createDateTimeFormatOptionsForZonedDateTime(context, options,
                zonedDateTime.getTimeZoneId());
        if (context.hasPendingException() || dateTimeFormatOptions == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(context, null,
                new JSValue[]{locales, dateTimeFormatOptions}, "any", "all");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (zonedDateTime.getCalendarId() != TemporalCalendarId.ISO8601
                && !matchesCalendar(context, dateTimeFormat, zonedDateTime.getCalendarId())) {
            return JSUndefined.INSTANCE;
        }
        JSValue instant = JSTemporalInstant.create(context, zonedDateTime.getEpochNanoseconds());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{instant});
    }

    static JSValue formatDate(JSContext context, JSTemporalCalendarDate date, JSValue[] args) {
        JSValue formatter = create(context, args, "timeStyle");
        if (formatter == null || (!(date instanceof JSTemporalPlainDate)
                && !matchesCalendar(context, formatter, date.getCalendarId()))) {
            return JSUndefined.INSTANCE;
        }
        if (date instanceof JSTemporalPlainYearMonth && date.getCalendarId() != TemporalCalendarId.ISO8601) {
            IsoCalendarDate fields = date.toIsoCalendarDate();
            IsoDate middle = IsoDate.calendarDateToIsoDate(context, date.getCalendarId(), fields.year(), null,
                    fields.monthCode(), 15, "constrain");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (middle != null) {
                date = JSTemporalPlainYearMonth.create(context, middle, date.getCalendarId());
            }
        }
        return JSIntlObject.dateTimeFormatFormat(context, formatter, new JSValue[]{date});
    }

    public static <T extends JSTemporalCalendarDate> JSValue formatDate(JSContext context, JSValue receiver,
            JSValue[] args, TemporalReceiver<T> type) {
        T date = type.require(context, receiver, "toLocaleString");
        return date == null ? JSUndefined.INSTANCE : formatDate(context, date, args);
    }

    public static JSValue formatDateTime(JSContext context, JSValue value, JSValue[] args, String defaults) {
        JSValue locales = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(context, null, new JSValue[]{locales, options},
                "any", defaults);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{value});
    }

    static boolean matchesCalendar(JSContext context, JSValue formatter, TemporalCalendarId calendar) {
        JSValue options = JSIntlObject.dateTimeFormatResolvedOptions(context, formatter, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return false;
        }
        if (options instanceof JSObject object) {
            JSValue value = object.get(PropertyKey.fromString("calendar"));
            if (context.hasPendingException()) {
                return false;
            }
            TemporalCalendarId formatterCalendar = TemporalCalendarId.createFromCalendarString(context, value);
            if (context.hasPendingException()) {
                return false;
            }
            if (!calendar.equals(formatterCalendar)) {
                context.throwRangeError("Invalid date/time value");
                return false;
            }
        }
        return true;
    }

    private static boolean shouldApplyZonedDateTimeDefaultComponents(JSContext context, JSObject options) {
        for (String component : DATE_TIME_COMPONENTS) {
            JSValue value = options.get(PropertyKey.fromString(component));
            if (context.hasPendingException() || (value != null && !(value instanceof JSUndefined))) {
                return false;
            }
        }
        return true;
    }
}
