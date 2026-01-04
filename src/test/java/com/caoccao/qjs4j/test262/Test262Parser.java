/*
 * Copyright (c) 2024. caoccao.com Sam Cao
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

package com.caoccao.qjs4j.test262;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses test262 test files extracting YAML frontmatter and JavaScript code.
 */
public class Test262Parser {
    private static final Pattern FRONTMATTER_PATTERN = 
        Pattern.compile("/\\*---\\s*(.+?)\\s*---\\*/", Pattern.DOTALL);

    public Test262TestCase parse(Path testFile) throws IOException {
        String content = Files.readString(testFile);
        Test262TestCase testCase = new Test262TestCase(testFile);

        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        
        if (matcher.find()) {
            String yaml = matcher.group(1);
            parseYaml(yaml, testCase);
            
            // Extract JavaScript code after frontmatter
            int codeStart = matcher.end();
            testCase.setCode(content.substring(codeStart).trim());
        } else {
            // No frontmatter - entire file is code
            testCase.setCode(content);
        }
        
        return testCase;
    }

    private void parseYaml(String yaml, Test262TestCase testCase) {
        String[] lines = yaml.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.startsWith("description:")) {
                testCase.setDescription(extractValue(line, "description:"));
            } else if (line.startsWith("esid:")) {
                testCase.setEsid(extractValue(line, "esid:"));
            } else if (line.startsWith("flags:")) {
                testCase.setFlags(parseArray(line, i, lines));
            } else if (line.startsWith("features:")) {
                testCase.setFeatures(parseArray(line, i, lines));
            } else if (line.startsWith("includes:")) {
                testCase.setIncludes(parseArray(line, i, lines));
            } else if (line.equals("negative:")) {
                testCase.setNegative(parseNegative(i, lines));
            }
        }
    }

    private String extractValue(String line, String prefix) {
        String value = line.substring(prefix.length()).trim();
        // Remove quotes if present
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        } else if (value.startsWith("'") && value.endsWith("'")) {
            value = value.substring(1, value.length() - 1);
        }
        // Handle multi-line values starting with >
        if (value.equals(">")) {
            return ""; // Multi-line description, could be enhanced
        }
        return value;
    }

    private Set<String> parseArray(String line, int currentIndex, String[] lines) {
        Set<String> result = new HashSet<>();
        
        // Inline array: [item1, item2]
        if (line.contains("[") && line.contains("]")) {
            String arrayContent = line.substring(line.indexOf('[') + 1, line.indexOf(']'));
            String[] items = arrayContent.split(",");
            for (String item : items) {
                String cleaned = item.trim();
                if (!cleaned.isEmpty()) {
                    result.add(removeQuotes(cleaned));
                }
            }
        } else {
            // Multi-line array
            for (int i = currentIndex + 1; i < lines.length; i++) {
                String arrayLine = lines[i].trim();
                if (arrayLine.startsWith("-")) {
                    String item = arrayLine.substring(1).trim();
                    result.add(removeQuotes(item));
                } else if (!arrayLine.isEmpty() && !arrayLine.startsWith(" ")) {
                    // Next key found
                    break;
                }
            }
        }
        
        return result;
    }

    private Test262TestCase.NegativeInfo parseNegative(int currentIndex, String[] lines) {
        Test262TestCase.NegativeInfo negative = new Test262TestCase.NegativeInfo();
        
        for (int i = currentIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.startsWith("phase:")) {
                negative.setPhase(extractValue(line, "phase:"));
            } else if (line.startsWith("type:")) {
                negative.setType(extractValue(line, "type:"));
            } else if (!line.isEmpty() && !line.startsWith(" ") && line.contains(":")) {
                // Next key found
                break;
            }
        }
        
        return negative;
    }

    private String removeQuotes(String str) {
        str = str.trim();
        if ((str.startsWith("\"") && str.endsWith("\"")) ||
            (str.startsWith("'") && str.endsWith("'"))) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }
}
