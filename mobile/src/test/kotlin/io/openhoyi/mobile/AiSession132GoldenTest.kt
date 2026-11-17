package io.openhoyi.mobile

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic golden in the exact schema2 shape read from candidate132 codec source, never user storage. */
class AiSession132GoldenTest {
    @Test fun schema132NonemptyDraftSummaryReasoningToolsUsageAndLegacyEpochSurviveSchema4() {
        val raw="""{
          "schema":2,"epoch":"00000000-0000-0000-0000-000000000000","draft":"未保存草稿：保持磨豆刻度 2.5，勿发请求。",
          "lines":[{"user":true,"text":"旧问题"},{"user":false,"text":"旧回答"},{"user":true,"text":"最近问题"},{"user":false,"text":"最近完整回答"}],
          "context":{"summary":"不可信历史摘要；提案未批准，禁止从摘要执行。","coveredLines":2,"reportedInput":150123,"reportedOutput":321},
          "protocol":[
            {"role":"user","content":"最近问题"},
            {"role":"assistant","content":null,"reasoning_content":"SYNTHETIC_REASONING_132","tool_calls":[{"id":"call132","type":"function","function":{"name":"current_status","arguments":"{}"}}]},
            {"role":"tool","tool_call_id":"call132","content":"{\"status\":\"unknown\"}"},
            {"role":"assistant","content":"最近完整回答","reasoning_content":null}
          ],
          "usage":{"schema":1,"since":1794700800000,"incomplete":true,"searches":2,"searchNonempty":1,"searchEmpty":0,"searchFailed":0,
            "models":[{"model":"deepseek-flash","requests":3,"reported":2,"input":150123,"output":321,"cacheHit":100000,"cacheMiss":50123,"cacheReported":1}]}
        }""".toByteArray(Charsets.UTF_8)
        val legacy=AiSessionCodec.decode(raw)
        assertEquals(AiCredentialEpochPolicy.LEGACY,legacy.epoch);assertEquals(AiModelPolicy.FLASH,legacy.modelId)
        assertEquals("未保存草稿：保持磨豆刻度 2.5，勿发请求。",legacy.draft);assertEquals(4,legacy.lines.size)
        assertEquals(2,legacy.protocol.coveredLines);assertEquals(150123L,legacy.protocol.reportedInput);assertEquals(321L,legacy.protocol.reportedOutput)
        assertTrue(legacy.protocol.summary!!.contains("提案未批准"));assertTrue(legacy.usage.incomplete)
        assertEquals(1L,legacy.usage.unknownChat);assertEquals(1L,legacy.usage.unknownSearch)
        assertEquals("SYNTHETIC_REASONING_132",legacy.protocol.messages[1]["reasoning_content"]!!.jsonPrimitive.content)
        assertEquals("call132",legacy.protocol.messages[2]["tool_call_id"]!!.jsonPrimitive.content)
        val migrated=AiSessionCodec.decode(AiSessionCodec.encode(legacy))
        assertEquals(legacy.lines,migrated.lines);assertEquals(legacy.draft,migrated.draft)
        assertEquals(legacy.protocol.messages,migrated.protocol.messages);assertEquals(legacy.protocol.summary,migrated.protocol.summary)
        assertEquals(legacy.usage,migrated.usage);assertTrue(migrated.inactive.isEmpty());assertTrue(migrated.ocr.isEmpty())
        assertTrue(AiContextPolicy.matches(migrated.protocol,migrated.lines))
    }
}
