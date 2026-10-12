package cn.com.omnimind.nativeui.omniflow

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/** Whether the OmniFlow plugin can serve the pages. */
enum class OmniFlowAvailability { Loading, Ready, NotInstalled, Disabled, Failed }

/** One paged list. [error] is shown only while nothing has loaded. */
@Immutable
data class OmniFlowList<T>(
    val items: List<T> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
)

/** The open Function sheet; it shows the list row at once and refreshes from `get_function`. */
@Immutable
data class FunctionSheetState(val function: OmniFlowFunction, val syncing: Boolean = false, val syncFailed: Boolean = false)

/** The run arguments dialog for [function]; [errors] name the fields to fix. */
@Immutable
data class ArgumentsRequest(
    val function: OmniFlowFunction,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, ArgumentError> = emptyMap(),
)

/** A one-shot message: a string resource with an optional argument (often the tool's own error text). */
@Immutable
data class OmniFlowNotice(@StringRes val text: Int, val arg: String? = null)

@Immutable
data class ExecutionCenterState(
    val availability: OmniFlowAvailability = OmniFlowAvailability.Loading,
    val availabilityError: String? = null,
    val enablingPlugin: Boolean = false,
    val tab: OmniFlowTab = OmniFlowTab.Functions,
    val functions: OmniFlowList<OmniFlowFunction> = OmniFlowList(),
    val runLogs: OmniFlowList<RunLogSummary> = OmniFlowList(),
    /** RunLogs being compiled into Functions; their buttons show progress. */
    val registeringRunIds: Set<String> = emptySet(),
    /** The Function being replayed; one at a time, since a replay drives the screen. */
    val runningFunctionId: String? = null,
    val sheet: FunctionSheetState? = null,
    val arguments: ArgumentsRequest? = null,
    val confirmDelete: OmniFlowFunction? = null,
    /** A replay waits for the accessibility service; the dialog explains and opens settings. */
    val accessibilityNeeded: Boolean = false,
    val notice: OmniFlowNotice? = null,
)

data class ExecutionCenterActions(
    val setTab: (OmniFlowTab) -> Unit = {},
    val refresh: () -> Unit = {},
    val loadMore: (OmniFlowTab) -> Unit = {},
    val enablePlugin: () -> Unit = {},
    val openPlugin: () -> Unit = {},
    val openFunction: (OmniFlowFunction) -> Unit = {},
    val closeFunction: () -> Unit = {},
    val retryFunction: () -> Unit = {},
    val run: (OmniFlowFunction) -> Unit = {},
    val setArgument: (name: String, value: String) -> Unit = { _, _ -> },
    val confirmArguments: () -> Unit = {},
    val dismissArguments: () -> Unit = {},
    val openAccessibilitySettings: () -> Unit = {},
    val dismissAccessibility: () -> Unit = {},
    val enhance: (OmniFlowFunction) -> Unit = {},
    val requestDelete: (OmniFlowFunction?) -> Unit = {},
    val confirmDelete: () -> Unit = {},
    val openRunLog: (String) -> Unit = {},
    val registerRunLog: (RunLogSummary) -> Unit = {},
    val dismissNotice: () -> Unit = {},
)

@Immutable
data class RunLogPageState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: RunLogDetail? = null,
    val linkedFunction: OmniFlowFunction? = null,
    val registering: Boolean = false,
    /** The step whose sheet is open. */
    val openStep: Int? = null,
    /** The recorded screen shown over the step sheet. */
    val screen: RunLogScreenState? = null,
    val screenLoading: Boolean = false,
    val notice: OmniFlowNotice? = null,
)

data class RunLogActions(
    val refresh: () -> Unit = {},
    val register: () -> Unit = {},
    val openFunction: () -> Unit = {},
    val openStep: (Int?) -> Unit = {},
    val showScreen: (RunLogStep) -> Unit = {},
    val closeScreen: () -> Unit = {},
    val dismissNotice: () -> Unit = {},
)
