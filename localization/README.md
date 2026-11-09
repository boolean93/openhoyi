# 中文与英文资源

应用仅支持简体中文 `zh-Hans` 和英文 `en`。默认中文资源在 `mobile/src/main/res/values/`，英文资源在 `values-en/`。设置中的语言选择及旧偏好回退由应用统一处理。

`catalog/source.json` 是默认 `strings.xml` 的中文快照，包含文件 SHA256 和原始模板；`catalog/en.json` 是对应英文目录。`drafts/safety/en.json` 和 `drafts/basic-controls/en.json` 保留英文分组来源。其他语言不再维护或打包。历史实施记录保留在 `docs/plans/`，不作为当前支持声明。

## 检查与生成

```sh
python3 -m unittest discover -s localization/tests
python3 localization/validate_catalog.py
python3 localization/android_resources.py --check
```

校验检查中文快照与默认 XML 一致、英文完整覆盖，以及重复键、空值、格式参数、换行和非法控制字符。格式参数允许调整顺序，索引、类型、补零宽度和次数必须一致。

修改英文 `catalog/en.json` 后运行 `python3 localization/android_resources.py` 生成 `values-en/strings.xml`，不要直接修改该生成文件。共享 `app_name` 不生成英文覆盖，保留各构建变体名称。功能拆分资源的中文和英文 XML 也由 APK 校验器逐项核对。

构建两个 APK 后可检查实际编译资源：

```sh
python3 localization/verify_apk_resources.py \
  --aapt2 "$ANDROID_HOME/build-tools/35.0.0/aapt2" \
  --android-jar "$ANDROID_HOME/platforms/android-35/android.jar" \
  --alpha mobile/build/outputs/apk/alpha/debug/mobile-alpha-debug.apk \
  --mock mobile/build/outputs/apk/mock/debug/mobile-mock-debug.apk
```

APK 校验逐字比较默认中文和英文资源、变体名称，以及复杂转义的实际编译结果；同时检查整个 APK（含依赖）的配置，只允许默认、中文和英文语言配置。机械检查不能替代英文语义审校、界面切换验收或真实 Service/BLE 验收。
