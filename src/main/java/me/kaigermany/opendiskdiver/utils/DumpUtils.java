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
			System.out.print(hexInt(offset) + ' ');
			for (int i = 0; i < 16; i++) {
				int a = offset + i;
				if (a < in.length)
					System.out.print(hexByte(in[a]) + ' ');
				else
					System.out.print("   ");
			}
			for (int i = 0; i < 16; i++) {
				int a = offset + i;
				if (a < in.length) {
					if(in[a] >= 32 && in[a] <= 126){
						System.out.print((char) in[a]);
					} else {
						System.out.print('.');
					}
				} else {
					System.out.print(' ');
				}
			}
			System.out.println();
			offset += 16;
			if (offset >= in.length)
				break;
		}
	}
	

	public static String binaryDumpToString(byte[] in) {
		StringBuilder sb = new StringBuilder(in.length * 2);
		int offset = 0;
		while (true) {
			sb.append(hexInt(offset) + ' ');
			for (int i = 0; i < 16; i++) {
				int a = offset + i;
				if (a < in.length)
					sb.append(hexByte(in[a]) + ' ');
				else
					sb.append("   ");
			}
			for (int i = 0; i < 16; i++) {
				int a = offset + i;
				if (a < in.length) {
					if(in[a] >= 32 && in[a] <= 126){
						sb.append((char) in[a]);
					} else {
						sb.append('.');
					}
				} else {
					sb.append(' ');
				}
			}
			sb.append("\r\n");
			offset += 16;
			if (offset >= in.length)
				break;
		}
		return sb.toString();
	}
}
