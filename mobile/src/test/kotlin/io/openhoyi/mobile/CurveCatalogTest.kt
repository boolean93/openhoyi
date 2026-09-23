package io.openhoyi.mobile

import io.openhoyi.protocol.CoffeeCommands
import org.junit.Assert.*
import org.junit.Test

class CurveCatalogTest {
    @Test fun capturedProfilesRemainExact() {
        val expected = listOf(
            "02175B006C005A410000015E1600AA00000000DA",
            "02DF5C0046001426140000A0050190008C000059",
            "02DF5C00880014231200009605019000820000AC",
        )
        assertEquals(expected, CurveCatalog.profiles.map { CoffeeCommands.start(it.parameters).frame.hex() })
        assertEquals(listOf(2700, 0, 3400), CurveCatalog.profiles.map { it.targetHundredthsGram })
        assertEquals(3, CurveCatalog.profiles.map { it.id }.toSet().size)
        assertTrue(CurveCatalog.profiles.all(CurveCatalog::validated))
        assertNull(CurveCatalog.find("unknown"))
    }
}
