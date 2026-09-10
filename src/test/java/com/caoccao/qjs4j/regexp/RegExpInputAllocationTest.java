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

package com.caoccao.qjs4j.regexp;

import com.caoccao.qjs4j.BaseTest;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class RegExpInputAllocationTest extends BaseTest {
    @Test
    public void testRepeatedStickyLookaheadsDoNotCopyTheSubject() {
        ThreadMXBean threads = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        assumeTrue(threads != null && threads.isThreadAllocatedMemorySupported()
                && threads.isThreadAllocatedMemoryEnabled(), "thread allocation accounting is unavailable");
        RegExpEngine engine = new RegExpEngine(
                new RegExpCompiler(context.getUnicodePropertyResolver()).compile("(?=x)", "y"));
        for (int i = 0; i < 100; i++) {
            engine.exec("x", 0);
        }
        String subject = "x".repeat(1_000_000);
        RegExpEngine.MatchResult[] matches = new RegExpEngine.MatchResult[100];
        long threadId = Thread.currentThread().getId();
        long allocatedBefore = threads.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < matches.length; i++) {
            matches[i] = engine.exec(subject, 900_000 + i);
        }
        long allocatedBytes = threads.getThreadAllocatedBytes(threadId) - allocatedBefore;
        for (int i = 0; i < matches.length; i++) {
            assertThat(matches[i]).isNotNull();
            assertThat(matches[i].startIndex()).isEqualTo(900_000 + i);
            assertThat(matches[i].endIndex()).isEqualTo(900_000 + i);
            assertThat(matches[i].getMatch()).isEmpty();
        }
        // The split loop uses sticky lookaheads like these. Copying the full subject in both
        // the outer match and its assertion used 800 MB for these 100 zero-width matches.
        // Allow ample space for match state while rejecting allocation proportional to the subject.
        assertThat(allocatedBytes).as("allocation for 100 sticky lookaheads").isLessThan(8L * 1024 * 1024);
    }
}
