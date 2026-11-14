# 视觉控制与快速决策：公开代码核对及部署检查点

本轮暂停扩场景，只保留计算器、闹钟、便签；目标是同设备、同起点的完整成功率与耗时对照。已完成 StartLux 四次截图闭环探索，独立验收均失败；同条件 OmniBot 基线尚未完成，不能宣称已完成比较。

## 实测更新

StartLux 原始权重已全部核对 SHA256，两个分片与 HF LFS 哈希一致。3090 上以 BF16、torch 2.10.0、transformers 5.8.1、torchvision 0.25.0 运行，占约 9.4 GiB 显存。health 确认 images=true，双向注意力一致性检查通过。未装线性注意力加速内核，延迟不是优化后的性能上限。原 35a3 服务已在确认无在途请求后停止，服务配置保留；W1 下载暂停以优先测试视觉模型。

每项仅一次探索，不代表稳定统计成功率。模型自报 done 不等于验收通过。

| Run | 策略 | 步数 | 总耗时 s | 独立结果 |
|---|---|---:|---:|---|
| startlux-calc-03 | 动作与 X/Y 分别选择 | 8 | 18.50 | 17+28 最终显示 `0÷`，失败 |
| startlux-notes-01 | 同上 | 2 | 4.79 | 强制停止重开后仍为空白，失败 |
| startlux-alarm-01 | 同上 | 8 | 18.06 | 目标 07:35，实际新增 11:56，失败 |
| startlux-calc-regions-01 | 动作→区域→区域内位置 | 8 | 19.80 | 最终显示 375，失败；不是成功修复 |

前两个计算器启动尝试因 ADB 重连和前台查询不兼容失败，未进入推理，排除在模型成绩之外。修正前台查询为 dumpsys window，并增加快照恢复后的有界就绪等待。运行异常也保存终态，不自动重放动作。以上均为 harness 修正，不是 OmniBot 产品修复。

纯视觉运行时实际检查 Bound/Enabled accessibility services 为空。原始截图、模型请求/响应、操作时序和 oracle XML 保留在 hwstaff 专用实验目录，模型从未获得 oracle 内容。同图诊断正确识别显示值 0、C 位于底行第三列、下一步应按 1；这说明视觉入口可用，但不能证明数字坐标定位或终止判断可靠。

当前 AVD 已配置 DeepSeek 对照 Provider，凭据只由宿主私有引用读取，不复制进设备或仓库。但 GUI 启动暴露 Alpine 缺少 NumPy，尚未进入模型推理，不能将其记为模型任务失败；正在补齐测试环境。下一候选 OpenBMB/AgentCPM-GUI 8B 原生接受截图并输出 0–1000 坐标和动作 JSON。公开代码固定在 `2168ae21b1bed1cdb88736d422934825795a9fd7`，模型源代码已审查，权重分片下载中，依赖导入已验证；尚无其推理成绩。来源：https://github.com/OpenBMB/AgentCPM-GUI

测试对象已随隔离快照恢复。进入闹钟列表后独立核对只剩 8:30/9:00 两条基线，计算器回到 0。时钟页的 digital_clock 不能当成闹钟列表条目；验收需要确认页面与对象身份。

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
