package app.opentv

import app.opentv.data.model.PlexRef
import app.opentv.data.repo.CatalogRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The player screen keys Plex-only behaviour off the media key, so the namespace it depends on is
 * pinned here: building a key and reading it back must round-trip, and anything that is not a Plex
 * key must read as null rather than as a Plex item with a garbage identity.
 */
class PlexMediaKeyTest {

    @Test
    fun `a built key parses back to the same source and item`() {
        val key = CatalogRepository.plexMediaKey(sourceId = 7L, ratingKey = "12345")
        assertThat(CatalogRepository.parsePlexMediaKey(key))
            .isEqualTo(PlexRef(sourceId = 7L, ratingKey = "12345"))
    }

    @Test
    fun `every other key namespace parses as not-plex`() {
        assertThat(CatalogRepository.parsePlexMediaKey("ep:12")).isNull()
        assertThat(CatalogRepository.parsePlexMediaKey("movie:34")).isNull()
        assertThat(CatalogRepository.parsePlexMediaKey("catchup:56")).isNull()
        assertThat(CatalogRepository.parsePlexMediaKey("rec:78")).isNull()
        assertThat(CatalogRepository.parsePlexMediaKey("")).isNull()
    }

    @Test
    fun `malformed plex keys parse as not-plex rather than throwing`() {
        assertThat(CatalogRepository.parsePlexMediaKey("plex:")).isNull()
        assertThat(CatalogRepository.parsePlexMediaKey("plex:abc")).isNull()
        assertThat(CatalogRepository.parsePlexMediaKey("plex:7:")).isNull()
        assertThat(CatalogRepository.parsePlexMediaKey("plex::123")).isNull()
        assertThat(CatalogRepository.parsePlexMediaKey("plex:notanumber:123")).isNull()
    }

    @Test
    fun `a rating key containing colons survives the round trip`() {
        val key = CatalogRepository.plexMediaKey(sourceId = 7L, ratingKey = "a:b:c")
        assertThat(CatalogRepository.parsePlexMediaKey(key)?.ratingKey).isEqualTo("a:b:c")
    }
}
