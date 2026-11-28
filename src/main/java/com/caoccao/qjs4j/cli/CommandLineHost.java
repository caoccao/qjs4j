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

package com.caoccao.qjs4j.cli;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The host functions the command-line interpreter adds for scripts written for Node or browsers:
 * {@code queueMicrotask}, {@code setTimeout}, {@code setInterval}, {@code clearTimeout} and {@code clearInterval}.
 * <p>
 * They are host APIs, not ECMAScript, so the engine has none of them (QuickJS's {@code qjs} offers a timer only through
 * its {@code os} module). Timers run after the script and its promise jobs, earliest first and in creation order when
 * due together, each followed by the jobs it queued; the interpreter exits once none is left. Delays follow Node: a
 * delay that is not a number from 1 to 2<sup>31</sup> − 1 milliseconds is 1. An exception escaping a callback is
 * uncaught, as in Node: it ends the run, and no job or timer queued behind it runs.
 */
final class CommandLineHost {
    private static final double MAX_DELAY_MILLIS = Integer.MAX_VALUE;
    private static final String UNCAUGHT_MICROTASK_ERROR = "uncaught exception in a queueMicrotask callback";
    private final Map<Integer, Timer> activeTimers;
    private final LongSupplier clock;
    private final JSContext context;
    private int nextTimerId;
    private long nextTimerSequence;
    private final NavigableSet<Timer> timerQueue;
    /**
     * What a {@code queueMicrotask} callback threw, from when it ends the drain until it is reported.
     */
    private JSValue uncaughtMicrotaskError;

    CommandLineHost(JSContext context) {
        this(context, System::nanoTime);
    }

    /**
     * Create the host layer with its own clock, so a test can make timers fall due together.
     *
     * @param context
     *            the context to install into
     * @param clock
     *            the time in nanoseconds
     */
    CommandLineHost(JSContext context, LongSupplier clock) {
        this.context = context;
        this.clock = clock;
        activeTimers = new HashMap<>();
        nextTimerId = 1;
        nextTimerSequence = 0;
        timerQueue = new TreeSet<>(
                Comparator.comparingLong((Timer timer) -> timer.dueNanos).thenComparingLong(timer -> timer.sequence));
        uncaughtMicrotaskError = null;
    }

    private JSValue clearTimer(JSValue[] args) {
        if (args.length > 0 && args[0] instanceof JSNumber number) {
            Timer timer = activeTimers.remove((int) number.value());
            if (timer != null) {
                timerQueue.remove(timer);
                timer.cancelled = true;
            }
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Evaluate the script. Evaluation drains the jobs the script queued, so a {@code queueMicrotask} callback that
     * throws can end it.
     *
     * @param source
     *            the source code
     * @param sourceName
     *            the file name for stack traces
     * @param moduleMode
     *            whether to evaluate the source as a module
     * @throws JSException
     *             when the script, or a {@code queueMicrotask} callback it queued, threw
     */
    void eval(String source, String sourceName, boolean moduleMode) {
        try {
            context.eval(source, sourceName, moduleMode);
        } catch (JSTerminationException e) {
            throw uncaughtMicrotaskError(e);
        }
    }

    /**
     * Install the functions on the context's global object.
     */
    void install() {
        JSObject global = context.getGlobalObject();
        global.set(PropertyKey.fromString("queueMicrotask"),
                new JSNativeFunction(context, "queueMicrotask", 1, (ctx, thisArg, args) -> queueMicrotask(args)));
        global.set(PropertyKey.fromString("setTimeout"),
                new JSNativeFunction(context, "setTimeout", 2, (ctx, thisArg, args) -> setTimer(args, false)));
        global.set(PropertyKey.fromString("setInterval"),
                new JSNativeFunction(context, "setInterval", 2, (ctx, thisArg, args) -> setTimer(args, true)));
        global.set(PropertyKey.fromString("clearTimeout"),
                new JSNativeFunction(context, "clearTimeout", 1, (ctx, thisArg, args) -> clearTimer(args)));
        global.set(PropertyKey.fromString("clearInterval"),
                new JSNativeFunction(context, "clearInterval", 1, (ctx, thisArg, args) -> clearTimer(args)));
    }

    /**
     * Call a callback from the host, as a job: with no caller to receive it, an exception it throws is returned.
     *
     * @return the thrown value, or null when the callback returned normally
     */
    private JSValue invoke(JSFunction callback, JSValue[] args) {
        JSValue thrown = null;
        try {
            callback.call(context, JSUndefined.INSTANCE, args);
        } catch (JSVirtualMachineException e) {
            if (e.getJsValue() == null) {
                // An engine failure, not a JavaScript exception: the interpreter reports it as one.
                throw e;
            }
            thrown = e.getJsValue();
        }
        if (context.hasPendingException()) {
            thrown = context.takePendingException();
        }
        return thrown;
    }

    private JSValue queueMicrotask(JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("The \"callback\" argument must be of type function");
        }
        context.enqueueMicrotask(() -> {
            JSValue error = invoke(callback, JSValue.NO_ARGS);
            if (error != null) {
                // Fatal, as in Node, so the drain must end here: an error recorded for later let every job behind
                // it run first, and one that kept requeueing itself kept the error from ever being reported. The
                // drain records a RuntimeException and goes on; termination is the signal it lets through.
                uncaughtMicrotaskError = error;
                throw new JSTerminationException(UNCAUGHT_MICROTASK_ERROR);
            }
        });
        return JSUndefined.INSTANCE;
    }

    /**
     * Run the host jobs and promise jobs the last step queued.
     *
     * @throws JSException
     *             when a {@code queueMicrotask} callback threw
     */
    void runJobs() {
        try {
            context.getRuntime().runJobs();
            context.processMicrotasks();
        } catch (JSTerminationException e) {
            throw uncaughtMicrotaskError(e);
        }
    }

    /**
     * Run timers until none is left, each followed by the jobs it queued.
     *
     * @param continueAfterTurn
     *            asked after each timer and its jobs whether to go on
     * @return false when stopped by {@code continueAfterTurn}
     * @throws JSException
     *             when a callback threw, or a {@code queueMicrotask} callback that ran during one
     */
    boolean runTimers(BooleanSupplier continueAfterTurn) {
        while (true) {
            Timer timer = timerQueue.pollFirst();
            if (timer == null) {
                return true;
            }
            if (timer.cancelled) {
                continue;
            }
            long waitNanos = timer.dueNanos - clock.getAsLong();
            if (waitNanos > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(waitNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            }
            if (timer.intervalNanos > 0) {
                // Rescheduled before the callback runs, so the callback can clear its own interval.
                timer.dueNanos = Math.max(clock.getAsLong(), timer.dueNanos) + timer.intervalNanos;
                timer.sequence = nextTimerSequence++;
                timerQueue.add(timer);
            } else {
                activeTimers.remove(timer.id);
            }
            JSValue error;
            try {
                error = invoke(timer.callback, timer.args);
            } catch (JSTerminationException e) {
                // The callback drained the microtask queue itself, through eval, and a queueMicrotask callback threw.
                throw uncaughtMicrotaskError(e);
            }
            if (error != null) {
                throw new JSException(error);
            }
            runJobs();
            if (!continueAfterTurn.getAsBoolean()) {
                return false;
            }
        }
    }

    private JSValue setTimer(JSValue[] args, boolean repeat) {
        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("The \"callback\" argument must be of type function");
        }
        double delayMillis = args.length > 1 ? JSTypeConversions.toDouble(context, args[1]) : 1;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(delayMillis >= 1 && delayMillis <= MAX_DELAY_MILLIS)) {
            delayMillis = 1;
        }
        long delayNanos = TimeUnit.MICROSECONDS.toNanos((long) (delayMillis * 1000));
        Timer timer = new Timer(nextTimerId++, callback,
                args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : JSValue.NO_ARGS,
                clock.getAsLong() + delayNanos, repeat ? delayNanos : 0, nextTimerSequence++);
        activeTimers.put(timer.id, timer);
        timerQueue.add(timer);
        return JSNumber.of(timer.id);
    }

    /**
     * The exception a drain ended by a {@code queueMicrotask} callback reports: the callback's own error.
     *
     * @param termination
     *            the termination that ended the drain
     * @return the callback's error, as an uncaught exception
     * @throws JSTerminationException
     *             the termination itself, when the host did not raise it, as for an interrupt
     */
    private JSException uncaughtMicrotaskError(JSTerminationException termination) {
        JSValue error = uncaughtMicrotaskError;
        if (error == null) {
            throw termination;
        }
        uncaughtMicrotaskError = null;
        return new JSException(error);
    }

    private static final class Timer {
        private final JSValue[] args;
        private final JSFunction callback;
        private boolean cancelled;
        private long dueNanos;
        private final int id;
        private final long intervalNanos;
        private long sequence;

        private Timer(int id, JSFunction callback, JSValue[] args, long dueNanos, long intervalNanos, long sequence) {
            this.id = id;
            this.callback = callback;
            this.args = args;
            this.dueNanos = dueNanos;
            this.intervalNanos = intervalNanos;
            this.sequence = sequence;
            cancelled = false;
        }
    }
}
