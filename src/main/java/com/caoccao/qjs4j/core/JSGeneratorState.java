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

import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.YieldResult;

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
    private boolean awaitSuspended;
    // Execution state that needs to be preserved across yields
    // TODO: Add full state preservation (PC, stack, locals) for proper resumption
    private boolean isCompleted;
    private YieldResult lastYieldResult;
    private ResumeKind pendingResumeKind;
    private JSValue pendingResumeValue;
    private State state;
    private StackFrame suspendedFrame;
    private int suspendedProgramCounter;
    private JSStackValue[] suspendedStackValues;
    private int yieldCount;  // Track how many times we've yielded (workaround for no PC saving)

    public JSGeneratorState(JSBytecodeFunction function, JSValue thisArg, JSValue[] args) {
        this.function = function;
        this.thisArg = thisArg;
        this.args = args;
        this.state = State.SUSPENDED_START;
        this.isCompleted = false;
        this.yieldCount = 0;
        this.resumeRecords = new ArrayList<>();
        this.pendingResumeValue = null;
        this.pendingResumeKind = null;
        this.awaitSuspended = false;
        this.suspendedFrame = null;
        this.suspendedStackValues = null;
        this.suspendedProgramCounter = 0;
    }

    public void clearSuspendedExecutionState() {
        this.suspendedFrame = null;
        this.suspendedProgramCounter = 0;
        this.suspendedStackValues = null;
    }

    public ResumeRecord consumePendingResumeRecord() {
        if (pendingResumeKind == null) {
            return null;
        }
        ResumeRecord resumeRecord = new ResumeRecord(pendingResumeKind, pendingResumeValue);
        pendingResumeKind = null;
        pendingResumeValue = null;
        return resumeRecord;
    }

    public JSValue[] getArgs() {
        return args;
    }

    public JSBytecodeFunction getFunction() {
        return function;
    }

    public YieldResult getLastYieldResult() {
        return lastYieldResult;
    }

    public List<ResumeRecord> getResumeRecords() {
        return Collections.unmodifiableList(resumeRecords);
    }

    public State getState() {
        return state;
    }

    public StackFrame getSuspendedFrame() {
        return suspendedFrame;
    }

    public int getSuspendedProgramCounter() {
        return suspendedProgramCounter;
    }

    public JSStackValue[] getSuspendedStackValues() {
        return suspendedStackValues;
    }

    public JSValue getThisArg() {
        return thisArg;
    }

    public int getYieldCount() {
        return yieldCount;
    }

    public boolean hasPendingResumeRecord() {
        return pendingResumeKind != null;
    }

    public boolean hasSuspendedExecutionState() {
        return suspendedFrame != null && suspendedStackValues != null;
    }

    public void incrementYieldCount() {
        this.yieldCount++;
    }

    public boolean isAwaitSuspended() {
        return awaitSuspended;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void recordResume(ResumeKind kind, JSValue value) {
        resumeRecords.add(new ResumeRecord(kind, value));
    }

    public void saveSuspendedExecutionState(StackFrame frame, int programCounter, JSStackValue[] stackValues) {
        this.suspendedFrame = frame;
        this.suspendedProgramCounter = programCounter;
        this.suspendedStackValues = stackValues;
    }

    public void setAwaitSuspended(boolean awaitSuspended) {
        this.awaitSuspended = awaitSuspended;
    }

    public void setLastYieldResult(YieldResult lastYieldResult) {
        this.lastYieldResult = lastYieldResult;
    }

    public void setCompleted(boolean completed) {
        this.isCompleted = completed;
        if (completed) {
            this.state = State.COMPLETED;
            clearSuspendedExecutionState();
            pendingResumeKind = null;
            pendingResumeValue = null;
            awaitSuspended = false;
            lastYieldResult = null;
        }
    }

    public void setPendingResumeRecord(ResumeKind kind, JSValue value) {
        this.pendingResumeKind = kind;
        this.pendingResumeValue = value;
    }

    public void setState(State state) {
        this.state = state;
    }

    public enum ResumeKind {
        NEXT,
        RETURN,
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
