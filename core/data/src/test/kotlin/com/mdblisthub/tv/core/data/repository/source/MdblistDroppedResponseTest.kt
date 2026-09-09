package com.mdblisthub.tv.core.data.repository.source

import com.mdblisthub.tv.core.network.dto.MdblistDroppedResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MdblistDroppedResponseTest {

    @Test
    fun `accepts a show that was updated`() {
        val response = Json.decodeFromString<MdblistDroppedResponseDto>(
            """{"updated":{"shows":1},"existing":{"shows":0},"not_found":{"shows":0},"errors":[]}""",
        )

        assertTrue(response.acceptedShow())
    }

    @Test
    fun `rejects HTTP 200 payload when no show was changed`() {
        val response = Json.decodeFromString<MdblistDroppedResponseDto>(
            """{"updated":{"shows":0},"existing":{"shows":0},"not_found":{"shows":1},"errors":[{"type":"show"}]}""",
        )

        assertFalse(response.acceptedShow())
    }
}
