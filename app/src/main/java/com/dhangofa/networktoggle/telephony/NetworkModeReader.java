package com.dhangofa.networktoggle.telephony;

/** Main entry point for reading the current active network mode. */
import android.content.Context;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;

public final class NetworkModeReader {
	private final AppPreferences appPreferences;
	private final ShizukuBinderModeReader shizukuBinderReader;
	private final PrivilegedModeReader privilegedModeReader;
	private final SimResolver simResolver;

	public NetworkModeReader(Context context, AppPreferences appPreferences, SimResolver simResolver) {
		this.appPreferences = appPreferences;
		this.shizukuBinderReader = new ShizukuBinderModeReader(simResolver);
		this.privilegedModeReader = new PrivilegedModeReader(context, simResolver);
		this.simResolver = simResolver;
	}

	public NetworkMode readCurrentMode() {
		return readCurrentMode(appPreferences.getTargetSim());
	}

	public NetworkMode readCurrentMode(TargetSim targetSim) {
		ExecutionMode executionMode = appPreferences.getExecutionMode();
		if (executionMode == ExecutionMode.NONE) {
			return NetworkMode.UNKNOWN;
		}

		if (targetSim == TargetSim.BOTH) {
			NetworkMode mode1 = readCurrentMode(TargetSim.SIM_1);
			NetworkMode mode2 = readCurrentMode(TargetSim.SIM_2);

			return mode1 != NetworkMode.UNKNOWN && mode1 == mode2
					? mode1
					: NetworkMode.UNKNOWN;
		}

		return readSingleMode(executionMode, targetSim);
	}

	private NetworkMode readSingleMode(ExecutionMode executionMode, TargetSim targetSim) {
		NetworkMode mode = NetworkMode.UNKNOWN;
		// 1. Shizuku Fast-Path (Binder IPC)
		if (executionMode == ExecutionMode.SHIZUKU) {
			mode = shizukuBinderReader.readCurrentMode(executionMode, targetSim);
		}

		// 2. Root/Shizuku privileged shell fallback path
		if (mode == NetworkMode.UNKNOWN) {
			mode = privilegedModeReader.readCurrentMode(executionMode, targetSim);
		}
		return mode;
	}
}
