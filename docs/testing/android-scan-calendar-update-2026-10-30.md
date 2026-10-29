# LI18 单条虚构活动的本地日历更新，2026-10-30

先通过 Android Calendar Provider 只读确认唯一可写 `LOCAL` 日历与随机测试标题不存在。正常聊天分别调用 `calendar_event_create` 和 `calendar_event_update`：创建 2026-11-07 10:00–10:30 的单条虚构活动，然后按创建结果和独立 provider 验证过的同一 eventId 改为 11:15–11:45，时区均为 `Asia/Shanghai`，不邀请他人、不设置提醒。每轮各只有请求的对应工具调用，均正常终止。

独立 provider 检查显示创建、更新后的起止时间对应预期毫秒，标题、eventId 和日历 ID 保持一致，时区为 `Asia/Shanghai`，提醒为空。随后正常聊天只按该 ID 删除，独立查询精确标题、eventId 和该 ID 对应提醒均为空。这是受限的单个本地事件更新子场景，不等于一周多项活动、其他账户的日历同步或原始 LI18 完整通过；未审计其他日程的全部内容。私有明细保存在忽略的 `.session-history/scan-20261030-calendar-update/`。
