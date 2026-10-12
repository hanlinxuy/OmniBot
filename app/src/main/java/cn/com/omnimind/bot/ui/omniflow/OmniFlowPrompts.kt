package cn.com.omnimind.bot.ui.omniflow

/**
 * The Agent request that enhances a Function (Dart
 * `buildFunctionEnhancementPrompt`). The enhancement itself belongs to the
 * official OmniFlow writer; this only forwards the local source and the
 * current draft. `source_run_id` is UI provenance, not part of
 * `omniflow.function.v2`, so it stays out of the artifact.
 */
internal fun functionEnhancementPrompt(function: Map<String, Any?>): String {
    val functionId = OmniFlowPayloads.text(function["function_id"])
    val sourceRunId = OmniFlowPayloads.text(function["source_run_id"])
    val artifact = OmniFlowPayloads.json(function - "source_run_id")
    return "请调用本机 OmniFlow 官方 save_function 完成 Function enhance。" +
        "参数必须是 run_id=$sourceRunId、functions=[下面的完整 Function]、enhance=true；" +
        "不要执行 Function，不要调用 list_run_logs/get_run_log/get_function，" +
        "不要把 Function 或 RunLog 写到远端，也不要自行改写增强规则。" +
        "由 OmniFlow 内置的官方增强流程生成并写回本地 Store；若 source_run_id 为空，" +
        "直接返回明确错误。\n\n" +
        "function_id: $functionId\n" +
        "source_run_id: $sourceRunId\n" +
        "function: $artifact"
}

/** What a replay is for, as its goal: name, description and the arguments (Dart `_replayGoal`). */
internal fun replayGoal(name: String, description: String, functionId: String, arguments: Map<String, Any?>): String {
    val lines = buildList {
        if (name.isNotEmpty()) add(name)
        if (description.isNotEmpty() && description != name) add(description)
        if (arguments.isNotEmpty()) add("参数: ${OmniFlowPayloads.json(arguments)}")
    }
    return lines.joinToString("\n").trim().ifEmpty { functionId }
}
