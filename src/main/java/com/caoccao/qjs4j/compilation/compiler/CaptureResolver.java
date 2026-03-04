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
    private final BindingLookup bindingLookup;
    private final LinkedHashMap<String, CaptureBinding> capturedBindings;
    private final CaptureResolver parentResolver;

    CaptureResolver(CaptureResolver parentResolver, BindingLookup bindingLookup) {
        this.parentResolver = parentResolver;
        this.bindingLookup = bindingLookup;
        this.capturedBindings = new LinkedHashMap<>();
    }

    Integer findCapturedBindingIndex(String name) {
        CaptureBinding binding = capturedBindings.get(name);
        return binding != null ? binding.slot() : null;
    }

    int getCapturedBindingCount() {
        return capturedBindings.size();
    }

    String[] getCapturedBindingNamesBySlot() {
        if (capturedBindings.isEmpty()) {
            return null;
        }
        String[] capturedBindingNames = new String[capturedBindings.size()];
        for (var entry : capturedBindings.entrySet()) {
            capturedBindingNames[entry.getValue().slot()] = entry.getKey();
        }
        return capturedBindingNames;
    }

    Collection<CaptureBinding> getCapturedBindings() {
        return capturedBindings.values();
    }

    boolean isCapturedBindingImmutable(String name) {
        CaptureBinding captureBinding = capturedBindings.get(name);
        if (captureBinding != null) {
            return captureBinding.source().immutable();
        }
        if (parentResolver == null) {
            return false;
        }
        Integer capturedIndex = resolveCapturedBindingIndex(name);
        if (capturedIndex == null) {
            return false;
        }
        CaptureBinding resolvedBinding = capturedBindings.get(name);
        return resolvedBinding != null && resolvedBinding.source().immutable();
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
        BindingInfo bindingInfo = bindingLookup.findBinding(name);
        if (bindingInfo != null) {
            return new CaptureSource(CaptureSourceType.LOCAL, bindingInfo.index(), bindingInfo.immutable());
        }

        Integer capturedIndex = findCapturedBindingIndex(name);
        if (capturedIndex != null) {
            CaptureBinding captureBinding = capturedBindings.get(name);
            return new CaptureSource(
                    CaptureSourceType.VAR_REF,
                    capturedIndex,
                    captureBinding != null && captureBinding.source().immutable());
        }

        if (parentResolver == null) {
            return null;
        }

        CaptureSource parentSource = parentResolver.resolveCaptureSourceForChild(name);
        if (parentSource == null) {
            return null;
        }

        int capturedSlot = registerCapturedBinding(name, parentSource);
        return new CaptureSource(CaptureSourceType.VAR_REF, capturedSlot, parentSource.immutable());
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
    interface BindingLookup {
        BindingInfo findBinding(String name);
    }

    record BindingInfo(int index, boolean immutable) {
    }

    record CaptureBinding(int slot, CaptureSource source) {
    }

    record CaptureSource(CaptureSourceType type, int index, boolean immutable) {
    }
}
