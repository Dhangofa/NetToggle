package com.dhangofa.networktoggle.telephony;

/**
 * Main entry point for reading the current active network mode.
 * It routes the request to either the Root or Shizuku readers depending on user settings.
 */

import android.content.Context;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

public final class NetworkModeReader {
	private final AppPreferences appPreferences;
	private final ShizukuBinderModeReader shizukuBinderReader;
	private final PrivilegedModeReader privilegedModeReader;
	
	public NetworkModeReader(Context context, AppPreferences appPreferences, SimResolver simResolver) {
		this.appPreferences = appPreferences;
		this.shizukuBinderReader = new ShizukuBinderModeReader(simResolver);
		this.privilegedModeReader = new PrivilegedModeReader(context, simResolver);
	}
	
	public NetworkMode readCurrentMode() {
		ExecutionMode executionMode = appPreferences.getExecutionMode();
		if (executionMode == ExecutionMode.NONE) return NetworkMode.UNKNOWN;
		// 1. Shizuku Fast-Path (Binder IPC)
		if (executionMode == ExecutionMode.SHIZUKU) {
			NetworkMode mode = shizukuBinderReader.readCurrentMode(executionMode);
			if (mode != NetworkMode.UNKNOWN) return mode;
		}
		// 2. Root/Shizuku privileged shell fallback path
		return privilegedModeReader.readCurrentMode(executionMode, appPreferences.getTargetSim());
	}
}

