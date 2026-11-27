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

import com.caoccao.qjs4j.core.error.JSError;

/**
 * The stack trace captured for an error, or for an object {@code Error.captureStackTrace} was applied to.
 * <p>
 * The frames and the message are captured when the error is created, but the {@code stack} string is assembled on first
 * read, as V8 does: its first line reads {@code name} at that moment, so a subclass that assigns {@code this.name}
 * after {@code super()} reads as {@code MyError: message} rather than {@code Error: message}, while a message changed
 * after construction does not show. A script assigning to {@code stack} replaces the value for good.
 */
public final class JSStackTrace {
    private final String frames;
    private final JSValue message;
    private JSValue value;

    /**
     * @param frames
     *            the formatted frames, one {@code "    at ..."} line each and no trailing line break, or null when
     *            {@code Error.stackTraceLimit} was not a number, which makes {@code stack} undefined
     * @param message
     *            the owner's {@code message} when the trace was captured
     */
    JSStackTrace(String frames, JSValue message) {
        this.frames = frames;
        this.message = message;
        this.value = null;
    }

    /**
     * The first line: {@code Error.prototype.toString}'s rule, over the owner's current {@code name} and the
     * {@code message} it had when the trace was captured.
     * <p>
     * An exception while computing it does not escape the {@code stack} getter: as in V8, the line becomes
     * {@code <error: ...>} describing that exception, or {@code <error>} when even that fails.
     */
    private String formatHeader(JSContext context, JSObject owner) {
        String name = JSError.NAME;
        String messageText = "";
        JSValue nameValue = owner.get(PropertyKey.NAME);
        if (!context.hasPendingException() && !(nameValue instanceof JSUndefined)) {
            name = JSTypeConversions.toString(context, nameValue).value();
        }
        if (!context.hasPendingException() && !(message instanceof JSUndefined)) {
            messageText = JSTypeConversions.toString(context, message).value();
        }
        if (context.hasPendingException()) {
            JSValue exception = context.takePendingException();
            JSValue description = JSError.errorToString(context, exception, JSValue.NO_ARGS);
            if (context.hasPendingException()) {
                context.clearPendingException();
                return "<error>";
            }
            return "<error: " + ((JSString) description).value() + ">";
        }
        if (name.isEmpty()) {
            return messageText;
        }
        return messageText.isEmpty() ? name : name + ": " + messageText;
    }

    /**
     * The frames without the header line, for diagnostics that must not run script.
     *
     * @return the frames, or null when none were captured
     */
    String getFrames() {
        return frames;
    }

    /**
     * The value of {@code stack}, assembling it on first read.
     *
     * @param context
     *            the realm reading it
     * @param owner
     *            the object the trace belongs to
     * @return the value
     */
    JSValue getValue(JSContext context, JSObject owner) {
        if (value == null) {
            if (frames == null) {
                value = JSUndefined.INSTANCE;
            } else {
                String headerText = formatHeader(context, owner);
                value = new JSString(frames.isEmpty() ? headerText : headerText + "\n" + frames);
            }
        }
        return value;
    }

    void setValue(JSValue value) {
        this.value = value;
    }
}
