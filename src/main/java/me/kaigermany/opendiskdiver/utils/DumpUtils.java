package me.kaigermany.opendiskdiver.utils;

public class DumpUtils {
	private static char[] HEX_CHARACTERS = "0123456789ABCDEF".toCharArray();
	
	public static String hexByte(int val){
		return new String(new char[] {
			HEX_CHARACTERS[(val >>> 4) & 0xF],
			HEX_CHARACTERS[val & 0xF]
		});
	}
	
	public static String hexInt(int val){
		return new String(new char[] {
			HEX_CHARACTERS[val >>> 28],
			HEX_CHARACTERS[(val >>> 24) & 0xF],
			
			HEX_CHARACTERS[(val >>> 20) & 0xF],
			HEX_CHARACTERS[(val >>> 16) & 0xF],
			
			HEX_CHARACTERS[(val >>> 12) & 0xF],
			HEX_CHARACTERS[(val >>> 8) & 0xF],
			
			HEX_CHARACTERS[(val >>> 4) & 0xF],
			HEX_CHARACTERS[val & 0xF]
		});
	}
	

	public static void binaryDump(byte[] in) {
		int offset = 0;
		while (true) {
			System.out.print(hexInt(offset) + " ");
			for (int i = 0; i < 16; i++) {
				int a = offset + i;
				if (a < in.length)
					System.out.print(hexByte(in[a]) + " ");
				else
					System.out.print("   ");
			}
			for (int i = 0; i < 16; i++) {
				int a = offset + i;
				if (a < in.length)
					System.out.print((char) in[a]);
				else
					System.out.print(" ");
			}
			System.out.println();
			offset += 16;
			if (offset >= in.length)
				break;
		}
	}
}
