# Mika 本地 UI 控制研究与 OmniBot 适配

研究固定提交：[`wangzan101/Mika@d681593`](https://github.com/wangzan101/Mika/tree/d68159366abaea523c5e4e6fd4dc83aad78f9698)。本地采用稀疏 Git checkout，只取相关源码及 README/LICENSE/NOTICE；网络传输失败的文件通过 GitHub 连接器补取，并与该提交的 Git blob 哈希逐一核对。上游为 Apache-2.0，NOTICE 为 `Mika / Copyright 2026 wangzan101`。本次独立实现相关控制思路，没有整体复制上游执行器或引入其运行生命周期。

## 源码结论

| 边界 | Mika 实现 | 此 fork 的适配 |
|---|---|---|
| 滚轮 | [`WheelDriver.kt`](https://github.com/wangzan101/Mika/blob/d68159366abaea523c5e4e6fd4dc83aad78f9698/app/src/main/java/com/wangzan101/mika/cap/phone/a11y/WheelDriver.kt)：可编辑值优先 SET_TEXT；其次无障碍 forward/backward；最后控件列几何/必要 OCR。读回并稳定后才成功 | `set_wheel`：唯一控件选择器、每次重新观察、本地调节与稳定读回；Android 为 NumberPicker/SeekBar 的小幅纵向动作优先使用其 advertised 原生滚动；不可读时交回现有规划器 |
| 步长与停机 | [`WheelMath.kt`](https://github.com/wangzan101/Mika/blob/d68159366abaea523c5e4e6fd4dc83aad78f9698/core/src/main/kotlin/com/wangzan101/mika/core/cap/phone/WheelMath.kt)：从观察估计项高/方向/实际循环边界，有限步数与批量调整，无进展时退出 | 初版每次一个原生滚轮动作；几何路径由实际控件行计算；不猜看不到的环绕范围。上限 32 次调整、控制调用时间预算 45 秒（在子动作之间检查），无变化即交回 |
| 批量 | [`BatchRunner.kt`](https://github.com/wangzan101/Mika/blob/d68159366abaea523c5e4e6fd4dc83aad78f9698/core/src/main/kotlin/com/wangzan101/mika/core/agent/BatchRunner.kt)：1–20 步顺序执行，复用同一 ActionExecutor/guards/log；每步核验/等待，失败、意外切换、新消息时停 | `ui_steps` 1–8 步，支持 click/input_text/set_wheel/wait_until；每步在新树唯一定位，使用原 execute_robust_action、state_id、RunLog 与取消路径；失败不重放，返回已完成步数/失败位置 |
| 等待 | [`UntilWait.kt`](https://github.com/wangzan101/Mika/blob/d68159366abaea523c5e4e6fd4dc83aad78f9698/core/src/main/kotlin/com/wangzan101/mika/core/uistate/UntilWait.kt)：本地条件读取，事件唤醒加轮询后备，出现/消失/位置/稳定条件；空屏不证明消失 | `wait_until` XML 轮询，200 ms 间隔、200–10000 ms 超时；支持 appears/gone/stable，同包且非空树；稳定至少 400 ms。本次没有移植事件唤醒或 location 条件 |
| 系统功能 | README/工具栈包括系统接口与 GUI 组合 | OmniBot 原有 alarm_reminder_create/timer_create/calendar 工具继续优先；不再建第二套 Intent 工具 |

速度收益来自减少模型决策轮次与重复等待，不能从代码或剪辑演示推断任务速度。Mika 对自绘滚轮的 OCR 校正、批量原生滚轮步进等能力没有全部移植。

## 实现边界

- Android 修改在 `androidgui`。点击只有唯一 resource-id + 命中 bounds + advertised ACTION_CLICK 才走原生动作。NumberPicker/SeekBar 原生滚动仅用于同列、两端落在控件内、距离不超过半个控件高度的纵向动作。原生拒绝直接返回失败，不再派发补偿手势。
- XML 补充 actions/range 信息。vivo Notes 的日期/时/分是 SeekBar，能提供当前值及 forward/backward，但本次实机没有 range；不能据此假定 24/60 周期或任意 setProgress 能力。
- Python 通过官方 whole-file override 接口部署 `controls.py`、`core.py`、`planner.py`、`engine.py`。固定运行时 ZIP 保持原样。控制工具是现有 GUI planner 的工具，不是外层新增 ACP/MCP 生命周期。
- 所有子动作依旧进入现有 robust dispatch、Android act、支付确认守卫、取消与日志。观察和等待使用同一 host。批量执行不是跨应用计划，不接受坐标猜测或嵌套批次；文本输入后读回。
- Android 已返回 `host_completed` 且结果为 `stable` 或 `completed_by_action` 时，Python 不再额外固定 sleep(1)。host timeout/delegated/缺少证据时保留原等待；没有把 timeout 解释为稳定。
- 日期接受同设备年份的显式日期、月日以及“今天/明天/后天”，使用当前观察的设备日期。界面不暴露年份时不证明持久年份；不同年份的目标返回失败，不能用隐含年份假造验收。
- 本地工具不负责 OCR。控件不可读时返回具体失败，现有模型可重新观察并采用原视觉定位路径。计算器最终数值仍需视觉/独立验收。

验证与实际局限见 [真机结果](../testing/android-mika-controls-2026-10-31.md)。

## 实机发现后的协议修复

新增工具后的完整待办回归，provider 在 `parallel_tool_calls=false` 且要求单工具调用时返回两个调用，原 parser 按单调用契约拒绝并终止。该响应中的调用都未派发。现有 engine 仅对这一明确的调用数量契约错误，在原 planner 循环及原 max_steps 预算内提供一次纠正信息，要求单个 `ui_steps` 或单个下一动作。再次违反仍终止；其他 provider/解析异常不走此分支。没有执行、合并或重放被拒绝的动作。此行为有实际失败样本和一次纠正上限的离线测试。


进一步核对 [`AndroidScreen.kt`](https://github.com/wangzan101/Mika/blob/d68159366abaea523c5e4e6fd4dc83aad78f9698/app/src/main/java/com/wangzan101/mika/cap/phone/a11y/AndroidScreen.kt)：`awaitChange` 等待当前 display 的无障碍事件；`withEventClock` 在执行块前订阅，`EventClock` 依据最后一次事件时间判定连续安静，并在有界期限内结束。它不是固定长 sleep，也不是让模型反复截图问“好了没有”。本次 OmniBot 沿用原宿主判稳并加 XML 条件等待，没有移植 Mika 的虚拟显示或整套事件总线。
