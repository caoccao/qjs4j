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
import com.caoccao.qjs4j.core.temporal.TemporalCalendarId;

/** Intl.DateTimeFormat setup shared by the Temporal toLocaleString methods. */
final class TemporalLocaleFormat {
    private TemporalLocaleFormat() {
    }

    static JSValue create(JSContext context, JSValue[] args, String excludedStyle) {
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
}
