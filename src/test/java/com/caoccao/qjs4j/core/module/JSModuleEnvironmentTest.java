package com.caoccao.qjs4j.core.module;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regressions for the module environment defects originally documented in review 02. */
class JSModuleEnvironmentTest extends BaseJavetTest {
    @TempDir
    Path moduleDirectory;

    private void assertModuleWithJavet(String source, String probe) throws IOException {
        Path entry = moduleDirectory.resolve("main.mjs");
        Files.writeString(entry, source);
        assertWithJavet(() -> {
            v8Runtime.resetContext();
            v8Runtime.setV8ModuleResolver((runtime, name, referrer) -> {
                Path path = moduleDirectory.resolve(name).normalize();
                try {
                    return runtime.getExecutor(Files.readString(path)).setResourceName(path.toString())
                            .compileV8Module();
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            });
            v8Runtime.getExecutor(source).setResourceName(entry.toString()).setModule(true).executeVoid();
            v8Runtime.await();
            try {
                return v8Runtime.getExecutor(probe).executeString();
            } finally {
                v8Runtime.removeV8Modules(true);
            }
        }, () -> {
            resetContext().eval(source, entry.toString(), true);
            context.processMicrotasks();
            return context.eval(probe).toString();
        });
    }

    @Test
    void asyncModuleClosuresReadLiveImportsAfterSuspension() throws IOException {
        Files.writeString(moduleDirectory.resolve("dep.mjs"), "export let x=1;export function inc(){x++}");
        assertModuleWithJavet("""
                import {x,inc} from './dep.mjs';
                await Promise.resolve();
                inc();globalThis.read=()=>x;globalThis.bump=inc;export const after=x;
                """, "(()=>{const first=read();bump();return JSON.stringify([first,read()])})()");
    }

    @Test
    void deferredCycleEvaluatesAsyncDependencyOnce() throws IOException {
        Files.writeString(moduleDirectory.resolve("dep.mjs"), """
                import './main.mjs';
                globalThis.runs=(globalThis.runs||0)+1;
                await Promise.resolve();
                export const x=42;
                """);
        // Javet's V8 build disables import-defer syntax, so this proposal uses an explicit semantic assertion.
        String source = "import defer * as dep from './dep.mjs'; globalThis.read=()=>dep.x;";
        Files.writeString(moduleDirectory.resolve("main.mjs"), source);
        context.eval(source, moduleDirectory.resolve("main.mjs").toString(), true);
        context.processMicrotasks();
        assertEvalText("JSON.stringify([runs,read()])", "[1,42]");
    }

    @Test
    void directEvalAssignsToTheLocalThatShadowsAnImport() throws IOException {
        Files.writeString(moduleDirectory.resolve("dep.mjs"), "export const x=10;export let y=20;");
        assertModuleWithJavet("""
                import {x,y} from './dep.mjs';
                function f(){let x=1;eval('x=2');return x}
                function g(){let x=1;eval('x+=5');return x}
                function h(){var x=1;eval('x=3');return x}
                function k(){let x=1;eval('[x]=[4]');return x}
                function m(){let x=1;eval('x++');return x}
                function n(){let y=1;eval('({y}={y:7})');return y}
                function p(){let x=1;eval('(function(){x=8})()');return x}
                function q(){let x=1;eval('for(x of [9]){}');return x}
                function r(){try{eval('x=9');return 'assigned'}catch(e){return e.name}}
                function s(){try{eval('[y]=[9]');return 'assigned'}catch(e){return e.name}}
                globalThis.read=()=>JSON.stringify([f(),g(),h(),k(),m(),n(),p(),q(),r(),s(),x,y]);
                """, "read()");
    }

    @Test
    void functionsCalledFromEvalKeepTheirOwnImports() throws IOException {
        Files.writeString(moduleDirectory.resolve("dep.mjs"), "export const x=10;");
        assertModuleWithJavet("""
                import {x} from './dep.mjs';
                function write(){x=9}
                function writeRef(){[x]=[9]}
                function read(){return x}
                function f(){let x=1;try{eval('write()');return 'assigned '+x}catch(e){return e.name+' '+x}}
                function g(){let x=1;try{eval('writeRef()');return 'assigned '+x}catch(e){return e.name+' '+x}}
                function h(){let x=1;return eval('read()')+' '+x}
                function k(){let x=1;eval('function inner(){x=5}inner()');return x}
                function m(){let x=1;return eval('(()=>{x=6})(),x')}
                globalThis.read=()=>JSON.stringify([f(),g(),h(),k(),m(),x]);
                """, "read()");
    }

    @Test
    void functionsFromAnEarlierEvalDoNotSeeAnotherEvalsOverlay() throws IOException {
        Files.writeString(moduleDirectory.resolve("dep.mjs"), "export const x=10;");
        assertModuleWithJavet("""
                import {x} from './dep.mjs';
                const write=eval('()=>{x=9}');
                const read=eval('()=>x');
                function f(){let x=1;try{eval('write()');return 'assigned '+x}catch(e){return e.name+' '+x}}
                function g(){let x=1;return eval('read()')+' '+x}
                function h(){let x=1;return eval('(()=>{const inner=eval("()=>x");return inner()})()')}
                function k(){let x=1;eval('(function(){eval("x=8")})()');return x}
                function p(){let x=1;function q(){let x=2;return eval('write()')}
                try{return eval('q()')+' '+x}catch(e){return e.name+' '+x}}
                globalThis.read=()=>JSON.stringify([f(),g(),h(),k(),p(),x]);
                """, "read()");
    }

    @Test
    void generatedBindingsAreNotVisibleToEval() throws IOException {
        assertModuleWithJavet("""
                export const x=1;
                globalThis.probe=eval('typeof __q'+'js4jModuleExports');
                globalThis.read=()=>eval('typeof __q'+'js4jModuleExports');
                """, "JSON.stringify([probe,read(),Object.keys(globalThis).filter(k=>k.startsWith('__qjs4j'))])");
    }

    @Test
    void namespaceImportsOutliveModuleEvaluation() throws IOException {
        Files.writeString(moduleDirectory.resolve("dep.mjs"), "export let x=1;export function inc(){x++}");
        assertModuleWithJavet("""
                import * as ns from './dep.mjs';
                globalThis.read=()=>ns.x;globalThis.bump=()=>ns.inc();
                """, "(()=>{const first=read();bump();return JSON.stringify([first,read()])})()");
    }

    @Test
    void retainedImportsAreLiveAndImmutable() throws IOException {
        Files.writeString(moduleDirectory.resolve("dep.mjs"), "export let x=1;export function inc(){x++}");
        assertModuleWithJavet("""
                import {x,inc} from './dep.mjs';
                globalThis.readX=()=>x;globalThis.bumpX=inc;
                globalThis.writeX=()=>{try{x=10}catch(e){return e.name}};
                globalThis.updateX=()=>{try{x++}catch(e){return e.name}};
                globalThis.evalWriteX=()=>{try{eval('x=10')}catch(e){return e.name}};
                globalThis.evalX=()=>eval('x');
                globalThis.indirectX=()=> (0,eval)('typeof x');
                globalThis.shadowX=()=>{let x=9;return eval('x')};
                """,
                """
                        (()=>{const first=readX();bumpX();return JSON.stringify([first,readX(),evalX(),writeX(),updateX(),evalWriteX(),indirectX(),shadowX(),Object.hasOwn(globalThis,'x')])})()
                        """);
    }

    @Test
    void unsupportedImportTypeFailsBeforeBody() throws IOException {
        Files.writeString(moduleDirectory.resolve("dep.mjs"), "globalThis.depRan=true;export const x=1;");
        Path entry = moduleDirectory.resolve("main.mjs");
        String source = "import {x} from './dep.mjs' with {type:'javascript'};globalThis.ran=true;";
        // Import attributes are a host contract: the native V8 resolver leaves validation to its embedder.
        assertThatThrownBy(() -> context.eval(source, entry.toString(), true)).hasMessageContaining("Import attribute")
                .hasMessageContaining("not supported");
        assertEvalText("typeof ran+','+typeof depRan", "undefined,undefined");
    }
}
