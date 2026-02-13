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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the execution state of a generator function.
 * Based on QuickJS JSAsyncFunctionState and JSGeneratorData structures.
 * <p>
 * Generators can be suspended at yield points and resumed later.
 * This class stores all the necessary state to resume execution:
 * - Program counter (where to resume)
 * - Value stack (operands)
 * - Local variables
 * - Generator state (SUSPENDED_START, SUSPENDED_YIELD, EXECUTING, COMPLETED)
 */
public final class JSGeneratorState {
    private final JSValue[] args;
    private final JSBytecodeFunction function;
    private final List<ResumeRecord> resumeRecords;
    private final JSValue thisArg;
    // Execution state that needs to be preserved across yields
    // TODO: Add full state preservation (PC, stack, locals) for proper resumption
    private boolean isCompleted;
    private State state;
    private int yieldCount;  // Track how many times we've yielded (workaround for no PC saving)

    public JSGeneratorState(JSBytecodeFunction function, JSValue thisArg, JSValue[] args) {
        this.function = function;
        this.thisArg = thisArg;
        this.args = args;
        this.state = State.SUSPENDED_START;
        this.isCompleted = false;
        this.yieldCount = 0;
        this.resumeRecords = new ArrayList<>();
    }

    public JSValue[] getArgs() {
        return args;
    }

    public JSBytecodeFunction getFunction() {
        return function;
    }

    public List<ResumeRecord> getResumeRecords() {
        return Collections.unmodifiableList(resumeRecords);
    }

    public State getState() {
        return state;
    }

    public JSValue getThisArg() {
        return thisArg;
    }

    public int getYieldCount() {
        return yieldCount;
    }

    public void incrementYieldCount() {
        this.yieldCount++;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void recordResume(ResumeKind kind, JSValue value) {
        resumeRecords.add(new ResumeRecord(kind, value));
    }

    public void setCompleted(boolean completed) {
        this.isCompleted = completed;
        if (completed) {
            this.state = State.COMPLETED;
        }
    }

    public void setState(State state) {
        this.state = state;
    }

    public enum ResumeKind {
        NEXT,
        THROW
    }

    /**
     * Generator state constants matching QuickJS JS_GENERATOR_STATE_*
     */
    public enum State {
        SUSPENDED_START,    // Created but not yet executed (before OP_initial_yield)
        SUSPENDED_YIELD,    // Suspended at a yield point
        EXECUTING,          // Currently executing
        COMPLETED           // Execution finished
    }

    public record ResumeRecord(ResumeKind kind, JSValue value) {
    }
}
