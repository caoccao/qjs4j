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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code SharedArrayBuffer} is shared across agents by definition, and the Test262 agent host
 * hands the same instance to other runtimes, so its mutable length is read and written from several
 * threads. It was a plain {@code int} behind an unsynchronised check-then-write: two agents could
 * read the same old length, ask for 50 and 100, and write 100 then 50 — both calls reporting
 * success while the buffer observably shrank — and a new length had no happens-before edge that
 * made it visible to another thread at all.
 */
public class JSSharedArrayBufferGrowthTest extends BaseJavetTest {
    @Test
    public void testGrowBeyondTheMaximumIsARangeError() {
        assertStringWithJavet("""
                const s = new SharedArrayBuffer(8, { maxByteLength: 64 });
                try { s.grow(128); 'grown'; } catch (e) { e.constructor.name; }""");
    }

    @Test
    public void testGrowIsMonotonic() {
        assertStringWithJavet("""
                const s = new SharedArrayBuffer(8, { maxByteLength: 64 });
                s.grow(32);
                let shrank = 'rejected';
                try { s.grow(16); shrank = 'accepted'; } catch (e) { shrank = e.constructor.name; }
                [s.byteLength, shrank].join(',');""");
    }

    @Test
    public void testGrowToTheSameLengthIsAllowed() {
        assertStringWithJavet("""
                const s = new SharedArrayBuffer(8, { maxByteLength: 64 });
                s.grow(8);
                String(s.byteLength);""");
    }

    @Test
    public void testNonGrowableBufferRejectsGrow() {
        assertStringWithJavet("""
                const s = new SharedArrayBuffer(8);
                try { s.grow(16); 'grown'; } catch (e) { e.constructor.name; }""");
    }

    /**
     * Race several agents growing the same buffer to different targets from one barrier.
     * <p>
     * Not compared against V8: Javet drives a single agent, and the defect only exists between
     * threads. What is asserted is the specification's own guarantee — growth is monotonic — under
     * an interleaving that used to break it.
     */
    @Test
    @Timeout(60)
    public void testRacingAgentsNeverSeeTheBufferShrink() throws InterruptedException {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            List<Integer> targets = List.of(64, 128, 256, 512, 1024, 2048, 4096, 8192);
            for (int attempt = 0; attempt < 50; attempt++) {
                JSSharedArrayBuffer buffer = new JSSharedArrayBuffer(context, 8, 8192);
                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch finished = new CountDownLatch(targets.size());
                AtomicInteger observedShrinks = new AtomicInteger();
                AtomicInteger largestAccepted = new AtomicInteger(8);
                for (int target : targets) {
                    Thread agent = new Thread(() -> {
                        try {
                            start.await();
                            int before = buffer.getByteLength();
                            try {
                                buffer.grow(target);
                                largestAccepted.accumulateAndGet(target, Math::max);
                            } catch (RuntimeException rejected) {
                                // A target below the length this agent already observed is
                                // correctly refused rather than shrinking the buffer.
                            }
                            if (buffer.getByteLength() < before) {
                                observedShrinks.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            finished.countDown();
                        }
                    }, "grow-agent-" + target);
                    agent.setDaemon(true);
                    agent.start();
                }
                start.countDown();
                assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
                assertThat(observedShrinks.get())
                        .as("attempt " + attempt + ": a growable SharedArrayBuffer must never shrink")
                        .isZero();
                assertThat(buffer.getByteLength())
                        .as("attempt " + attempt + ": the final length is the largest accepted target")
                        .isEqualTo(largestAccepted.get());
            }
        }
    }
}
