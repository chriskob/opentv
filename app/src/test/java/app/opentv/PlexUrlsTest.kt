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
    fun `an image url resolves plex's relative thumb path and adds the token`() {
        val url = PlexUrls.image(server, "/library/metadata/54321/thumb/999", token)
        assertThat(url).isEqualTo("https://plex.buickgn.us/library/metadata/54321/thumb/999?X-Plex-Token=$token")
    }

    @Test
    fun `an image url can request a width, which is how tv-sized art is fetched cheaply`() {
        val url = PlexUrls.image(server, "/library/metadata/54321/thumb/999", token, width = 300)
        assertThat(url).isEqualTo("https://plex.buickgn.us/library/metadata/54321/thumb/999/300?X-Plex-Token=$token")
    }

    @Test
    fun `a missing thumb yields no url rather than a broken one`() {
        assertThat(PlexUrls.image(server, null, token)).isNull()
        assertThat(PlexUrls.image(server, "", token)).isNull()
        assertThat(PlexUrls.image(server, "   ", token)).isNull()
    }

    @Test
    fun `an already-absolute image url is not given a second token`() {
        val once = "https://images.plex.tv/photo?key=abc"
        assertThat(PlexUrls.image(server, once, token)).isEqualTo("https://images.plex.tv/photo?key=abc&X-Plex-Token=$token")
        val twice = "https://images.plex.tv/photo?key=abc&X-Plex-Token=already-here"
        assertThat(PlexUrls.image(server, twice, token)).isEqualTo(twice)
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
        // Bracketed context keys are percent-encoded, and a space in the device name too.
        assertThat(url).contains("context%5Bdevice%5D%5Bproduct%5D=OpenTV")
        assertThat(url).contains("Living+Room")
        // Nothing before the '#' carries the code.
        assertThat(url.substringBefore('#')).doesNotContain("ABCD")
    }
}
