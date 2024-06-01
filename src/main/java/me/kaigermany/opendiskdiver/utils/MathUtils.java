package me.kaigermany.opendiskdiver.utils;

public class MathUtils {
	public static long clampExp(long val, long step) {
		long diff = val % step;
		if (diff == 0) return val;
		return val + step - diff;
	}
	
}
