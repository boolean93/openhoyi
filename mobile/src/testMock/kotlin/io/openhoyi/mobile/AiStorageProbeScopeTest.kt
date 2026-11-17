package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AiStorageProbeScopeTest {
    @Test fun onlyCanonicalInternalIdsAndKnownAliasKinds() {
        val id=UUID.randomUUID().toString()
        assertEquals(id,AiStorageProbeScope.checked(id))
        assertEquals("io.openhoyi.mobile.mock.storage_probe.$id.session",AiStorageProbeScope.alias(id,"session"))
        listOf("../x",id.uppercase(),"", "00000000-0-0-0-0").forEach { assertTrue(runCatching { AiStorageProbeScope.checked(it) }.isFailure) }
        assertTrue(runCatching { AiStorageProbeScope.alias(id,"deepseek_api_key") }.isFailure)
    }
    @Test fun fixedProviderPathsNoQueryOrForeignAuthority() {
        AiStorageProbeScope.paths.forEach { assertTrue(AiStorageProbeScope.accepts(AiStorageProbeScope.AUTHORITY,it,null,null)) }
        assertFalse(AiStorageProbeScope.accepts("media","/image",null,null))
        assertFalse(AiStorageProbeScope.accepts(AiStorageProbeScope.AUTHORITY,"/../image",null,null))
        assertFalse(AiStorageProbeScope.accepts(AiStorageProbeScope.AUTHORITY,"/image","path=secret",null))
        assertFalse(AiStorageProbeScope.accepts(AiStorageProbeScope.AUTHORITY,"/image",null,"x"))
    }
}
