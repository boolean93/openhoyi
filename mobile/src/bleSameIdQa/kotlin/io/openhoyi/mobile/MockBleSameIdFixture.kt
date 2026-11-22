package io.openhoyi.mobile

/**
 * Explicit integration point for the detached MobileService/fake-GATT fixture.
 * Takes no Activity, Context or device address, so it cannot accidentally reuse the live service.
 * The eventual body may construct a fresh hermetic service host through its approved factory.
 */
internal object MockBleSameIdFixture {
    fun run(): Result = Result.Blocked

    sealed interface Result {
        data object Blocked : Result
        fun render(chinese: Boolean): String = when (this) {
            Blocked -> if (chinese) "未运行：模拟服务尚未接入。没有发出蓝牙命令。"
                else "Not run: the fake service is not wired. No Bluetooth command was sent."
        }
    }
}
