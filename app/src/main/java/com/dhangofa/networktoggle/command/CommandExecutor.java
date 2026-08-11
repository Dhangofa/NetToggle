package com.dhangofa.networktoggle.command;

import com.dhangofa.networktoggle.model.CommandResult;

public interface CommandExecutor {
    CommandResult execute(String command);
}
