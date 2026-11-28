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

package com.caoccao.qjs4j.core.collection;

import com.caoccao.qjs4j.core.JSValue;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/** Insertion-ordered storage with constant-space live cursors and no retained deleted values. */
final class OrderedHashTable<T> {
    private final Map<JSMap.KeyWrapper, Node<T>> entries = new HashMap<>();
    private Node<T> head = new Node<>();
    private Node<T> tail = head;

    private void append(JSMap.KeyWrapper key, T value) {
        // The empty tail is a stable insertion frontier for cursors created on an empty table.
        Node<T> node = tail;
        node.key = key;
        node.value = value;
        tail = new Node<>();
        tail.previous = node;
        node.next = tail;
        entries.put(key, node);
    }

    void clear() {
        for (Node<T> node : entries.values()) {
            node.key = null;
            node.value = null;
            node.previous = null;
        }
        entries.clear();
        head = tail;
        tail.previous = null;
    }

    boolean containsKey(JSValue key) {
        return entries.containsKey(JSMap.KeyWrapper.forCollection(key));
    }

    Cursor<T> cursor() {
        return new Cursor<>(head);
    }

    T get(JSMap.KeyWrapper key) {
        return getOrDefault(key, null);
    }

    T getOrDefault(JSMap.KeyWrapper key, T defaultValue) {
        Node<T> node = entries.get(key);
        return node == null ? defaultValue : node.value;
    }

    List<JSMap.KeyWrapper> keys() {
        List<JSMap.KeyWrapper> keys = new ArrayList<>(size());
        for (Node<T> node = head; node != tail; node = node.next) {
            keys.add(node.key);
        }
        return keys;
    }

    void put(JSMap.KeyWrapper key, T value) {
        Node<T> node = entries.get(key);
        if (node != null) {
            node.value = value;
            return;
        }
        append(key, value);
    }

    void putIfAbsent(JSMap.KeyWrapper key, T value) {
        if (!entries.containsKey(key)) {
            append(key, value);
        }
    }

    boolean remove(JSValue key) {
        Node<T> node = entries.remove(JSMap.KeyWrapper.forCollection(key));
        if (node == null) {
            return false;
        }
        node.key = null;
        node.value = null;
        if (node.previous == null) {
            head = node.next;
        } else {
            node.previous.next = node.next;
        }
        node.next.previous = node.previous;
        node.previous = null;
        // Keep the forward link: a cursor parked on this deleted node must reach later insertions.
        return true;
    }

    int size() {
        return entries.size();
    }

    Iterable<T> values() {
        return () -> new Iterator<>() {
            private final Cursor<T> cursor = cursor();
            private Node<T> next = cursor.nextEntry();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public T next() {
                if (next == null) {
                    throw new NoSuchElementException();
                }
                T value = next.value;
                next = cursor.nextEntry();
                return value;
            }
        };
    }

    static final class Cursor<T> {
        private Node<T> next;

        private Cursor(Node<T> next) {
            this.next = next;
        }

        T next() {
            Node<T> node = nextEntry();
            return node == null ? null : node.value;
        }

        Node<T> nextEntry() {
            while (next != null) {
                Node<T> node = next;
                next = node.next;
                if (node.key != null) {
                    return node;
                }
            }
            return null;
        }
    }

    static final class Node<T> {
        private JSMap.KeyWrapper key;

        private Node<T> next;

        private Node<T> previous;
        private T value;
        JSMap.KeyWrapper key() {
            return key;
        }

        T value() {
            return value;
        }
    }
}
