# vivo Android 扫描记录：2026-10-29

设备为 vivo X100 Pro / V2324A，Android 16；GUI 使用设备已有 DeepSeek 官方 `deepseek-flash` 配置。所有原始截图、模型内容及 RunLog 保存在本地忽略目录；这里只发布测试任务和脱敏结论。

## 范围与边界

全量适配清单有 280 个来源条目，不代表 280 个独立可运行测试或已全部真机执行。当前 11 项银行、支付、微信及包含结算的完整目标标为 `blocked_policy`；`huawei.lock` 与 `com.huawei.works` 同样禁止访问。166 项缺原基准依赖，99 项尚待验证，BA02/BA19/BA20/LI22 已可安排执行。ready 不等于独立成功。来源版本、迁移规格与包依赖见 `android-scenario-inventory.json`。

## 已复现的问题和修复

1. **语义定位遗漏虚拟按键。** vivo 计算器按键可聚焦但未标记 clickable。允许唯一、可见、启用的 focusable 节点提供本身的手势边界；支持短角色标签及同一节点 text + content-desc 的精确组合（如 `AC 清除`）。歧义和任意长描述仍回退，不做任意模糊匹配。
2. **页面摘要遗漏按键。** 原 24 条摘要会漏掉 1/2/3/AC。改为优先呈现带标签的可操作节点，限制为 48 条。计算器表达式和结果并未出现在其无障碍 XML 文本中，验收必须结合图像；不能把 XML 无读数解释为没有结果。
3. **截图包含自己的浮层。** Android 14+ 被错误假设为自动排除浮层；vivo 实际截图包含任务条和变暗遮罩。截图前复用现有隐藏回调，finally 恢复；真机已核验完整按键与内容不再被遮挡。
4. **逐步规划缺少原生对话历史。** 仅提供当前截图及文字动作摘要，仍出现多次清空。保留本次 run 中模型实际返回的调用 ID、原始参数、content/reasoning，并配对 primitive action 的真实回执。语义重定位不改写原始调用。无法取得可靠回执的分支回退摘要，不伪造 tool 结果。旧截图不保留；每次 run 新建 planner。
5. **thinking 请求不兼容。** 官方接口实测拒绝 thinking + required tool_choice。候选改为 low thinking + auto，执行前仍严格要求且只接受一个有效工具调用；2048 completion token 预算包含思考。模型空 reasoning 字符串也保留，不因此抹去已配对历史。明确要求完成时调用 finished；不会把普通文字回答隐式转成成功。
6. **GUI 混淆宿主聊天与目标 App。** 正常聊天入口曾在自己的工具详情/遮罩间循环，甚至点到自身停止。观察结果携带实际 `execution_host_package`，当前页为宿主时提示其是执行状态页面；目标在其他 App 时应打开目标。用户明确要求操作宿主仍允许。这是提示，不是硬性访问拦截。
7. **官方 Python override 缺配置目录。** 修复开发覆盖目录漏复制 `config/paper_androidworld.json`，并修复已有同版本覆盖目录，保留用户 Python 修改。官方 apply/reload 真机成功。
8. **DeepSeek 能力声明过时。** 官方 `deepseek-flash` 已支持图像，修正旧 false 判断；代理能力仍保持未知。这不是计算器循环的独立根因。[官方文档](https://api-docs.deepseek.com/guides/vision/)

## 复现实验

所有 GUI 实验使用相同模型；每次最多 20 步，不以提高步数掩盖循环。

| 变更与入口 | 题目 | 结果 |
| --- | --- | --- |
| 语义定位 + 最近 5 步摘要，GUI 组件 | 12×34 | 20 步失败，11 次清空 |
| 加精确组合标签，GUI 组件 | 12×34 | 20 步失败，16 次清空 |
| 加截图隐藏浮层，GUI 组件 | 12×34 | 20 步失败，10 次清空 |
| 最近 20 步摘要与持续任务提示，GUI 组件 | 12×34 | 20 步失败，5 次清空 |
| low thinking + required | 12×34 | 官方 400，0 个动作 |
| low thinking + auto，GUI 组件 | 12×34 | 9 动作，截图 408；一次多余删除/重输 |
| 同上变体 | 27×16 | 20 步失败，9 次清空；说明前一轮不是稳定修复 |
| 原生调用历史与回执，GUI 组件 | 27×16 | 8 动作，截图 432，只清空一次 |
| 正常聊天入口，尚无宿主标识 | 12×34 | 自身工具卡片循环，14 动作后取消/失败 |
| 正常聊天入口，添加宿主标识 | 12×34 | 9 动作，画面 408；模型未调用 finished，协议失败，不计端到端成功 |

GUI 组件由 debug receiver 调用同一 `OmniVlmPlugin` 执行；它不是完整聊天入口验收。工具 `success=true` 仅说明派发，最终结果另外看画面。完整入口及后续场景的结果将在下方记录。

## 复现方式

先构建并安装本分支 debug APK。Python runtime 是打包资产，保持不可变；按 `scripts/android-scenario-scan/overrides/README.md` 核对当前文件哈希，通过官方开发接口依次应用完整候选文件。只构建 APK 不会自动启用 Python 候选。回滚使用官方接口；不要直接编辑固定 runtime 目录。

验证包括 Python 覆盖测试、Node 清单/异常分析器测试、mock 提交器测试、原生截图/host/override/provider 定向测试，以及真机逐轮回归。mock 的成功只证明脚本控制流，不计入真机成功数。

## 本轮计算器验收

正常聊天入口 `gui-639e17cd-5f9d-4d52-a30b-7134510f4f67`：8 动作、1 次清空、运行 succeeded；截图独立核验 12×34=408，结束后原生观察仍为计算器。组件变体 `debug-vlm-1be2b574-2bf3-4f07-89a4-91af463ee4e1`：8 动作、1 次清空、截图核验27×16=432。本轮通过不意味着所有输入和界面状态均已验证。

## 后续扫描

- BA20 闹钟 GUI 组件：15 动作新增唯一测试标签，独立观察读回 `07:00 / 每天 / 已开启`；9 动作删除该测试项。原先可见的 5 条闹钟描述及开关状态保持不变，另 2 条因视口变化新显示，不作为新增对象。此项没有经过正常聊天入口，不冒称聊天端到端通过。创建 run `debug-vlm-2aaedf6a-688d-41ec-9607-82e041246511`，清理 run `debug-vlm-e42958bb-b7fc-46c0-8971-d51cfb5abd2f`。
- BA02 衍生只读检查：系统设置搜索并进入电池页，3 动作完成，截图独立核验；未操作开关。run `debug-vlm-02030e3b-494b-482b-91ca-577113ad5187`。这是明确限定的子场景，不计为 BA02 原始整项通过。
- 一次计算器聊天提交在发送前被通知守卫阻断；确认未提交后才重试，未产生重复任务。

仍需完善：BA20 正常聊天入口、更多任务/表单/取消/旋转变体、闹钟时间滚轮和删除路径的多余步骤，以及剩余清单的依赖与验收实现。截图取消时恢复控件的协程边界尚未专门复现，本轮不为未证实问题扩大修复。全库 issue 保持打开。

本轮安装 APK SHA-256：`3a1343be60488c181e9776e9f230d14af6182cea16080fd0473eeb8aa543beb9`。Python override 另行应用，来源及应用步骤见 overrides README。


## 浮框避让与原生闹钟加速

- 浮框原来按点击的上下半屏切换位置，动作结束后又复位，导致反复上下移动。现在仅当实际触摸起点落入浮框真实屏幕边界才避让，移动后保持位置；保留用户拖动位置、停止/接管入口及截图前隐藏。边界和坐标转换单测通过，已安装；完整视觉稳定性仍需更多滑动/键盘场景验收。
- 复用 `alarm_reminder_create`，增加 ISO 星期 `repeatDays`，转换为 Android `AlarmClock.EXTRA_DAYS`；每日设置一次传入全部七天。`exact_alarm` 明确拒绝重复参数。系统闹钟默认 `skipUi=true` 请求直接创建；显式 false 保留编辑入口。工具回执保留 `creationVerified=false`，不得将 Intent 派发当成创建成功。
- 旧 BA20 GUI 组件创建用时 63.256 秒、15 动作。第一轮正常聊天原生创建使用编辑入口，后续 GUI 核验/保存为 3 动作、26.763 秒（此为 GUI 子任务时间，不是全程时间）；独立读回 07:00/每天/已开启，测试项已清理。run `gui-ae0e2653-5293-405b-9d36-08454b2b006c`。
- 默认直接创建的第一轮已独立读回目标闹钟，但并发调试观察广播触发 ANR，进程退出，GUI 验收停留 0 步。系统 exit-info 指向 `DebugOmniFlowObserveReceiver`；此轮只算创建效果确认，不能算正常聊天端到端通过，也不能据最后一行日志归因于 Python 或模型。测试项已清理，重跑时不并发注入原生观察请求。
- 本次定向原生测试共 21 项通过：闹钟参数/Intent/未验证语义 3，设备 MCP 4，浮框边界 4，坐标/动作反馈 10。APK 已构建安装，SHA-256 `672455eed87d07d55d04c11239e7d73ef860ae956adc3ea8e6b8d48002ac0bfd`。

- 不并发观察重跑 `gui-5839a3fe-22ec-4ebd-b6ae-51efbd693a2c`：系统闹钟再次创建正确，独立控制侧读回 07:00/每天/已开启；GUI 核验 0 步等待 30.105 秒后返回 `android_gui_accessibility_not_ready`。系统 dumpsys 表明前一轮 ANR 后服务仍 Enabled，但在 Crashed 集合且未 Bound。仅重启本应用的无障碍绑定后确认 Bound、Crashed 为空。此轮也不计完整聊天通过。

加速后续验收标准：分别记录用户提交至原生派发、实际对象读回、最终答复耗时；比较相同入口与初始状态，不把 15→0 次创建操作等同于端到端提速倍数。系统/API能力优先，重复流程使用有前置条件与结果校验的技能，缺少接口或状态不符才进入 GUI。当前仍待处理只读校验触发完整视觉规划、无障碍崩溃状态反馈和调试观察超时。

- 恢复绑定后的只读 GUI 组件验证通过：`debug-vlm-cd95f119-d7dc-4ab3-aac7-9356f0697848`，0 次点击、1 次模型调用、8.692 秒，其中模型调用 1.541 秒。当前列表直接显示目标时间、每天和已开启，按目标立即 finished。它是单独恢复验收，不与前面的正常聊天耗时拼接成一次端到端跑分。


## 扩展扫描：便签与专注计时

LI04 的便签输入/重开/搜索子场景：创建唯一测试标题和两行中文正文，保存返回、按标题搜索、重新打开。GUI 组件 run `debug-vlm-5be90b99-d180-4098-a424-c4b38477844d` succeeded，13 动作、14 次模型调用、53.911 秒。独立新观察可精确读取标题，但 vivo 便签正文不暴露于无障碍树；独立新截图确认两行正文及换行正确。不能把树中没有正文记为输入失败，也不能只凭工具 success 判断正文正确。搜索后出现额外返回和两次结果点击，保留为效率观察，未证明定位错误。此结果不等于 LI04 三餐规划完整任务通过。


便签清理 run `debug-vlm-c807a6b0-bb2b-4119-9f7b-36d575454ea4`：4 动作、18.462 秒；只删除测试笔记，未清空回收站。独立搜索结果中不再有测试笔记条目；查询字符串本身仍在搜索框，不能把它误计成笔记仍存在。

BA02 原目标组件 run `debug-vlm-8e0eff05-2c8c-4c66-ab37-8bd975b1592e`：19 动作、93.723 秒；独立读取系统 zen_mode=1，时钟显示25分钟计时器和递减余时。恢复 run `debug-vlm-d696de46-0bb5-44bb-b0f3-fc325247522b` 20 动作、104.339 秒失败：已取消本轮计时并将选择值恢复00:28:53，但同名设置结果导致反复点击，勿扰未关闭。操作者随后通过系统通知命令关闭本轮勿扰，独立核验zen_mode=0、无运行中计时且选择值与基线一致。因此本项是目标创建通过、恢复需要辅助，不记为全程独立成功。

### 本轮发现与修复边界

- **同名设置结果上下文不足。** 保存XML证实三个“专注模式”属于不同设置路径；执行器正确返回歧义而非错误唯一匹配，随后模型坐标可能落入顶部遮挡或其他结果。补充每个最近可点击父行内标签、子标题、屏幕边界到实际动作回执，保持原模型请求与现有回退，不盲选第一项。12项Python测试通过，官方override三个文件应用/reload成功。
- **真机复测未证明效率改善。** `debug-vlm-9698bf3a-4cfe-4a59-8d21-6c9a5ad14f8b` 只读导航最终成功，但16动作、103.715秒；10次长描述 target_missing，未触发本次 target_ambiguous 候选反馈，最后改走声音与振动菜单才到达勿扰。系统zen_mode保持0。不能以最终成功宣称同名搜索已修好。
- **计时器原生能力缺失。** GUI创建与恢复需要多次滚轮操作。增加共享Agent/MCP的timer_create，以ACTION_SET_TIMER派发秒数、可选名称和skipUi，默认请求直接创建；返回creationVerified=false，不伪造运行验收或未实现的取消能力。参数与Intent/注册定向测试7项通过。
- **日历回查缺提醒字段。** LI22正常聊天经原生工具在本地日历创建2026-11-04 15:00–15:30、提前60分钟提醒；独立日历provider核对时间、单一对象与提醒均正确。但原列表返回缺reminderMinutes，模型只能引用创建回执。修复现有listEvents，对当前页eventIds批量读一次Reminders并按ID关联，空提醒明确[]，不改写权限。14项定向测试通过。
- **日历修复已真机验收并清理。** 新版正常聊天查询显示reminderMinutes=[60]，条件核对后仅删除该测试事件，再查为空；独立provider查询事件与对应提醒均为空。创建、原生读回及条件删除经过正常聊天入口。
- **新增待修：聊天行内代码无障碍漏读。** 同一正常聊天结果的截图可见eventId/时间/[60]等行内代码，无障碍描述中对应值缺失；普通文本可见。已保留两份私密证据，尚未修改Flutter渲染/语义，不将截图正确视作无障碍完整。


原生计时器真机：正常聊天请求25分钟测试计时，随后GUI只负责验收，run `gui-957acd87-33cd-447f-96e7-aabedba82cbb` succeeded，1动作、21.471秒；该动作是为了刷新观察而点击不可交互标题，仍是效率问题。独立观察确认唯一测试标签和运行中的倒计时，操作者仅取消本轮计时器。不能把这段验收耗时与含勿扰设置的93.723秒直接计算提速倍数。原生工具未添加取消接口。

本轮Python覆盖回归12项、Node扫描/分析器7项通过；timer/MCP定向7项、日历定向14项通过；构建安装成功。APK SHA-256：`f9b67807e205dd3726f959cfc9a36fa9c69a419bbab8b1fdf1057455c72b33f4`。Python覆盖另外通过官方apply/reload应用，构建APK不会自动替代覆盖文件。

最终恢复：计时选择器恢复run `debug-vlm-607e7fc3-a488-4aa3-a429-10b903d17724` 7动作、38.036秒通过；独立核对00:28:53、无运行中计时、原可见常用预设仍在。勿扰zen_mode=0，临时USB常亮恢复原值0。测试笔记已软删除、未清空回收站，测试日历事件和提醒已删除。

## 2026-10-30 01:51–03:40 breadth-first continuation

This accounting covers this continuation only, not the entire 280-item inventory or a claim of original benchmark completion. A source is counted once even if it has multiple subcases. “Accepted” means the deliberately bounded subcase and its relevant side-effect checks met the stated conditions; “partial” includes missing source fields, wrong extra claims or unverified side effects. Fixture/dependency blocks are reported separately and can overlap an exercised source.

| Measure | Count | Scope |
|---|---:|---|
| Distinct source scenarios exercised | 16 | LI03, LI15, RC07, PlanCommuteRouteSmsTask, LI12, HE07, ListRecentCommitsEmailTask, ExtractBenchmarkComparisonEmailTask, LI16, CheckDistanceAskUserTask1, SendWebpageAudioCountSmsTask, HE05, MattermostResourceConflictResolutionTask, GoogleMapsAlibabaPhoneContactTask, PhotoManagementTask, BA07 |
| Broad capability groups | 14 | Scheduling/conflict reasoning; filtered calendar plus weather; public content/restaurant search; route reading; music catalog; still-image understanding; repository source retrieval; paper comparison; missing-information clarification; web text counting; video playback/temporal description; contact creation/provider visibility; native media metadata; form entry/routing |
| Bounded subcases accepted | 4 | HE07 synthetic image, public recent-commit retrieval, missing-destination clarification, unique media metadata query |
| Partial or with observed defects/side effects | 12 | The other exercised sources; see linked individual batch records. No original external-message or booking workflow is claimed complete. |
| Dependency/fixture blocks | 2 | LI10 intended reading app/baseline unavailable; LI03 calendar fixture insert not verified. LI03 also has a separate text subcase above. |

Product changes in this continuation: `4ccc941e` adds dynamic embedded-WebView attachment metadata (focused JVM tests and APK installed; live propagation was not exercised by the foreground-browser retest); `79f779b3` detects the observed unreadable contact-provider rows and warns about uncertain-write retries (17 JVM tests; the same production code was verified on a temporary debug build, and a clean APK was installed); `122b229b` adds a narrow VLM result-context notice for the recorded permanent-denial button; `bb31f57d` adds bilingual guidance to respect explicit dependency/environment boundaries. The latter two passed focused JVM tests and were installed on the phone; neither has live end-to-end proof of model compliance or enforcement. The latter is prompt guidance, not a sandbox; the later no-file failure demonstrates that prompts alone do not guarantee boundaries. Earlier production memory/truncation changes and their evidence are recorded in their own batch notes. Host scanner fixes `7b709468`, `32fc52e3`, and `f5115622` cover partial streamed JSON and guarded transient focus reads; 15 offline runner tests passed, without replay or access-denial bypass. They are not mobile-product fixes.

The video follow-up (`362f6a46`) shows screenshot-attachment metadata and a pre-seek player state at 00:05; the following state is essentially the same frame with controls hidden, so the supposed 00:03 seek is not independently established. The error cannot be attributed solely to visual reasoning. Outstanding unknown-baseline files, environment and app state remain in [the pending list](android-scan-pending-artifacts-2026-10-30.md).
