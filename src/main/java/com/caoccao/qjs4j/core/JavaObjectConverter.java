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

import java.util.*;

/**
 * {@link JSValue#toJavaObject()} for an object graph: without recursion, and with every object converted once.
 * <p>
 * Objects and arrays used to convert themselves recursively, so a cycle ({@code c.self = c}) or a deep nest overflowed
 * the Java stack with a raw {@link StackOverflowError} — a public API with no VM frame around it to turn that into a
 * {@code RangeError}. Dates, maps, sets and regular expressions also came out as empty maps. Here the graph is read
 * from a work list first, every object once, and the Java containers are created and filled afterwards, so a revisit
 * yields the same Java object and the depth costs heap rather than stack.
 * <ul>
 * <li>array → {@link List}, integral numbers as {@link Integer}/{@link Long} as before</li>
 * <li>object → {@link LinkedHashMap} of string keys, entries whose value converts to null left out as before</li>
 * <li>{@code Map} → {@link LinkedHashMap}, {@code Set} → {@link LinkedHashSet}, integral numbers as for arrays</li>
 * <li>{@code Date} → {@link java.time.ZonedDateTime} in the system time zone, as Javet converts it, or null for an
 * invalid date</li>
 * <li>{@code RegExp} → its {@code /source/flags} text</li>
 * <li>any other object whose class converts itself (functions, primitive wrappers) → its own conversion</li>
 * </ul>
 * <p>
 * A map key or set element is hashed by value, as Java's hashed collections hash it, so everything it reaches must be
 * complete before it is inserted: maps and sets are populated last, each after every container its keys reach. A key
 * converts to a container whose {@code hashCode} and {@code equals} keep the {@link List}, {@link Set} or {@link Map}
 * contract but walk its contents with a work list, so a key of any depth hashes on any stack; the collections' own
 * recursive methods overflowed a small stack a few hundred levels down. A key whose contents lead into a cycle cannot
 * be hashed by value at all — its hash code would never finish — so it converts to a container that hashes and compares
 * by identity, the way JavaScript compares object keys. A cyclic key used to throw a raw {@link StackOverflowError},
 * and a key inserted before its own contents were complete could not be found afterwards.
 */
final class JavaObjectConverter {
    /**
     * Whether a class leaves {@code toJavaObject()} to this converter: it does unless it overrides the method below
     * {@link JSObject} and {@link JSArray}.
     */
    private static final ClassValue<Boolean> CONVERTED_HERE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Class<?> declaringClass = type.getMethod("toJavaObject").getDeclaringClass();
                return declaringClass == JSObject.class || declaringClass == JSArray.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };
    /**
     * The Java object for every JavaScript object met: a leaf's conversion as soon as it is met, a container's once the
     * graph has been read.
     */
    private final IdentityHashMap<JSObject, Object> converted = new IdentityHashMap<>();
    /**
     * The objects used as a map key or a set element.
     */
    private final Set<JSObject> hashedMembers = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * The hash codes worked out while maps and sets are filled, shared by every key: their contents no longer change,
     * so a key nested in a key is not hashed again at every level.
     */
    private final IdentityHashMap<Object, Integer> keyHashes = new IdentityHashMap<>();
    private final IdentityHashMap<JSObject, Node> nodeOf = new IdentityHashMap<>();
    /**
     * The containers, in the order they were met.
     */
    private final List<Node> nodes = new ArrayList<>();
    private final ArrayDeque<Node> pending = new ArrayDeque<>();

    private JavaObjectConverter() {
    }

    private void addMember(Node node, List<JSValue> list, JSValue member) {
        list.add(member);
        discover(member);
        link(node, member);
        if (member instanceof JSObject object) {
            hashedMembers.add(object);
        }
    }

    private void addNode(JSObject object, Kind kind) {
        Node node = new Node(object, kind);
        nodeOf.put(object, node);
        nodes.add(node);
        pending.add(node);
    }

    private void addValue(Node node, JSValue value) {
        node.values.add(value);
        discover(value);
        link(node, value);
    }

    /**
     * Create every container, empty: one that is a key hashes by value without recursion, or by identity when a cycle
     * can be reached from it.
     */
    private void createContainers() {
        for (Node node : nodes) {
            Object container;
            if (!hashedMembers.contains(node.object)) {
                container = switch (node.kind) {
                    case ARRAY -> new ArrayList<>();
                    case MAP, OBJECT -> new LinkedHashMap<>();
                    case SET -> new LinkedHashSet<>();
                };
            } else {
                container = switch (node.kind) {
                    case ARRAY -> node.reachesCycle ? new IdentityHashedList() : new ValueHashedList();
                    case MAP, OBJECT -> node.reachesCycle ? new IdentityHashedMap() : new ValueHashedMap();
                    case SET -> node.reachesCycle ? new IdentityHashedSet() : new ValueHashedSet();
                };
            }
            converted.put(node.object, container);
        }
    }

    private void discover(JSValue value) {
        if (!(value instanceof JSObject object) || converted.containsKey(object) || nodeOf.containsKey(object)) {
            return;
        }
        if (object instanceof JSDate date) {
            converted.put(object, date.getLocalZonedDateTime());
        } else if (object instanceof JSRegExp regExp) {
            converted.put(object, "/" + regExp.getPattern() + "/" + regExp.getFlags());
        } else if (object instanceof JSMap) {
            addNode(object, Kind.MAP);
        } else if (object instanceof JSSet) {
            addNode(object, Kind.SET);
        } else if (!CONVERTED_HERE.get(object.getClass())) {
            converted.put(object, object.toJavaObject());
        } else {
            addNode(object, object instanceof JSArray ? Kind.ARRAY : Kind.OBJECT);
        }
    }

    @SuppressWarnings("unchecked")
    private void fillListsAndObjects() {
        for (Node node : nodes) {
            if (node.kind == Kind.ARRAY) {
                List<Object> list = (List<Object>) converted.get(node.object);
                for (JSValue element : node.values) {
                    list.add(memberValue(element));
                }
            } else if (node.kind == Kind.OBJECT) {
                Map<Object, Object> map = (Map<Object, Object>) converted.get(node.object);
                for (int i = 0; i < node.values.size(); i++) {
                    JSValue value = node.values.get(i);
                    Object javaValue = value instanceof JSObject object ? converted.get(object) : value.toJavaObject();
                    if (javaValue != null) {
                        map.put(node.propertyNames.get(i), javaValue);
                    }
                }
            }
        }
    }

    /**
     * Populate the maps and sets, a component of the graph after every component it reaches, so that a key hashed by
     * value is complete — every container it reaches filled — before it is inserted.
     */
    @SuppressWarnings("unchecked")
    private void fillMapsAndSets(List<List<Node>> components) {
        for (List<Node> component : components) {
            for (Node node : component) {
                if (node.kind == Kind.MAP) {
                    Map<Object, Object> map = (Map<Object, Object>) converted.get(node.object);
                    for (int i = 0; i < node.values.size(); i++) {
                        map.put(keyValue(node.mapKeys.get(i)), memberValue(node.values.get(i)));
                    }
                } else if (node.kind == Kind.SET) {
                    Set<Object> set = (Set<Object>) converted.get(node.object);
                    for (JSValue element : node.values) {
                        set.add(keyValue(element));
                    }
                }
            }
        }
    }

    /**
     * The strongly connected components of the container graph, each after every component it reaches (Tarjan's
     * algorithm, with an explicit stack).
     */
    private List<List<Node>> findComponents() {
        List<List<Node>> components = new ArrayList<>();
        ArrayDeque<Node> componentStack = new ArrayDeque<>();
        ArrayDeque<Node> work = new ArrayDeque<>();
        int nextIndex = 0;
        for (Node start : nodes) {
            if (start.index >= 0) {
                continue;
            }
            start.index = start.lowLink = nextIndex++;
            start.onStack = true;
            componentStack.push(start);
            work.push(start);
            while (!work.isEmpty()) {
                Node node = work.peek();
                if (node.nextChild < node.children.size()) {
                    Node child = node.children.get(node.nextChild++);
                    if (child.index < 0) {
                        child.index = child.lowLink = nextIndex++;
                        child.onStack = true;
                        componentStack.push(child);
                        work.push(child);
                    } else if (child.onStack) {
                        node.lowLink = Math.min(node.lowLink, child.index);
                    }
                    continue;
                }
                work.pop();
                if (!work.isEmpty()) {
                    Node parent = work.peek();
                    parent.lowLink = Math.min(parent.lowLink, node.lowLink);
                }
                if (node.lowLink == node.index) {
                    List<Node> component = new ArrayList<>();
                    Node member;
                    do {
                        member = componentStack.pop();
                        member.onStack = false;
                        component.add(member);
                    } while (member != node);
                    components.add(component);
                }
            }
        }
        return components;
    }

    /**
     * The conversion of a map key or a set element, its hash code worked out with the others'.
     */
    private Object keyValue(JSValue member) {
        Object key = memberValue(member);
        JavaCollectionKeys.remember(key, keyHashes);
        return key;
    }

    private void link(Node node, JSValue value) {
        if (value instanceof JSObject object) {
            Node child = nodeOf.get(object);
            if (child != null) {
                node.children.add(child);
            }
        }
    }

    /**
     * Record, for every container, whether a cycle can be reached from it: a component's successors come before it, so
     * theirs are known.
     */
    private void markCycles(List<List<Node>> components) {
        for (List<Node> component : components) {
            Node first = component.get(0);
            boolean reachesCycle = component.size() > 1 || first.children.contains(first);
            for (Node member : component) {
                for (Node child : member.children) {
                    reachesCycle |= child.reachesCycle;
                }
            }
            for (Node member : component) {
                member.reachesCycle = reachesCycle;
            }
        }
    }

    /**
     * The conversion of a map or set entry or of an array element: an object's was made already; a primitive converts
     * here, integral numbers as {@link Integer}/{@link Long}, like Javet.
     */
    private Object memberValue(JSValue value) {
        return value instanceof JSObject object ? converted.get(object) : toJavaArrayElement(value);
    }

    /**
     * Read a container's entries, running getters exactly once and in the order they always ran.
     */
    private void read(Node node) {
        JSObject object = node.object;
        if (node.kind == Kind.ARRAY) {
            JSArray array = (JSArray) object;
            long length = array.getLength();
            for (long i = 0; i < length; i++) {
                addValue(node, array.get(i));
            }
        } else if (node.kind == Kind.OBJECT) {
            for (PropertyKey key : object.getOwnPropertyKeys()) {
                JSValue value = object.get(key);
                node.propertyNames.add(key.toPropertyString());
                addValue(node, value);
            }
        } else if (node.kind == Kind.MAP) {
            for (Map.Entry<JSMap.KeyWrapper, JSValue> entry : ((JSMap) object).entries()) {
                addMember(node, node.mapKeys, entry.getKey().value());
                addValue(node, entry.getValue());
            }
        } else {
            for (JSMap.KeyWrapper element : ((JSSet) object).values()) {
                addMember(node, node.values, element.value());
            }
        }
    }

    static Object convert(JSObject root) {
        JavaObjectConverter converter = new JavaObjectConverter();
        converter.discover(root);
        while (!converter.pending.isEmpty()) {
            converter.read(converter.pending.poll());
        }
        List<List<Node>> components = converter.findComponents();
        converter.markCycles(components);
        converter.createContainers();
        converter.fillListsAndObjects();
        converter.fillMapsAndSets(components);
        JavaCollectionKeys.forget(converter.keyHashes);
        return converter.converted.get(root);
    }

    /**
     * Integral finite numbers are boxed as Integer/Long instead of Double, aligned with Javet's object conversion.
     */
    static Object toJavaArrayElement(JSValue value) {
        if (value instanceof JSNumber number) {
            double numberValue = number.value();
            if (Double.isFinite(numberValue) && numberValue == Math.rint(numberValue)
                    && Double.doubleToRawLongBits(numberValue) != Double.doubleToRawLongBits(-0.0d)) {
                if (numberValue >= Integer.MIN_VALUE && numberValue <= Integer.MAX_VALUE) {
                    return (int) numberValue;
                }
                if (numberValue >= Long.MIN_VALUE && numberValue <= Long.MAX_VALUE) {
                    return (long) numberValue;
                }
            }
        }
        return value.toJavaObject();
    }

    private static final class IdentityHashedList extends ValueHashedList implements JavaCollectionKeys.IdentityKey {
        private static final long serialVersionUID = 1L;
    }

    private static final class IdentityHashedMap extends ValueHashedMap implements JavaCollectionKeys.IdentityKey {
        private static final long serialVersionUID = 1L;
    }

    private static final class IdentityHashedSet extends ValueHashedSet implements JavaCollectionKeys.IdentityKey {
        private static final long serialVersionUID = 1L;
    }

    private enum Kind {
        /**
         * An array, converted to a list.
         */
        ARRAY,
        /**
         * A {@code Map}, converted to a map with converted keys.
         */
        MAP,
        /**
         * Any other object converted here, converted to a map of its property names.
         */
        OBJECT,
        /**
         * A {@code Set}, converted to a set.
         */
        SET,
    }

    /**
     * A container met in the graph: what was read from it, and its place in the graph.
     */
    private static final class Node {
        private final List<Node> children = new ArrayList<>();
        private int index = -1;
        private final Kind kind;
        private int lowLink;
        /**
         * A map's keys, in order, beside its {@link #values}.
         */
        private final List<JSValue> mapKeys = new ArrayList<>();
        private int nextChild;
        private final JSObject object;
        private boolean onStack;
        /**
         * An object's property names, in order, beside its {@link #values}.
         */
        private final List<String> propertyNames = new ArrayList<>();
        private boolean reachesCycle;
        /**
         * An array's elements, an object's property values, a map's values or a set's elements.
         */
        private final List<JSValue> values = new ArrayList<>();

        private Node(JSObject object, Kind kind) {
            this.object = object;
            this.kind = kind;
        }
    }

    private static sealed class ValueHashedList extends ArrayList<Object> implements JavaCollectionKeys.CollectionKey
            permits IdentityHashedList {
        private static final long serialVersionUID = 1L;
        private transient Integer knownHash;

        @Override
        public void cacheHash(Integer hash) {
            knownHash = hash;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || !(this instanceof JavaCollectionKeys.IdentityKey) && other instanceof List
                    && JavaCollectionKeys.valueEquals(this, other);
        }

        @Override
        public int hashCode() {
            return JavaCollectionKeys.collectionHash(this, knownHash);
        }
    }

    private static sealed class ValueHashedMap extends LinkedHashMap<Object, Object>
            implements
                JavaCollectionKeys.CollectionKey
            permits IdentityHashedMap {
        private static final long serialVersionUID = 1L;
        private transient Integer knownHash;

        @Override
        public void cacheHash(Integer hash) {
            knownHash = hash;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || !(this instanceof JavaCollectionKeys.IdentityKey) && other instanceof Map
                    && JavaCollectionKeys.valueEquals(this, other);
        }

        @Override
        public int hashCode() {
            return JavaCollectionKeys.collectionHash(this, knownHash);
        }
    }

    private static sealed class ValueHashedSet extends LinkedHashSet<Object> implements JavaCollectionKeys.CollectionKey
            permits IdentityHashedSet {
        private static final long serialVersionUID = 1L;
        private transient Integer knownHash;

        @Override
        public void cacheHash(Integer hash) {
            knownHash = hash;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || !(this instanceof JavaCollectionKeys.IdentityKey) && other instanceof Set
                    && JavaCollectionKeys.valueEquals(this, other);
        }

        @Override
        public int hashCode() {
            return JavaCollectionKeys.collectionHash(this, knownHash);
        }
    }
}
