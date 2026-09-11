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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.core.JSArray;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSValue;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs collection assertions in a small JVM that the parent test can terminate if collection stalls.
 */
public final class ConstantObjectGcProbe {
    private static WeakReference<JSValue> evaluateTemplate(JSContext context, int index) {
        // Keep the strong reference in a separate frame, away from the collection loop.
        JSValue value = context.eval("tag`payload" + index + " ${1} ${2} ${3}`");
        if (!(value instanceof JSArray)) {
            throw new AssertionError("Expected a template object, got " + value);
        }
        return new WeakReference<>(value);
    }

    public static void main(String[] args) throws InterruptedException {
        int count = Integer.parseInt(args[0]);
        try (JSRuntime runtime = new JSRuntime(); JSContext context = runtime.createContext()) {
            context.eval("function tag(strings) { return strings }");
            List<WeakReference<JSValue>> references = new ArrayList<>();
            System.out.println("Evaluating " + count + " distinct template objects");
            for (int index = 0; index < count; index++) {
                references.add(evaluateTemplate(context, index));
            }
            // Replace the last tag call's cached state with an unsampled template, then release
            // the most recently compiled program. The VM may retain that one last call.
            context.eval("tag`release ${0}`");
            for (int index = 0; index < 20; index++) {
                context.eval("1");
            }
            long live = count;
            for (int attempt = 0; attempt < 20 && live > 0; attempt++) {
                System.out.println("Collecting, attempt " + (attempt + 1));
                System.gc();
                Thread.sleep(25);
                // Unlike get(), refersTo() cannot temporarily keep a referent alive.
                live = references.stream().filter(reference -> !reference.refersTo(null)).count();
            }
            Reference.reachabilityFence(context);
            if (live != 0) {
                throw new AssertionError(live + " of " + count + " template objects outlived their bytecode");
            }
            System.out.println("All " + count + " template objects were collected while the VM remained alive");
        }
    }
}
