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

package com.caoccao.qjs4j.builtins.temporal.duration;

import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDuration;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDurationDouble;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/** Duration component accessors in the order installed on the prototype and on rounded results. */
public enum TemporalDurationProperty implements JSNativeCallback {
    DAYS(TemporalUnit.DAY, TemporalDuration::days, TemporalDurationDouble::days),
    HOURS(TemporalUnit.HOUR, TemporalDuration::hours, TemporalDurationDouble::hours),
    MICROSECONDS(TemporalUnit.MICROSECOND, TemporalDuration::microseconds, TemporalDurationDouble::microseconds),
    MILLISECONDS(TemporalUnit.MILLISECOND, TemporalDuration::milliseconds, TemporalDurationDouble::milliseconds),
    MINUTES(TemporalUnit.MINUTE, TemporalDuration::minutes, TemporalDurationDouble::minutes),
    MONTHS(TemporalUnit.MONTH, TemporalDuration::months, TemporalDurationDouble::months),
    NANOSECONDS(TemporalUnit.NANOSECOND, TemporalDuration::nanoseconds, TemporalDurationDouble::nanoseconds),
    SECONDS(TemporalUnit.SECOND, TemporalDuration::seconds, TemporalDurationDouble::seconds),
    WEEKS(TemporalUnit.WEEK, TemporalDuration::weeks, TemporalDurationDouble::weeks),
    YEARS(TemporalUnit.YEAR, TemporalDuration::years, TemporalDurationDouble::years);

    public static final List<TemporalDurationProperty> COMPONENTS = List.of(YEARS, MONTHS, WEEKS, DAYS, HOURS, MINUTES,
            SECONDS, MILLISECONDS, MICROSECONDS, NANOSECONDS);
    private final ToLongFunction<TemporalDuration> component;
    private final PropertyKey key;
    private final ToDoubleFunction<TemporalDurationDouble> override;

    TemporalDurationProperty(TemporalUnit unit, ToLongFunction<TemporalDuration> component,
            ToDoubleFunction<TemporalDurationDouble> override) {
        key = unit.durationProperty();
        this.component = component;
        this.override = override;
    }

    @Override
    public JSValue call(JSContext context, JSValue receiver, JSValue... args) {
        JSTemporalDuration duration = TemporalReceiver.DURATION.require(context, receiver, propertyName());
        return duration == null ? JSUndefined.INSTANCE : JSNumber.of(component.applyAsLong(duration.getDuration()));
    }

    void defineOverride(JSTemporalDuration duration, TemporalDurationDouble fields) {
        duration.defineProperty(key, PropertyDescriptor.defaultData(JSNumber.of(override.applyAsDouble(fields))));
    }

    public String propertyName() {
        return key.asString();
    }
}
