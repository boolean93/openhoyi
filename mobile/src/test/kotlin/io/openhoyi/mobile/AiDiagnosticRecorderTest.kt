package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiDiagnosticRecorderTest {
    @Test fun failureThenManualSendIsLinkedWithoutPayloadAndSuccessResetsLink() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        var now = 0L
        val recorder = AiDiagnosticRecorder({ kind, fields -> events += kind to fields }, { now })
        val first = recorder.begin("PRIVATE_KEY_BODY_URL_FILENAME")
        now = 12_000_000
        recorder.failed(first, AiProblem.NETWORK, AiDiagnostic(AiStage.CONNECT, AiFailureKind.CONNECT, 12, 999))
        val second = recorder.begin(AiModelPolicy.FLASH)
        recorder.succeeded(second)
        recorder.begin(AiModelPolicy.PRO)
        assertEquals(first.id, events[2].second["previousUnsuccessfulAttemptId"])
        assertNotEquals(first.id, second.id)
        assertFalse(events.last().second.containsKey("previousUnsuccessfulAttemptId"))
        assertEquals("unknown", events.first().second["model"])
        assertEquals("12", events[1].second["durationMs"])
        assertFalse(events[1].second.containsKey("httpStatus"))
        val allowed = setOf("scope", "attemptId", "model", "previousUnsuccessfulAttemptId", "durationMs", "problem", "stage", "failureKind", "httpStatus")
        assertTrue(events.all { it.second.keys.all(allowed::contains) })
        assertFalse(events.toString().contains("PRIVATE"))
    }
    @Test fun cancellationIsTerminalAndLateCallbacksCannotChangeNextAttempt() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val recorder = AiDiagnosticRecorder({ kind, fields -> events += kind to fields })
        val first = recorder.begin(AiModelPolicy.FLASH)
        recorder.cancelled(first)
        val second = recorder.begin(AiModelPolicy.PRO)
        recorder.failed(first, AiProblem.NETWORK, null)
        recorder.cancelled(first)
        recorder.succeeded(first)
        recorder.succeeded(second)
        recorder.begin(AiModelPolicy.FLASH)
        assertEquals(listOf("ai.reply.started", "ai.reply.cancelled", "ai.reply.started", "ai.reply.succeeded", "ai.reply.started"), events.map { it.first })
        assertFalse(events.last().second.containsKey("previousUnsuccessfulAttemptId"))
    }
    @Test fun sinkFailureNeverChangesRequestAndDurationsAreBounded() {
        var now = 10_000_000L
        val events = mutableListOf<Map<String, String>>()
        val recorder = AiDiagnosticRecorder({ _, fields -> events += fields; throw IllegalStateException("PRIVATE") }, { now })
        val first = recorder.begin(AiModelPolicy.FLASH)
        now = 0
        recorder.failed(first, AiProblem.AUTH, AiDiagnostic(AiStage.RESPONSE_HEADERS, AiFailureKind.HTTP, 0, 401))
        assertEquals("0", events.last()["durationMs"])
        assertEquals("401", events.last()["httpStatus"])
        val second = recorder.begin(AiModelPolicy.FLASH)
        now = Long.MAX_VALUE
        recorder.succeeded(second)
        assertEquals("86400000", events.last()["durationMs"])
    }
    @Test fun replyEventsActuallyReachExistingDiagnosticZipWithBuildIdentity() {
        val dir = java.nio.file.Files.createTempDirectory("ai-trace-test").toFile()
        try {
            io.openhoyi.trace.TraceStore(dir, appInfo = io.openhoyi.trace.TraceAppInfo("test", 140, "mock")).use { store ->
                val recorder = AiDiagnosticRecorder(record = store::record)
                val first = recorder.begin(AiModelPolicy.FLASH)
                recorder.failed(first, AiProblem.NETWORK, AiDiagnostic(AiStage.CONNECT, AiFailureKind.CONNECT, 1))
                recorder.succeeded(recorder.begin(AiModelPolicy.FLASH))
                val bytes = java.io.ByteArrayOutputStream()
                val done = java.util.concurrent.CountDownLatch(1)
                var failure: Throwable? = null
                store.export({ bytes }) { failure = it; done.countDown() }
                assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
                assertNull(failure)
                val logs = StringBuilder()
                java.util.zip.ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name.endsWith(".jsonl")) logs.append(zip.readBytes().toString(Charsets.UTF_8))
                    }
                }
                assertTrue(logs.contains("ai.reply.failed"))
                assertTrue(logs.contains("ai.reply.succeeded"))
                assertTrue(logs.contains("previousUnsuccessfulAttemptId"))
                assertTrue(logs.contains("\"versionCode\":140"))
                assertFalse(logs.contains("PRIVATE"))
            }
        } finally { dir.deleteRecursively() }
    }

}
