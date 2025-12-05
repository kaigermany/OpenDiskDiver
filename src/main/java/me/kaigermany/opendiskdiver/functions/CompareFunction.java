package me.kaigermany.opendiskdiver.functions;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import me.kaigermany.opendiskdiver.gui.UI;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;
import me.kaigermany.opendiskdiver.utils.DumpUtils;

public class CompareFunction {
	public static void compareDrives(UI ui, ReadableSource source1, ReadableSource source2, File logFile) {
		try{
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(logFile), 4096);
			try {
				long cnt1 = source1.numSectors();
				long cnt2 = source2.numSectors();
				bos.write((
						"Source1.numSectors = " + cnt1 + "\r\n"
					+	"Source2.numSectors = " + cnt2 + "\r\n"
					).getBytes(StandardCharsets.UTF_8));
				if (cnt1 != cnt2) {
					bos.write((
							"Source1.numSectors MUST be Source2.numSectors! -> exit."
						).getBytes(StandardCharsets.UTF_8));
					return;
				}
				compareDrivesDetailed(ui, source1, source2, bos);
				//compareDrivesSimple(ui, source1, source2, bos);
			} finally {
				bos.close();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	private static void compareDrivesSimple(UI ui, ReadableSource source1, ReadableSource source2, BufferedOutputStream bos) throws IOException {
		long size = source1.numSectors();
		byte[] sector1 = new byte[512];
		byte[] sector2 = sector1.clone();
		long diffCount = 0;
		long lastRedraw = 0;
		long lastPos = 0;
		boolean isEqualRegion = true;
		for (long pos = 0; pos < size; pos++) {
			long t = System.currentTimeMillis();
			if (t - 250 >= lastRedraw) {
				lastRedraw = t;
				ui.showInfo(new String[] {
					"Scanner Location: " + pos + " / " + size + "   " + (Math.floor(10000D * pos / size) / 100) + " %",
					"diffCount: " + diffCount
				}, false);
			}
			
			source1.readSector(pos, sector1);
			source2.readSector(pos, sector2);
			
			boolean isEqual = Arrays.equals(sector1, sector2);
			if(isEqual != isEqualRegion){
				long last = pos - 1;
				String logMsg;
				if(last < lastPos){
					logMsg = "Single " + (isEqualRegion ? "Equal" : "Invalid") + " sector at " + lastPos + "\r\n";
				} else {
					logMsg = (isEqualRegion ? "Equal" : "Invalid") + " region from " + lastPos + " to " + last + "\r\n";
				}
				bos.write(logMsg.getBytes(StandardCharsets.UTF_8));
				isEqualRegion = isEqual;
				lastPos = pos;
			}
			if(!isEqual){
				diffCount++;
			}
		}
		long last = size - 1;
		String logMsg;
		if(last < lastPos){
			logMsg = "Single " + (isEqualRegion ? "Equal" : "Invalid") + " sector at " + lastPos + "\r\n";
		} else {
			logMsg = (isEqualRegion ? "Equal" : "Invalid") + " region from " + lastPos + " to " + last + "\r\n";
		}
		bos.write(logMsg.getBytes(StandardCharsets.UTF_8));
	}
	
	private static void compareDrivesDetailed(UI ui, ReadableSource source1, ReadableSource source2, BufferedOutputStream bos) throws IOException {
		long size = source1.numSectors();
		byte[] sector1 = new byte[512];
		byte[] sector2 = sector1.clone();
		byte[] xorDiff = sector1.clone();
		long diffCount = 0;
		long lastRedraw = 0;
		for (long pos = 0; pos < size; pos++) {
			long t = System.currentTimeMillis();
			if (t - 250 >= lastRedraw) {
				lastRedraw = t;
				ui.showInfo(new String[] {
					"Scanner Location: " + pos + " / " + size + "   " + (Math.floor(10000D * pos / size) / 100) + " %",
					"diffCount: " + diffCount
				}, false);
			}
			
			source1.readSector(pos, sector1);
			source2.readSector(pos, sector2);
			for(int i=0; i<512; i++) xorDiff[i] = (byte)(sector1[i] ^ sector2[i]);
			if (ByteArrayUtils.isEmptySector(xorDiff)) {
				continue;
			} else {
				int numDifferingBytes = 0;
				for(int i=0; i<512; i++) if(xorDiff[i] != 0) numDifferingBytes++;
				boolean areOnlyBitflips = true;
				for(int i=0; i<512; i++) {
					if(Integer.bitCount(xorDiff[i] & 0xFF) > 1) {
						areOnlyBitflips = false;
						break;
					}
				}
				
				if(numDifferingBytes > 4 && !areOnlyBitflips){
					String d1 = DumpUtils.binaryDumpToString(sector1);
					String d2 = DumpUtils.binaryDumpToString(sector2);
					bos.write((
						"Diff at sector " + pos + ": numDifferingBytes = " + numDifferingBytes + "\r\n"
						+ "----- Source 1: -----\r\n"
						+ d1
						+"\r\n"
						+ "----- Source 2: -----\r\n"
						+ d2
						+"\r\n"
					).getBytes(StandardCharsets.UTF_8));
				} else {
					StringBuilder sb = new StringBuilder(1024);
					if(areOnlyBitflips){
						sb.append("Diff at sector ").append(pos)
							.append(": numDifferingBytes = ").append(numDifferingBytes)
							.append("\r\n  FROM   |    TO    |   XOR    | pos in sector");
						for(int i=0; i<512; i++){
							if(xorDiff[i] != 0){
								int b1 = 256 | (sector1[i] & 0xFF);
								int b2 = 256 | (sector2[i] & 0xFF);
								sb.append("\r\n")
								.append(Integer.toBinaryString(b1).substring(1))
								.append(" | ")
								.append(Integer.toBinaryString(b2).substring(1))
								.append(" | ")
								.append(Integer.toBinaryString(256 | (xorDiff[i] & 0xFF)).substring(1))
								.append(" | ").append(i);
							}
						}
					} else {
						sb.append("Diff at sector ").append(pos)
							.append(": numDifferingBytes = ").append(numDifferingBytes)
							.append("\r\n  FROM   |    TO    |   XOR    | from |  to  | pos in sector");
						for(int i=0; i<512; i++){
							if(xorDiff[i] != 0){
								int b1 = 256 | (sector1[i] & 0xFF);
								int b2 = 256 | (sector2[i] & 0xFF);
								sb.append("\r\n")
								.append(Integer.toBinaryString(b1).substring(1))
								.append(" | ")
								.append(Integer.toBinaryString(b2).substring(1))
								.append(" | ")
								.append(Integer.toBinaryString(256 | (xorDiff[i] & 0xFF)).substring(1))
								.append(" | 0x")
								.append(Integer.toHexString(b1).substring(1).toUpperCase())
								.append(" | 0x")
								.append(Integer.toHexString(b2).substring(1).toUpperCase())
								.append(" | ").append(i);
							}
						}
					}
					bos.write(sb.append("\r\n\r\n").toString().getBytes(StandardCharsets.UTF_8));
				}
				diffCount++;
			}
		}
	}
}
