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

import com.caoccao.qjs4j.core.PropertyKey;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;

/** Time fields interleave with month and offset fields in the observable order of property access. */
public final class TemporalTimeFields extends TemporalPropertyBag {
    private Integer hour;
    private Integer microsecond;
    private Integer millisecond;
    private Integer minute;
    private Integer nanosecond;
    private Integer second;

    public TemporalTimeFields(JSContext context, JSObject fields) {
        super(context, fields);
    }

    @Override
    public boolean hasTimeField() {
        return super.hasTimeField() || hour != null || microsecond != null || millisecond != null || minute != null
                || nanosecond != null || second != null;
    }

    public void readNanosecond() {
        nanosecond = integerByKey(PropertyKey.NANOSECOND);
    }

    public void readSecond() {
        second = integerByKey(PropertyKey.SECOND);
    }

    public void readThroughMinute() {
        hour = integerByKey(PropertyKey.HOUR);
        microsecond = integerByKey(PropertyKey.MICROSECOND);
        millisecond = integerByKey(PropertyKey.MILLISECOND);
        minute = integerByKey(PropertyKey.MINUTE);
    }

    public IsoTime regulate(IsoTime defaults, String overflow, String error) {
        return IsoTime.regulate(context, overflow, error, hour == null ? defaults.hour() : hour,
                minute == null ? defaults.minute() : minute, second == null ? defaults.second() : second,
                millisecond == null ? defaults.millisecond() : millisecond,
                microsecond == null ? defaults.microsecond() : microsecond,
                nanosecond == null ? defaults.nanosecond() : nanosecond);
    }
}
