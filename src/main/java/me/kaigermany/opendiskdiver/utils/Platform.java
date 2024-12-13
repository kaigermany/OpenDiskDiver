package me.kaigermany.opendiskdiver.utils;

public class Platform {
	private static final boolean win;
	private static final boolean mac;
	private static final boolean linux;
	
	static{
		String osName = System.getProperty("os.name");
		win = osName.startsWith("Windows") || osName.startsWith("Windows CE");
		mac = osName.startsWith("Mac") || osName.startsWith("Darwin");
		linux = !(win ^ mac);//win == mac;//!win & !mac;
	}
	
	public static boolean isWindows(){
		return win;
	}
	
	public static boolean isMac(){
		return mac;
	}

	public static boolean isLinux(){
		return linux;
	}
}
