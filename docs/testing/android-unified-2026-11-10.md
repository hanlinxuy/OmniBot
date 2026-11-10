# iOSWorld / MobileWorld Android 统一进度

已建立 334 个可追溯来源变体：iOSWorld 133、MobileWorld 201。[逐项 Android 映射](iosworld-android-mapping.md)有全部 133 行；[完整目录](android-unified-scenarios.json)保留原始目标、iOS 逐条 rubric、源码 pin/hash、能力对应、依赖、验收、清理和结果。[本批结果](android-adaptation-results.json)记录成功与失败尝试。

保守归并为 333 个场景模板：`clock-001` 与 `SetAlarmTask` 共用“创建并验证闹钟”模板，但时间、标签、重复、铃声和振动条件独立验收。未确认的相似任务保留独立。MobileWorld 两条 `SayHelloRoommatesAskUserTask` 虽提示词相同，一条缺少联系人、必须先创建，因此不按文本相同去重。这个数字是当前确认的归并结果，后续逐项适配可能发现更多可共用模板。

| iOSWorld 状态 | 项数 |
|---|---:|
| Android 文件适配已独立验收并清理 | 2 |
| 本地能力适配候选，尚未完成本批验收 | 4 |
| 需要隔离的源数据/跨应用 fixture | 93 |
| 用户银行/支付排除范围，整项不执行 | 34 |

旧的“原生 iOS 依赖”分类已改为具体能力与 fixture 依赖。93 项没有被判成不可迁移，也没有被标为就绪。跨应用任务的原始各步骤全部保留，不能去掉难点或排除步骤后宣称整项通过。原 MobileWorld 历史结果继续保留，本批未重复计为新增覆盖。

## 本批手机 Pi / 35a3 实跑

| 来源 | 独立验收 | 请求 / 秒 | 清理 |
|---|---|---|---|
| cloudsheets-003 首次尝试 | 宿主 ADB 文件名拆参，验收失败，不计通过 | 不报成功耗时 | 已另行确认目录不存在 |
| cloudsheets-003 修复后 | 真实 XLSX 仅一个非 Pending 状态变为 Pending，其余单元格不变，回答姓名正确 | 5 / 23.70 | 目录不存在 |
| cloudsheets-003 可复跑脚本复验 | 同上 | 5 / 24.32 | 目录不存在 |
| clouddocs-004 初次 | 原正文完整保留，追加 Q2 内容，读回保存；有两次门禁误拒 | 7 / 21.73 | 目录不存在 |
| clouddocs-004 门禁修复后 | 多行正文直接保存，原正文保留，零拒绝 | 5 / 20.33 | 目录不存在 |

新增覆盖是 **2 个来源任务**，不是 4 个任务。都是 Android native-file adaptations：真实 XLSX / 原文文本导出，非原 iOS GUI 官方分数。模型通过手机 Pi 官方 ACP `initialize → session/new → session/prompt → session/close` 执行；宿主不代做模型编辑。35a3 使用本机临时 SSH 转发访问既有模型服务。未启动 iPhone 模拟器。

发现并修复两项 **harness 问题**：

1. ADB shell 不保留 Python argv 的空格边界，独立文件读回现使用 shell 引用。保留失败尝试，使用新夹具重跑，不补写成首轮通过。
2. 原命令门禁错误拒绝引号内换行，模型重复尝试。门禁现在区分带引号文本与真正的命令串联/替换；2 个针对性测试方法覆盖多行正文、字面量和 7 个拒绝用例。真机复验请求数 7→5、拒绝 2→0；单轮时间差不作稳定性能结论。

上游 `workspace_snapshot.json` 没有任务要求的志愿者表；实际 fixture 从 `SeedDataFactory.swift` 补充种子提取，没有自造替代数据。没有发现足够证据要求修改 OmniBot 产品逻辑，本批不虚报产品修复。

## 继续扩覆盖的顺序

1. `cloudslides-002`：迁移原始 slide 内容为真实 PPTX，验收第二页 safety 条目及其余内容保留。
2. `multi-013`：在独立文档、PPTX、XLSX verifier 齐备后做三文件串联；不能用单一文本输出替代三份文件的落盘。
3. `notes-001`：保留文件夹与原文条件，用 vivo 控件定位验收新增 bullet，并清理唯一夹具。
4. 复用已有 Mail/Mattermost fixture 服务迁移沟通任务；只对隔离测试对象发消息，完整保留源线程上下文与验收条件。

运行入口、依赖与复现命令见 [`android-workspace/README.md`](../../scripts/benchmark-adapters/android-workspace/README.md)。目录生成器支持 `--results` 合入本批结果；缺少 fixture 的项不能直接进入自动执行队列。
