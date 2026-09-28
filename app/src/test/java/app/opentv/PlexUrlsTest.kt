package app.opentv

import app.opentv.data.remote.PlexUrls
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The URL strings are where Plex integration quietly breaks, and they are the only part of it that
 * can be checked without a Plex account - so they are checked here rather than discovered live.
 */
class PlexUrlsTest {

    private val server = "https://plex.buickgn.us"
    private val token = "tok-SECRET-abc123"
    private val clientId = "opentv-device-uuid"

    @Test
    fun `a play url carries the token, requests hls, and keeps plex's own part path`() {
        val url = PlexUrls.play(
            serverBase = server,
            partKey = "/library/parts/12/34/file.mkv",
            token = token,
            clientIdentifier = clientId,
        )
        assertThat(url).isEqualTo(
            "https://plex.buickgn.us/library/parts/12/34/file.mkv" +
                "?X-Plex-Token=tok-SECRET-abc123&protocol=hls&session=opentv-device-uuid" +
                "&X-Plex-Client-Identifier=opentv-device-uuid",
        )
    }

    @Test
    fun `direct play drops the hls parameters but keeps the token`() {
        val url = PlexUrls.play(server, "/library/parts/1/2/file.mkv", token, clientId, hls = false)
        assertThat(url).isEqualTo("https://plex.buickgn.us/library/parts/1/2/file.mkv?X-Plex-Token=$token")
        assertThat(url).doesNotContain("protocol")
        assertThat(url).doesNotContain("session=")
    }

    @Test
    fun `a trailing slash on the server address does not double up`() {
        val url = PlexUrls.play("https://plex.buickgn.us/", "/library/parts/1/2/file.mkv", token, clientId)
        assertThat(url).startsWith("https://plex.buickgn.us/library/parts/1/2/file.mkv?")
        assertThat(url).doesNotContain(".gus//")
    }

    @Test
    fun `a part key without a leading slash is still joined correctly`() {
        val url = PlexUrls.play(server, "library/parts/1/2/file.mkv", token, clientId)
        assertThat(url).startsWith("https://plex.buickgn.us/library/parts/1/2/file.mkv?")
    }

    @Test
    fun `an image url resolves plex's relative thumb path and carries no token`() {
        val url = PlexUrls.image(server, "/library/metadata/54321/thumb/999")
        assertThat(url).isEqualTo("https://plex.buickgn.us/library/metadata/54321/thumb/999")
    }

    @Test
    fun `an image url can request a width, which is how tv-sized art is fetched cheaply`() {
        val url = PlexUrls.image(server, "/library/metadata/54321/thumb/999", width = 300)
        assertThat(url).isEqualTo("https://plex.buickgn.us/library/metadata/54321/thumb/999/300")
    }

    @Test
    fun `no image url ever contains the token`() {
        // The token moved to an `X-Plex-Token` header. A credential in a query string ends up in
        // Coil's disk-cache keys, in any log that prints a poster URL, and behind a reverse proxy
        // in front of whatever rules it applies - and every Plex poster failed while it was there.
        val paths = listOf(
            "/library/metadata/54321/thumb/999",
            "/library/art/12",
            "https://images.plex.tv/photo?key=abc&X-Plex-Token=leaked",
            "/library/metadata/1/thumb/2?X-Plex-Token=leaked",
        )
        for (path in paths) {
            val url = PlexUrls.image(server, path, width = 300)
            assertThat(url).doesNotContain(token)
            assertThat(url).doesNotContain("X-Plex-Token")
            assertThat(url).doesNotContain("leaked")
        }
    }

    @Test
    fun `a missing thumb yields no url rather than a broken one`() {
        assertThat(PlexUrls.image(server, null)).isNull()
        assertThat(PlexUrls.image(server, "")).isNull()
        assertThat(PlexUrls.image(server, "   ")).isNull()
    }

    @Test
    fun `redact removes the token from a play url`() {
        val url = PlexUrls.play(server, "/library/parts/1/2/file.mkv", token, clientId)
        val safe = PlexUrls.redact(url)
        assertThat(safe).doesNotContain(token)
        assertThat(safe).contains("X-Plex-Token=***")
        // The rest of the url must survive, or a redacted log is useless for diagnosis.
        assertThat(safe).contains("protocol=hls")
        assertThat(safe).contains("/library/parts/1/2/file.mkv")
    }

    @Test
    fun `redact handles the token appearing last, first, and as a path segment`() {
        assertThat(PlexUrls.redact("https://h/x?a=1&X-Plex-Token=$token"))
            .isEqualTo("https://h/x?a=1&X-Plex-Token=***")
        assertThat(PlexUrls.redact("https://h/x?X-Plex-Token=$token&a=1"))
            .isEqualTo("https://h/x?X-Plex-Token=***&a=1")
        // A token that is not behind a parameter name can only be found by its value, so the token
        // has to be passed in. This is the shape a leak would take if a server ever echoed it back
        // inside a path, and it is the reason redact is not name-only.
        assertThat(PlexUrls.redact("https://h/$token/stream", token)).doesNotContain(token)
        assertThat(PlexUrls.redact("https://h/$token/stream", token)).isEqualTo("https://h/***/stream")
    }

    @Test
    fun `redact still blanks the parameter when the token we hold is a different one`() {
        // A stale url from a previous sign-in still must not print a credential.
        val stale = "https://h/x?X-Plex-Token=old-token-value"
        val safe = PlexUrls.redact(stale, "some-other-token")
        assertThat(safe).doesNotContain("old-token-value")
    }

    @Test
    fun `redact leaves a url with no token untouched`() {
        val plain = "https://plex.buickgn.us/library/sections?pretty=1"
        assertThat(PlexUrls.redact(plain)).isEqualTo(plain)
    }

    @Test
    fun `the pin approval url uses a fragment so the code never reaches plex's server`() {
        val url = PlexUrls.pinApprovalUrl(clientId, "ABCD", "OpenTV", "Living Room")
        assertThat(url).startsWith("https://app.plex.tv/auth#?")
        assertThat(url).contains("clientID=$clientId")
        assertThat(url).contains("code=ABCD")
        // Bracketed context keys are percent-encoded.
        assertThat(url).contains("context%5Bdevice%5D%5Bproduct%5D=OpenTV")
        // Nothing before the '#' carries the code.
        assertThat(url.substringBefore('#')).doesNotContain("ABCD")
    }

    @Test
    fun `a space in a fragment value is percent-encoded, never a plus`() {
        // URLEncoder is a FORM encoder: it writes a space as '+', which is right in a query string
        // and wrong in a fragment, where '+' is a literal plus. A device name reached Plex as
        // "OpenTV+on+AFTR" because of it. Percent-encoding is correct in both positions.
        val url = PlexUrls.pinApprovalUrl(clientId, "abc", "OpenTV", "OpenTV on AFTR")
        assertThat(url).contains("deviceName%5D=OpenTV%20on%20AFTR")
        assertThat(url).doesNotContain("+")
    }

    @Test
    fun `a strong pin's long code survives the url intact`() {
        // The code is an opaque token, and the auth link has to carry it byte for byte. Anything
        // that mangles it produces "unable to complete this request" after the viewer signs in.
        val strong = "obf51911w2nycssh2ob8q8q4"
        val url = PlexUrls.pinApprovalUrl(clientId, strong, "OpenTV", "OpenTV on AFTR")
        assertThat(url).contains("code=$strong")
    }
}
