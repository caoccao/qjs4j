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

/**
 * Implementation of Temporal.PlainDate constructor and static methods.
 */
public final class TemporalPlainDateConstructor {

    private TemporalPlainDateConstructor() {
    }

    /**
     * Temporal.PlainDate.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalPlainDate firstDate = toTemporalDateObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate secondDate = toTemporalDateObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(firstDate.getIsoDate().compareTo(secondDate.getIsoDate()));
    }

    /**
     * Temporal.PlainDate(isoYear, isoMonth, isoDay, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainDate.");
            return JSUndefined.INSTANCE;
        }
        int isoYear = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 2 ? args[2] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            calendarId = TemporalCalendarId.createFromCalendarString(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        IsoDate isoDate = new IsoDate(isoYear, isoMonth, isoDay);
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = resolveTemporalPrototype(context, "PlainDate");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainDate(context, isoDate, calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainDate createPlainDate(JSContext context, IsoDate isoDate, TemporalCalendarId calendarId) {
        JSObject prototype = getTemporalPrototype(context, "PlainDate");
        return createPlainDate(context, isoDate, calendarId, prototype);
    }

    static JSTemporalPlainDate createPlainDate(JSContext context, IsoDate isoDate, TemporalCalendarId calendarId, JSObject prototype) {
        JSTemporalPlainDate plainDate = new JSTemporalPlainDate(context, isoDate, calendarId);
        if (prototype != null) {
            plainDate.setPrototype(prototype);
        }
        return plainDate;
    }

    static JSValue dateFromFields(JSContext context, JSObject fields, JSValue options) {
        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalCalendarId.createFromCalendarValue(context, calendarValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue dayValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDay = !(dayValue instanceof JSUndefined) && dayValue != null;
        int dayOfMonth = Integer.MIN_VALUE;
        if (hasDay) {
            dayOfMonth = TemporalUtils.toIntegerThrowOnInfinity(context, dayValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonth = !(monthValue instanceof JSUndefined) && monthValue != null;
        int month = Integer.MIN_VALUE;
        if (hasMonth) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCode = !(monthCodeValue instanceof JSUndefined) && monthCodeValue != null;
        IsoMonth parsedMonthCode = null;
        if (hasMonthCode) {
            String monthCodeText;
            if (monthCodeValue instanceof JSString monthCodeString) {
                monthCodeText = monthCodeString.value();
            } else if (monthCodeValue instanceof JSObject) {
                JSValue primitiveMonthCode =
                        JSTypeConversions.toPrimitive(context, monthCodeValue, JSTypeConversions.PreferredType.STRING);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (!(primitiveMonthCode instanceof JSString primitiveMonthCodeString)) {
                    context.throwTypeError("Temporal error: Month code must be string.");
                    return JSUndefined.INSTANCE;
                }
                monthCodeText = primitiveMonthCodeString.value();
            } else {
                context.throwTypeError("Temporal error: Month code must be string.");
                return JSUndefined.INSTANCE;
            }
            parsedMonthCode = IsoMonth.parseByMonthCode(
                    context,
                    monthCodeText,
                    "Temporal error: Month code out of range.");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        int year = Integer.MIN_VALUE;
        if (hasYear) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        boolean yearDerivedFromEra = false;

        boolean calendarSupportsEras = calendarId.hasEra();
        if (!calendarSupportsEras) {
            if (!hasYear) {
                context.throwTypeError("Temporal error: Date argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
        } else {
            JSValue eraValue = fields.get(PropertyKey.fromString("era"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            boolean hasEra = !(eraValue instanceof JSUndefined) && eraValue != null;
            String era = null;
            if (hasEra) {
                era = JSTypeConversions.toString(context, eraValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            JSValue eraYearValue = fields.get(PropertyKey.fromString("eraYear"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            boolean hasEraYear = !(eraYearValue instanceof JSUndefined) && eraYearValue != null;
            Integer eraYear = null;
            if (hasEraYear) {
                eraYear = TemporalUtils.toIntegerThrowOnInfinity(context, eraYearValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            if (hasEra != hasEraYear) {
                context.throwTypeError("Temporal error: Date argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            if (!hasYear && hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalEra.createByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                year = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                hasYear = true;
                yearDerivedFromEra = true;
            } else if (hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalEra.createByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int expectedYear = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (year != expectedYear) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
            }
        }

        if (!hasYear) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        if (!hasDay) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        if (!hasMonth && !hasMonthCode) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        int resolvedMonth;
        if (hasMonth) {
            resolvedMonth = month;
        } else {
            resolvedMonth = parsedMonthCode.month();
        }

        String monthCodeText = null;
        if (parsedMonthCode != null) {
            monthCodeText = IsoMonth.toMonthCode(parsedMonthCode.month());
            if (parsedMonthCode.leapMonth()) {
                monthCodeText += "L";
            }
        }
        Integer monthFromProperty = hasMonth ? resolvedMonth : null;
        IsoDate convertedIsoDate = TemporalCalendarMath.calendarDateToIsoDate(
                context,
                calendarId,
                year,
                monthFromProperty,
                monthCodeText,
                dayOfMonth,
                overflow);
        if (context.hasPendingException() || convertedIsoDate == null) {
            return JSUndefined.INSTANCE;
        }
        return createPlainDate(context, convertedIsoDate, calendarId);
    }

    static JSValue dateFromString(JSContext context, String input) {
        IsoDate date = IsoDate.parseDateString(context, input);
        if (date == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        int annotationStart = input.indexOf('[');
        while (annotationStart >= 0) {
            int annotationEnd = input.indexOf(']', annotationStart);
            if (annotationEnd <= annotationStart) {
                break;
            }
            String annotationContent = input.substring(annotationStart + 1, annotationEnd);
            if (!annotationContent.isEmpty() && annotationContent.charAt(0) == '!') {
                annotationContent = annotationContent.substring(1);
            }
            int equalSignIndex = annotationContent.indexOf('=');
            if (equalSignIndex > 0) {
                String annotationKey = annotationContent.substring(0, equalSignIndex);
                if ("u-ca".equals(annotationKey)) {
                    String annotationValue = annotationContent.substring(equalSignIndex + 1);
                    calendarId = TemporalCalendarId.createFromCalendarString(context, new JSString(annotationValue));
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    break;
                }
            }
            annotationStart = input.indexOf('[', annotationEnd + 1);
        }
        return createPlainDate(context, date, calendarId);
    }

    /**
     * Temporal.PlainDate.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalDate(context, item, options);
    }

    static JSObject getTemporalPrototype(JSContext context, String typeName) {
        JSValue temporal = context.getGlobalObject().get(PropertyKey.fromString("Temporal"));
        if (temporal instanceof JSObject temporalObj) {
            JSValue constructor = temporalObj.get(PropertyKey.fromString(typeName));
            if (constructor instanceof JSObject constructorObj) {
                JSValue prototype = constructorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject prototypeObj) {
                    return prototypeObj;
                }
            }
        }
        return null;
    }

    static JSObject resolveTemporalPrototype(JSContext context, String typeName) {
        JSValue constructorNewTarget = context.getConstructorNewTarget();
        if (constructorNewTarget instanceof JSObject constructorObject) {
            JSValue constructorPrototype = constructorObject.get(PropertyKey.PROTOTYPE);
            if (context.hasPendingException()) {
                return null;
            }
            if (constructorPrototype instanceof JSObject) {
                JSObject resolvedPrototype = context.getPrototypeFromConstructor(constructorObject, JSObject.NAME);
                if (context.hasPendingException()) {
                    return null;
                }
                return resolvedPrototype;
            }
        }
        return getTemporalPrototype(context, typeName);
    }

    /**
     * ToTemporalDate abstract operation — converts item to JSTemporalPlainDate.
     */
    public static JSValue toTemporalDate(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainDate plainDate) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDate(context, plainDate.getIsoDate(), plainDate.getCalendarId());
        }
        if (item instanceof JSTemporalPlainDateTime plainDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            return createPlainDate(context, localDateTime.date(), zonedDateTime.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            return dateFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            JSValue parsedDate = dateFromString(context, itemStr.value());
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return parsedDate;
        }
        context.throwTypeError("Temporal error: Date argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainDate toTemporalDateObject(JSContext context, JSValue item) {
        JSValue result = toTemporalDate(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalPlainDate) result;
    }

}
