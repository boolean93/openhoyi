package io.openhoyi.mobile

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import java.lang.reflect.InvocationTargetException

/** Synthetic branch fixture only. No KeyStore, URL connection or model execution is reachable. */
internal object DeepSeekApiCompatFixture {
    fun run(): String {
        var stage = "INIT"
        val inert = AiSafeTransport({ error("NO_CREDENTIAL") }, { true }, AiRequestBudget(), { error("NO_NETWORK") })
        try {
            AiSdkLogging.install()
            val client = DeepSeekLLMClient(httpClient = inert)
            val type = Class.forName("ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage\$Assistant")
            val constructor = type.constructors.single { it.parameterCount == 7 }
            val reasoning = constructor.newInstance(null, "synthetic bounded reasoning", null, null, null, null, null)
            // The prepare method only checks non-empty call-list and carries it through, never executes calls.
            val toolCall = constructor.newInstance(null, null, null, null, null, listOf<Any?>(null), null)
            val prepare = DeepSeekLLMClient::class.java.getDeclaredMethod("prepareMessagesForDeepSeek", List::class.java, Boolean::class.javaPrimitiveType)
            prepare.isAccessible = true
            stage = "MERGE"
            val result = prepare.invoke(client, listOf(reasoning, toolCall), false) as List<*>
            stage = "VERIFY"
            check(result.size == 1)
            check(type.getMethod("getReasoningContent").invoke(result.single()) == "synthetic bounded reasoning")
            check((type.getMethod("getToolCalls").invoke(result.single()) as List<*>).size == 1)
            return "SDK_API_OK"
        } catch (failure: InvocationTargetException) {
            return if (failure.cause is NoSuchMethodError) "SDK_API_MISSING_$stage" else "FIXTURE_ERROR_$stage"
        } catch (_: NoSuchMethodError) { return "SDK_API_MISSING_$stage" }
        catch (_: Throwable) { return "FIXTURE_ERROR_$stage" }
        finally { inert.close() }
    }
}
