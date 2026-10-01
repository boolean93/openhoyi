package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import org.junit.Assert.*
import org.junit.Test

class ResourceMessageTest {
    @Test fun snapshotCanRerenderWithoutChangingCapturedTextOrState() {
        val template=ResourceMessage(R.string.service_event_shot_state,"RUNNING")
        val message=SnapshotMessage.resource(template,DefaultStringResources::resolve)
        val snapshot=MobileSnapshot(coffeeState=DeviceState.READY,message=message)
        val original=message.initialText
        repeat(3) {
            assertEquals("new language: RUNNING",snapshot.messageForDisplay { id,args ->
                assertEquals(R.string.service_event_shot_state,id)
                "new language: ${args[0]}"
            })
        }
        assertEquals(original,snapshot.message?.initialText)
        assertEquals(DeviceState.READY,snapshot.coffeeState)
        assertSame(message,snapshot.copy(scanning=true).message)
    }
    @Test fun rawAndEmptyMessagesNeverFallBackOrBecomeFormatInstructions() {
        for(value in listOf("","device %1\$s","UNKNOWN")) {
            val snapshot=MobileSnapshot(message=SnapshotMessage.raw(value))
            assertEquals(value,snapshot.messageForDisplay { _,_ -> error("Raw message must not resolve resources") })
        }
        val message=SnapshotMessage.resource(ResourceMessage(R.string.service_tare_unknown),DefaultStringResources::resolve)
        assertEquals("",MobileSnapshot(message=message).messageForDisplay { _,_ -> "" })
    }
    @Test fun mutableArgumentsAreFrozenAndResolverCannotModifyFutureArguments() {
        val argument=StringBuilder("before")
        val template=ResourceMessage(R.string.service_event_shot_state,argument)
        argument.append(" changed")
        assertEquals("before",template.render { _,args -> args[0].toString() })
        template.render { _,args ->
            @Suppress("UNCHECKED_CAST")
            (args as Array<Any?>)[0]="tampered"
            "ignored"
        }
        assertEquals("before",template.render { _,args -> args[0].toString() })
    }
    @Test fun numericAndNullArgumentsKeepTheirFormatterTypes() {
        val message=SnapshotMessage.resource(ResourceMessage(R.string.application_version,"0.1",7),DefaultStringResources::resolve)
        assertEquals("应用版本：0.1（7）",message.initialText)
        val template=ResourceMessage(R.string.service_event_shot_state,null)
        assertEquals("null",template.render { _,args -> String.format("%s",*args) })
    }
    @Test fun mutableNumericSubclassesAreCapturedAsText() {
        val integer = object : java.math.BigInteger("7") {
            var label = "before integer"
            override fun toByte(): Byte = 7
            override fun toShort(): Short = 7
            override fun toString() = label
        }
        val decimal = object : java.math.BigDecimal("7.5") {
            var label = "before decimal"
            override fun toByte(): Byte = 7
            override fun toShort(): Short = 7
            override fun toString() = label
        }
        val template = ResourceMessage(R.string.application_package, integer, decimal)
        integer.label = "changed integer"
        decimal.label = "changed decimal"
        assertEquals("before integer / before decimal", template.render { _, args ->
            "${args[0]} / ${args[1]}"
        })
        val exact = ResourceMessage(R.string.application_package,
            java.math.BigInteger("7"), java.math.BigDecimal("7.5"))
        assertEquals("7 / 7.5", exact.render { _, args -> java.lang.String.format(java.util.Locale.ROOT, "%d / %.1f", *args) })
    }
    @Test fun diagnosticRepresentationDoesNotExposeArguments() {
        val resource=ResourceMessage(R.string.application_package,"private-value")
        val message=SnapshotMessage.resource(resource) { _,args -> args[0].toString() }
        assertFalse(resource.toString().contains("private-value"))
        assertFalse(message.toString().contains("private-value"))
    }
    @Test fun allDefaultTemplatesMatchTheirPreviousFormatting() {
        val keys=org.json.JSONObject(java.io.File("../localization/catalog/source.json").readText())
            .getJSONObject("strings").keys().asSequence().toList()
        assertEquals(872,keys.size)
        val pattern=Regex("%([1-9][0-9]*)\\$([0-9]*)([sd])")
        for(key in keys) {
            val id=R.string::class.java.getField(key).getInt(null)
            val format=DefaultStringResources.template(id)
            val matches=pattern.findAll(format).toList()
            val args=Array<Any?>(matches.maxOfOrNull { it.groupValues[1].toInt() } ?: 0) { "参数-${it+1}" }
            for(match in matches) if(match.groupValues[3]=="d") args[match.groupValues[1].toInt()-1]=7
            val expected=DefaultStringResources.resolve(id,args)
            var captures=0
            val message=SnapshotMessage.resource(ResourceMessage(id,*args)) { resource,values ->
                captures++
                DefaultStringResources.resolve(resource,values)
            }
            assertEquals(1,captures)
            assertEquals(key,expected,message.initialText)
            assertEquals(key,expected,message.render(DefaultStringResources::resolve))
            assertEquals(1,captures)
        }
    }
}
