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
        constructor.method("from", 1, TemporalDurationConstructor::from);
        constructor.method("compare", 2, TemporalDurationConstructor::compare);
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
        prototype.method("with", 1, TemporalDurationPrototype::with);
        prototype.method("negated", 0, TemporalDurationPrototype::negated);
        prototype.method("abs", 0, TemporalDurationPrototype::abs);
        prototype.method("add", 1, TemporalDurationPrototype::add);
        prototype.method("subtract", 1, TemporalDurationPrototype::subtract);
        prototype.method("round", 1, TemporalDurationPrototype::round);
        prototype.method("total", 1, TemporalDurationPrototype::total);
        prototype.method("toString", 0, TemporalDurationPrototype::toStringMethod);
        prototype.method("toJSON", 0, TemporalDurationPrototype::toJSON);
        prototype.method("toLocaleString", 0, TemporalDurationPrototype::toLocaleString);
        prototype.method("valueOf", 0, TemporalDurationPrototype::valueOf);
    }),
    INSTANT("Instant", 1, TemporalInstantConstructor::construct, constructor -> {
        constructor.method("from", 1, TemporalInstantConstructor::from);
        constructor.method("fromEpochMilliseconds", 1, TemporalInstantConstructor::fromEpochMilliseconds);
        constructor.method("fromEpochNanoseconds", 1, TemporalInstantConstructor::fromEpochNanoseconds);
        constructor.method("compare", 2, TemporalInstantConstructor::compare);
    }, prototype -> {
        prototype.getter("epochMilliseconds", TemporalInstantPrototype::epochMilliseconds);
        prototype.getter("epochNanoseconds", TemporalInstantPrototype::epochNanoseconds);
        prototype.method("add", 1, TemporalInstantPrototype::add);
        prototype.method("subtract", 1, TemporalInstantPrototype::subtract);
        prototype.method("until", 1, TemporalInstantPrototype::until);
        prototype.method("since", 1, TemporalInstantPrototype::since);
        prototype.method("round", 1, TemporalInstantPrototype::round);
        prototype.method("equals", 1, TemporalInstantPrototype::equals);
        prototype.method("toString", 0, TemporalInstantPrototype::toStringMethod);
        prototype.method("toJSON", 0, TemporalInstantPrototype::toJSON);
        prototype.method("toLocaleString", 0, TemporalInstantPrototype::toLocaleString);
        prototype.method("valueOf", 0, TemporalInstantPrototype::valueOf);
        prototype.method("toZonedDateTimeISO", 1, TemporalInstantPrototype::toZonedDateTimeISO);
    }),
    PLAIN_DATE("PlainDate", 3, TemporalPlainDateConstructor::construct, constructor -> {
        constructor.method("compare", 2, TemporalPlainDateConstructor::compare);
        constructor.method("from", 1, TemporalPlainDateConstructor::from);
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
        prototype.method("add", 1, TemporalPlainDatePrototype::add);
        prototype.method("equals", 1, TemporalPlainDatePrototype::equals);
        prototype.method("since", 1, TemporalPlainDatePrototype::since);
        prototype.method("subtract", 1, TemporalPlainDatePrototype::subtract);
        prototype.method("toJSON", 0, TemporalPlainDatePrototype::toJSON);
        prototype.method("toLocaleString", 0, TemporalPlainDatePrototype::toLocaleString);
        prototype.method("toPlainDateTime", 0, TemporalPlainDatePrototype::toPlainDateTime);
        prototype.method("toPlainMonthDay", 0, TemporalPlainDatePrototype::toPlainMonthDay);
        prototype.method("toPlainYearMonth", 0, TemporalPlainDatePrototype::toPlainYearMonth);
        prototype.method("toString", 0, TemporalPlainDatePrototype::toStringMethod);
        prototype.method("toZonedDateTime", 1, TemporalPlainDatePrototype::toZonedDateTime);
        prototype.method("until", 1, TemporalPlainDatePrototype::until);
        prototype.method("valueOf", 0, TemporalPlainDatePrototype::valueOf);
        prototype.method("with", 1, TemporalPlainDatePrototype::with);
        prototype.method("withCalendar", 1, TemporalPlainDatePrototype::withCalendar);
    }),
    PLAIN_DATE_TIME("PlainDateTime", 3, TemporalPlainDateTimeConstructor::construct, constructor -> {
        constructor.method("from", 1, TemporalPlainDateTimeConstructor::from);
        constructor.method("compare", 2, TemporalPlainDateTimeConstructor::compare);
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
        prototype.method("add", 1, TemporalPlainDateTimePrototype::add);
        prototype.method("equals", 1, TemporalPlainDateTimePrototype::equals);
        prototype.method("round", 1, TemporalPlainDateTimePrototype::round);
        prototype.method("since", 1, TemporalPlainDateTimePrototype::since);
        prototype.method("subtract", 1, TemporalPlainDateTimePrototype::subtract);
        prototype.method("toJSON", 0, TemporalPlainDateTimePrototype::toJSON);
        prototype.method("toLocaleString", 0, TemporalPlainDateTimePrototype::toLocaleString);
        prototype.method("toPlainDate", 0, TemporalPlainDateTimePrototype::toPlainDate);
        prototype.method("toPlainTime", 0, TemporalPlainDateTimePrototype::toPlainTime);
        prototype.method("toString", 0, TemporalPlainDateTimePrototype::toStringMethod);
        prototype.method("toZonedDateTime", 1, TemporalPlainDateTimePrototype::toZonedDateTime);
        prototype.method("until", 1, TemporalPlainDateTimePrototype::until);
        prototype.method("valueOf", 0, TemporalPlainDateTimePrototype::valueOf);
        prototype.method("with", 1, TemporalPlainDateTimePrototype::with);
        prototype.method("withCalendar", 1, TemporalPlainDateTimePrototype::withCalendar);
        prototype.method("withPlainTime", 0, TemporalPlainDateTimePrototype::withPlainTime);
    }),
    PLAIN_MONTH_DAY("PlainMonthDay", 2, TemporalPlainMonthDayConstructor::construct, constructor -> {
        constructor.method("from", 1, TemporalPlainMonthDayConstructor::from);
    }, prototype -> {
        prototype.getter("calendarId", TemporalPlainMonthDayPrototype::calendarId);
        prototype.getter("day", TemporalPlainMonthDayPrototype::day);
        prototype.getter("monthCode", TemporalPlainMonthDayPrototype::monthCode);
        prototype.getter("referenceISOYear", TemporalPlainMonthDayPrototype::referenceISOYear);
        prototype.method("equals", 1, TemporalPlainMonthDayPrototype::equals);
        prototype.method("toJSON", 0, TemporalPlainMonthDayPrototype::toJSON);
        prototype.method("toLocaleString", 0, TemporalPlainMonthDayPrototype::toLocaleString);
        prototype.method("toPlainDate", 1, TemporalPlainMonthDayPrototype::toPlainDate);
        prototype.method("toString", 0, TemporalPlainMonthDayPrototype::toStringMethod);
        prototype.method("valueOf", 0, TemporalPlainMonthDayPrototype::valueOf);
        prototype.method("with", 1, TemporalPlainMonthDayPrototype::with);
    }),
    PLAIN_TIME("PlainTime", 0, TemporalPlainTimeConstructor::construct, constructor -> {
        constructor.method("compare", 2, TemporalPlainTimeConstructor::compare);
        constructor.method("from", 1, TemporalPlainTimeConstructor::from);
    }, prototype -> {
        prototype.getter("hour", TemporalPlainTimePrototype::hour);
        prototype.getter("microsecond", TemporalPlainTimePrototype::microsecond);
        prototype.getter("millisecond", TemporalPlainTimePrototype::millisecond);
        prototype.getter("minute", TemporalPlainTimePrototype::minute);
        prototype.getter("nanosecond", TemporalPlainTimePrototype::nanosecond);
        prototype.getter("second", TemporalPlainTimePrototype::second);
        prototype.method("add", 1, TemporalPlainTimePrototype::add);
        prototype.method("equals", 1, TemporalPlainTimePrototype::equals);
        prototype.method("round", 1, TemporalPlainTimePrototype::round);
        prototype.method("since", 1, TemporalPlainTimePrototype::since);
        prototype.method("subtract", 1, TemporalPlainTimePrototype::subtract);
        prototype.method("toJSON", 0, TemporalPlainTimePrototype::toJSON);
        prototype.method("toLocaleString", 0, TemporalPlainTimePrototype::toLocaleString);
        prototype.method("toString", 0, TemporalPlainTimePrototype::toStringMethod);
        prototype.method("until", 1, TemporalPlainTimePrototype::until);
        prototype.method("valueOf", 0, TemporalPlainTimePrototype::valueOf);
        prototype.method("with", 1, TemporalPlainTimePrototype::with);
    }),
    PLAIN_YEAR_MONTH("PlainYearMonth", 2, TemporalPlainYearMonthConstructor::construct, constructor -> {
        constructor.method("compare", 2, TemporalPlainYearMonthConstructor::compare);
        constructor.method("from", 1, TemporalPlainYearMonthConstructor::from);
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
        prototype.method("add", 1, TemporalPlainYearMonthPrototype::add);
        prototype.method("equals", 1, TemporalPlainYearMonthPrototype::equals);
        prototype.method("since", 1, TemporalPlainYearMonthPrototype::since);
        prototype.method("subtract", 1, TemporalPlainYearMonthPrototype::subtract);
        prototype.method("toJSON", 0, TemporalPlainYearMonthPrototype::toJSON);
        prototype.method("toLocaleString", 0, TemporalPlainYearMonthPrototype::toLocaleString);
        prototype.method("toPlainDate", 1, TemporalPlainYearMonthPrototype::toPlainDate);
        prototype.method("toString", 0, TemporalPlainYearMonthPrototype::toStringMethod);
        prototype.method("until", 1, TemporalPlainYearMonthPrototype::until);
        prototype.method("valueOf", 0, TemporalPlainYearMonthPrototype::valueOf);
        prototype.method("with", 1, TemporalPlainYearMonthPrototype::with);
    }),
    ZONED_DATE_TIME("ZonedDateTime", 2, TemporalZonedDateTimeConstructor::construct, constructor -> {
        constructor.method("compare", 2, TemporalZonedDateTimeConstructor::compare);
        constructor.method("from", 1, TemporalZonedDateTimeConstructor::from);
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
        prototype.method("add", 1, TemporalZonedDateTimePrototype::add);
        prototype.method("equals", 1, TemporalZonedDateTimePrototype::equals);
        prototype.method("getTimeZoneTransition", 1, TemporalZonedDateTimePrototype::getTimeZoneTransition);
        prototype.method("round", 1, TemporalZonedDateTimePrototype::round);
        prototype.method("since", 1, TemporalZonedDateTimePrototype::since);
        prototype.method("startOfDay", 0, TemporalZonedDateTimePrototype::startOfDay);
        prototype.method("subtract", 1, TemporalZonedDateTimePrototype::subtract);
        prototype.method("toInstant", 0, TemporalZonedDateTimePrototype::toInstant);
        prototype.method("toJSON", 0, TemporalZonedDateTimePrototype::toJSON);
        prototype.method("toLocaleString", 0, TemporalZonedDateTimePrototype::toLocaleString);
        prototype.method("toPlainDate", 0, TemporalZonedDateTimePrototype::toPlainDate);
        prototype.method("toPlainDateTime", 0, TemporalZonedDateTimePrototype::toPlainDateTime);
        prototype.method("toPlainTime", 0, TemporalZonedDateTimePrototype::toPlainTime);
        prototype.method("toString", 0, TemporalZonedDateTimePrototype::toStringMethod);
        prototype.method("until", 1, TemporalZonedDateTimePrototype::until);
        prototype.method("valueOf", 0, TemporalZonedDateTimePrototype::valueOf);
        prototype.method("with", 1, TemporalZonedDateTimePrototype::with);
        prototype.method("withCalendar", 1, TemporalZonedDateTimePrototype::withCalendar);
        prototype.method("withPlainTime", 0, TemporalZonedDateTimePrototype::withPlainTime);
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
