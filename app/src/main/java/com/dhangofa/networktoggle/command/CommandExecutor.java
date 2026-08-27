package com.dhangofa.networktoggle.command;

/**
 * Interface for executing shell commands.
 * Provides a common contract to swap between Root and Shizuku implementations seamlessly.
 */

import com.dhangofa.networktoggle.model.CommandResult;

public interface CommandExecutor {
    CommandResult execute(String command);
}
