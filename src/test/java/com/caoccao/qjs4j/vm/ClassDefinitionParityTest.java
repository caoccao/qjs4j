package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class ClassDefinitionParityTest extends BaseJavetTest {
    @Test
    public void testAbsentAndNullHeritage() {
        assertStringWithJavet(
                """
                        (() => {
                          const ordinary = (() => { class Subject  {  }; return Subject; })();
                          const nullBase = (() => { class Subject extends null { constructor() { return Object.create(new.target.prototype); } }; return Subject; })();
                          const instance = new nullBase();
                          const defaultNullBase = (() => { class Subject extends null {  }; return Subject; })();
                          let error;
                          try { new defaultNullBase(); } catch (e) { error = e.name; }
                          return [ordinary.name, new ordinary() instanceof ordinary,
                            Object.getPrototypeOf(ordinary) === Function.prototype,
                            Object.getPrototypeOf(ordinary.prototype) === Object.prototype,
                            Object.getPrototypeOf(nullBase) === Function.prototype,
                            Object.getPrototypeOf(nullBase.prototype) === null,
                            Object.getPrototypeOf(instance) === nullBase.prototype, error].join('|');
                        })()
                        """,
                """
                        (() => {
                          const ordinary = (() => { return (class Subject  {  }); })();
                          const nullBase = (() => { return (class Subject extends null { constructor() { return Object.create(new.target.prototype); } }); })();
                          const instance = new nullBase();
                          const defaultNullBase = (() => { return (class Subject extends null {  }); })();
                          let error;
                          try { new defaultNullBase(); } catch (e) { error = e.name; }
                          return [ordinary.name, new ordinary() instanceof ordinary,
                            Object.getPrototypeOf(ordinary) === Function.prototype,
                            Object.getPrototypeOf(ordinary.prototype) === Object.prototype,
                            Object.getPrototypeOf(nullBase) === Function.prototype,
                            Object.getPrototypeOf(nullBase.prototype) === null,
                            Object.getPrototypeOf(instance) === nullBase.prototype, error].join('|');
                        })()
                        """,
                """
                        (() => {
                          const ordinary = (() => { const key = 'chosen'; return ({ [key]: class  {  } })[key]; })();
                          const nullBase = (() => { const key = 'chosen'; return ({ [key]: class extends null { constructor() { return Object.create(new.target.prototype); } } })[key]; })();
                          const instance = new nullBase();
                          const defaultNullBase = (() => { const key = 'chosen'; return ({ [key]: class extends null {  } })[key]; })();
                          let error;
                          try { new defaultNullBase(); } catch (e) { error = e.name; }
                          return [ordinary.name, new ordinary() instanceof ordinary,
                            Object.getPrototypeOf(ordinary) === Function.prototype,
                            Object.getPrototypeOf(ordinary.prototype) === Object.prototype,
                            Object.getPrototypeOf(nullBase) === Function.prototype,
                            Object.getPrototypeOf(nullBase.prototype) === null,
                            Object.getPrototypeOf(instance) === nullBase.prototype, error].join('|');
                        })()
                        """,
                """
                        (() => {
                          const ordinary = (() => { const key = Symbol('chosen'); return ({ [key]: class  {  } })[key]; })();
                          const nullBase = (() => { const key = Symbol('chosen'); return ({ [key]: class extends null { constructor() { return Object.create(new.target.prototype); } } })[key]; })();
                          const instance = new nullBase();
                          const defaultNullBase = (() => { const key = Symbol('chosen'); return ({ [key]: class extends null {  } })[key]; })();
                          let error;
                          try { new defaultNullBase(); } catch (e) { error = e.name; }
                          return [ordinary.name, new ordinary() instanceof ordinary,
                            Object.getPrototypeOf(ordinary) === Function.prototype,
                            Object.getPrototypeOf(ordinary.prototype) === Object.prototype,
                            Object.getPrototypeOf(nullBase) === Function.prototype,
                            Object.getPrototypeOf(nullBase.prototype) === null,
                            Object.getPrototypeOf(instance) === nullBase.prototype, error].join('|');
                        })()
                        """);
    }

    @Test
    public void testInheritanceNamesAndDescriptors() {
        assertStringWithJavet("""
                (() => {
                  function Base(value) { this.value = value; }
                  Base.prototype.describe = function () { return 'base:' + this.value; };
                  Base.label = 'static';
                  const subject = (() => { class Subject extends Base { read() { return this.value; }
                static read() { return this.label; }
                 }; return Subject; })();
                  const instance = new subject(7);
                  const prototype = Object.getOwnPropertyDescriptor(subject, 'prototype');
                  const constructor = Object.getOwnPropertyDescriptor(subject.prototype, 'constructor');
                  const name = Object.getOwnPropertyDescriptor(subject, 'name');
                  return [subject.name, instance.value, instance.read(), subject.read(),
                    Object.getPrototypeOf(subject) === Base,
                    Object.getPrototypeOf(subject.prototype) === Base.prototype,
                    instance.constructor === subject,
                    prototype.writable, prototype.enumerable, prototype.configurable,
                    constructor.writable, constructor.enumerable, constructor.configurable,
                    name.writable, name.enumerable, name.configurable].join('|');
                })()
                """, """
                (() => {
                  function Base(value) { this.value = value; }
                  Base.prototype.describe = function () { return 'base:' + this.value; };
                  Base.label = 'static';
                  const subject = (() => { return (class Subject extends Base { read() { return this.value; }
                static read() { return this.label; }
                 }); })();
                  const instance = new subject(7);
                  const prototype = Object.getOwnPropertyDescriptor(subject, 'prototype');
                  const constructor = Object.getOwnPropertyDescriptor(subject.prototype, 'constructor');
                  const name = Object.getOwnPropertyDescriptor(subject, 'name');
                  return [subject.name, instance.value, instance.read(), subject.read(),
                    Object.getPrototypeOf(subject) === Base,
                    Object.getPrototypeOf(subject.prototype) === Base.prototype,
                    instance.constructor === subject,
                    prototype.writable, prototype.enumerable, prototype.configurable,
                    constructor.writable, constructor.enumerable, constructor.configurable,
                    name.writable, name.enumerable, name.configurable].join('|');
                })()
                """,
                """
                        (() => {
                          function Base(value) { this.value = value; }
                          Base.prototype.describe = function () { return 'base:' + this.value; };
                          Base.label = 'static';
                          const subject = (() => { const key = 'chosen'; return ({ [key]: class extends Base { read() { return this.value; }
                        static read() { return this.label; }
                         } })[key]; })();
                          const instance = new subject(7);
                          const prototype = Object.getOwnPropertyDescriptor(subject, 'prototype');
                          const constructor = Object.getOwnPropertyDescriptor(subject.prototype, 'constructor');
                          const name = Object.getOwnPropertyDescriptor(subject, 'name');
                          return [subject.name, instance.value, instance.read(), subject.read(),
                            Object.getPrototypeOf(subject) === Base,
                            Object.getPrototypeOf(subject.prototype) === Base.prototype,
                            instance.constructor === subject,
                            prototype.writable, prototype.enumerable, prototype.configurable,
                            constructor.writable, constructor.enumerable, constructor.configurable,
                            name.writable, name.enumerable, name.configurable].join('|');
                        })()
                        """,
                """
                        (() => {
                          function Base(value) { this.value = value; }
                          Base.prototype.describe = function () { return 'base:' + this.value; };
                          Base.label = 'static';
                          const subject = (() => { const key = Symbol('chosen'); return ({ [key]: class extends Base { read() { return this.value; }
                        static read() { return this.label; }
                         } })[key]; })();
                          const instance = new subject(7);
                          const prototype = Object.getOwnPropertyDescriptor(subject, 'prototype');
                          const constructor = Object.getOwnPropertyDescriptor(subject.prototype, 'constructor');
                          const name = Object.getOwnPropertyDescriptor(subject, 'name');
                          return [subject.name, instance.value, instance.read(), subject.read(),
                            Object.getPrototypeOf(subject) === Base,
                            Object.getPrototypeOf(subject.prototype) === Base.prototype,
                            instance.constructor === subject,
                            prototype.writable, prototype.enumerable, prototype.configurable,
                            constructor.writable, constructor.enumerable, constructor.configurable,
                            name.writable, name.enumerable, name.configurable].join('|');
                        })()
                        """);
    }

    @Test
    public void testInvalidParentConstructors() {
        assertStringWithJavet("""
                (() => [17, {}, () => {}].map(Base => {
                  try { (() => { class Subject extends Base {  }; return Subject; })(); return 'missing error'; }
                  catch (e) { return e.name; }
                }).join('|'))()
                """, """
                (() => [17, {}, () => {}].map(Base => {
                  try { (() => { return (class Subject extends Base {  }); })(); return 'missing error'; }
                  catch (e) { return e.name; }
                }).join('|'))()
                """,
                """
                        (() => [17, {}, () => {}].map(Base => {
                          try { (() => { const key = 'chosen'; return ({ [key]: class extends Base {  } })[key]; })(); return 'missing error'; }
                          catch (e) { return e.name; }
                        }).join('|'))()
                        """,
                """
                        (() => [17, {}, () => {}].map(Base => {
                          try { (() => { const key = Symbol('chosen'); return ({ [key]: class extends Base {  } })[key]; })(); return 'missing error'; }
                          catch (e) { return e.name; }
                        }).join('|'))()
                        """);
    }

    @Test
    public void testPrototypeGetterOrderingAndExceptions() {
        assertStringWithJavet(
                """
                        (() => ['object', 'null', 'primitive', 'throw'].map(mode => {
                          const log = [];
                          const marker = {};
                          const Base = new Proxy(function () {}, { get(target, key, receiver) {
                            log.push('get:' + String(key));
                            if (key === 'prototype') {
                              if (mode === 'throw') throw marker;
                              return mode === 'object' ? {} : mode === 'null' ? null : 17;
                            }
                            return Reflect.get(target, key, receiver);
                          } });
                          let outcome;
                          try { (() => { class Subject extends Base { static value = (log.push('field'), 1); }; return Subject; })(); outcome = 'ok'; }
                          catch (e) { outcome = e === marker ? 'marker' : e.name; }
                          // Evaluate another class after the error to check that the VM recovered.
                          const recovered = class { static value = 7; };
                          return [outcome, log.join(','), recovered.value].join('|');
                        }).join(';'))()
                        """,
                """
                        (() => ['object', 'null', 'primitive', 'throw'].map(mode => {
                          const log = [];
                          const marker = {};
                          const Base = new Proxy(function () {}, { get(target, key, receiver) {
                            log.push('get:' + String(key));
                            if (key === 'prototype') {
                              if (mode === 'throw') throw marker;
                              return mode === 'object' ? {} : mode === 'null' ? null : 17;
                            }
                            return Reflect.get(target, key, receiver);
                          } });
                          let outcome;
                          try { (() => { return (class Subject extends Base { static value = (log.push('field'), 1); }); })(); outcome = 'ok'; }
                          catch (e) { outcome = e === marker ? 'marker' : e.name; }
                          // Evaluate another class after the error to check that the VM recovered.
                          const recovered = class { static value = 7; };
                          return [outcome, log.join(','), recovered.value].join('|');
                        }).join(';'))()
                        """,
                """
                        (() => ['object', 'null', 'primitive', 'throw'].map(mode => {
                          const log = [];
                          const marker = {};
                          const Base = new Proxy(function () {}, { get(target, key, receiver) {
                            log.push('get:' + String(key));
                            if (key === 'prototype') {
                              if (mode === 'throw') throw marker;
                              return mode === 'object' ? {} : mode === 'null' ? null : 17;
                            }
                            return Reflect.get(target, key, receiver);
                          } });
                          let outcome;
                          try { (() => { const key = 'chosen'; return ({ [key]: class extends Base { static value = (log.push('field'), 1); } })[key]; })(); outcome = 'ok'; }
                          catch (e) { outcome = e === marker ? 'marker' : e.name; }
                          // Evaluate another class after the error to check that the VM recovered.
                          const recovered = class { static value = 7; };
                          return [outcome, log.join(','), recovered.value].join('|');
                        }).join(';'))()
                        """,
                """
                        (() => ['object', 'null', 'primitive', 'throw'].map(mode => {
                          const log = [];
                          const marker = {};
                          const Base = new Proxy(function () {}, { get(target, key, receiver) {
                            log.push('get:' + String(key));
                            if (key === 'prototype') {
                              if (mode === 'throw') throw marker;
                              return mode === 'object' ? {} : mode === 'null' ? null : 17;
                            }
                            return Reflect.get(target, key, receiver);
                          } });
                          let outcome;
                          try { (() => { const key = Symbol('chosen'); return ({ [key]: class extends Base { static value = (log.push('field'), 1); } })[key]; })(); outcome = 'ok'; }
                          catch (e) { outcome = e === marker ? 'marker' : e.name; }
                          // Evaluate another class after the error to check that the VM recovered.
                          const recovered = class { static value = 7; };
                          return [outcome, log.join(','), recovered.value].join('|');
                        }).join(';'))()
                        """);
    }

}
