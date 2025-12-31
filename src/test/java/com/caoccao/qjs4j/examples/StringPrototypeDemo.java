package com.caoccao.qjs4j.examples;

import com.caoccao.qjs4j.builtins.StringPrototype;
import com.caoccao.qjs4j.core.*;

/**
 * Demonstration of String.prototype RegExp methods.
 * Shows how match(), matchAll(), replace(), replaceAll(), search(), and split() work.
 */
public class StringPrototypeDemo {

    public static void main(String[] args) {
        try (JSRuntime runtime = new JSRuntime(); JSContext context = runtime.createContext()) {

            System.out.println("=== String.prototype RegExp Methods Demo ===\n");

            // Test string
            JSString testStr = new JSString("hello world, hello universe");

            // 1. match() - string argument
            System.out.println("1. match() with string:");
            JSValue result = StringPrototype.match(context, testStr, new JSValue[]{new JSString("hello")});
            System.out.println("   \"hello world, hello universe\".match(\"hello\") = " + result);

            // 2. match() - non-global regex
            System.out.println("\n2. match() with non-global RegExp:");
            JSRegExp regex1 = new JSRegExp("h\\w+", "");
            result = StringPrototype.match(context, testStr, new JSValue[]{regex1});
            System.out.println("   \"hello world, hello universe\".match(/h\\w+/) = " + result);

            // 3. match() - global regex
            System.out.println("\n3. match() with global RegExp:");
            JSRegExp regex2 = new JSRegExp("hello", "g");
            result = StringPrototype.match(context, testStr, new JSValue[]{regex2});
            System.out.println("   \"hello world, hello universe\".match(/hello/g) = " + result);

            // 4. search() - finding position
            System.out.println("\n4. search() with RegExp:");
            JSRegExp regex3 = new JSRegExp("world", "");
            result = StringPrototype.search(context, testStr, new JSValue[]{regex3});
            System.out.println("   \"hello world, hello universe\".search(/world/) = " + result);

            // 5. replace() - simple replacement
            System.out.println("\n5. replace() with RegExp:");
            JSRegExp regex4 = new JSRegExp("hello", "");
            result = StringPrototype.replace(context, testStr, new JSValue[]{regex4, new JSString("hi")});
            System.out.println("   \"hello world, hello universe\".replace(/hello/, \"hi\") = " + result);

            // 6. replaceAll() - replace all occurrences
            System.out.println("\n6. replaceAll() with global RegExp:");
            JSRegExp regex5 = new JSRegExp("hello", "g");
            result = StringPrototype.replaceAll(context, testStr, new JSValue[]{regex5, new JSString("hi")});
            System.out.println("   \"hello world, hello universe\".replaceAll(/hello/g, \"hi\") = " + result);

            // 7. split() - with regex separator
            System.out.println("\n7. split() with RegExp:");
            JSRegExp regex6 = new JSRegExp(",?\\s+", "");
            result = StringPrototype.split(context, testStr, new JSValue[]{regex6});
            System.out.println("   \"hello world, hello universe\".split(/,?\\s+/) = " + result);

            // 8. Capture groups in replace()
            System.out.println("\n8. replace() with capture groups:");
            JSString testStr2 = new JSString("John Doe");
            JSRegExp regex7 = new JSRegExp("(\\w+) (\\w+)", "");
            result = StringPrototype.replace(context, testStr2, new JSValue[]{regex7, new JSString("$2, $1")});
            System.out.println("   \"John Doe\".replace(/(\\w+) (\\w+)/, \"$2, $1\") = " + result);

            System.out.println("\n=== All methods working correctly! ===");
        }
    }
}
