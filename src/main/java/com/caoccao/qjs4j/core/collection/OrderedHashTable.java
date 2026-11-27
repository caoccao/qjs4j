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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/** Insertion-ordered storage with constant-space live cursors and no retained deleted values. */
final class OrderedHashTable<T> {
    private final Map<JSMap.KeyWrapper, Node<T>> entries = new HashMap<>();
    private Node<T> head = new Node<>();
    private Node<T> tail = head;

    void clear() {
        for (Node<T> node : entries.values()) {
            node.value = null;
            node.previous = null;
        }
        entries.clear();
        head = tail;
        tail.previous = null;
    }

    boolean containsKey(JSMap.KeyWrapper key) {
        return entries.containsKey(key);
    }

    Cursor<T> cursor() {
        return new Cursor<>(head);
    }

    T get(JSMap.KeyWrapper key) {
        Node<T> node = entries.get(key);
        return node == null ? null : node.value;
    }

    void put(JSMap.KeyWrapper key, T value) {
        Node<T> node = entries.get(key);
        if (node != null) {
            node.value = value;
            return;
        }
        // The empty tail is a stable insertion frontier for cursors created on an empty table.
        node = tail;
        node.value = value;
        tail = new Node<>();
        tail.previous = node;
        node.next = tail;
        entries.put(key, node);
    }

    T remove(JSMap.KeyWrapper key) {
        Node<T> node = entries.remove(key);
        if (node == null) {
            return null;
        }
        T value = node.value;
        node.value = null;
        if (node.previous == null) {
            head = node.next;
        } else {
            node.previous.next = node.next;
        }
        node.next.previous = node.previous;
        node.previous = null;
        // Keep the forward link: a cursor parked on this deleted node must reach later insertions.
        return value;
    }

    int size() {
        return entries.size();
    }

    Iterable<T> values() {
        return () -> new Iterator<>() {
            private final Cursor<T> cursor = cursor();
            private T next = cursor.next();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public T next() {
                if (next == null) {
                    throw new NoSuchElementException();
                }
                T value = next;
                next = cursor.next();
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
            while (next != null) {
                Node<T> node = next;
                next = node.next;
                if (node.value != null) {
                    return node.value;
                }
            }
            return null;
        }
    }

    private static final class Node<T> {
        private Node<T> next;
        private Node<T> previous;
        private T value;
    }
}
