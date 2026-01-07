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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DatePrototype methods.
 */
public class DatePrototypeTest extends BaseJavetTest {

    @Test
    public void testDateConstructorFunctionVsToString() {
        // Date() function and date.toString() should use same format
        context.eval("var d = new Date();");

        // Get toString result
        JSValue toStringResult = context.eval("d.toString();");
        assertThat(toStringResult).isInstanceOf(JSString.class);

        // Both should match V8 format pattern
        String pattern = "^\\w{3} \\w{3} \\d{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT[+-]\\d{4} \\([A-Z]{3,4}\\)$";
        assertThat(((JSString) toStringResult).value()).matches(pattern);
    }

    @Test
    public void testDateFunctionReturnsV8Format() {
        // Date() function (without new) should also return V8 format
        JSValue result = context.eval("Date()");
        assertThat(result).isInstanceOf(JSString.class);

        String str = ((JSString) result).value();

        // Should follow V8 format
        assertThat(str).matches("^(Mon|Tue|Wed|Thu|Fri|Sat|Sun) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \\d{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT[+-]\\d{4} \\([A-Z]{3,4}\\)$");
    }

    @Test
    public void testDateToStringConsistency() {
        // Multiple calls should return consistent format
        JSDate date = new JSDate(1704067200000L); // 2024-01-01 00:00:00 UTC

        JSValue result1 = DatePrototype.toStringMethod(context, date, new JSValue[]{});
        JSValue result2 = DatePrototype.toStringMethod(context, date, new JSValue[]{});

        assertThat(((JSString) result1).value()).isEqualTo(((JSString) result2).value());
    }

    @Test
    public void testDateToStringDifferentTimezones() {
        // The toString method uses local timezone
        // We can't hardcode the exact output, but we can verify the format
        JSDate date = new JSDate(0); // Unix epoch

        JSValue result = DatePrototype.toStringMethod(context, date, new JSValue[]{});
        String str = ((JSString) result).value();

        // Should contain 1970 (epoch year)
        assertThat(str).contains("1970");

        // Should contain "Jan" (epoch month)
        assertThat(str).contains("Jan");

        // Should have GMT offset
        assertThat(str).contains("GMT");
    }

    @Test
    public void testDateToStringFormat() {
        // Test with specific timestamp: 2025-01-01 00:00:00 UTC
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.toStringMethod(context, date, new JSValue[]{});
        assertThat(result).isInstanceOf(JSString.class);

        String str = ((JSString) result).value();

        // V8 format: "EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)"
        // Should contain:
        // - Day name (Mon, Tue, Wed, etc.)
        assertThat(str).matches("^(Mon|Tue|Wed|Thu|Fri|Sat|Sun) .*");

        // - Month name (Jan, Feb, Mar, etc.)
        assertThat(str).contains("Jan");

        // - Two-digit day (01 not 1)
        assertThat(str).contains(" 01 ");

        // - Four-digit year
        assertThat(str).contains("2025");

        // - "GMT" prefix
        assertThat(str).contains("GMT");

        // - Timezone offset (+xxxx or -xxxx)
        assertThat(str).matches(".*GMT[+-]\\d{4}.*");

        // - Timezone abbreviation in parentheses
        assertThat(str).matches(".*\\([A-Z]{3,4}\\)$");
    }

    @Test
    public void testDateToStringOnNonDate() {
        // Calling toString on non-Date should throw TypeError
        JSValue result = DatePrototype.toStringMethod(context, new JSString("not a date"), new JSValue[]{});
        assertThat(result).isInstanceOf(JSError.class);
        assertThat(context.hasPendingException()).isTrue();

        JSValue exception = context.getPendingException();
        assertThat(exception).isInstanceOf(JSError.class);
    }

    @Test
    public void testDateToStringPrototypeMethod() {
        // Test via prototype method call
        context.eval("var d = new Date(1735689600000);");
        JSValue result = context.eval("d.toString();");

        assertThat(result).isInstanceOf(JSString.class);
        String str = ((JSString) result).value();

        // Should match V8 format pattern
        assertThat(str).matches("^\\w{3} \\w{3} \\d{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT[+-]\\d{4} \\([A-Z]{3,4}\\)$");
    }

    @Test
    public void testDateToStringVsRFC1123() {
        // This test documents the difference between V8 and RFC 1123 formats
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.toStringMethod(context, date, new JSValue[]{});
        String v8Format = ((JSString) result).value();

        // V8 format should NOT have a comma after day name
        assertThat(v8Format).doesNotMatch("^\\w{3}, .*");

        // V8 format should have "GMT" before offset
        assertThat(v8Format).contains("GMT");

        // V8 format should have timezone abbreviation in parentheses at the end
        assertThat(v8Format).matches(".*\\([A-Z]{3,4}\\)$");
    }

    @Test
    public void testDateToStringWithJavet() {
        // Compare with Javet (V8) to ensure format matches
        assertStringWithJavet("new Date(1735689600000).toString();");
    }

    @Test
    public void testGetDate() {
        // Test with 2024-01-01 00:00:00 UTC
        assertIntegerWithJavet("new Date(1704067200000).getDate();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getDate(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetDay() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getDay(context, date, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> {
            double day = jsNum.value();
            assertThat(day).isBetween(0.0, 6.0);
        });

        // Edge case: called on non-Date
        result = DatePrototype.getDay(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetFullYear() {
        // Test with 2024-01-01 00:00:00 UTC
        assertIntegerWithJavet("new Date(1704067200000).getFullYear();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getFullYear(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetHours() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getHours(context, date, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> {
            double hours = jsNum.value();
            assertThat(hours).isBetween(0.0, 23.0);
        });

        // Edge case: called on non-Date
        result = DatePrototype.getHours(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetMilliseconds() {
        // Test with 2024-01-01 00:00:00 UTC
        assertIntegerWithJavet("new Date(1704067200000).getMilliseconds();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getMilliseconds(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetMinutes() {
        // Test with 2024-01-01 00:00:00 UTC
        assertIntegerWithJavet("new Date(1704067200000).getMinutes();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getMinutes(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetMonth() {
        // Test with 2024-01-01 00:00:00 UTC (January = month 0)
        assertIntegerWithJavet("new Date(1704067200000).getMonth();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getMonth(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetSeconds() {
        // Test with 2024-01-01 00:00:00 UTC
        assertIntegerWithJavet("new Date(1704067200000).getSeconds();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getSeconds(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetTime() {
        // Test with a known timestamp: 2024-01-01 00:00:00 UTC (1704067200000L)
        assertDoubleWithJavet("new Date(1704067200000).getTime();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getTime(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetUTCDate() {
        // Test with 2024-01-01 00:00:00 UTC
        assertIntegerWithJavet("new Date(1704067200000).getUTCDate();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getUTCDate(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetUTCFullYear() {
        // Test with 2024-01-01 00:00:00 UTC
        assertIntegerWithJavet("new Date(1704067200000).getUTCFullYear();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getUTCFullYear(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetUTCHours() {
        // Test with 2024-01-01 00:00:00 UTC
        assertIntegerWithJavet("new Date(1704067200000).getUTCHours();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getUTCHours(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetUTCMonth() {
        // Test with 2024-01-01 00:00:00 UTC
        assertIntegerWithJavet("new Date(1704067200000).getUTCMonth();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.getUTCMonth(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testToISOString() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.toISOString(context, date, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> {
            String isoString = jsStr.value();
            assertThat(isoString).startsWith("2024-01-01T00:00:00");
        });

        // Edge case: called on non-Date
        result = DatePrototype.toISOString(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testToJSON() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.toJSON(context, date, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> {
            String jsonString = jsStr.value();
            assertThat(jsonString).startsWith("2024-01-01T00:00:00");
        });

        // Edge case: called on non-Date
        result = DatePrototype.toJSON(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testToString() {
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.toStringMethod(context, date, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> {
            String str = jsStr.value();
            assertThat(str).isNotEmpty();
        });

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.toStringMethod(context, new JSString("not date"), new JSValue[]{}));
        assertPendingException(context);

        assertStringWithJavet(
                "new Date(1735689600000).toString();");
    }

    @Test
    public void testValueOf() {
        // Test with 2024-01-01 00:00:00 UTC
        assertDoubleWithJavet("new Date(1704067200000).valueOf();");

        // Edge case: called on non-Date
        JSValue result = DatePrototype.valueOf(context, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }
}