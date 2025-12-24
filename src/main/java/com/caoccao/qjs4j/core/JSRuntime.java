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

import com.caoccao.qjs4j.memory.GarbageCollector;
import com.caoccao.qjs4j.util.AtomTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Represents a JavaScript runtime environment.
 * Manages contexts, garbage collection, atom table, and job queue.
 */
public final class JSRuntime {
    private final List<JSContext> contexts;
    private final GarbageCollector gc;
    private final AtomTable atoms;
    private final Queue<Job> jobQueue;

    public JSRuntime() {
        this.contexts = new ArrayList<>();
        this.gc = new GarbageCollector();
        this.atoms = new AtomTable();
        this.jobQueue = new ConcurrentLinkedQueue<>();
    }

    public JSContext createContext() {
        JSContext context = new JSContext(this);
        contexts.add(context);
        return context;
    }

    public void runJobs() {
        while (!jobQueue.isEmpty()) {
            Job job = jobQueue.poll();
            if (job != null) {
                job.run();
            }
        }
    }

    public void enqueueJob(Job job) {
        jobQueue.offer(job);
    }

    public AtomTable getAtoms() {
        return atoms;
    }

    public GarbageCollector getGarbageCollector() {
        return gc;
    }

    @FunctionalInterface
    public interface Job {
        void run();
    }
}
