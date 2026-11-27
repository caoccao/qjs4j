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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.core.BuiltinFunctionList.Method;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalDurationConstructor;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalDurationPrototype;
import com.caoccao.qjs4j.builtins.temporal.instant.TemporalInstantConstructor;
import com.caoccao.qjs4j.builtins.temporal.instant.TemporalInstantPrototype;
import com.caoccao.qjs4j.builtins.temporal.plain.*;
import com.caoccao.qjs4j.builtins.temporal.zoned.TemporalZonedDateTimeConstructor;
import com.caoccao.qjs4j.builtins.temporal.zoned.TemporalZonedDateTimePrototype;

import java.util.function.Consumer;

/** The constructor and prototype properties installed for each Temporal type, in observable property order. */
enum TemporalIntrinsic {
    DURATION("Duration", 0, TemporalDurationConstructor::construct, constructor -> {
        constructor.method(Method.FROM, TemporalDurationConstructor::from);
        constructor.method(Method.COMPARE, TemporalDurationConstructor::compare);
    }, prototype -> {
        prototype.getter("years", TemporalDurationPrototype::years);
        prototype.getter("months", TemporalDurationPrototype::months);
        prototype.getter("weeks", TemporalDurationPrototype::weeks);
        prototype.getter("days", TemporalDurationPrototype::days);
        prototype.getter("hours", TemporalDurationPrototype::hours);
        prototype.getter("minutes", TemporalDurationPrototype::minutes);
        prototype.getter("seconds", TemporalDurationPrototype::seconds);
        prototype.getter("milliseconds", TemporalDurationPrototype::milliseconds);
        prototype.getter("microseconds", TemporalDurationPrototype::microseconds);
        prototype.getter("nanoseconds", TemporalDurationPrototype::nanoseconds);
        prototype.getter("sign", TemporalDurationPrototype::sign);
        prototype.getter("blank", TemporalDurationPrototype::blank);
        prototype.method(Method.WITH, TemporalDurationPrototype::with);
        prototype.method("negated", 0, TemporalDurationPrototype::negated);
        prototype.method("abs", 0, TemporalDurationPrototype::abs);
        prototype.method(Method.ADD, TemporalDurationPrototype::add);
        prototype.method(Method.SUBTRACT, TemporalDurationPrototype::subtract);
        prototype.method(Method.ROUND, TemporalDurationPrototype::round);
        prototype.method("total", 1, TemporalDurationPrototype::total);
        prototype.method("toString", 0, TemporalDurationPrototype::toStringMethod);
        prototype.method("toJSON", 0, TemporalDurationPrototype::toJSON);
        prototype.method(Method.TO_LOCALE_STRING, TemporalDurationPrototype::toLocaleString);
        prototype.method(Method.VALUE_OF, TemporalDurationPrototype::valueOf);
    }),
    INSTANT("Instant", 1, TemporalInstantConstructor::construct, constructor -> {
        constructor.method(Method.FROM, TemporalInstantConstructor::from);
        constructor.method("fromEpochMilliseconds", 1, TemporalInstantConstructor::fromEpochMilliseconds);
        constructor.method("fromEpochNanoseconds", 1, TemporalInstantConstructor::fromEpochNanoseconds);
        constructor.method(Method.COMPARE, TemporalInstantConstructor::compare);
    }, prototype -> {
        prototype.getter("epochMilliseconds", TemporalInstantPrototype::epochMilliseconds);
        prototype.getter("epochNanoseconds", TemporalInstantPrototype::epochNanoseconds);
        prototype.method(Method.ADD, TemporalInstantPrototype::add);
        prototype.method(Method.SUBTRACT, TemporalInstantPrototype::subtract);
        prototype.method(Method.UNTIL, TemporalInstantPrototype::until);
        prototype.method(Method.SINCE, TemporalInstantPrototype::since);
        prototype.method(Method.ROUND, TemporalInstantPrototype::round);
        prototype.method(Method.EQUALS, TemporalInstantPrototype::equals);
        prototype.method("toString", 0, TemporalInstantPrototype::toStringMethod);
        prototype.method("toJSON", 0, TemporalInstantPrototype::toJSON);
        prototype.method(Method.TO_LOCALE_STRING, TemporalInstantPrototype::toLocaleString);
        prototype.method(Method.VALUE_OF, TemporalInstantPrototype::valueOf);
        prototype.method("toZonedDateTimeISO", 1, TemporalInstantPrototype::toZonedDateTimeISO);
    }),
    PLAIN_DATE("PlainDate", 3, TemporalPlainDateConstructor::construct, constructor -> {
        constructor.method(Method.COMPARE, TemporalPlainDateConstructor::compare);
        constructor.method(Method.FROM, TemporalPlainDateConstructor::from);
    }, prototype -> {
        prototype.getter("calendarId", TemporalPlainDatePrototype::calendarId);
        prototype.getter("day", TemporalPlainDatePrototype::day);
        prototype.getter("dayOfWeek", TemporalPlainDatePrototype::dayOfWeek);
        prototype.getter("dayOfYear", TemporalPlainDatePrototype::dayOfYear);
        prototype.getter("daysInMonth", TemporalPlainDatePrototype::daysInMonth);
        prototype.getter("daysInWeek", TemporalPlainDatePrototype::daysInWeek);
        prototype.getter("daysInYear", TemporalPlainDatePrototype::daysInYear);
        prototype.getter("era", TemporalPlainDatePrototype::era);
        prototype.getter("eraYear", TemporalPlainDatePrototype::eraYear);
        prototype.getter("inLeapYear", TemporalPlainDatePrototype::inLeapYear);
        prototype.getter("month", TemporalPlainDatePrototype::month);
        prototype.getter("monthCode", TemporalPlainDatePrototype::monthCode);
        prototype.getter("monthsInYear", TemporalPlainDatePrototype::monthsInYear);
        prototype.getter("weekOfYear", TemporalPlainDatePrototype::weekOfYear);
        prototype.getter("year", TemporalPlainDatePrototype::year);
        prototype.getter("yearOfWeek", TemporalPlainDatePrototype::yearOfWeek);
        prototype.method(Method.ADD, TemporalPlainDatePrototype::add);
        prototype.method(Method.EQUALS, TemporalPlainDatePrototype::equals);
        prototype.method(Method.SINCE, TemporalPlainDatePrototype::since);
        prototype.method(Method.SUBTRACT, TemporalPlainDatePrototype::subtract);
        prototype.method("toJSON", 0, TemporalPlainDatePrototype::toJSON);
        prototype.method(Method.TO_LOCALE_STRING, TemporalPlainDatePrototype::toLocaleString);
        prototype.method(Method.TO_PLAIN_DATE_TIME, TemporalPlainDatePrototype::toPlainDateTime);
        prototype.method("toPlainMonthDay", 0, TemporalPlainDatePrototype::toPlainMonthDay);
        prototype.method("toPlainYearMonth", 0, TemporalPlainDatePrototype::toPlainYearMonth);
        prototype.method("toString", 0, TemporalPlainDatePrototype::toStringMethod);
        prototype.method(Method.TO_ZONED_DATE_TIME, TemporalPlainDatePrototype::toZonedDateTime);
        prototype.method(Method.UNTIL, TemporalPlainDatePrototype::until);
        prototype.method(Method.VALUE_OF, TemporalPlainDatePrototype::valueOf);
        prototype.method(Method.WITH, TemporalPlainDatePrototype::with);
        prototype.method(Method.WITH_CALENDAR, TemporalPlainDatePrototype::withCalendar);
    }),
    PLAIN_DATE_TIME("PlainDateTime", 3, TemporalPlainDateTimeConstructor::construct, constructor -> {
        constructor.method(Method.FROM, TemporalPlainDateTimeConstructor::from);
        constructor.method(Method.COMPARE, TemporalPlainDateTimeConstructor::compare);
    }, prototype -> {
        prototype.getter("calendarId", TemporalPlainDateTimePrototype::calendarId);
        prototype.getter("day", TemporalPlainDateTimePrototype::day);
        prototype.getter("dayOfWeek", TemporalPlainDateTimePrototype::dayOfWeek);
        prototype.getter("dayOfYear", TemporalPlainDateTimePrototype::dayOfYear);
        prototype.getter("daysInMonth", TemporalPlainDateTimePrototype::daysInMonth);
        prototype.getter("daysInWeek", TemporalPlainDateTimePrototype::daysInWeek);
        prototype.getter("daysInYear", TemporalPlainDateTimePrototype::daysInYear);
        prototype.getter("era", TemporalPlainDateTimePrototype::era);
        prototype.getter("eraYear", TemporalPlainDateTimePrototype::eraYear);
        prototype.getter("hour", TemporalPlainDateTimePrototype::hour);
        prototype.getter("inLeapYear", TemporalPlainDateTimePrototype::inLeapYear);
        prototype.getter("microsecond", TemporalPlainDateTimePrototype::microsecond);
        prototype.getter("millisecond", TemporalPlainDateTimePrototype::millisecond);
        prototype.getter("minute", TemporalPlainDateTimePrototype::minute);
        prototype.getter("month", TemporalPlainDateTimePrototype::month);
        prototype.getter("monthCode", TemporalPlainDateTimePrototype::monthCode);
        prototype.getter("monthsInYear", TemporalPlainDateTimePrototype::monthsInYear);
        prototype.getter("nanosecond", TemporalPlainDateTimePrototype::nanosecond);
        prototype.getter("second", TemporalPlainDateTimePrototype::second);
        prototype.getter("weekOfYear", TemporalPlainDateTimePrototype::weekOfYear);
        prototype.getter("year", TemporalPlainDateTimePrototype::year);
        prototype.getter("yearOfWeek", TemporalPlainDateTimePrototype::yearOfWeek);
        prototype.method(Method.ADD, TemporalPlainDateTimePrototype::add);
        prototype.method(Method.EQUALS, TemporalPlainDateTimePrototype::equals);
        prototype.method(Method.ROUND, TemporalPlainDateTimePrototype::round);
        prototype.method(Method.SINCE, TemporalPlainDateTimePrototype::since);
        prototype.method(Method.SUBTRACT, TemporalPlainDateTimePrototype::subtract);
        prototype.method("toJSON", 0, TemporalPlainDateTimePrototype::toJSON);
        prototype.method(Method.TO_LOCALE_STRING, TemporalPlainDateTimePrototype::toLocaleString);
        prototype.method("toPlainDate", 0, TemporalPlainDateTimePrototype::toPlainDate);
        prototype.method(Method.TO_PLAIN_TIME, TemporalPlainDateTimePrototype::toPlainTime);
        prototype.method("toString", 0, TemporalPlainDateTimePrototype::toStringMethod);
        prototype.method(Method.TO_ZONED_DATE_TIME, TemporalPlainDateTimePrototype::toZonedDateTime);
        prototype.method(Method.UNTIL, TemporalPlainDateTimePrototype::until);
        prototype.method(Method.VALUE_OF, TemporalPlainDateTimePrototype::valueOf);
        prototype.method(Method.WITH, TemporalPlainDateTimePrototype::with);
        prototype.method(Method.WITH_CALENDAR, TemporalPlainDateTimePrototype::withCalendar);
        prototype.method(Method.WITH_PLAIN_TIME, TemporalPlainDateTimePrototype::withPlainTime);
    }),
    PLAIN_MONTH_DAY("PlainMonthDay", 2, TemporalPlainMonthDayConstructor::construct, constructor -> {
        constructor.method(Method.FROM, TemporalPlainMonthDayConstructor::from);
    }, prototype -> {
        prototype.getter("calendarId", TemporalPlainMonthDayPrototype::calendarId);
        prototype.getter("day", TemporalPlainMonthDayPrototype::day);
        prototype.getter("monthCode", TemporalPlainMonthDayPrototype::monthCode);
        prototype.getter("referenceISOYear", TemporalPlainMonthDayPrototype::referenceISOYear);
        prototype.method(Method.EQUALS, TemporalPlainMonthDayPrototype::equals);
        prototype.method("toJSON", 0, TemporalPlainMonthDayPrototype::toJSON);
        prototype.method(Method.TO_LOCALE_STRING, TemporalPlainMonthDayPrototype::toLocaleString);
        prototype.method("toPlainDate", 1, TemporalPlainMonthDayPrototype::toPlainDate);
        prototype.method("toString", 0, TemporalPlainMonthDayPrototype::toStringMethod);
        prototype.method(Method.VALUE_OF, TemporalPlainMonthDayPrototype::valueOf);
        prototype.method(Method.WITH, TemporalPlainMonthDayPrototype::with);
    }),
    PLAIN_TIME("PlainTime", 0, TemporalPlainTimeConstructor::construct, constructor -> {
        constructor.method(Method.COMPARE, TemporalPlainTimeConstructor::compare);
        constructor.method(Method.FROM, TemporalPlainTimeConstructor::from);
    }, prototype -> {
        prototype.getter("hour", TemporalPlainTimePrototype::hour);
        prototype.getter("microsecond", TemporalPlainTimePrototype::microsecond);
        prototype.getter("millisecond", TemporalPlainTimePrototype::millisecond);
        prototype.getter("minute", TemporalPlainTimePrototype::minute);
        prototype.getter("nanosecond", TemporalPlainTimePrototype::nanosecond);
        prototype.getter("second", TemporalPlainTimePrototype::second);
        prototype.method(Method.ADD, TemporalPlainTimePrototype::add);
        prototype.method(Method.EQUALS, TemporalPlainTimePrototype::equals);
        prototype.method(Method.ROUND, TemporalPlainTimePrototype::round);
        prototype.method(Method.SINCE, TemporalPlainTimePrototype::since);
        prototype.method(Method.SUBTRACT, TemporalPlainTimePrototype::subtract);
        prototype.method("toJSON", 0, TemporalPlainTimePrototype::toJSON);
        prototype.method(Method.TO_LOCALE_STRING, TemporalPlainTimePrototype::toLocaleString);
        prototype.method("toString", 0, TemporalPlainTimePrototype::toStringMethod);
        prototype.method(Method.UNTIL, TemporalPlainTimePrototype::until);
        prototype.method(Method.VALUE_OF, TemporalPlainTimePrototype::valueOf);
        prototype.method(Method.WITH, TemporalPlainTimePrototype::with);
    }),
    PLAIN_YEAR_MONTH("PlainYearMonth", 2, TemporalPlainYearMonthConstructor::construct, constructor -> {
        constructor.method(Method.COMPARE, TemporalPlainYearMonthConstructor::compare);
        constructor.method(Method.FROM, TemporalPlainYearMonthConstructor::from);
    }, prototype -> {
        prototype.getter("calendarId", TemporalPlainYearMonthPrototype::calendarId);
        prototype.getter("daysInMonth", TemporalPlainYearMonthPrototype::daysInMonth);
        prototype.getter("daysInYear", TemporalPlainYearMonthPrototype::daysInYear);
        prototype.getter("era", TemporalPlainYearMonthPrototype::era);
        prototype.getter("eraYear", TemporalPlainYearMonthPrototype::eraYear);
        prototype.getter("inLeapYear", TemporalPlainYearMonthPrototype::inLeapYear);
        prototype.getter("month", TemporalPlainYearMonthPrototype::month);
        prototype.getter("monthCode", TemporalPlainYearMonthPrototype::monthCode);
        prototype.getter("monthsInYear", TemporalPlainYearMonthPrototype::monthsInYear);
        prototype.getter("referenceISODay", TemporalPlainYearMonthPrototype::referenceISODay);
        prototype.getter("year", TemporalPlainYearMonthPrototype::year);
        prototype.method(Method.ADD, TemporalPlainYearMonthPrototype::add);
        prototype.method(Method.EQUALS, TemporalPlainYearMonthPrototype::equals);
        prototype.method(Method.SINCE, TemporalPlainYearMonthPrototype::since);
        prototype.method(Method.SUBTRACT, TemporalPlainYearMonthPrototype::subtract);
        prototype.method("toJSON", 0, TemporalPlainYearMonthPrototype::toJSON);
        prototype.method(Method.TO_LOCALE_STRING, TemporalPlainYearMonthPrototype::toLocaleString);
        prototype.method("toPlainDate", 1, TemporalPlainYearMonthPrototype::toPlainDate);
        prototype.method("toString", 0, TemporalPlainYearMonthPrototype::toStringMethod);
        prototype.method(Method.UNTIL, TemporalPlainYearMonthPrototype::until);
        prototype.method(Method.VALUE_OF, TemporalPlainYearMonthPrototype::valueOf);
        prototype.method(Method.WITH, TemporalPlainYearMonthPrototype::with);
    }),
    ZONED_DATE_TIME("ZonedDateTime", 2, TemporalZonedDateTimeConstructor::construct, constructor -> {
        constructor.method(Method.COMPARE, TemporalZonedDateTimeConstructor::compare);
        constructor.method(Method.FROM, TemporalZonedDateTimeConstructor::from);
    }, prototype -> {
        prototype.getter("calendar", TemporalZonedDateTimePrototype::calendar);
        prototype.getter("calendarId", TemporalZonedDateTimePrototype::calendarId);
        prototype.getter("day", TemporalZonedDateTimePrototype::day);
        prototype.getter("dayOfWeek", TemporalZonedDateTimePrototype::dayOfWeek);
        prototype.getter("dayOfYear", TemporalZonedDateTimePrototype::dayOfYear);
        prototype.getter("daysInMonth", TemporalZonedDateTimePrototype::daysInMonth);
        prototype.getter("daysInWeek", TemporalZonedDateTimePrototype::daysInWeek);
        prototype.getter("daysInYear", TemporalZonedDateTimePrototype::daysInYear);
        prototype.getter("epochMilliseconds", TemporalZonedDateTimePrototype::epochMilliseconds);
        prototype.getter("epochNanoseconds", TemporalZonedDateTimePrototype::epochNanoseconds);
        prototype.getter("era", TemporalZonedDateTimePrototype::era);
        prototype.getter("eraYear", TemporalZonedDateTimePrototype::eraYear);
        prototype.getter("hour", TemporalZonedDateTimePrototype::hour);
        prototype.getter("hoursInDay", TemporalZonedDateTimePrototype::hoursInDay);
        prototype.getter("inLeapYear", TemporalZonedDateTimePrototype::inLeapYear);
        prototype.getter("microsecond", TemporalZonedDateTimePrototype::microsecond);
        prototype.getter("millisecond", TemporalZonedDateTimePrototype::millisecond);
        prototype.getter("minute", TemporalZonedDateTimePrototype::minute);
        prototype.getter("month", TemporalZonedDateTimePrototype::month);
        prototype.getter("monthCode", TemporalZonedDateTimePrototype::monthCode);
        prototype.getter("monthsInYear", TemporalZonedDateTimePrototype::monthsInYear);
        prototype.getter("nanosecond", TemporalZonedDateTimePrototype::nanosecond);
        prototype.getter("offset", TemporalZonedDateTimePrototype::offset);
        prototype.getter("offsetNanoseconds", TemporalZonedDateTimePrototype::offsetNanoseconds);
        prototype.getter("second", TemporalZonedDateTimePrototype::second);
        prototype.getter("timeZoneId", TemporalZonedDateTimePrototype::timeZoneId);
        prototype.getter("weekOfYear", TemporalZonedDateTimePrototype::weekOfYear);
        prototype.getter("year", TemporalZonedDateTimePrototype::year);
        prototype.getter("yearOfWeek", TemporalZonedDateTimePrototype::yearOfWeek);
        prototype.method(Method.ADD, TemporalZonedDateTimePrototype::add);
        prototype.method(Method.EQUALS, TemporalZonedDateTimePrototype::equals);
        prototype.method("getTimeZoneTransition", 1, TemporalZonedDateTimePrototype::getTimeZoneTransition);
        prototype.method(Method.ROUND, TemporalZonedDateTimePrototype::round);
        prototype.method(Method.SINCE, TemporalZonedDateTimePrototype::since);
        prototype.method("startOfDay", 0, TemporalZonedDateTimePrototype::startOfDay);
        prototype.method(Method.SUBTRACT, TemporalZonedDateTimePrototype::subtract);
        prototype.method("toInstant", 0, TemporalZonedDateTimePrototype::toInstant);
        prototype.method("toJSON", 0, TemporalZonedDateTimePrototype::toJSON);
        prototype.method(Method.TO_LOCALE_STRING, TemporalZonedDateTimePrototype::toLocaleString);
        prototype.method("toPlainDate", 0, TemporalZonedDateTimePrototype::toPlainDate);
        prototype.method(Method.TO_PLAIN_DATE_TIME, TemporalZonedDateTimePrototype::toPlainDateTime);
        prototype.method(Method.TO_PLAIN_TIME, TemporalZonedDateTimePrototype::toPlainTime);
        prototype.method("toString", 0, TemporalZonedDateTimePrototype::toStringMethod);
        prototype.method(Method.UNTIL, TemporalZonedDateTimePrototype::until);
        prototype.method(Method.VALUE_OF, TemporalZonedDateTimePrototype::valueOf);
        prototype.method(Method.WITH, TemporalZonedDateTimePrototype::with);
        prototype.method(Method.WITH_CALENDAR, TemporalZonedDateTimePrototype::withCalendar);
        prototype.method(Method.WITH_PLAIN_TIME, TemporalZonedDateTimePrototype::withPlainTime);
        prototype.method("withTimeZone", 1, TemporalZonedDateTimePrototype::withTimeZone);
    });

    private final JSNativeCallback construct;
    private final BuiltinFunctionList constructorMethods;
    private final String constructorName;
    private final int length;
    private final BuiltinFunctionList prototypeFunctions;

    TemporalIntrinsic(String name, int length, JSNativeCallback construct,
            Consumer<BuiltinFunctionList.Builder> constructorMethods,
            Consumer<BuiltinFunctionList.Builder> prototypeFunctions) {
        this.constructorName = name;
        this.length = length;
        this.construct = construct;
        this.constructorMethods = new BuiltinFunctionList(constructorMethods);
        this.prototypeFunctions = new BuiltinFunctionList(prototypeFunctions);
    }

    void install(JSObject temporal) {
        JSContext context = temporal.getContext();
        JSObject prototype = context.createJSObject();
        JSGlobalObject.defineToStringTag(prototype, "Temporal." + constructorName);
        prototypeFunctions.install(prototype);
        JSNativeFunction constructor = new JSNativeFunction(context, constructorName, length, construct, true, false);
        constructorMethods.install(constructor);
        JSGlobalObject.linkConstructor(constructor, prototype);
        JSGlobalObject.defineBuiltin(temporal, constructorName, constructor);
    }
}
