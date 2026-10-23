# M7 剩余轴字与 legend 目标视觉验收

## 限定结论

本轮实际查看剩余 **72 个独立滚达目标的原 PNG 目标区域**，四配置下机器水流图例、bar/ml/s、纵轴数字和中间时间刻度均清晰，目标字形未见裁切、重叠、缺字或被滚动条遮挡。结合[前次已看8个首末轴字目标](2026-10-23-m7-short-detail-visual-review.md)，现可关闭这批 **80 个 Canvas 目标逐项滚达的可读性缺口**。不是80项同时可见，不签M7主态、说明、动作或完整页面通过。

## 来源与查看方法

仅使用 `build/task-first-evidence/ci-8b52379-legacy-pr/acceptance-index.json` 指定的新PR原件：run39893290655，head `8b5237989eaae99181ff137b8873896eb96bda7c`，实际merge/source `44d34c52a3f3b18537f59cb92607bcc7c6252d09`；ZIP SHA256 `bbbf0013a3872dcc75eef9d586743fbc24c7ca3a6fbf575e9b855f03fccb331d`。不使用旧失败图，也未全展开ZIP。

本轮不重复查看 index0，以及1.0的index20/1.3的index18。新增范围为light/dark各1.0的index1–19（38张）、各1.3的index1–17（34张）。先看light1.0/index1完整原PNG确认坐标，再从每张原图无缩放裁出500×250px目标邻域，按配置排版逐项查看。裁片横向以同帧目标glyph中心为中心（限制在原图宽度内），纵向覆盖chart viewport中心上下125px；原viewport高216px，因此包含上下真实裁切边界。没有补画字形、重采、锐化、换色或重渲染字体；页头标签在裁片外。工具显示较高的1.0联系表时有轻微整体缩放。此方法用于逐字目标边界，不冒称逐张全屏布局复审。

72组PNG与geometry SHA已独立重算，均匹配索引；从ZIP按需读72份frame proof并核SHA，逐组确认 `acceptedAttempt.geometryBefore.target == geometry.json == geometryAfter.target`。用同帧 `chartOriginScreenPx + glyphBoundsPx` 重算屏幕字形框，72项均落在实际 `chartVisibleRectPx` 内且chartMappingVerified=true；同时逐项对照 actualDraw 文本与目标索引。此数值核对仅配对/辅助边界，视觉判断来自实际像素，不以机械PASS代替。

## G1–G4

对照[旧版双轴合同](../superpowers/plans/2026-10-16-legacy-chart-axes.md)、[固定字体合同](../superpowers/plans/2026-10-16-fixed-chinese-typography.md)及[短窗滚达合同](../plans/2026-10-23-legacy-short-detail-viewport.md)。

| 项 | 已见结果与边界 |
|---|---|
| G1 位置/间距 | 左压力、右机器水流各自贴合对应轴，数字与网格/轨迹留有可辨间隔，单位不挤压目标刻度；时间数字及s分隔自然。局部滚动条没有覆盖当前目标。仅审这些目标邻域，不认证全页padding或其它控件间距。 |
| G2 字形/字体 | 机器水流中文、全角括号、拉丁单位、小数点和数字均完整；0/6/9及1/7可辨，未见方框缺字或异常粗体。72项实际Paint均weight400、fakeBold=false；fontAssetSha256沿原证据a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da。candidate_asset_association_not_platform_glyph_identity仅是资产关联，不冒称逐glyph平台身份认证。 |
| G3 字号/边界 | 1.0实际31.5px，1.3实际42.3px，均与同帧Paint相符，放大后没有以缩字容纳预算。1.0压力0/2.4/4.8/7.2/9.6/12、流量0/1.2/2.4/3.6/4.8/6；1.3压力0/3/6/9/12、流量0/1.5/3/4.5/6。短高减少刻度数量符合合同，不是漏数据。新增0.0/7.5/15.0/22.5 s目标均完整；30.0 s沿前次实审。本轮全部目标自身无截顶、截底、横向溢出或重叠。Canvas无TextView行框，不拿布局常量代实际glyph/metrics。 |
| G4 颜色/线型 | light压力#176BD1、水流#007E89、时间#657183；dark压力#8CB8FF、水流#66CAD3、时间#A9B9B8，与实际Paint对应。实图蓝色压力实线和青色机器水流虚线、图例短虚线清楚，网格弱于数据和轴字；浅深主题均能辨认。未作全屏合成对比度或色觉障碍认证。 |

部分帧的相邻非目标legend、单位或其它刻度处于viewport之外而被裁切；联系表也会截掉横向远端的非目标内容。两者不能混称目标失败，也不能据这些帧签全部轴字同时完整。当前目标整字在真实viewport内的判断已由同帧几何和像素交叉核对。

## 已看原件清单

以下每行表示实际查看过一张原PNG的目标邻域。共同本地根目录为 `build/task-first-evidence/ci-8b52379-legacy-pr/selected/`；每节profile下的精确路径是 `detail/special-states/short-window/chart-text-{index}-reachable/window.png`，同目录 `geometry.json`。ZIP对应路径仅把 `selected/` 换成 `44d34c52a3f3b18537f59cb92607bcc7c6252d09/`。完整路径身份和两项SHA逐行列明，不以相似截图代替其它index。

### tablet-user-landscape-light-font-1.0

| index / 目标 | PNG SHA256 | geometry SHA256 |
|---|---|---|
| 1 / legend `机器水流（ml/s）` | `44a4747cebac4a2ce5bb5c629acbf8cf17bb3d298f9664a43fe6a78748c52a8d` | `c67530a04553efcab36c5d0715ef7e13bc940d1d4bd2d78d9e120c1d4ea8c3d0` |
| 2 / pressure-unit `bar` | `0c2f4c66f81090e593f3fbdd2645bcc8f671f206681937573ff3bad4a3a56aa5` | `a130ef180c0ae2d7652f58e65a7f07e7b07a526becfe702bff27259bfe271dc2` |
| 3 / flow-unit `ml/s` | `1dfeca9f3b97c035c3e0a5e86b6d0fa36b6d836a22e361fbc9f65fb513065607` | `a01c38e048daa56ecf6268ace50bc0b4e206ce473ba85e9ce0d91b847aa24246` |
| 4 / pressure-tick `0.0` | `11773ebd915a6d742d2cdf997f7c631be4b0f04f7b23a61e2134f3d1b16ea82c` | `5012da07083bacc77d7fb092678a3ce1ac8f924d088d7d8086d2190c7c7b28e0` |
| 5 / flow-tick `0.0` | `3f08ab11bf33129d400009a9bac59d9beb128403cc4fdf3b936b37d55fb8a1ae` | `5012da07083bacc77d7fb092678a3ce1ac8f924d088d7d8086d2190c7c7b28e0` |
| 6 / pressure-tick `2.4` | `06b130305df0533c9b11988d2f3e8197935f2e7323edabc0a16bfb74066f9f5d` | `b9cb5fb072351629db93ad74b9f28a2a9589d5e4ff48b4e9c5897df5a22649dd` |
| 7 / flow-tick `1.2` | `b82779602d22bc1ceaeb4b5030e288416124ec045bc37a59d060dac2886a1e9c` | `b9cb5fb072351629db93ad74b9f28a2a9589d5e4ff48b4e9c5897df5a22649dd` |
| 8 / pressure-tick `4.8` | `32ddf8de4cae443f29050e803baa633c82bda762d89d657df31171306bd4f5c7` | `a5abef7783d6359bbc03b0754487971426d6ba47ad6f7714a63a481e2945b9a0` |
| 9 / flow-tick `2.4` | `72b0cdf4718380334d3d70c24e4811eb1337517e19549945465b26266c7ab3e9` | `a5abef7783d6359bbc03b0754487971426d6ba47ad6f7714a63a481e2945b9a0` |
| 10 / pressure-tick `7.2` | `0a3aa1a6744e32b82f5780f586b1f57a88ab3634e4de948358f2fc254c6d8c51` | `a25ff6106ef535cabe10686b9eef6e5617fd4c1405a6890e7db09bb9947e5d89` |
| 11 / flow-tick `3.6` | `c7e2db95b24c88234208277b36acef010c23a6d7d40754fb51dc26929a863472` | `a25ff6106ef535cabe10686b9eef6e5617fd4c1405a6890e7db09bb9947e5d89` |
| 12 / pressure-tick `9.6` | `8f7c12d25424727fcf8e917f351475faf61ebc76e4619a49b9f3efaa0f0189eb` | `ccf15845e1d4e0260535b3c14fb9b420f86d597d4230c064ffaf34d445623394` |
| 13 / flow-tick `4.8` | `9f5f959c93255207b2fc9f853f9f1e9a466b0b29651c646a2d7bbe5699e04b57` | `ccf15845e1d4e0260535b3c14fb9b420f86d597d4230c064ffaf34d445623394` |
| 14 / pressure-tick `12.0` | `141331fed93f7acf3b1aae5540e1a8d66cc473c0218de378f2c73b4565aebb73` | `92237728553edc3b8b4f603d7fda53c5ee1bb2b4d5c611944fba7851752a20d7` |
| 15 / flow-tick `6.0` | `5a4b70a2ca968b76d490b699fc74317f541138e1ac90bf68e80423e845450cde` | `92237728553edc3b8b4f603d7fda53c5ee1bb2b4d5c611944fba7851752a20d7` |
| 16 / time-tick `0.0 s` | `9dc66bbc20fa884c711e11b6c52c114820a2f27dbbef05c06ca005e91f5a5444` | `5012da07083bacc77d7fb092678a3ce1ac8f924d088d7d8086d2190c7c7b28e0` |
| 17 / time-tick `7.5 s` | `62126ca61a51c84d4a056bf99d1172245a92426b6a1bf5a07c7b69837d51f4c7` | `5012da07083bacc77d7fb092678a3ce1ac8f924d088d7d8086d2190c7c7b28e0` |
| 18 / time-tick `15.0 s` | `61bf00c3eaf7ffa892168044171c5678427cc43289f8eb63c49da7fc8df02076` | `5012da07083bacc77d7fb092678a3ce1ac8f924d088d7d8086d2190c7c7b28e0` |
| 19 / time-tick `22.5 s` | `61bf00c3eaf7ffa892168044171c5678427cc43289f8eb63c49da7fc8df02076` | `5012da07083bacc77d7fb092678a3ce1ac8f924d088d7d8086d2190c7c7b28e0` |

### tablet-user-landscape-light-font-1.3

| index / 目标 | PNG SHA256 | geometry SHA256 |
|---|---|---|
| 1 / legend `机器水流（ml/s）` | `f36abb42daa06eb5226401cf2a3ea869d666ede882058d9f30f8742b56db32eb` | `749203bc3a8e463788eac80e95d8cbabb6c2c81fc4714be5b20cbb372b51167b` |
| 2 / pressure-unit `bar` | `6c686f6522c0e6c246166168cb135ff80b8028c5d4ef95923a3ee94d188ff438` | `a04e282cae4020d3757cb91f00feb29bc73eb1a094a619031688c4759e23202d` |
| 3 / flow-unit `ml/s` | `e9db3a6b009b0bd91ec73fb304eb98114fcc467d882f5d5a6877e48157a8bd19` | `914abd7bd5ad5f952d0bac31e822927bfce9481638bc9359d83368c8eb6b4e7a` |
| 4 / pressure-tick `0.0` | `ad3373322745a504f93414c9434d2b62a1c5803e0007ff9ba29af4f15c2990a7` | `4e7b502402331dcda80e983a4f268d17c1bf2302d1a9a1cfd8e6a66a354020e2` |
| 5 / flow-tick `0.0` | `a259846a48ebde70ef8a2688695bce5f5bc9525c5da9e1e424c7057e05236553` | `4e7b502402331dcda80e983a4f268d17c1bf2302d1a9a1cfd8e6a66a354020e2` |
| 6 / pressure-tick `3.0` | `26c37a3aa420d4008bf2540b6df235657609c90247460295d08cdb46ffc299a9` | `e290f3700751d9450d3942c52b9b8a5d880fddb877de9f421b1351e2c706eb13` |
| 7 / flow-tick `1.5` | `e68ee1eda9c2d266ac7a4e016966a5cf0ae660ef98394f6ffb399d5ecc80633e` | `340abc7fdad20669c8c346a2e673f2b2e34e87c4b2e8d15b8c1eef6197035935` |
| 8 / pressure-tick `6.0` | `f918ba421814a30585adf947732cef9dd430df5ca5dd3eb6db895d8e298d7d9f` | `9e8c81c00007a1ce4ed52480c41677ceb255c0b4ce3c3d0cd2f0e38f33b79322` |
| 9 / flow-tick `3.0` | `477a01898b39abc033e7e039e4e9a67968681a9b7ac5963ccaae231c942e5617` | `9e8c81c00007a1ce4ed52480c41677ceb255c0b4ce3c3d0cd2f0e38f33b79322` |
| 10 / pressure-tick `9.0` | `7e8481cfacf76658f99f677aef937d734655e40e9a4796ce9e742fe33e9a4356` | `adf616c0b0e8b48ceb5c79ef84e2f60714910c1113d43c34f6646774ca6df49d` |
| 11 / flow-tick `4.5` | `3b2c260e370cffce0f7cb2166c897e59636e31883eedee67539d9e4beeeb04b5` | `ac809c56ffaaa8673290b8994d749144b43e72e136333bdf0abbe0f17c243a08` |
| 12 / pressure-tick `12.0` | `cd23f2f65e06c058923b0425abc10d674cce2253382033d062fde47cb2748e9a` | `9a363bf489cd1cf95375f2173235be82b3fedd71cbd0c5b2f5026f8c9e2a1e47` |
| 13 / flow-tick `6.0` | `ac71152cede8451e2926117a1819bff2be15e1291b0b2fd71b3b1f3e28134471` | `9a363bf489cd1cf95375f2173235be82b3fedd71cbd0c5b2f5026f8c9e2a1e47` |
| 14 / time-tick `0.0 s` | `ad3373322745a504f93414c9434d2b62a1c5803e0007ff9ba29af4f15c2990a7` | `4e7b502402331dcda80e983a4f268d17c1bf2302d1a9a1cfd8e6a66a354020e2` |
| 15 / time-tick `7.5 s` | `a259846a48ebde70ef8a2688695bce5f5bc9525c5da9e1e424c7057e05236553` | `4e7b502402331dcda80e983a4f268d17c1bf2302d1a9a1cfd8e6a66a354020e2` |
| 16 / time-tick `15.0 s` | `a259846a48ebde70ef8a2688695bce5f5bc9525c5da9e1e424c7057e05236553` | `4e7b502402331dcda80e983a4f268d17c1bf2302d1a9a1cfd8e6a66a354020e2` |
| 17 / time-tick `22.5 s` | `a259846a48ebde70ef8a2688695bce5f5bc9525c5da9e1e424c7057e05236553` | `4e7b502402331dcda80e983a4f268d17c1bf2302d1a9a1cfd8e6a66a354020e2` |

### tablet-user-landscape-dark-font-1.0

| index / 目标 | PNG SHA256 | geometry SHA256 |
|---|---|---|
| 1 / legend `机器水流（ml/s）` | `c88664de50512c1c649877796a0482136e606c8d76ec733e035c5d39d1d13e44` | `25ea564e3520234f2e601c621846257b244ac63b4893b1001163d3389630821d` |
| 2 / pressure-unit `bar` | `f8352f88ed0473a059ae0e5aa586336e33505d99b9920268bb9ce5460bca89bc` | `0cc9d79813d3e5e2479e0e5bde5ec6628c35394dbc147277b7ca293922e11743` |
| 3 / flow-unit `ml/s` | `9af7ca4bad40e011a3a98390704b6535381da37a9bbf827ffe4a355c853612dd` | `6d72dcb56dc0c1ba0364ab2447a0b07dff0ef8fffbe3b28381cff6003b06dd91` |
| 4 / pressure-tick `0.0` | `8d3e019108cae5b452c82452e820da73d66c08e5d34551c4437c2c38413d5e74` | `a13838e2518885e86129fa98d313bade167d060f26030edb02dee237e6e91581` |
| 5 / flow-tick `0.0` | `59f763cfd50d02c4f3bf0afac5b98c5a3a9b0567a94d7dac0131d3cef3c701b4` | `a13838e2518885e86129fa98d313bade167d060f26030edb02dee237e6e91581` |
| 6 / pressure-tick `2.4` | `627aab001cd667ea3ab744ea0f23a0b9143951afb728e6e618ce7d4931a22664` | `dd4e6b41654aa203cf2f965a350c2812e258d937532983148c0a0176c521786c` |
| 7 / flow-tick `1.2` | `159420c14b9d969997a004e9aee91137602c45bf77767e18b83fa09b09f0d047` | `dd4e6b41654aa203cf2f965a350c2812e258d937532983148c0a0176c521786c` |
| 8 / pressure-tick `4.8` | `9884434b23fedea0fe5adf3f60aed1c4b07be1e75d20096195eae5e7f8381e13` | `ddc96993cc6da468dbf4500ff7df9f9e8cf20b8061e0e2bec3025154587f7f7c` |
| 9 / flow-tick `2.4` | `57a5032e99c4b8a82be0feae4db45db0f610e12338eafacd88ffcbcdd65ea52c` | `ddc96993cc6da468dbf4500ff7df9f9e8cf20b8061e0e2bec3025154587f7f7c` |
| 10 / pressure-tick `7.2` | `e922aff64134f04d1223b394fa432545b81e53a1dd31965ec99bd2483bda86d3` | `bdcf77d50798c8e2a7026b5b9c388aff4f4e84acaf06fb13de7d44541ac6a73c` |
| 11 / flow-tick `3.6` | `400e2b49888eeef36ea5ceb05ca43b6165b00b719414725ad99da857157c591c` | `bdcf77d50798c8e2a7026b5b9c388aff4f4e84acaf06fb13de7d44541ac6a73c` |
| 12 / pressure-tick `9.6` | `06f40bdaa01bb76ec29960e9ad037a758098def646ebdb4997fb487645e9997e` | `f758c4e2e4f3ff2e9ed754f4904c49eddb9ec54bbda94ecacba5555a62a7c647` |
| 13 / flow-tick `4.8` | `5c49a671adfe5e3f0c87e1adb58d61030c45ea00b06fc0b3e4006bae2e31670b` | `f758c4e2e4f3ff2e9ed754f4904c49eddb9ec54bbda94ecacba5555a62a7c647` |
| 14 / pressure-tick `12.0` | `0fa4c9841e12d9ba76f563493af9ecccf7592e1363b44db79c326ec2d2ea47c9` | `cc9b224b6e7ee8347db62a01df2de742efae2d3ff80cbff630801463e6781d41` |
| 15 / flow-tick `6.0` | `d22c20bd0afc0656312880d5e857eed2e9c80386ca5d79e8c8f3e018188cabb8` | `cc9b224b6e7ee8347db62a01df2de742efae2d3ff80cbff630801463e6781d41` |
| 16 / time-tick `0.0 s` | `90f18592a253e225933d96a07c8c52a3206180a644789dc4cb787d8ac27c1a47` | `a13838e2518885e86129fa98d313bade167d060f26030edb02dee237e6e91581` |
| 17 / time-tick `7.5 s` | `ad3ea05a07f406d19de96d8864cce29b261678f8072c4b54c6e02d38f12e0572` | `a13838e2518885e86129fa98d313bade167d060f26030edb02dee237e6e91581` |
| 18 / time-tick `15.0 s` | `ad3ea05a07f406d19de96d8864cce29b261678f8072c4b54c6e02d38f12e0572` | `a13838e2518885e86129fa98d313bade167d060f26030edb02dee237e6e91581` |
| 19 / time-tick `22.5 s` | `ad3ea05a07f406d19de96d8864cce29b261678f8072c4b54c6e02d38f12e0572` | `a13838e2518885e86129fa98d313bade167d060f26030edb02dee237e6e91581` |

### tablet-user-landscape-dark-font-1.3

| index / 目标 | PNG SHA256 | geometry SHA256 |
|---|---|---|
| 1 / legend `机器水流（ml/s）` | `363288d4f8265b8ecf0de5a2624d27655d5c3cba05186e53f2b9f3fa28f912ad` | `203e35ad0161423d563aaaa9ef25d6e8b1d237982b53f41fcf1b5ff5da8771c3` |
| 2 / pressure-unit `bar` | `cf9cce32fba8222d4369d2b7d230cb9275a4b893285470618d2f3f71623ad62a` | `b722a77d419231189b68d1bf4fafb1b5713aaacceb11a37f6ffbf8bc15af4e87` |
| 3 / flow-unit `ml/s` | `ac767a3420ffa74143578a704c7a5b399ab6e3daca499f9a962aa19bc1e213c9` | `b8f976afa70c3b5c0cd3b14fbb4bf6d6c5302bb5c2f3e993530383d439984aa1` |
| 4 / pressure-tick `0.0` | `6f476b899ec02c7f6f97aba895e4a4b99f185b778db9b7e99d419e3a79999332` | `586f9d3cf2a30d702710dc46ce0a3ae93f662283a7a0cf01a95f23e5d6a1baef` |
| 5 / flow-tick `0.0` | `032dc816de16a7021f2ce4dfa9a694779cc810c5d91008c071eab7a6bb603f27` | `586f9d3cf2a30d702710dc46ce0a3ae93f662283a7a0cf01a95f23e5d6a1baef` |
| 6 / pressure-tick `3.0` | `a6ff65bc3dae05a3e00b33764830207addf50b2c2b787c71df26960fa93eb377` | `48dddb8bcd9104c7488c79c67cc39d3345cec3a21e5a6090e769dbb725fe15a6` |
| 7 / flow-tick `1.5` | `90bc02bc4564cf5ed209bc8be05da266ad66934970f7cd7a0ac71c3cd9819106` | `a7bb58c2afeff2718f47cfc10dfab48d7a24f159b2dd7656d1f41cfcd24e0281` |
| 8 / pressure-tick `6.0` | `618c61294167ed67025c0bd26aff39a7afc9ab58d942fb5b8c0f2e8a70d34dde` | `9627909968012d59227e4f44078e0920a228409bf04f1943cd82aa1cee5bcce9` |
| 9 / flow-tick `3.0` | `08755d2fce30d598f4dd2b8e2cb586331329dbb166f4c1ad62864a24907bccf0` | `9627909968012d59227e4f44078e0920a228409bf04f1943cd82aa1cee5bcce9` |
| 10 / pressure-tick `9.0` | `a781601cdf2bbcd12a598d669adb157490bed15a52f68c17c914d1e1d455a9e2` | `61dfc4b9d05666effe758ece9e6f88ea22dc0ccedef428c34ff1dd03575f151f` |
| 11 / flow-tick `4.5` | `c98113cb5e8d2f880b5cfab047348277937e2c24e4c3b62a6e68b4741e469002` | `28a2319c2d40d4fe880639f15af8ab21cc1b53d4ce3240d37d30bc1f5270705d` |
| 12 / pressure-tick `12.0` | `bdf5888c96832fd0342c912d6fa40da2e3149ca8e00a1571b7c29c45b0b4cc1c` | `22428e5c01e431134742d9fbc8fa7dc2229bf573d5e87e3bfe94aa085addb4ea` |
| 13 / flow-tick `6.0` | `26fe3b5cec26e9802b6c16f1075a78411ac8ca54421a4d25b3b700a3adca88fe` | `22428e5c01e431134742d9fbc8fa7dc2229bf573d5e87e3bfe94aa085addb4ea` |
| 14 / time-tick `0.0 s` | `7e1150093a2fe47b0a48f4470c52d72367424d47efd2a217cd3322e8ad3ce583` | `586f9d3cf2a30d702710dc46ce0a3ae93f662283a7a0cf01a95f23e5d6a1baef` |
| 15 / time-tick `7.5 s` | `ade226be0ea6fc05cf7b8c109f04aedc1c01d41fd45ef3f4228fc0e341b7c0af` | `586f9d3cf2a30d702710dc46ce0a3ae93f662283a7a0cf01a95f23e5d6a1baef` |
| 16 / time-tick `15.0 s` | `ade226be0ea6fc05cf7b8c109f04aedc1c01d41fd45ef3f4228fc0e341b7c0af` | `586f9d3cf2a30d702710dc46ce0a3ae93f662283a7a0cf01a95f23e5d6a1baef` |
| 17 / time-tick `22.5 s` | `ade226be0ea6fc05cf7b8c109f04aedc1c01d41fd45ef3f4228fc0e341b7c0af` | `586f9d3cf2a30d702710dc46ce0a3ae93f662283a7a0cf01a95f23e5d6a1baef` |

## 仍未验收

本轮没有看shortInitial、shortReachableAction、normalOriginal，也没有看完整说明Dialog、支持区全部动作或Curve/History/Detail三主态。前次恢复数值/有限恢复图结论不扩大；本次不新增恢复视觉结论。未认证真机、其它尺寸/语言、全24页或整个Legacy视觉通过。只新增本文，不修改旧review、源码或计划；未构建、触发CI、操作设备、提交或推送。
