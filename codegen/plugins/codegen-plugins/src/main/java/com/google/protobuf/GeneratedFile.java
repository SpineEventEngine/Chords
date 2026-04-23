/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.protobuf;

/**
 * Compatibility shim for protobuf-generated classes compiled against an older
 * protobuf Java runtime.
 *
 * <p>Some transitive Spine artifacts on this module's compile classpath still
 * contain generated classes such as {@code io.spine.option.OptionsProto} that
 * extend {@code com.google.protobuf.GeneratedFile}. That base class existed in
 * older protobuf Java runtimes, but it is no longer present in protobuf 4.x,
 * which this module uses.
 *
 * <p>As a result, javac fails while resolving those generated Spine classes,
 * even though they do not rely on any behavior from {@code GeneratedFile}
 * itself. In practice it is only a marker superclass in this build.
 *
 * <p>This shim keeps the compile classpath compatible until the upstream Spine
 * artifacts are regenerated against a protobuf runtime that no longer emits
 * references to {@code GeneratedFile}. Once those dependencies are updated,
 * this class should be removed.
 */
public abstract class GeneratedFile {
    protected GeneratedFile() {
    }
}
