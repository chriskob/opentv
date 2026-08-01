/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The [Activity] behind a Compose [Context], unwrapping the `ContextWrapper` chain Compose hands
 * you. Needed for the handful of things that are genuinely window-level — like holding the screen
 * awake during playback — where a view flag isn't enough on every TV box.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
