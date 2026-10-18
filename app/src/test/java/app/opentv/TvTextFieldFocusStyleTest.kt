/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import java.io.File
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * A focused text field is one box, not two.
 *
 * [app.opentv.ui.components.TvOutlinedTextField] wraps a Material3 `OutlinedTextField`, which draws
 * its own rounded border and already recolours it for focus. The wrapper used to paint a second,
 * thicker ring around that border whenever the row held the cursor - the "rectangle in a rectangle"
 * the maintainer photographed on the Plex connect screen (2026-10-18). The ring is gone; the
 * field's own box is the only outline, and these pins keep a second one from creeping back in
 * behind a colour change.
 */
class TvTextFieldFocusStyleTest {

    private val tvTextField: String by lazy {
        File("src/main/java/app/opentv/ui/components/TvTextField.kt").readText()
    }

    @Test
    fun theComponent_drawsNoOutlineAroundTheField() {
        // `Modifier.border` was the wrapper's ring. Nothing in this component draws an outline of
        // its own any more: the only box on screen is the Material field's.
        assertThat(tvTextField.contains("foundation.border")).isFalse()
        assertThat(Regex("""\.border\(""").containsMatchIn(tvTextField)).isFalse()
    }

    @Test
    fun theOneBox_carriesTheFocusAccentItself() {
        // Focus stays obvious without a second outline: the box's border, its fill and the trailing
        // pencil all move to the accent while the row holds the cursor (isBoxFocused).
        assertThat(tvTextField.contains("focusedBorderColor = AppTheme.primary")).isTrue()
        assertThat(
            tvTextField.contains(
                "unfocusedBorderColor = if (isBoxFocused) AppTheme.primary else AppTheme.palette.textDisabled",
            ),
        ).isTrue()
        assertThat(
            tvTextField.contains(
                "unfocusedContainerColor = if (isBoxFocused) AppTheme.cardFocusBg else AppTheme.palette.chipSurface",
            ),
        ).isTrue()
    }
}
