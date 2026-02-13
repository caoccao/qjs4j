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
 * Based on QuickJS JS_CLASS_GENERATOR and JSGeneratorData.
 * <p>
 * Supports two modes:
 * - Bytecode generators: created by function* declarations/expressions,
 * execution managed by GeneratorState and the VM
 * - Iterator function generators: simplified mode using an IteratorFunction callback
 * <p>
 * Prototype methods (next, return, throw) are defined on Generator.prototype
 * in JSGlobalObject, not on each instance.
 */
public final class JSGenerator extends JSObject {
    private final JSContext context;
    private final JSGeneratorState generatorState;
    private final JSIterator.IteratorFunction iteratorFunction;
    private boolean done;
    private JSValue returnValue;
    private State state;

    /**
     * Create a generator backed by bytecode execution.
     * Used by JSBytecodeFunction.call() for function* generators.
     */
    public JSGenerator(JSContext context, JSGeneratorState generatorState) {
        super();
        this.context = context;
        this.generatorState = generatorState;
        this.iteratorFunction = null;
        this.done = false;
        this.returnValue = JSUndefined.INSTANCE;
        this.state = State.SUSPENDED_START;
    }

    /**
     * Create a generator with the given iteration logic.
     * Used for manually created generators (fromArray, fromIteratorFunction).
     */
    public JSGenerator(JSContext context, JSIterator.IteratorFunction iteratorFunction) {
        super();
        this.context = context;
        this.generatorState = null;
        this.iteratorFunction = iteratorFunction;
        this.done = false;
        this.returnValue = JSUndefined.INSTANCE;
        this.state = State.SUSPENDED_START;
    }

    /**
     * Helper to create a generator from an array.
     */
    public static JSGenerator fromArray(JSContext context, JSArray array) {
        final int[] index = {0};
        return new JSGenerator(context, () -> {
            if (index[0] < array.getLength()) {
                JSValue value = array.get(index[0]++);
                return JSIterator.IteratorResult.of(context, value);
            }
            return JSIterator.IteratorResult.done(context);
        });
    }

    /**
     * Helper to create a simple generator from an iterator function.
     */
    public static JSGenerator fromIteratorFunction(JSContext context, JSIterator.IteratorFunction iteratorFunction) {
        return new JSGenerator(context, iteratorFunction);
    }

    /**
     * Create an iterator result object: { value: any, done: boolean }
     */
    private JSObject createIteratorResult(JSValue value, boolean isDone) {
        JSObject result = context.createJSObject();
        result.set("value", value);
        result.set("done", JSBoolean.valueOf(isDone));
        return result;
    }

    /**
     * Get the current state of the generator.
     */
    public State getState() {
        return state;
    }

    /**
     * Generator.prototype.next(value)
     * Based on QuickJS js_generator_next with GEN_MAGIC_NEXT.
     */
    public JSObject next(JSValue value) {
        if (state == State.EXECUTING) {
            context.throwTypeError("cannot invoke a running generator");
            return createIteratorResult(JSUndefined.INSTANCE, true);
        }

        if (state == State.COMPLETED) {
            return createIteratorResult(JSUndefined.INSTANCE, true);
        }

        State previousState = state;
        state = State.EXECUTING;

        if (generatorState != null) {
            return nextBytecode(value, previousState);
        } else {
            return nextIterator(value);
        }
    }

    private JSObject nextBytecode(JSValue value, State previousState) {
        if (generatorState.isCompleted()) {
            state = State.COMPLETED;
            return createIteratorResult(JSUndefined.INSTANCE, true);
        }

        if (previousState == State.SUSPENDED_YIELD) {
            generatorState.recordResume(JSGeneratorState.ResumeKind.NEXT, value);
        }

        try {
            JSValue yieldValue = context.getVirtualMachine().executeGenerator(generatorState, context);
            if (generatorState.isCompleted()) {
                state = State.COMPLETED;
                return createIteratorResult(yieldValue, true);
            } else {
                state = State.SUSPENDED_YIELD;
                return createIteratorResult(yieldValue, false);
            }
        } catch (Exception e) {
            state = State.COMPLETED;
            generatorState.setCompleted(true);
            if (!context.hasPendingException()) {
                context.throwError(e.getMessage() == null ? "Generator execution failed" : e.getMessage());
            }
            JSValue pendingException = context.getPendingException();
            return createIteratorResult(pendingException != null ? pendingException : JSUndefined.INSTANCE, true);
        }
    }

    private JSObject nextIterator(JSValue value) {
        if (done) {
            state = State.COMPLETED;
            return createIteratorResult(returnValue, true);
        }

        JSIterator.IteratorResult result = iteratorFunction.next();

        if (result.done) {
            done = true;
            state = State.COMPLETED;
            returnValue = result.value;
            return createIteratorResult(result.value, true);
        } else {
            state = State.SUSPENDED_YIELD;
            return createIteratorResult(result.value, false);
        }
    }

    /**
     * Generator.prototype.return(value)
     * Based on QuickJS js_generator_next with GEN_MAGIC_RETURN.
     */
    public JSObject returnMethod(JSValue value) {
        JSValue returnVal = value != null ? value : JSUndefined.INSTANCE;

        if (state == State.EXECUTING) {
            context.throwTypeError("cannot invoke a running generator");
            return createIteratorResult(JSUndefined.INSTANCE, true);
        }

        if (state == State.COMPLETED) {
            return createIteratorResult(returnVal, true);
        }

        if (state == State.SUSPENDED_START) {
            // Generator hasn't started yet - just complete it
            state = State.COMPLETED;
            done = true;
            if (generatorState != null) {
                generatorState.setCompleted(true);
            }
            return createIteratorResult(returnVal, true);
        }

        // Generator is suspended at a yield point
        state = State.COMPLETED;
        done = true;
        if (generatorState != null) {
            // Run to completion so finally blocks execute
            while (!generatorState.isCompleted()) {
                context.getVirtualMachine().executeGenerator(generatorState, context);
            }
        }
        return createIteratorResult(returnVal, true);
    }

    /**
     * Generator.prototype.throw(exception)
     * Based on QuickJS js_generator_next with GEN_MAGIC_THROW.
     */
    public JSObject throwMethod(JSValue exception) {
        if (state == State.EXECUTING) {
            context.throwTypeError("cannot invoke a running generator");
            return createIteratorResult(JSUndefined.INSTANCE, true);
        }

        if (state == State.COMPLETED) {
            context.setPendingException(exception);
            return createIteratorResult(JSUndefined.INSTANCE, true);
        }

        if (state == State.SUSPENDED_START) {
            // Generator hasn't started - complete and throw
            state = State.COMPLETED;
            done = true;
            if (generatorState != null) {
                generatorState.setCompleted(true);
            }
            context.setPendingException(exception);
            return createIteratorResult(JSUndefined.INSTANCE, true);
        }

        if (generatorState != null) {
            state = State.EXECUTING;
            generatorState.recordResume(JSGeneratorState.ResumeKind.THROW, exception);
            try {
                JSValue yieldValue = context.getVirtualMachine().executeGenerator(generatorState, context);
                if (generatorState.isCompleted()) {
                    state = State.COMPLETED;
                    done = true;
                    return createIteratorResult(yieldValue, true);
                }
                state = State.SUSPENDED_YIELD;
                return createIteratorResult(yieldValue, false);
            } catch (Exception e) {
                state = State.COMPLETED;
                done = true;
                generatorState.setCompleted(true);
                if (!context.hasPendingException()) {
                    context.setPendingException(exception);
                }
                return createIteratorResult(JSUndefined.INSTANCE, true);
            }
        }

        // Iterator-backed generator: close and throw into caller.
        state = State.COMPLETED;
        done = true;
        context.setPendingException(exception);
        return createIteratorResult(JSUndefined.INSTANCE, true);
    }

    @Override
    public String toString() {
        return "[object Generator]";
    }

    /**
     * Generator state matching QuickJS JSGeneratorStateEnum.
     */
    public enum State {
        SUSPENDED_START,    // Created but not yet started
        SUSPENDED_YIELD,    // Suspended at a yield expression
        EXECUTING,          // Currently running
        COMPLETED           // Finished execution
    }
}
