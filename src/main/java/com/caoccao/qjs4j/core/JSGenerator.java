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

import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import com.caoccao.qjs4j.vm.YieldResult;

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
    public static final String NAME = "Generator";
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

    public JSObject completeReturnWithoutResume(JSValue value) {
        JSValue returnValue = value != null ? value : JSUndefined.INSTANCE;
        state = State.COMPLETED;
        done = true;
        if (generatorState != null) {
            generatorState.setCompleted(true);
        }
        return createIteratorResult(returnValue, true);
    }

    /**
     * Create an iterator result object: { value: any, done: boolean }
     */
    private JSObject createIteratorResult(JSValue value, boolean isDone) {
        JSObject result = context.createJSObject();
        result.set(PropertyKey.VALUE, value);
        result.set(PropertyKey.DONE, JSBoolean.valueOf(isDone));
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
            if (generatorState.hasSuspendedExecutionState()) {
                generatorState.setPendingResumeRecord(JSGeneratorState.ResumeKind.NEXT, value);
            }
            generatorState.recordResume(JSGeneratorState.ResumeKind.NEXT, value);
        } else if (previousState == State.SUSPENDED_START && generatorState.hasSuspendedExecutionState()) {
            // Resume after INITIAL_YIELD: the generator saved execution state at
            // INITIAL_YIELD, so set a pending resume record to trigger the
            // suspended-state resume path instead of re-executing from the start.
            generatorState.setPendingResumeRecord(JSGeneratorState.ResumeKind.NEXT, value);
        }

        try {
            JSValue yieldValue = context.getVirtualMachine().executeGenerator(generatorState, context);
            if (generatorState.isAwaitSuspended()) {
                state = State.SUSPENDED_YIELD;
                return createIteratorResult(JSUndefined.INSTANCE, false);
            }
            if (generatorState.isCompleted()) {
                state = State.COMPLETED;
                return createIteratorResult(yieldValue, true);
            } else {
                state = State.SUSPENDED_YIELD;
                // Check if this was a yield* - if so, return the raw iterator result without wrapping
                // This matches QuickJS behavior where *pdone = 2 signals not to wrap the result
                YieldResult lastYield = generatorState.getLastYieldResult();
                if (lastYield != null && lastYield.isYieldStar() && yieldValue instanceof JSObject resultObj) {
                    return resultObj;
                }
                return createIteratorResult(yieldValue, false);
            }
        } catch (Exception e) {
            state = State.COMPLETED;
            generatorState.setCompleted(true);
            if (!context.hasPendingException()) {
                if (e instanceof JSVirtualMachineException vmException) {
                    if (vmException.getJsValue() != null) {
                        context.setPendingException(vmException.getJsValue());
                    } else if (vmException.getJsError() != null) {
                        context.setPendingException(vmException.getJsError());
                    } else {
                        context.throwError(e.getMessage() == null ? "Generator execution failed" : e.getMessage());
                    }
                } else {
                    context.throwError(e.getMessage() == null ? "Generator execution failed" : e.getMessage());
                }
            }
            // Abrupt completion from generator.next() must throw to the caller.
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e);
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
        if (generatorState != null) {
            // Check if suspended at a yield* point - need to handle delegation protocol
            // Following QuickJS js_generator_next with GEN_MAGIC_RETURN:
            // For SUSPENDED_YIELD_STAR, the return value and magic are pushed on the stack,
            // and the yield* bytecode calls iterator.return(value).
            YieldResult lastYield = generatorState.getLastYieldResult();
            if (lastYield != null && lastYield.isYieldStar()) {
                JSObject delegateIterator = lastYield.delegateIterator();
                if (delegateIterator != null) {
                    JSValue returnMethodValue = delegateIterator.get(context, PropertyKey.RETURN);
                    if (context.hasPendingException()) {
                        state = State.COMPLETED;
                        done = true;
                        generatorState.setCompleted(true);
                        throw new JSVirtualMachineException(
                                context.getPendingException().toString(),
                                context.getPendingException());
                    }
                    if (!(returnMethodValue instanceof JSUndefined) && !(returnMethodValue instanceof JSNull)) {
                        if (!(returnMethodValue instanceof JSFunction returnFunction)) {
                            state = State.COMPLETED;
                            done = true;
                            generatorState.setCompleted(true);
                            throw new JSVirtualMachineException(
                                    context.throwTypeError("iterator return is not a function"));
                        }
                        JSValue returnResult = returnFunction.call(context, delegateIterator, new JSValue[]{returnVal});
                        if (context.hasPendingException()) {
                            state = State.COMPLETED;
                            done = true;
                            generatorState.setCompleted(true);
                            throw new JSVirtualMachineException(
                                    context.getPendingException().toString(),
                                    context.getPendingException());
                        }
                        if (!(returnResult instanceof JSObject returnResultObject)) {
                            state = State.COMPLETED;
                            done = true;
                            generatorState.setCompleted(true);
                            throw new JSVirtualMachineException(
                                    context.throwTypeError("iterator must return an object"));
                        }
                        JSValue doneValue = returnResultObject.get(context, PropertyKey.DONE);
                        if (context.hasPendingException()) {
                            state = State.COMPLETED;
                            done = true;
                            generatorState.setCompleted(true);
                            throw new JSVirtualMachineException(
                                    context.getPendingException().toString(),
                                    context.getPendingException());
                        }
                        boolean iteratorDone = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
                        if (context.hasPendingException()) {
                            state = State.COMPLETED;
                            done = true;
                            generatorState.setCompleted(true);
                            throw new JSVirtualMachineException(
                                    context.getPendingException().toString(),
                                    context.getPendingException());
                        }
                        if (iteratorDone) {
                            JSValue iteratorValue = returnResultObject.get(context, PropertyKey.VALUE);
                            if (context.hasPendingException()) {
                                state = State.COMPLETED;
                                done = true;
                                generatorState.setCompleted(true);
                                throw new JSVirtualMachineException(
                                        context.getPendingException().toString(),
                                        context.getPendingException());
                            }
                            state = State.COMPLETED;
                            done = true;
                            generatorState.setCompleted(true);
                            return createIteratorResult(iteratorValue, true);
                        }
                        state = State.SUSPENDED_YIELD;
                        return returnResultObject;
                    }
                }
                // The replay-based generator implementation replays an earlier yield point
                // before reaching the delegated yield* resume site. Preserve RETURN for the
                // later yield* handler by consuming a dummy NEXT during replay.
                generatorState.recordResume(JSGeneratorState.ResumeKind.NEXT, JSUndefined.INSTANCE);
                generatorState.recordResume(JSGeneratorState.ResumeKind.RETURN, returnVal);
                state = State.EXECUTING;
                try {
                    JSValue yieldValue = context.getVirtualMachine().executeGenerator(generatorState, context);
                    if (generatorState.isCompleted()) {
                        state = State.COMPLETED;
                        done = true;
                        return createIteratorResult(yieldValue, true);
                    } else {
                        // Inner iterator returned done:false - continue delegation
                        state = State.SUSPENDED_YIELD;
                        YieldResult newYield = generatorState.getLastYieldResult();
                        if (newYield != null && newYield.isYieldStar() && yieldValue instanceof JSObject resultObj) {
                            return resultObj;
                        }
                        return createIteratorResult(yieldValue, false);
                    }
                } catch (Exception e) {
                    state = State.COMPLETED;
                    done = true;
                    generatorState.setCompleted(true);
                    throw e;
                }
            }

            // Regular yield - resume with RETURN semantics
            // The VM will trigger force return, skipping code after yield,
            // skipping catch handlers, but executing finally handlers.
            state = State.EXECUTING;
            if (generatorState.hasSuspendedExecutionState()) {
                generatorState.setPendingResumeRecord(JSGeneratorState.ResumeKind.RETURN, returnVal);
            }
            generatorState.recordResume(JSGeneratorState.ResumeKind.RETURN, returnVal);

            try {
                JSValue result = context.getVirtualMachine().executeGenerator(generatorState, context);
                if (generatorState.isCompleted()) {
                    state = State.COMPLETED;
                    done = true;
                    // Use the actual result, not returnVal, because a return statement
                    // in a finally block can override the return value
                    return createIteratorResult(result, true);
                } else {
                    // Generator yielded during a finally block - suspend there
                    state = State.SUSPENDED_YIELD;
                    return createIteratorResult(result, false);
                }
            } catch (Exception e) {
                state = State.COMPLETED;
                done = true;
                generatorState.setCompleted(true);
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(e);
            }
        } else {
            state = State.COMPLETED;
            done = true;
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
            if (generatorState.hasSuspendedExecutionState()) {
                generatorState.setPendingResumeRecord(JSGeneratorState.ResumeKind.THROW, exception);
            } else {
                generatorState.recordResume(JSGeneratorState.ResumeKind.THROW, exception);
            }
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
                    if (e instanceof JSVirtualMachineException virtualMachineException) {
                        if (virtualMachineException.getJsValue() != null) {
                            context.setPendingException(virtualMachineException.getJsValue());
                        } else if (virtualMachineException.getJsError() != null) {
                            context.setPendingException(virtualMachineException.getJsError());
                        } else {
                            context.throwError(
                                    e.getMessage() == null ? "Generator execution failed" : e.getMessage());
                        }
                    } else {
                        context.setPendingException(exception);
                    }
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
