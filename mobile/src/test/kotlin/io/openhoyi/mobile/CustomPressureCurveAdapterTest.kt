package io.openhoyi.mobile

import io.openhoyi.protocol.CoffeeCommands
import io.openhoyi.protocol.CustomPressureStartPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class CustomPressureCurveAdapterTest {
    private fun resource(name:String)=requireNotNull(javaClass.classLoader!!.getResourceAsStream(name))
        .bufferedReader().use {it.readText()}
    private fun documents():List<CustomCurveDocument> {
        val rows=JSONObject(resource("custom-pressure-curves.json")).getJSONArray("items")
        return (0 until rows.length()).map {CustomCurveDocument.decode(rows.getJSONObject(it).getJSONObject("customDocument").toString())}
    }
    @Test fun independentEvidenceBindsTheExactInputAndOrderedSlots() {
        val input=resource("custom-pressure-curves.json")
        val hash=MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        val lines=resource("custom-pressure-wire.tsv").trimEnd().lines()
        assertTrue(lines.first().startsWith("# legacy-curve-wire-v1\tsource-sha256="))
        assertTrue(lines.first().endsWith("export-sha256=$hash"))
        for((position,line) in lines.drop(1).withIndex()) {
            val fields=line.split('\t')
            assertEquals(4,fields.size)
            assertEquals(position/6,fields[0].toInt())
            assertEquals(listOf(7,1,2,3,4,5)[position%6],fields[1].toInt())
        }
    }
    @Test fun supportedPressureRecipesMatchIndependentLegacyEncoderAtEverySlotAndScaleMode() {
        val docs=documents()
        val lines=resource("custom-pressure-wire.tsv").trimEnd().lines()
        assertTrue(lines.first().contains("encoder-sha256=635ddbe7bbc63d8d74b5053cd5c2ca1d5e8a0dfab0f24447171e5043fd01855f"))
        assertEquals(72,docs.size);assertEquals(docs.size*6+1,lines.size)
        for(line in lines.drop(1)) {
            val fields=line.split('\t');val doc=docs[fields[0].toInt()];val slot=fields[1].toInt()
            for(scale in listOf(false,true)) {
                val profile=CustomPressureCurveAdapter.profile(doc,scale,slot)
                assertNotNull("Supported recipe ${doc.id} slot=$slot scale=$scale",profile)
                profile!!
                assertTrue(CustomPressureStartPolicy.permits(profile.parameters))
                assertEquals(doc.id,profile.id)
                assertEquals(if(scale)doc.targetHundredthsGram else 0,profile.targetHundredthsGram)
                assertEquals(fields[if(scale)3 else 2],CoffeeCommands.start(profile.parameters).frame.hex())
            }
        }
    }
    @Test fun unsupportedOrLossyParametersAreRejectedWithoutSilentRounding() {
        val doc=documents()[2]
        assertNull(CustomPressureCurveAdapter.profile(doc.copy(controlMode=CustomCurveDocument.ControlMode.FLOW_RAW),false))
        assertNull(CustomPressureCurveAdapter.profile(doc.copy(stages=listOf(CustomCurveDocument.Stage(90,351))),false))
        assertNull(CustomPressureCurveAdapter.profile(doc.copy(targetHundredthsGram=1801),true))
        assertNull(CustomPressureCurveAdapter.profile(doc.copy(targetHundredthsGram=1801),false))
        assertNull(CustomPressureCurveAdapter.profile(doc.copy(stages=listOf(CustomCurveDocument.Stage(121,350))),false))
        assertNull(CustomPressureCurveAdapter.profile(doc.copy(temperatureC=106),false))
        assertNull(CustomPressureCurveAdapter.profile(doc.copy(stages=emptyList()),false))
        for(slot in listOf(0,6,8,-1))assertNull(CustomPressureCurveAdapter.profile(doc,false,slot))
    }
    @Test fun parameterAdaptationAloneDoesNotGrantExecutionPermission() {
        val doc=documents().first()
        val profile=CustomPressureCurveAdapter.profile(doc,false)
        assertNotNull(profile)
        assertFalse(CurveCatalog.validated(profile!!))
        val item=CurveLibraryItem(doc.id,doc.name,"draft","",null,customDocument=doc)
        val library=CurveLibrary(emptyList(),draftsProvider={listOf(item)})
        assertFalse(library.canStart(item));assertNull(library.resolve(doc.id,false))
    }
}
