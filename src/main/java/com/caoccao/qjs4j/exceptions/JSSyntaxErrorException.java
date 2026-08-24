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

package com.caoccao.qjs4j.exceptions;

import com.caoccao.qjs4j.compilation.ast.SourceLocation;

public class JSSyntaxErrorException extends JSErrorException {
    public JSSyntaxErrorException(String message) {
        super(message);
    }

    public JSSyntaxErrorException(String message, SourceLocation sourceLocation) {
        super(message, sourceLocation);
    }

    public JSErrorType getErrorType() {
        return JSErrorType.SyntaxError;
    }

    public JSSyntaxErrorException withSourceLocation(SourceLocation sourceLocation) {
        if (getSourceLocation() != null || sourceLocation == null) {
            return this;
        }
        return new JSSyntaxErrorException(getMessage(), sourceLocation);
    }
}
