/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// TODO:2024-08-29:dmitry.pikhulya: this file is not a part of the original
//   `config` module's content. Consider updating the `config`
//   module accordingly.
//   See https://github.com/SpineEventEngine/Chords/issues/3

// https://github.com/JetBrains/compose-jb
object Material3 {
    object Desktop {
        private const val version = "1.5.12"
        const val lib = "org.jetbrains.compose.material3:material3-desktop:$version"
    }
}
