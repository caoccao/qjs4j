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

/** Calendar-aware ISO text for plain dates, year-months and month-days. */
final class TemporalDateFormat {
    private TemporalDateFormat() {
    }

    static JSValue format(JSContext context, JSTemporalCalendarDate date, JSValue options) {
        String calendarName = TemporalUtils.getCalendarNameOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendar = date.getCalendarId();
        TemporalDisplayCalendar display = TemporalDisplayCalendar.fromString(calendarName);
        boolean includeReferenceDate = calendar != TemporalCalendarId.ISO8601 || display.requiresAnnotation();
        IsoDate isoDate = date.getIsoDate();
        String text;
        if (!includeReferenceDate && date instanceof JSTemporalPlainMonthDay) {
            text = isoDate.toMonthDayString();
        } else if (!includeReferenceDate && date instanceof JSTemporalPlainYearMonth) {
            text = isoDate.toYearMonthString();
        } else {
            text = isoDate.toString();
        }
        return new JSString(TemporalUtils.maybeAppendCalendar(text, calendar, calendarName));
    }

    static <T extends JSTemporalCalendarDate> JSValue formatDate(JSContext context, JSValue receiver, JSValue[] args,
            TemporalReceiver<T> type) {
        T date = type.require(context, receiver, "toString");
        return date == null ? JSUndefined.INSTANCE : format(context, date, JSValue.argumentOrUndefined(args, 0));
    }
}
