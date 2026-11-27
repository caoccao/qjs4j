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

package com.caoccao.qjs4j.builtins.date;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.date.JSDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Unit tests for Date constructor and Date.prototype methods.
 */
public class DateConstructorTest extends BaseJavetTest {

    private void assertLocalDateField(JSNativeCallback getter, double minimum, double maximum,
            JSValue invalidReceiver) {
        JSDate date = new JSDate(context, 1750000000000L);
        assertThat(getter.call(context, date, JSValue.NO_ARGS)).isInstanceOfSatisfying(JSNumber.class,
                number -> assertThat(number.value()).isBetween(minimum, maximum));
        assertPendingTypeError(context, getter.call(context, invalidReceiver, JSValue.NO_ARGS));
    }

    @Test
    public void testDateCalledWithNew() {
        // new Date() creates a Date object
        assertEvalType("new Date()", JSDate.class);

        // Verify it has Date.prototype methods
        assertEvalType("new Date().getTime()", JSNumber.class);
    }

    @Test
    public void testDateCalledWithoutNew() {
        // Date() without new returns a string representing current date/time
        JSValue result = context.eval("Date()");
        assertThat(result).isInstanceOf(JSString.class);

        // The string should contain typical date components
        String dateStr = ((JSString) result).value();
        assertThat(dateStr).isNotEmpty();
        // RFC 1123 format typically contains day name and month
        assertThat(dateStr).matches(".*\\d{4}.*"); // Contains year
    }

    @Test
    public void testDateFunctionVsConstructorBehavior() {
        // Test the key difference: function call vs constructor call

        // Function call: returns string, ignores arguments
        assertStringWithJavet("typeof Date();",
                // Constructor call: returns object
                "typeof new Date();");
    }

    @Test
    public void testDateInstanceMethods() {
        // Skip on Windows due to timezone format differences
        assumeFalse(isWindows(), "Skipping on Windows - timezone format differences with V8");

        // Date instance should have prototype methods
        assertStringWithJavet("var d = new Date(1750000000000); JSON.stringify([d.getTime(),d.toString()]);");
    }

    @Test
    public void testDateIsConstructor() {
        // Date should be recognized as a constructor
        assertBooleanWithJavet("Date.prototype.constructor === Date;");
    }

    @Test
    public void testDateLengthProperty() {
        // Date.length should be 7 (max number of arguments)
        assertIntegerWithJavet("Date.length;");
    }

    @Test
    public void testDatePrototype() {
        // Date.prototype should exist and be an object
        assertEvalType("Date.prototype", JSObject.class);

        // Date.prototype.constructor should be Date
        JSValue constructor = context.eval("Date.prototype.constructor");
        JSValue dateConstructor = context.eval("Date");
        assertThat(constructor).isSameAs(dateConstructor);
    }

    @Test
    public void testDateStaticMethods() {
        // Date.now() should work
        assertStringWithJavet("typeof Date.now;",
                // Date.parse() should work
                "typeof Date.parse;",
                // Date.UTC() should work
                "typeof Date.UTC;");
    }

    @Test
    public void testDateTypeIsFunction() {
        // typeof Date should be "function"
        assertStringWithJavet("typeof Date;");
    }

    @Test
    public void testDateWithArgumentsAsConstructor() {
        // new Date(2025, 0, 1) creates a Date object with specified date
        JSValue result = context.eval("new Date(1750000000000)"); // Specific timestamp
        assertThat(result).isInstanceOf(JSDate.class);

        JSDate date = (JSDate) result;
        assertThat(date.getTimeValue()).isEqualTo(1750000000000L);
    }

    @Test
    public void testDateWithArgumentsAsFunction() {
        // Date(2025, 0, 1) without new still returns current date string (ignores args)
        assertEvalType("Date(2025, 0, 1)", JSString.class);
    }

    @Test
    public void testGetDate() {
        assertLocalDateField(DatePrototype::getDate, 1.0, 31.0, new JSNumber(123));
    }

    @Test
    public void testGetDay() {
        assertLocalDateField(DatePrototype::getDay, 0.0, 6.0, JSNull.INSTANCE);
    }

    @Test
    public void testGetFullYear() {
        // Normal case: 2025-06-15
        JSDate date = new JSDate(context, 1750000000000L); // Approximately 2025-06-15

        JSValue result = callBuiltin(DatePrototype::getFullYear, date);
        // We don't assert exact year as it depends on timezone
        assertThat(result).isInstanceOf(JSNumber.class);

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::getFullYear, new JSObject(context)));
    }

    @Test
    public void testGetHours() {
        assertLocalDateField(DatePrototype::getHours, 0.0, 23.0, new JSArray(context));
    }

    @Test
    public void testGetMilliseconds() {
        JSDate date = new JSDate(context, 1750000000123L); // 123 milliseconds

        assertThat(callBuiltin(DatePrototype::getMilliseconds, date)).isInstanceOfSatisfying(JSNumber.class, jsNum -> {
            assertThat(jsNum.value()).isBetween(0.0, 999.0);
        });

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::getMilliseconds, new JSObject(context)));
    }

    @Test
    public void testGetMinutes() {
        assertLocalDateField(DatePrototype::getMinutes, 0.0, 59.0, new JSString("not date"));
    }

    @Test
    public void testGetMonth() {
        assertLocalDateField(DatePrototype::getMonth, 0.0, 11.0, JSUndefined.INSTANCE);
    }

    @Test
    public void testGetSeconds() {
        assertLocalDateField(DatePrototype::getSeconds, 0.0, 59.0, JSBoolean.TRUE);
    }

    @Test
    public void testGetTime() {
        // Normal case
        long timestamp = System.currentTimeMillis();
        JSDate date = new JSDate(context, timestamp);

        assertNumberValue(callBuiltin(DatePrototype::getTime, date), timestamp);

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::getTime, new JSString("not date")));
    }

    @Test
    public void testGetUTCDate() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(context, 1735689600000L);

        assertNumberValue(callBuiltin(DatePrototype::getUTCDate, date), 1.0);

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::getUTCDate, new JSNumber(42)));
    }

    @Test
    public void testGetUTCFullYear() {
        // Known timestamp: 2025-01-01T00:00:00.000Z = 1735689600000
        JSDate date = new JSDate(context, 1735689600000L);

        assertNumberValue(callBuiltin(DatePrototype::getUTCFullYear, date), 2025.0);

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::getUTCFullYear, new JSString("not date")));
    }

    @Test
    public void testGetUTCHours() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(context, 1735689600000L);

        assertNumberValue(callBuiltin(DatePrototype::getUTCHours, date), 0.0);

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::getUTCHours, JSNull.INSTANCE));
    }

    @Test
    public void testGetUTCMonth() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(context, 1735689600000L);

        assertNumberValue(callBuiltin(DatePrototype::getUTCMonth, date), 0.0); // January
        // =
        // 0

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::getUTCMonth, JSUndefined.INSTANCE));
    }

    @Test
    public void testNow() {
        // Normal case
        long before = System.currentTimeMillis();
        JSValue result = callBuiltin(DateConstructor::now, JSUndefined.INSTANCE);
        long after = System.currentTimeMillis();

        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> {
            assertThat(jsNum.value()).isBetween((double) before, (double) after);
        });
    }

    @Test
    public void testParse() {
        // Normal case: ISO 8601 format
        var parse = builtin(DateConstructor::parse, JSUndefined.INSTANCE);
        JSValue result = parse.call("2025-01-01T00:00:00.000Z");
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> {
            assertThat(jsNum.value()).isGreaterThan(0.0);
        });

        // Edge case: no arguments
        result = parse.call();
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> {
            assertThat(jsNum.value()).isNaN();
        });

        // Edge case: invalid date string
        result = parse.call("not a date");
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> {
            assertThat(jsNum.value()).isNaN();
        });
    }

    @Test
    public void testToISOString() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(context, 1735689600000L);

        assertThat(callBuiltin(DatePrototype::toISOString, date)).isInstanceOfSatisfying(JSString.class, jsStr -> {
            String isoString = jsStr.value();
            assertThat(isoString).contains("2025-01-01");
            assertThat(isoString).endsWith("Z");
        });

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::toISOString, new JSArray(context)));
    }

    @Test
    public void testToJSON() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(context, 1735689600000L);

        assertThat(callBuiltin(DatePrototype::toJSON, date)).isInstanceOfSatisfying(JSString.class, jsStr -> {
            assertThat(jsStr.value()).contains("2025-01-01");
        });

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::toJSON, JSBoolean.FALSE));
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet("typeof Date;");
        assertIntegerWithJavet("Date.length;");
    }

    @Test
    public void testUTC() {
        // Normal case: Date.UTC(2025, 0, 1, 0, 0, 0)
        var utc = builtin(DateConstructor::UTC, JSUndefined.INSTANCE);
        JSValue result = utc.call(2025, 0, // January
                1, 0, 0, 0);
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> {
            assertThat(jsNum.value()).isGreaterThan(0.0);
        });

        // Edge case: only year
        result = utc.call(2025);
        assertThat(result).isInstanceOf(JSNumber.class);

        // Edge case: year and month
        result = utc.call(2025, 5); // June
        assertThat(result).isInstanceOf(JSNumber.class);

        // Edge case: no arguments (should return NaN)
        result = utc.call();
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> {
            assertThat(jsNum.value()).isNaN();
        });

        // Edge case: overflow fields should normalize instead of returning NaN.
        result = utc.call(2025, 12, 32);
        assertThat(result).isInstanceOf(JSNumber.class);
        assertDoubleWithJavet("Date.UTC(2025, 12, 32);");
    }

    @Test
    public void testValueOf() {
        long timestamp = 1735689600000L;
        JSDate date = new JSDate(context, timestamp);

        assertNumberValue(callBuiltin(DatePrototype::valueOf, date), timestamp);

        // Edge case: called on non-Date
        assertPendingTypeError(context, callBuiltin(DatePrototype::valueOf, new JSObject(context)));
    }
}
