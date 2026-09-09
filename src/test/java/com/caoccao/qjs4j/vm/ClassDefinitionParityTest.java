package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ClassDefinitionParityTest extends BaseJavetTest {
    @ParameterizedTest
    @ValueSource(strings = {"declaration", "expression", "computedString", "computedSymbol"})
    public void testAbsentAndNullHeritage(String form) {
        assertStringWithJavet("""
                (() => {
                  const ordinary = (() => { %s })();
                  const nullBase = (() => { %s })();
                  const instance = new nullBase();
                  const defaultNullBase = (() => { %s })();
                  let error;
                  try { new defaultNullBase(); } catch (e) { error = e.name; }
                  return [ordinary.name, new ordinary() instanceof ordinary,
                    Object.getPrototypeOf(ordinary) === Function.prototype,
                    Object.getPrototypeOf(ordinary.prototype) === Object.prototype,
                    Object.getPrototypeOf(nullBase) === Function.prototype,
                    Object.getPrototypeOf(nullBase.prototype) === null,
                    Object.getPrototypeOf(instance) === nullBase.prototype, error].join('|');
                })()
                """.formatted(definition(form, "", ""),
                definition(form, "extends null", "constructor() { return Object.create(new.target.prototype); }"),
                definition(form, "extends null", "")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"declaration", "expression", "computedString", "computedSymbol"})
    public void testInheritanceNamesAndDescriptors(String form) {
        assertStringWithJavet("""
                (() => {
                  function Base(value) { this.value = value; }
                  Base.prototype.describe = function () { return 'base:' + this.value; };
                  Base.label = 'static';
                  const subject = (() => { %s })();
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
                """.formatted(definition(form, "extends Base", """
                read() { return this.value; }
                static read() { return this.label; }
                """)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"declaration", "expression", "computedString", "computedSymbol"})
    public void testInvalidParentConstructors(String form) {
        assertStringWithJavet("""
                (() => [17, {}, () => {}].map(Base => {
                  try { (() => { %s })(); return 'missing error'; }
                  catch (e) { return e.name; }
                }).join('|'))()
                """.formatted(definition(form, "extends Base", "")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"declaration", "expression", "computedString", "computedSymbol"})
    public void testPrototypeGetterOrderingAndExceptions(String form) {
        assertStringWithJavet("""
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
                  try { (() => { %s })(); outcome = 'ok'; }
                  catch (e) { outcome = e === marker ? 'marker' : e.name; }
                  // Evaluate another class after the error to check that the VM recovered.
                  const recovered = class { static value = 7; };
                  return [outcome, log.join(','), recovered.value].join('|');
                }).join(';'))()
                """.formatted(definition(form, "extends Base", "static value = (log.push('field'), 1);")));
    }

    private static String definition(String form, String heritage, String body) {
        String tail = heritage + " { " + body + " }";
        return switch (form) {
            case "declaration" -> "class Subject " + tail + "; return Subject;";
            case "expression" -> "return (class Subject " + tail + ");";
            case "computedString" -> "const key = 'chosen'; return ({ [key]: class " + tail + " })[key];";
            case "computedSymbol" -> "const key = Symbol('chosen'); return ({ [key]: class " + tail + " })[key];";
            default -> throw new IllegalArgumentException(form);
        };
    }
}
