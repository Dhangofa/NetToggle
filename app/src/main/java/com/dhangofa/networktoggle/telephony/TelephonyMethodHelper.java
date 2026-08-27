package com.dhangofa.networktoggle.telephony;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility class designed to resolve hidden Android API fragmentation across different device OEMs.
 * It provides safe reflection methods to locate varying ITelephony method signatures and 
 * unwrap nested reflection exceptions for clearer diagnostic logging.
 */
final class TelephonyMethodHelper {
	private TelephonyMethodHelper() {}
	
	/**
	 * Attempts to find a method within a given class by iterating through a prioritized list
	 * of potential method signatures. This gracefully handles OEM-specific framework modifications
	 * (e.g., Samsung or Xiaomi appending extra parameters like a calling package string).
	 *
	 * @param type       The class to inspect (e.g., ITelephony.class).
	 * @param name       The exact string name of the target method.
	 * @param signatures A varargs array of potential parameter type arrays to check, in order of preference.
	 * @return The matching Method object, or null if no compatible signature is found.
	 */
	static Method find(Class<?> type, String name, Class<?>[]... signatures) {
		for (Class<?>[] signature : signatures) {
			for (Method method : type.getDeclaredMethods()) {
				if (!name.equals(method.getName())) {
					continue;
				}
				
				Class<?>[] actual = method.getParameterTypes();
				if (actual.length != signature.length) {
					continue;
				}
				
				boolean match = true;
				for (int i = 0; i < actual.length; i++) {
					if (actual[i] != signature[i]) {
						match = false;
						break;
					}
				}
				
				if (match) {
					return method;
				}
			}
		}
		return null;
	}
	
	/**
	 * Unwraps reflection exceptions to extract the true underlying cause.
	 * Reflection failures often bury the actual error (like a SecurityException) inside an 
	 * InvocationTargetException. This method drills down to the root Throwable to generate
	 * readable error messages for diagnostics.
	 *
	 * @param throwable The exception caught during reflection.
	 * @return A clean string describing the root cause class name and message.
	 */
	static String describe(Throwable throwable) {
		Throwable cause = throwable;
		while (cause instanceof InvocationTargetException && ((InvocationTargetException) cause).getTargetException() != null) {
			cause = ((InvocationTargetException) cause).getTargetException();
		}
		
		String message = cause.getMessage();
		if (message == null || message.trim().isEmpty()) {
			return cause.getClass().getName();
		} else {
			return cause.getClass().getName() + ": " + message;
		}
	}
}
