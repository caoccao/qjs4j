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

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.*;

/**
 * Handles compilation of class expressions.
 */
final class ClassExpressionCompiler {
    private final CompilerContext compilerContext;

    ClassExpressionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileClassExpression(ClassExpression classExpr) {
        String className = classExpr.getId() != null ? classExpr.getId().getName()
                : (compilerContext.inferredClassName != null ? compilerContext.inferredClassName : "");

        int classNameLocalIndex = -1;
        boolean hasClassNameScope = classExpr.getId() != null;
        boolean classNameWasTDZ = hasClassNameScope && compilerContext.tdzLocals.contains(className);
        if (hasClassNameScope) {
            compilerContext.scopeManager.enterScope();
            classNameLocalIndex = compilerContext.scopeManager.currentScope().declareConstLocal(className);
            compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, classNameLocalIndex);
            compilerContext.tdzLocals.add(className);
        }

        compilerContext.pushState();
        compilerContext.inClassBody = true;

        if (classExpr.getSuperClass() != null) {
            compilerContext.functionCompiler.emitStrictClassBodyExpression(classExpr.getSuperClass());
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        List<MethodDefinition> methods = new ArrayList<>();
        List<MethodDefinition> privateInstanceMethods = new ArrayList<>();
        List<MethodDefinition> privateStaticMethods = new ArrayList<>();
        List<PropertyDefinition> instanceFields = new ArrayList<>();
        List<ClassElement> staticInitializers = new ArrayList<>();
        List<PropertyDefinition> computedFieldsInDefinitionOrder = new ArrayList<>();
        IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols = new IdentityHashMap<>();
        IdentityHashMap<PropertyDefinition, String> autoAccessorBackingNames = new IdentityHashMap<>();
        MethodDefinition constructor = null;
        LinkedHashMap<String, String> privateNameKinds = new LinkedHashMap<>();

        for (ClassElement element : classExpr.getBody()) {
            if (element instanceof MethodDefinition method) {
                if (method.isConstructor()) {
                    constructor = method;
                } else if (method.isPrivate()) {
                    if (method.isStatic()) {
                        privateStaticMethods.add(method);
                    } else {
                        privateInstanceMethods.add(method);
                    }
                    if (method.getKey() instanceof PrivateIdentifier privateId) {
                        compilerContext.classDeclarationCompiler.registerPrivateName(privateNameKinds, privateId.getName(), method.getKind());
                    }
                } else {
                    methods.add(method);
                }
            } else if (element instanceof PropertyDefinition field) {
                if (field.isStatic()) {
                    staticInitializers.add(field);
                } else {
                    instanceFields.add(field);
                }

                if (field.isPrivate() && field.getKey() instanceof PrivateIdentifier privateId) {
                    compilerContext.classDeclarationCompiler.registerPrivateName(privateNameKinds, privateId.getName(), "field");
                }

                if (field.isAutoAccessor() && !field.isPrivate()) {
                    String backingName = PropertyDefinition.createAutoAccessorBackingName(
                            autoAccessorBackingNames.size() + 1,
                            privateNameKinds.keySet());
                    autoAccessorBackingNames.put(field, backingName);
                    compilerContext.classDeclarationCompiler.registerPrivateName(privateNameKinds, backingName, "field");
                    methods.add(field.toAutoAccessorMethod(JSKeyword.GET, backingName));
                    methods.add(field.toAutoAccessorMethod(JSKeyword.SET, backingName));
                }

                if (field.isComputed() && !field.isPrivate()) {
                    computedFieldsInDefinitionOrder.add(field);
                    computedFieldSymbols.put(
                            field,
                            new JSSymbol("__computed_field_" + computedFieldsInDefinitionOrder.size())
                    );
                }
            } else if (element instanceof StaticBlock block) {
                staticInitializers.add(block);
            }
        }

        LinkedHashMap<String, JSSymbol> ownPrivateSymbols = new LinkedHashMap<>();
        for (String privateName : privateNameKinds.keySet()) {
            ownPrivateSymbols.put(privateName, new JSSymbol("#" + privateName));
        }
        Map<String, JSSymbol> privateSymbols = new LinkedHashMap<>(compilerContext.privateSymbols);
        privateSymbols.putAll(ownPrivateSymbols);
        IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols = new IdentityHashMap<>();
        for (Map.Entry<PropertyDefinition, String> entry : autoAccessorBackingNames.entrySet()) {
            JSSymbol backingSymbol = privateSymbols.get(entry.getValue());
            if (backingSymbol != null) {
                autoAccessorBackingSymbols.put(entry.getKey(), backingSymbol);
            }
        }
        List<ClassDeclarationCompiler.PrivateMethodEntry> privateInstanceMethodFunctions = compilerContext.classDeclarationCompiler.compilePrivateMethodFunctions(
                privateInstanceMethods, privateSymbols, computedFieldSymbols);
        List<ClassDeclarationCompiler.PrivateMethodEntry> privateStaticMethodFunctions = compilerContext.classDeclarationCompiler.compilePrivateMethodFunctions(
                privateStaticMethods, privateSymbols, computedFieldSymbols);

        JSBytecodeFunction constructorFunc;
        if (constructor != null) {
            constructorFunc = compilerContext.classDeclarationCompiler.compileMethodAsFunction(
                    constructor,
                    className,
                    classExpr.getSuperClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    autoAccessorBackingSymbols,
                    privateInstanceMethodFunctions,
                    true
            );
        } else {
            constructorFunc = compilerContext.classDeclarationCompiler.createDefaultConstructor(
                    className,
                    classExpr.getSuperClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    autoAccessorBackingSymbols,
                    privateInstanceMethodFunctions
            );
        }

        if (compilerContext.sourceCode != null && classExpr.getLocation() != null) {
            int startPos = classExpr.getLocation().offset();
            int endPos = classExpr.getLocation().endOffset();
            if (startPos >= 0 && endPos <= compilerContext.sourceCode.length()) {
                String classSource = compilerContext.sourceCode.substring(startPos, endPos);
                constructorFunc.setSourceCode(classSource);
            }
        }
        constructorFunc.setClassPrivateSymbols(ownPrivateSymbols.values());

        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, constructorFunc);
        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS, className);

        compilerContext.emitter.emitOpcode(Opcode.SWAP);

        for (MethodDefinition method : methods) {
            if (method.isStatic()) {
                compilerContext.emitter.emitOpcode(Opcode.SWAP);

                JSBytecodeFunction methodFunc = compilerContext.classDeclarationCompiler.compileMethodAsFunction(
                        method,
                        method.getSimpleName(),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        autoAccessorBackingSymbols,
                        List.of(),
                        false
                );

                String methodName = method.getSimpleName();
                compilerContext.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
            } else {
                JSBytecodeFunction methodFunc = compilerContext.classDeclarationCompiler.compileMethodAsFunction(
                        method,
                        method.getSimpleName(),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        autoAccessorBackingSymbols,
                        List.of(),
                        false
                );

                String methodName = method.getSimpleName();
                compilerContext.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
            }
        }

        compilerContext.classDeclarationCompiler.installPrivateStaticMethods(privateStaticMethodFunctions, privateSymbols);

        for (PropertyDefinition field : computedFieldsInDefinitionOrder) {
            compilerContext.classDeclarationCompiler.compileComputedFieldNameCache(field, computedFieldSymbols, privateSymbols);
        }

        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        compilerContext.emitter.emitOpcode(Opcode.NIP);

        if (hasClassNameScope) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, classNameLocalIndex);
        }

        for (ClassElement staticInitializer : staticInitializers) {
            JSBytecodeFunction staticInitializerFunc;
            if (staticInitializer instanceof PropertyDefinition staticField) {
                staticInitializerFunc = compilerContext.classDeclarationCompiler.compileStaticFieldInitializer(
                        staticField, computedFieldSymbols, privateSymbols, autoAccessorBackingSymbols, className);
            } else if (staticInitializer instanceof StaticBlock staticBlock) {
                staticInitializerFunc = compilerContext.classDeclarationCompiler.compileStaticBlock(staticBlock, className, privateSymbols);
            } else {
                continue;
            }

            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, staticInitializerFunc);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 0);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        if (hasClassNameScope) {
            compilerContext.scopeManager.exitScope();
            if (!classNameWasTDZ) {
                compilerContext.tdzLocals.remove(className);
            }
        }

        compilerContext.popState();
    }
}
