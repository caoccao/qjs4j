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

import com.caoccao.qjs4j.core.JSContext;

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
        return String.format(Locale.ROOT, "M%02d", month);
    }
}
