# 视觉控制与快速决策：公开代码核对及部署检查点

本轮暂停扩场景，只保留计算器、闹钟、便签；目标是同设备、同起点的完整成功率与耗时对照。当前尚无新模型的真实 GUI 成绩，不能宣称已完成比较。

## 公开实现核对

- WhaletechAI/W1-JEV 固定版本 `6b011cf3b63f18c1d82466639c36c68ad2a62cb1`，约 3.74B 参数。`structured_server.py` 明确拒绝 images；batch logits 路径读取候选答案位置后选择标签。它是文本结构化判断组件，不是公开的 Whale Phone 视觉策略模型。
- W1 官方文件为 FP32 checkpoint，运行时转换 BF16。权重大小 14,956,488,634 bytes，官方 LFS SHA256 `d4cf5a0a7d045cade9b9c247612940731b217c90d43a758f8eb0401a4fb36e2a`。尚未完成下载，未加载推理。
- StartLux-Decision 代码固定在 `a99977d9332decc9fb27a2488b4a83b533b6cd5f`。正式图片接口使用 `images` 和 `<image>` 标记，transformers backend 通过 vision tower 处理图片。
- StartLux v1 公开 Computer Use demo 的 `agent.py` 使用 Playwright DOM 收集控件与文字，模型接收候选选项，截图仅用于记录；输入框通过预置建议选择。因此该 demo 不能用作纯视觉能力证据。
- StartLux-Decision v1.1 4B Instant 的 HF revision 为 `416088f77cfce09633906a3675c7cd2e5dabcf10`；已找到官方 ModelScope 镜像并按其逐文件 SHA256 下载原始权重。两个 safetensors 分片共约 9.32 GB，仍在下载。
- Instant 需要训练时对应的双向 prompt attention；不能直接用普通因果 forward 替代。官方 transformers backend 有双向一致性检查。其参考实现未装线性注意力加速内核时可能显著偏慢，部署后必须记录实际 backend/kernel，不能把参考路径延迟当作产品极限。

## 本地实施

3090 原有 35a3 服务占约 20 GiB 显存，尚未停止。W1-JEV 与 StartLux 分别使用独立目录与 Python 环境。先完成下载、校验及依赖，再确认原服务无在途请求，决定 GPU 切换。

已实现 `scripts/visual-loop/run.py` 截图决策原型并复制到 hwstaff。没有读控件树、DOM 或 OCR，动作来自模型选择。现用通用坐标候选，因此必须把候选粒度和多问题前向成本列为 harness 变量。两个离线边界测试通过；这不是实机成功率。

已核对 hwstaff AVD 为 `omnibot-lab`，Android Clock Activity 可解析。未开始真实模型操作，未创建闹钟或便签，未改动 vivo。计算器/便签 fixture、完整失败终态记录、独立验收和清理，以及当前 OmniBot 的同条件 provider 配置仍待完成。

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
