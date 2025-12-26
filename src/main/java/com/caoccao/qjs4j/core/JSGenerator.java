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

/**
 * Represents a JavaScript Generator object.
 * Based on ES2020 Generator specification (simplified).
 *
 * A generator is both an iterator and an iterable.
 * It implements the generator protocol with next(), return(), and throw() methods.
 *
 * This is a simplified implementation that wraps an iterator function.
 * In a full implementation, generators would support yield expressions
 * and maintain execution state across calls.
 */
public final class JSGenerator extends JSObject {
    private final JSIterator.IteratorFunction iteratorFunction;
    private boolean done;
    private JSValue returnValue;

    /**
     * Generator state.
     */
    public enum GeneratorState {
        SUSPENDED_START,    // Created but not yet started
        SUSPENDED_YIELD,    // Suspended at a yield expression
        EXECUTING,          // Currently running
        COMPLETED           // Finished execution
    }

    private GeneratorState state;

    /**
     * Create a generator with the given iteration logic.
     * Simplified: uses an iterator function rather than bytecode with yield.
     */
    public JSGenerator(JSIterator.IteratorFunction iteratorFunction) {
        super();
        this.iteratorFunction = iteratorFunction;
        this.done = false;
        this.returnValue = JSUndefined.INSTANCE;
        this.state = GeneratorState.SUSPENDED_START;
    }

    /**
     * Generator.prototype.next(value)
     * ES2020 25.5.1.2
     * Resumes execution and returns the next yielded value.
     */
    public JSObject next(JSValue value) {
        if (state == GeneratorState.COMPLETED) {
            return createIteratorResult(returnValue, true);
        }

        if (done) {
            state = GeneratorState.COMPLETED;
            return createIteratorResult(returnValue, true);
        }

        state = GeneratorState.EXECUTING;

        JSIterator.IteratorResult result = iteratorFunction.next();

        if (result.done) {
            done = true;
            state = GeneratorState.COMPLETED;
            returnValue = result.value;
            return createIteratorResult(result.value, true);
        } else {
            state = GeneratorState.SUSPENDED_YIELD;
            return createIteratorResult(result.value, false);
        }
    }

    /**
     * Generator.prototype.return(value)
     * ES2020 25.5.1.3
     * Finishes the generator and returns the given value.
     */
    public JSObject returnMethod(JSValue value) {
        if (state == GeneratorState.COMPLETED) {
            return createIteratorResult(returnValue, true);
        }

        done = true;
        state = GeneratorState.COMPLETED;
        returnValue = value != null ? value : JSUndefined.INSTANCE;
        return createIteratorResult(returnValue, true);
    }

    /**
     * Generator.prototype.throw(exception)
     * ES2020 25.5.1.4
     * Throws an exception into the generator.
     * Simplified: just completes the generator with the exception.
     */
    public JSObject throwMethod(JSValue exception) {
        if (state == GeneratorState.COMPLETED) {
            // Generator already completed, re-throw the exception
            throw new RuntimeException("Uncaught exception in generator: " + exception);
        }

        done = true;
        state = GeneratorState.COMPLETED;

        // In a full implementation, this would resume the generator
        // at the current yield point and throw the exception there.
        // For simplicity, we just complete the generator.
        throw new RuntimeException("Exception thrown into generator: " + exception);
    }

    /**
     * Create an iterator result object: { value: any, done: boolean }
     */
    private JSObject createIteratorResult(JSValue value, boolean isDone) {
        JSObject result = new JSObject();
        result.set("value", value);
        result.set("done", JSBoolean.valueOf(isDone));
        return result;
    }

    /**
     * Get the current state of the generator.
     */
    public GeneratorState getState() {
        return state;
    }

    @Override
    public String toString() {
        return "[object Generator]";
    }

    /**
     * Helper to create a simple generator from an iterator function.
     */
    public static JSGenerator fromIteratorFunction(JSIterator.IteratorFunction iteratorFunction) {
        return new JSGenerator(iteratorFunction);
    }

    /**
     * Helper to create a generator from an array.
     */
    public static JSGenerator fromArray(JSArray array) {
        final int[] index = {0};
        return new JSGenerator(() -> {
            if (index[0] < array.getLength()) {
                JSValue value = array.get(index[0]++);
                return JSIterator.IteratorResult.of(value);
            }
            return JSIterator.IteratorResult.done();
        });
    }
}
