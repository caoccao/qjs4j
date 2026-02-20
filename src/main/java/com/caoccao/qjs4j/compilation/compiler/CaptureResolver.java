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

package com.caoccao.qjs4j.compilation.compiler;

import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * Manages closure variable captures across nested functions.
 */
final class CaptureResolver {
    private final LinkedHashMap<String, CaptureBinding> capturedBindings;
    private final LocalLookup localLookup;
    private final CaptureResolver parentResolver;

    CaptureResolver(CaptureResolver parentResolver, LocalLookup localLookup) {
        this.parentResolver = parentResolver;
        this.localLookup = localLookup;
        this.capturedBindings = new LinkedHashMap<>();
    }

    Integer findCapturedBindingIndex(String name) {
        CaptureBinding binding = capturedBindings.get(name);
        return binding != null ? binding.slot() : null;
    }

    int getCapturedBindingCount() {
        return capturedBindings.size();
    }

    Collection<CaptureBinding> getCapturedBindings() {
        return capturedBindings.values();
    }

    int registerCapturedBinding(String name, CaptureSource source) {
        CaptureBinding existing = capturedBindings.get(name);
        if (existing != null) {
            return existing.slot();
        }
        int slot = capturedBindings.size();
        capturedBindings.put(name, new CaptureBinding(slot, source));
        return slot;
    }

    CaptureSource resolveCaptureSourceForChild(String name) {
        Integer localIndex = localLookup.findLocal(name);
        if (localIndex != null) {
            return new CaptureSource(CaptureSourceType.LOCAL, localIndex);
        }

        Integer capturedIndex = findCapturedBindingIndex(name);
        if (capturedIndex != null) {
            return new CaptureSource(CaptureSourceType.VAR_REF, capturedIndex);
        }

        if (parentResolver == null) {
            return null;
        }

        CaptureSource parentSource = parentResolver.resolveCaptureSourceForChild(name);
        if (parentSource == null) {
            return null;
        }

        int capturedSlot = registerCapturedBinding(name, parentSource);
        return new CaptureSource(CaptureSourceType.VAR_REF, capturedSlot);
    }

    Integer resolveCapturedBindingIndex(String name) {
        Integer capturedIndex = findCapturedBindingIndex(name);
        if (capturedIndex != null || parentResolver == null) {
            return capturedIndex;
        }
        CaptureSource captureSource = parentResolver.resolveCaptureSourceForChild(name);
        if (captureSource == null) {
            return null;
        }
        return registerCapturedBinding(name, captureSource);
    }

    enum CaptureSourceType {
        LOCAL,
        VAR_REF
    }

    @FunctionalInterface
    interface LocalLookup {
        Integer findLocal(String name);
    }

    record CaptureBinding(int slot, CaptureSource source) {
    }

    record CaptureSource(CaptureSourceType type, int index) {
    }
}
