package io.openhoyi.session

fun wireTraceChecks():Int {
    var count=0
    fun case(name:String, block:()->Unit){block();count++;println("PASS $name")}
    case("wire trace redacts entire authentication and password-change frames") {
        for(opcode in listOf(1,11)) {
            val bytes=byteArrayOf(opcode.toByte(),12,1,2,3,4,5,6,77)
            val event=wireTrace("request",7,9,KnownGatt.coffeeWrite,bytes)
            check(event.hex=="[REDACTED]" && event.size==bytes.size)
            check(event.generation==7L && event.token==9L)
            val uppercase=Endpoint(KnownGatt.coffeeWrite.service.uppercase(),KnownGatt.coffeeWrite.characteristic.uppercase())
            check(wireTrace("request",7,9,uppercase,byteArrayOf(opcode.toByte())).hex=="[REDACTED]")
            check(wireTrace("request",7,9,uppercase,ByteArray(600){opcode.toByte()}).hex=="[REDACTED]")
        }
    }
    case("wire trace preserves nonsecret bytes and only redacts coffee write endpoint") {
        check(wireTrace("notification",1,null,KnownGatt.bookooNotify,byteArrayOf(1,11,-1)).hex=="010bff")
        check(wireTrace("request",1,2,KnownGatt.coffeeWrite,byteArrayOf(2,0)).hex=="0200")
        check(wireTrace("request",1,2,KnownGatt.coffeeWrite,byteArrayOf()).hex=="")
    }
    case("wire trace bounds unknown frames and retains original length") {
        val event=wireTrace("notification",1,null,KnownGatt.coffeeNotify,ByteArray(513){0x55})
        check(event.hex=="55".repeat(512)+"[TRUNCATED]")
        check(event.size==513)
    }
    case("wire trace snapshots bytes and isolates observer failures") {
        val bytes=byteArrayOf(2,7)
        val event=wireTrace("request",1,2,KnownGatt.coffeeWrite,bytes)
        bytes.fill(0)
        check(event.hex=="0207")
        emitWireTrace({error("observer failure")},event)
    }
    return count
}
