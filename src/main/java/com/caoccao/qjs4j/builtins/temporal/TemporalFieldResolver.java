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
import com.caoccao.qjs4j.core.temporal.TemporalParsedMonthCode;

import java.util.Locale;

/**
 * Shared field parsing and era resolution for Temporal constructors.
 */
final class TemporalFieldResolver {
    private TemporalFieldResolver() {
    }

    static boolean calendarUsesEras(String calendarId) {
        return "buddhist".equals(calendarId)
                || "coptic".equals(calendarId)
                || "ethioaa".equals(calendarId)
                || "ethiopic".equals(calendarId)
                || "gregory".equals(calendarId)
                || "hebrew".equals(calendarId)
                || "indian".equals(calendarId)
                || "islamic-civil".equals(calendarId)
                || "islamic-tbla".equals(calendarId)
                || "islamic-umalqura".equals(calendarId)
                || "japanese".equals(calendarId)
                || "persian".equals(calendarId)
                || "roc".equals(calendarId);
    }

    static String canonicalizeEraForCalendar(JSContext context, String calendarId, String era) {
        if (era == null) {
            return invalidEra(context);
        }

        String normalizedEra = era.toLowerCase(Locale.ROOT);
        if ("gregory".equals(calendarId)) {
            if ("ce".equals(normalizedEra) || "ad".equals(normalizedEra)) {
                return "ce";
            } else if ("bce".equals(normalizedEra) || "bc".equals(normalizedEra)) {
                return "bce";
            } else {
                return invalidEra(context);
            }
        } else if ("japanese".equals(calendarId)) {
            if ("ce".equals(normalizedEra) || "ad".equals(normalizedEra)) {
                return "ce";
            } else if ("bce".equals(normalizedEra) || "bc".equals(normalizedEra)) {
                return "bce";
            } else if ("meiji".equals(normalizedEra)
                    || "taisho".equals(normalizedEra)
                    || "showa".equals(normalizedEra)
                    || "heisei".equals(normalizedEra)
                    || "reiwa".equals(normalizedEra)) {
                return normalizedEra;
            } else {
                return invalidEra(context);
            }
        } else if ("roc".equals(calendarId)) {
            if ("roc".equals(normalizedEra) || "minguo".equals(normalizedEra)) {
                return "roc";
            } else if ("broc".equals(normalizedEra) || "before-roc".equals(normalizedEra)) {
                return "broc";
            } else {
                return invalidEra(context);
            }
        } else if ("buddhist".equals(calendarId)) {
            if ("be".equals(normalizedEra)) {
                return "be";
            } else {
                return invalidEra(context);
            }
        } else if ("coptic".equals(calendarId)) {
            if ("am".equals(normalizedEra)) {
                return "am";
            } else {
                return invalidEra(context);
            }
        } else if ("ethioaa".equals(calendarId)) {
            if ("aa".equals(normalizedEra)) {
                return "aa";
            } else {
                return invalidEra(context);
            }
        } else if ("ethiopic".equals(calendarId)) {
            if ("aa".equals(normalizedEra) || "am".equals(normalizedEra)) {
                return normalizedEra;
            } else {
                return invalidEra(context);
            }
        } else if ("hebrew".equals(calendarId)) {
            if ("am".equals(normalizedEra)) {
                return "am";
            } else {
                return invalidEra(context);
            }
        } else if ("indian".equals(calendarId)) {
            if ("shaka".equals(normalizedEra) || "saka".equals(normalizedEra)) {
                return "shaka";
            } else {
                return invalidEra(context);
            }
        } else if ("islamic-civil".equals(calendarId)
                || "islamic-tbla".equals(calendarId)
                || "islamic-umalqura".equals(calendarId)) {
            if ("ah".equals(normalizedEra) || "bh".equals(normalizedEra)) {
                return normalizedEra;
            } else {
                return invalidEra(context);
            }
        } else if ("persian".equals(calendarId)) {
            if ("ap".equals(normalizedEra)) {
                return "ap";
            } else {
                return invalidEra(context);
            }
        } else {
            return invalidEra(context);
        }
    }

    private static String invalidEra(JSContext context) {
        context.throwRangeError("Temporal error: Invalid era.");
        return null;
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

    static TemporalParsedMonthCode parseMonthCodeSyntax(JSContext context, String monthCodeText, String rangeErrorMessage) {
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
        return new TemporalParsedMonthCode(monthNumber, leapMonth);
    }

    static TemporalParsedMonthCode parseMonthCodeValue(
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

    private static int resolveJapaneseYearFromEra(String era, int eraYear) {
        if ("ce".equals(era)) {
            return eraYear;
        } else if ("bce".equals(era)) {
            return 1 - eraYear;
        } else if ("meiji".equals(era)) {
            return 1867 + eraYear;
        } else if ("taisho".equals(era)) {
            return 1911 + eraYear;
        } else if ("showa".equals(era)) {
            return 1925 + eraYear;
        } else if ("heisei".equals(era)) {
            return 1988 + eraYear;
        } else if ("reiwa".equals(era)) {
            return 2018 + eraYear;
        } else {
            return eraYear;
        }
    }

    static int yearFromEraAndEraYear(String calendarId, String era, int eraYear) {
        if ("gregory".equals(calendarId)) {
            if ("bce".equals(era)) {
                return 1 - eraYear;
            } else {
                return eraYear;
            }
        } else if ("japanese".equals(calendarId)) {
            return resolveJapaneseYearFromEra(era, eraYear);
        } else if ("roc".equals(calendarId)) {
            if ("broc".equals(era)) {
                return 1 - eraYear;
            } else {
                return eraYear;
            }
        } else if ("buddhist".equals(calendarId)) {
            return eraYear;
        } else if ("ethiopic".equals(calendarId)) {
            if ("aa".equals(era)) {
                return eraYear - 5500;
            } else {
                return eraYear;
            }
        } else if ("islamic-civil".equals(calendarId)
                || "islamic-tbla".equals(calendarId)
                || "islamic-umalqura".equals(calendarId)) {
            if ("bh".equals(era)) {
                return 1 - eraYear;
            } else {
                return eraYear;
            }
        } else {
            return eraYear;
        }
    }

}
