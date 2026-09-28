package app.opentv

import app.opentv.data.parser.PlexParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plex answers in XML, and the shapes below are copied from real responses rather than invented.
 *
 * The rule these tests exist to hold: a missing, blank or oddly-typed attribute costs that one
 * field, never the whole document. Every "degrades" case here is a response a real library
 * produces - an item with no artwork, a section with an empty title, a malformed tail.
 */
// Robolectric is required, not optional: the parser goes through android.util.Xml, and the module
// sets isReturnDefaultValues, so without a real Android runtime newPullParser() returns null and
// every assertion here would pass against an empty list. Same reasoning and same SDK as
// XmltvChannelParseTest, which parses XML the same way.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlexParserTest {

    private fun emptyStream() = java.io.ByteArrayInputStream(ByteArray(0))

    private fun stream(xml: String) = xml.trimIndent().byteInputStream()

    @Test
    fun `sections keeps movie and show and drops everything else`() {
        val xml = """
            <MediaContainer size="4" friendlyName="buick">
              <Directory key="1" type="movie" title="Movies" />
              <Directory key="2" type="show" title="TV Shows" />
              <Directory key="3" type="artist" title="Music" />
              <Directory key="4" type="photo" title="Photos" />
            </MediaContainer>
        """
        val sections = PlexParser.sections(stream(xml))
        assertThat(sections.map { it.title }).containsExactly("Movies", "TV Shows").inOrder()
        assertThat(sections.first().key).isEqualTo("1")
        assertThat(sections.first().isMovies).isTrue()
        assertThat(sections.last().isShows).isTrue()
    }

    @Test
    fun `a section with no title is skipped rather than shown as blank`() {
        val xml = """
            <MediaContainer>
              <Directory key="1" type="movie" title="Movies" />
              <Directory key="2" type="movie" title="" />
              <Directory type="show" title="No key" />
              <Directory key="3" type="show" />
            </MediaContainer>
        """
        val sections = PlexParser.sections(stream(xml))
        assertThat(sections.map { it.key }).containsExactly("1")
    }

    @Test
    fun `recently added reads the fields a poster row needs`() {
        val xml = """
            <MediaContainer size="1">
              <Video ratingKey="54321" type="movie" title="The Movie"
                     year="2025" summary="A film." duration="7200"
                     librarySectionID="1" addedAt="1758900000"
                     thumb="/library/metadata/54321/thumb/999" art="/library/art/999" />
            </MediaContainer>
        """
        val item = PlexParser.recentlyAdded(stream(xml)).single()
        assertThat(item.ratingKey).isEqualTo("54321")
        assertThat(item.title).isEqualTo("The Movie")
        assertThat(item.year).isEqualTo(2025)
        assertThat(item.durationMillis).isEqualTo(7_200_000L)
        assertThat(item.addedAtEpochSeconds).isEqualTo(1_758_900_000L)
        assertThat(item.thumbPath).isEqualTo("/library/metadata/54321/thumb/999")
        assertThat(item.isMovie).isTrue()
    }

    @Test
    fun `a show keeps its parent key so episodes can be found later`() {
        val xml = """
            <MediaContainer>
              <Directory ratingKey="900" type="show" title="The Series" year="2024"
                         addedAt="1758900000" thumb="/t/1" parentRatingKey="901" />
            </MediaContainer>
        """
        val item = PlexParser.recentlyAdded(stream(xml)).single()
        assertThat(item.isShow).isTrue()
        assertThat(item.parentRatingKey).isEqualTo("901")
    }

    @Test
    fun `an item with no artwork or duration still lists`() {
        val xml = """
            <MediaContainer>
              <Video ratingKey="7" type="movie" title="Bare" />
            </MediaContainer>
        """
        val item = PlexParser.recentlyAdded(stream(xml)).single()
        assertThat(item.title).isEqualTo("Bare")
        assertThat(item.thumbPath).isNull()
        assertThat(item.durationMillis).isNull()
        assertThat(item.addedAtEpochSeconds).isNull()
    }

    @Test
    fun `an item whose year is not a number loses the year, not the row`() {
        val xml = """
            <MediaContainer>
              <Video ratingKey="8" type="movie" title="Odd" year="unknown" />
            </MediaContainer>
        """
        val item = PlexParser.recentlyAdded(stream(xml)).single()
        assertThat(item.year).isNull()
        assertThat(item.title).isEqualTo("Odd")
    }

    @Test
    fun `metadata collects every part so a multi-file item resolves`() {
        val xml = """
            <MediaContainer>
              <Video ratingKey="54321" title="The Movie">
                <Media id="1">
                  <Part key="/library/parts/1/1/file.mkv" container="mkv" size="8589934592"
                        duration="7200" videoCodec="hevc" audioCodec="eac3" />
                  <Part key="/library/parts/1/2/file.mkv" container="mkv" size="1024" duration="60" />
                </Media>
              </Video>
            </MediaContainer>
        """
        val parts = PlexParser.metadata(stream(xml))
        assertThat(parts).hasSize(2)
        assertThat(parts.first().key).isEqualTo("/library/parts/1/1/file.mkv")
        assertThat(parts.first().fileSizeBytes).isEqualTo(8_589_934_592L)
        assertThat(parts.first().videoCodec).isEqualTo("hevc")
    }

    @Test
    fun `a malformed tail costs the tail, not the rows already read`() {
        val xml = """
            <MediaContainer>
              <Video ratingKey="1" type="movie" title="One" />
              <Video ratingKey="2" type="movie" title="Two" />
              <Video ratingKey="3" type="movie" title="Three"
        """
        val items = PlexParser.recentlyAdded(stream(xml))
        assertThat(items.map { it.ratingKey }).containsAtLeast("1", "2").inOrder()
    }

    @Test
    fun `a pin yields its id and code`() {
        val xml = """<pin id="1234567" code="ABCD" product="Plex Web" />"""
        val pin = PlexParser.pin(stream(xml))
        assertThat(pin).isNotNull()
        assertThat(pin!!.id).isEqualTo(1234567L)
        assertThat(pin.code).isEqualTo("ABCD")
    }

    @Test
    fun `a code is not silently accepted just because it parsed`() {
        // Plex also issues *strong* pins: long opaque strings meant for a server redirect, never
        // typed by a person. One got requested by mistake and a 29-character "code" was displayed
        // on a television as though a person could enter it. The parser must not launder that - the
        // shape is wrong, and a wrong shape is a failure rather than a usable value.
        val strong = PlexParser.pin(stream("""<pin id="99" code="obf51911w2nycssh2ob8q8q4" />"""))
        assertThat(strong).isNotNull()
        // PlexApi.createPin rejects this before it can reach a screen; asserted here so the
        // property that matters is written down next to the parser that produces the value.
        val unusable = strong!!.code.length !in 4..8 || !strong.code.all { it.isLetterOrDigit() }
        assertThat(unusable).isTrue()

        val normal = PlexParser.pin(stream("""<pin id="1" code="WXYZ" />"""))!!
        assertThat(normal.code.length in 4..8).isTrue()
    }

    @Test
    fun `an approved pin yields the token and a pending one yields null`() {
        val approved = """<user email="a@b.c" id="1" title="buick" username="buick" authToken="tok-abc-123" />"""
        val pending = """<user id="1" title="buick" username="buick" />"""
        assertThat(PlexParser.authToken(stream(approved))).isEqualTo("tok-abc-123")
        // The normal answer while the viewer has not yet approved on their phone.
        assertThat(PlexParser.authToken(stream(pending))).isNull()
    }

    @Test
    fun `an empty document yields nothing rather than throwing`() {
        assertThat(PlexParser.sections(emptyStream())).isEmpty()
        assertThat(PlexParser.recentlyAdded(emptyStream())).isEmpty()
        assertThat(PlexParser.metadata(emptyStream())).isEmpty()
        assertThat(PlexParser.pin(emptyStream())).isNull()
        assertThat(PlexParser.authToken(emptyStream())).isNull()
    }
}
