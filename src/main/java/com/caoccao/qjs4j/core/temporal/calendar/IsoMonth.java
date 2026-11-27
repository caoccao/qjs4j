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

package com.caoccao.qjs4j.core.temporal.calendar;

import com.caoccao.qjs4j.core.*;

import java.util.Locale;

public record IsoMonth(int month, boolean leapMonth) {
    public static IsoMonth parseByMonthCode(JSContext context, String monthCode, String rangeErrorMessage) {
        IsoMonth isoMonth = parseByMonthCode(monthCode);
        if (isoMonth == null) {
            context.throwRangeError(rangeErrorMessage);
            return null;
        }
        return isoMonth;
    }

    public static IsoMonth parseByMonthCode(String monthCode) {
        if (monthCode == null || monthCode.length() < 3 || monthCode.length() > 4) {
            return null;
        }
        if (monthCode.charAt(0) != 'M') {
            return null;
        }
        if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
            return null;
        }
        int monthNumber = Integer.parseInt(monthCode.substring(1, 3));
        boolean isLeapMonth = false;
        if (monthCode.length() == 4) {
            if (monthCode.charAt(3) != 'L') {
                return null;
            }
            isLeapMonth = true;
        }
        return new IsoMonth(monthNumber, isLeapMonth);
    }

    public static String toMonthCode(int month) {
        if (month >= 1 && month <= MONTH_CODES.length) {
            return MONTH_CODES[month - 1];
        }
        return String.format(Locale.ROOT, "M%02d", month);
    }

    /** Coerce a Temporal monthCode field, requiring its primitive value to be a string. */
    public static IsoMonth fromField(JSContext context, JSValue value) {
        if (value == null || value instanceof JSUndefined) {
            return null;
        }
        if (value instanceof JSObject) {
            value = JSTypeConversions.toPrimitive(context, value, JSTypeConversions.PreferredType.STRING);
            if (context.hasPendingException()) {
                return null;
            }
        }
        if (value instanceof JSString text) {
            return parseByMonthCode(context, text.value(), "Temporal error: Month code out of range.");
        }
        context.throwTypeError("Temporal error: Month code must be string.");
        return null;
    }

    public String monthCode() {
        String code = toMonthCode(month);
        return leapMonth ? code + "L" : code;
    }

    private static final String[] MONTH_CODES = {"M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10",
            "M11", "M12", "M13"};
}
