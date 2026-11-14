# 视觉控制与快速决策：公开代码核对及部署检查点

本轮暂停扩场景，只保留计算器、闹钟、便签；目标是同设备、同起点的完整成功率与耗时对照。StartLux 的计算器、闹钟及区域定位计算器复测未通过；便签可见输入正确，原强停验收不适用，不能宣称四次均失败。OmniBot 对照见下文。

## 实测更新

StartLux 原始权重已全部核对 SHA256，两个分片与 HF LFS 哈希一致。3090 上以 BF16、torch 2.10.0、transformers 5.8.1、torchvision 0.25.0 运行，占约 9.4 GiB 显存。health 确认 images=true，双向注意力一致性检查通过。未装线性注意力加速内核，延迟不是优化后的性能上限。原 35a3 服务已在确认无在途请求后停止，服务配置保留；W1 下载暂停以优先测试视觉模型。

每项仅一次探索，不代表稳定统计成功率。模型自报 done 不等于验收通过。

| Run | 策略 | 步数 | 总耗时 s | 独立结果 |
|---|---|---:|---:|---|
| startlux-calc-03 | 动作与 X/Y 分别选择 | 8 | 18.50 | 17+28 最终显示 `0÷`，失败 |
| startlux-notes-01 | 同上 | 2 | 4.79 | 终态截图确认输入正确；原强停验收无效，保存需重测 |
| startlux-alarm-01 | 同上 | 8 | 18.06 | 目标 07:35，实际新增 11:56，失败 |
| startlux-calc-regions-01 | 动作→区域→区域内位置 | 8 | 19.80 | 最终显示 375，失败；不是成功修复 |

前两个计算器启动尝试因 ADB 重连和前台查询不兼容失败，未进入推理，排除在模型成绩之外。修正前台查询为 dumpsys window，并增加快照恢复后的有界就绪等待。运行异常也保存终态，不自动重放动作。以上均为 harness 修正，不是 OmniBot 产品修复。

纯视觉运行时实际检查 Bound/Enabled accessibility services 为空。原始截图、模型请求/响应、操作时序和 oracle XML 保留在 hwstaff 专用实验目录，模型从未获得 oracle 内容。同图诊断正确识别显示值 0、C 位于底行第三列、下一步应按 1；这说明视觉入口可用，但不能证明数字坐标定位或终止判断可靠。

AVD 的 DeepSeek 对照 Provider 通过临时宿主转发访问官方 API，凭据只由私有引用读取，不复制进设备或仓库。补齐 Alpine NumPy 2.1.3 后已完成对照；缺依赖和转发失效的预检不计为模型任务失败。下一候选 OpenBMB/AgentCPM-GUI 8B 原生接受截图并输出 0–1000 坐标和动作 JSON。公开代码固定在 `2168ae21b1bed1cdb88736d422934825795a9fd7`，模型源代码已审查，依赖和 config/tokenizer 导入已验证；权重尚未完成下载，保留分片供续传，不报推理成绩。来源：https://github.com/OpenBMB/AgentCPM-GUI

测试对象已随隔离快照恢复。进入闹钟列表后独立核对只剩 8:30/9:00 两条基线，计算器回到 0。时钟页的 digital_clock 不能当成闹钟列表条目；验收需要确认页面与对象身份。

### OmniBot 对照进展

新模拟器缺少此前 vivo 上部署的 Python overrides。查询显示 disabled、modified_files=[]；6 个原始文件 SHA256 均与 pinned runtime 一致。原始计算器 run `debug-vlm-6513e004-96ea-497f-91d0-3e5ad4aae13b` 执行 20 次清除，78.656 s，结果保持 0，失败。

通过官方接口部署已有 controls/core/planner/context/semantic_grounding/execution/engine，逐文件读回哈希，38 项针对性测试通过。复测计算器 2 次模型调用、7 个控件动作、5.478 s，独立结果 45，通过。闹钟复测 11 次模型调用、10 动作、20.747 s，独立列表读取 7:35 AM 且该行开关 checked=true，原有 8:30/9:00 保持关闭，通过。

这是补齐已提交修复的部署，不是本轮新发明的模型或产品算法。比较对象为 StartLux 纯视觉候选选择和 OmniBot + DeepSeek 的现有控件/GUI 链路，不能作单因素归因。快照恢复后的两项输出出现相同 runtime run_id；原始证据按宿主唯一案例目录隔离，不按 run_id 合并，也不只靠 UUID 判断结果是否新鲜。

### 便签验收更正

StartLux 的 final.png 和 OmniBot 的终态截图均显示完整正确文本。OmniBot 首轮 4.820 s、2 动作、3 模型调用。但两者随后直接 force-stop 再打开都为空。

核对 Fossify Notes 1.7.0 的 [TextFragment.onPause](https://github.com/FossifyOrg/Notes/blob/1.7.0/app/src/main/kotlin/org/fossify/notes/fragments/TextFragment.kt#L105)，自动保存依赖页面暂停；直接强停绕过正常保存时机。因此撤回此前“没有输入成功”和“三项全失败”的结论。可见输入通过，持久化必须单独验证；若验收阶段通过 Home 触发自动保存，应明确这是正常生命周期验收，不冒称模型主动点击了保存。

正常生命周期复测：模型完成后，验收器按 Home，随后强停并重新打开应用。StartLux `startlux-notes-normal-02` 用 3 动作 / 3 请求 / 6.836 s；OmniBot `omnibot-notes-normal-02` 用 2 动作 / 3 调用 / 4.419 s。两者重开均精确读回 `visual-loop-owned-20261114`。耗时是模型控制阶段，不包含这段独立验收。结论为输入与正常生命周期持久化通过，不等于模型主动验证了保存。

## 本轮有效对照

| 场景 | StartLux 4B BF16，纯视觉候选策略 | OmniBot + DeepSeek，部署已有 overrides |
|---|---|---|
| 计算器 17+28 | 18.502 s，错误结果，失败 | 5.478 s，独立读回 45，通过 |
| 新建 07:35 闹钟 | 18.063 s，错误时间，失败 | 20.747 s，07:35 AM 且已开启，通过 |
| 写便签 | 6.836 s，输入正确，正常离开重开后持久化通过 | 4.419 s，同样通过 |

各项为小样本探索，不是官方 benchmark 分数或稳定统计成功率。两套系统模型、观察信息、动作接口均不同。这里支持的是系统选择建议，不是对 diffusion、模型大小或量化的单因素结论。

最终恢复比较快照并独立确认：测试便签正文不存在，闹钟只保留原始 8:30/9:00 且均关闭，USB 常亮值 0；无物理 vivo 操作。临时 DeepSeek 转发已停止，设备 Provider 中只有失效的临时本地地址及占位 key。3090 的 StartLux 服务保留，原 35a3 服务配置未改但当前未运行。

## 公开实现核对

- WhaletechAI/W1-JEV 固定版本 `6b011cf3b63f18c1d82466639c36c68ad2a62cb1`，约 3.74B 参数。`structured_server.py` 明确拒绝 images；batch logits 路径读取候选答案位置后选择标签。它是文本结构化判断组件，不是公开的 Whale Phone 视觉策略模型。
- W1 官方文件为 FP32 checkpoint，运行时转换 BF16。权重大小 14,956,488,634 bytes，官方 LFS SHA256 `d4cf5a0a7d045cade9b9c247612940731b217c90d43a758f8eb0401a4fb36e2a`。尚未完成下载，未加载推理。
- StartLux-Decision 代码固定在 `a99977d9332decc9fb27a2488b4a83b533b6cd5f`。正式图片接口使用 `images` 和 `<image>` 标记，transformers backend 通过 vision tower 处理图片。
- StartLux v1 公开 Computer Use demo 的 `agent.py` 使用 Playwright DOM 收集控件与文字，模型接收候选选项，截图仅用于记录；输入框通过预置建议选择。因此该 demo 不能用作纯视觉能力证据。
- StartLux-Decision v1.1 4B Instant 的 HF revision 为 `416088f77cfce09633906a3675c7cd2e5dabcf10`；从官方 ModelScope 镜像下载，逐文件 SHA256 已核对。两个 safetensors 分片共约 9.32 GB。
- Instant 需要训练时对应的双向 prompt attention；不能直接用普通因果 forward 替代。官方 transformers backend 有双向一致性检查。其参考实现未装线性注意力加速内核时可能显著偏慢，部署后必须记录实际 backend/kernel，不能把参考路径延迟当作产品极限。

## 本地实施

W1-JEV 与 StartLux 分别使用独立目录与 Python 环境。3090 当前加载 StartLux，原 35a3 的 systemd 配置保留。

已实现 `scripts/visual-loop/run.py` 截图决策原型并复制到 hwstaff。模型没有读控件树、DOM 或 OCR，动作来自模型选择。StartLux 使用通用坐标候选或条件区域策略，必须把候选粒度和多问题前向成本列为 harness 变量。三个离线边界测试通过；这不是实机成功率。

已核对 hwstaff AVD 为 `omnibot-lab`，Android Clock Activity 可解析。已从官方 GitHub release 安装 Fossify Calculator 1.4.0（`org.fossify.math`）与 Notes 1.7.0（`org.fossify.notes`），保存独立应用快照，不覆盖原权限基线。未改动 vivo。

## 必须分开报告

1. W1 文本判断准确率/延迟；不能计作 GUI 任务。
2. StartLux 截图决策正确性/耗时，包含坐标候选与实际前向次数。
3. 完整任务成功率、总耗时、人工接管、重复动作和清理。
4. 同模型不同 harness 与不同模型比较，分别解释，不能作单因素因果结论。

INT4 可能引入量化误差，但当前没有同一 35a3 模型的 BF16/Q4 对照，不能把历史失败归因于量化。

来源：
- https://huggingface.co/WhaletechAI/W1-JEV/tree/6b011cf3b63f18c1d82466639c36c68ad2a62cb1
- https://github.com/StartLuxLabs/StartLux-Decision/tree/a99977d9332decc9fb27a2488b4a83b533b6cd5f
- https://huggingface.co/startlux-models/StartLux-Decision-v1.1-4B-Instant
- https://modelscope.cn/models/StartLuxAI/StartLux-Decision-v1.1-4B-Instant
