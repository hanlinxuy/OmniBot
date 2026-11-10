import { createBashToolDefinition, createLocalBashOperations } from "@earendil-works/pi-coding-agent";

/** Keep Pi's process execution and cancellation owner; bound omitted timeouts. */
export function phoneBashTool(cwd: string, defaultTimeoutSeconds = 120) {
  const local = createLocalBashOperations();
  const tool = createBashToolDefinition(cwd, {
    operations: {
      exec: (command, directory, options) => local.exec(command, directory, {
        ...options,
        timeout: options.timeout ?? defaultTimeoutSeconds,
      }),
    },
  });
  tool.description += ` If timeout is omitted, the phone host uses ${defaultTimeoutSeconds} seconds. Set an explicit timeout for a longer command; a timeout is a failed tool call, not task completion.`;
  if (process.env.OMNIBOT_TERMINAL_DISTRIBUTION === "alpine") {
    tool.description += " This terminal uses Alpine/BusyBox: GNU-only options such as stat --format and find -printf are unavailable. Use stat -c, portable shell syntax or Python. Read a matching available skill before installing document-processing dependencies.";
  }
  return tool;
}
