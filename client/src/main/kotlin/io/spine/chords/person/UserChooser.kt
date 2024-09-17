/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.person

import com.teamdev.projects.v2.users.user.UserProfile
import io.spine.chords.ComponentCompanion
import io.spine.chords.EntityChooser
import io.spine.core.UserId
import io.spine.person.format

/**
 * A drop-down list that allows selecting a user defined by the [UserId] type.
 */
public class UserChooser : EntityChooser<UserId, UserProfile>() {

    /**
     * A component instance declaration API.
     */
    public companion object : ComponentCompanion<UserChooser>({ UserChooser() })

    init {
        label = "User"
    }

    override fun entityId(entityState: UserProfile): UserId = entityState.id
    override fun itemText(entityId: UserId, entityState: UserProfile?): String? =
        entityState?.info?.name?.format()
}
