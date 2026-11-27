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
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.error.JSError;
import java.util.function.BiFunction;

/** Shared Temporal validation failures and their abrupt-completion return values. */
public enum TemporalError {
    DURATION_FIELD_OUT_OF_RANGE("Duration field out of range.", JSContext::throwRangeError),
    EXPECTED_FINITE_INTEGER("Expected finite integer.", JSContext::throwRangeError),
    INVALID_DATE_TIME_ARGUMENT("DateTime argument must be object or string.", JSContext::throwTypeError),
    INVALID_DURATION("Duration was not valid.", JSContext::throwRangeError),
    INVALID_ISO_DATE("Invalid ISO date.", JSContext::throwRangeError),
    INVALID_OFFSET_STRING("Invalid offset string.", JSContext::throwRangeError),
    INVALID_OPTIONS("Option must be object: options.", JSContext::throwTypeError),
    INVALID_RELATIVE_TO("Invalid relativeTo option.", JSContext::throwTypeError),
    INVALID_ROUNDING_INCREMENT("Invalid rounding increment.", JSContext::throwRangeError),
    INVALID_ROUNDING_MODE("Invalid rounding mode.", JSContext::throwRangeError),
    INVALID_YEAR_ARGUMENT("year argument must be an object.", JSContext::throwTypeError),
    MISMATCHED_CALENDARS("Mismatched calendars.", JSContext::throwRangeError),
    MISSING_PARTIAL_FIELDS("Argument to with() must contain some date/time fields.", JSContext::throwTypeError),
    MONTH_CODE_OUT_OF_RANGE("Month code out of range.", JSContext::throwRangeError),
    NANOSECONDS_OUT_OF_RANGE("Nanoseconds out of range.", JSContext::throwRangeError);

    private final String message;
    private final BiFunction<JSContext, String, JSError> throwError;

    TemporalError(String detail, BiFunction<JSContext, String, JSError> throwError) {
        this.message = "Temporal error: " + detail;
        this.throwError = throwError;
    }

    public String message() {
        return message;
    }

    public JSError raise(JSContext context) {
        return throwError.apply(context, message);
    }

    /** Signal a failed conversion to a Java reference type. */
    public <T> T reject(JSContext context) {
        raise(context);
        return null;
    }

    /** Signal an abrupt completion from a native JavaScript callback. */
    public JSUndefined undefined(JSContext context) {
        raise(context);
        return JSUndefined.INSTANCE;
    }
}
