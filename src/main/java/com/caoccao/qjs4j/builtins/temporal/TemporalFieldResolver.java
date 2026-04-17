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
import com.caoccao.qjs4j.core.temporal.IsoMonth;
import com.caoccao.qjs4j.core.temporal.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.TemporalEra;

import java.util.Optional;

/**
 * Shared field parsing and era resolution for Temporal constructors.
 */
final class TemporalFieldResolver {
    private TemporalFieldResolver() {
    }

    static TemporalEra getEraByCalendarId(JSContext context, TemporalCalendarId calendarId, String era) {
        return Optional.ofNullable(era)
                .map(calendarId::toTemporalEra)
                .orElseGet(() -> {
                    context.throwRangeError("Temporal error: Invalid era.");
                    return null;
                });
    }

    static int parseMonthCode(JSContext context, String monthCodeText, String rangeErrorMessage) {
        if (monthCodeText == null || monthCodeText.length() != 3 || monthCodeText.charAt(0) != 'M') {
            context.throwRangeError(rangeErrorMessage);
            return 0;
        }
        if (!Character.isDigit(monthCodeText.charAt(1)) || !Character.isDigit(monthCodeText.charAt(2))) {
            context.throwRangeError(rangeErrorMessage);
            return 0;
        }
        int monthNumber = Integer.parseInt(monthCodeText.substring(1));
        if (monthNumber < 1 || monthNumber > 12) {
            context.throwRangeError(rangeErrorMessage);
            return 0;
        }
        return monthNumber;
    }

    static IsoMonth parseMonthCodeSyntax(JSContext context, String monthCodeText, String rangeErrorMessage) {
        if (monthCodeText == null || monthCodeText.length() < 3 || monthCodeText.length() > 4) {
            context.throwRangeError(rangeErrorMessage);
            return null;
        }
        if (monthCodeText.charAt(0) != 'M') {
            context.throwRangeError(rangeErrorMessage);
            return null;
        }
        if (!Character.isDigit(monthCodeText.charAt(1)) || !Character.isDigit(monthCodeText.charAt(2))) {
            context.throwRangeError(rangeErrorMessage);
            return null;
        }

        boolean leapMonth = false;
        if (monthCodeText.length() == 4) {
            if (monthCodeText.charAt(3) != 'L') {
                context.throwRangeError(rangeErrorMessage);
                return null;
            }
            leapMonth = true;
        }

        int monthNumber = Integer.parseInt(monthCodeText.substring(1, 3));
        return new IsoMonth(monthNumber, leapMonth);
    }

    static IsoMonth parseMonthCodeValue(
            JSContext context,
            JSValue monthCodeValue,
            String typeErrorMessage,
            String rangeErrorMessage) {
        String monthCodeText;
        if (monthCodeValue instanceof JSString monthCodeString) {
            monthCodeText = monthCodeString.value();
        } else if (monthCodeValue instanceof JSObject) {
            JSValue primitiveMonthCode =
                    JSTypeConversions.toPrimitive(context, monthCodeValue, JSTypeConversions.PreferredType.STRING);
            if (context.hasPendingException()) {
                return null;
            }
            if (!(primitiveMonthCode instanceof JSString primitiveMonthCodeString)) {
                context.throwTypeError(typeErrorMessage);
                return null;
            }
            monthCodeText = primitiveMonthCodeString.value();
        } else {
            context.throwTypeError(typeErrorMessage);
            return null;
        }
        return parseMonthCodeSyntax(context, monthCodeText, rangeErrorMessage);
    }

}
