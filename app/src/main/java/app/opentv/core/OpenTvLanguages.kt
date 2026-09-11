/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

/**
 * The UI languages OpenTV ships translations for, each labelled with its own endonym.
 *
 * The tag is both the value stored in settings and the resource qualifier (`values-<tag>`), so
 * adding a language is a new `values-xx/strings.xml` plus a line in [entries] — and dropping one is
 * the reverse, which is how the project went from thirty translations down to English and Polish.
 *
 * This is the single source of truth. The settings picker builds itself from [entries], and
 * [supports] is what stops a saved tag that no longer has resources behind it — someone who had
 * chosen German before it was removed — from being applied anyway. Without that check the process
 * would keep a German default [java.util.Locale] for dates and numbers while every string fell back
 * to English, which reads as "the guide's clock is wrong" rather than as a language problem.
 */
object OpenTvLanguages {

    val entries: List<Pair<String, String>> = listOf(
        "en" to "English",
        "pl" to "Polski",
    )

    private val tags: Set<String> = entries.map { it.first }.toSet()

    /** True when [tag] is a language we have resources for. Blank means "follow the device". */
    fun supports(tag: String): Boolean = tag.isBlank() || tag in tags
}
