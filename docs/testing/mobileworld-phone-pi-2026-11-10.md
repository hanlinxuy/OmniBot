# MobileWorld 历史失败任务迁移到手机 Pi（2026-11-10）

本批为真实 vivo V2324A 上的隔离适配测试，不是 MobileWorld 官方得分。外层 Agent 是手机 Alpine 中 Pi 1.4.0，经官方 ACP session 接口运行；Mac 只做模型转发、fixture 准备和独立验收。未替换为桌面 Codex Agent。三模型为 qwen3.6-35b-a3b-q4_k_m、官方 deepseek-flash、gpt-6.1-terra，reasoning 均 off。GUI 由共用 OmniFlow 执行器承担，不能归因于外层模型视觉能力。每模型每题单次，不能据此做可靠速度排名。

## 来源与适配

历史记录：phone-real-use/docs/runs/2026-11-10-qwen35-four-hour-iteration.md。源码快照 aa2ea47052dd15729bfca1f27fa6aca4630607a9，优先对照实际容器导出的任务定义。

- CheckConferenceDurationTask：沿用 15 条原始存储记录及 UTC 起止时刻，迁入原生日历，以唯一标题前缀隔离。明确参考月份 2025-10。无递归事件展开，allDay=false；因此不是完整原数据库环境。原 judge 只检查答案含 12。适配额外要求仅整数，单列格式约束，不能把这项失败当原题失败。
- InvoiceReceiptCopyTask：沿用原文件名和三个关键 mtime，11 月只应复制 invoice_2025_001.pdf。原任务是文件名与修改月份筛选，并非正文日期。隔离工作目录替换 /sdcard/Download；保留源文件和字节读回；禁止运行原初始化中清空 Download 的命令。内容沿用伪 PDF 形式，替换为确定性无个人信息样本。
- SetAlarmTask：原题周末 08:25、beebeep、无振动。vivo 缺少该音频，以 [AOSP Alarm_Beep_02.ogg](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/data/sounds/Alarm_Beep_02.ogg) 创建唯一测试文件；MediaStore 实际读回标题 BeeBeep Alarm。SHA256 `811612558a9e76830e9b7f5473b3a9310d9c86c6246c3e8cad14dd03c1589edd`。所有闹钟唯一测试标题，只清理这些对象。基线为原生创建 + 宿主独立 GUI 验收；GUI 修补是另一个明确分开的样本。未测试实际到点响铃。
- ChromeSearchBeijingWeatherTask：真机未安装 Chrome，原题依赖阻塞。另测同一北京今日最高气温目标的接口/可用浏览器适配，不算原 Chrome 任务通过。独立 Open-Meteo oracle 隐藏于模型，记录查询日期和时区。

## 已验收结果

| 场景 | 35a3 | DeepSeek | Terra |
| --- | --- | --- | --- |
| 发票筛选复制 | 通过，4 次模型请求，21.17s | 通过，6 次，23.53s | 通过，3 次，28.75s |
| 会议天数 | 最终 12；解释有中间矛盾；2 次，19.50s | 最终 12；2 次，15.29s | 仅 12；2 次，17.41s |
| 北京最高温适配 | 14，GUI，2 次，29.61s | 13，实时 API，4 次，16.49s | 13，实时 API，3 次，24.06s |
| 系统闹钟基线 | 08:25；漏传周末；铃声振动不符；2 次，13.12s | 08:25 周末正确；铃声振动不符；4 次，13.78s | 08:25 周末正确；铃声振动不符；2 次，13.79s |

会议天数：三者满足原 judge，人工核对最终答案也正确；额外仅整数格式仅 Terra 满足。harness 原始 result 保留，另写 acceptance-audit.json，按 ACP messageId 分开工具调用前说明与最终回答。没有凭关键词 12 自动推断推理正确。

闹钟：DeepSeek 正确查询日期；35a3 和 Terra 选了过去的日期，但 clock_app 按本地时分及 repeatDays 创建，日期并未作为系统闹钟起始日期。它们都明确表示仅派发、尚需核验。DeepSeek 尝试 alarm_reminder_list 被基线权限策略拒绝；该工具实际只列 exact_alarm，也不能验证系统闹钟。基线不是端到端自主 GUI 成功。

额外 GUI 修补：Terra Pi 对已有唯一测试闹钟调用 run_gui，约 62.22s、2 次外层模型请求。宿主重新打开设置读回：08:25、周六周日、mw-pi-20261110-BeeBeep、无振动，配置验收通过。这是共享 OmniFlow 的补齐能力，不是三个模型各自端到端成功，也不是实际响铃验证。

## 产品修复与边界

1. 现有 alarm_reminder_create 的两条入口都增加 clock_app 可选 vibrate、ringtoneUri，保持不传参数时原系统默认行为。传入非 content URI/非 silent 时明确拒绝；exact_alarm 不支持这两个参数，明确拒绝，不静默忽略。
2. 系统 Intent 携带 EXTRA_VIBRATE / EXTRA_RINGTONE；结果仍 creationVerified=false，并带请求值与 GUI 核验提示。vivo 实测忽略这两个 extras，故不能以工具成功代替配置成功。
3. MCP alarm_reminder_list 描述纠正为只列 OmniBot exact_alarm，避免用它误验系统时钟。
4. 原生创建后仅对未生效字段 GUI 补齐，已用唯一闹钟验证；尚未做为所有 clock_app 调用自动串联的产品流程。

验证：4 个 Alarm Robolectric 测试通过，覆盖星期映射、Intent 参数、不传参数保持默认、exact_alarm 拒绝不支持选项、creationVerified=false；开发 APK 构建通过。真机基线使用已安装的参数版；随后增加核验提示/工具描述的最终构建也已安装，设备 APK 与本地 SHA256 均为 `3c8d4646fa3d9eda14abac4cfe17e8f3d425ae0355377ccecff725e17efad152`。最终提示文字未再重复三模型行为比较。

## 证据与复现

本地证据目录 `phone-real-use/artifacts/mobileworld-pi-20261110/`，包含 conference.py、invoice.py、alarm.py、alarm-repair.py、weather.py、模型转发源码、每次独立 ACP 请求和结果、验收修订、原生对象读回、清理记录。凭据仅以受保护文件引用读取，不入版本库。不可把整目录发布：GUI 私有读回可能包含个人闹钟列表。公开只含唯一测试对象的结构化结果。

所有结果均是单次样本。请求数不等于工具数，缓存 token 不等于 KV 内存容量。API 日期天气可能变化；文件和日历使用固定 fixture。

天气独立 oracle：2026-11-10 Asia/Shanghai，Open-Meteo 最高温 11.7℃，三者均在原题 ±3℃ 内。35a3 的结果由共享 DeepSeek GUI 执行器读取 vivo 天气，非 35a3 独立视觉能力；其余两者经手机 Pi bash 查询 wttr.in 并读取当日日期。Terra 首次假设 localObsDateTime 字段存在报错，随后自行读取实际结构恢复。vivo 城市页仍显示“添加城市”，没有新增城市。

## 收尾与剩余项

已完成 12 次场景基线/适配运行 + 1 次 GUI 补齐。另有 1 次安装后 MCP 尚未就绪的 preflight 失败，0 次模型请求，不计模型失败。14 个唯一工作目录均读回不存在。三个测试闹钟逐一选择删除并在同一区域读回消失；测试音频 MediaStore 查询为空且文件不存在。常亮恢复原值 0。

仍未解决：Chrome 原任务缺依赖；vivo 忽略系统闹钟铃声/振动 extras，需要 GUI；35a3 漏周末参数；部分模型回答包含不必要解释或错误中间计数；Pi 未自动给模型当前本地日期，35a3/Terra 闹钟日期选择不可靠。GUI function recall 日志显示本地 encoder 路径缺失，执行本次走 VLM，无 skill 命中；未在本批修复或宣称加速。

后续优先：把“查询设备时间、原生创建、仅核验/补齐缺失字段”整理为可复用闹钟能力，单独验证厂商兼容性；为日期上下文加入真实设备时区信息；再扩展依赖满足的新任务。不要把这次部分成功包装成全场景已通过。
