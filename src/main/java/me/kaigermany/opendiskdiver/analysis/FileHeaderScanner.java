package me.kaigermany.opendiskdiver.analysis;

import java.util.function.BiConsumer;

public class FileHeaderScanner implements Scanner {
	@Override
	public void scan(byte[] nextSector, long sectorOffset, BiConsumer<Scanner, String> logger) {
		String type = classify(nextSector);
		if(type != null){
			logger.accept(this, "Found file header: " + type + " at Sector " + sectorOffset);
			System.out.println(type + " -> " + new String(nextSector, 0, 12));
		}
	}
	
	public static String classify(byte[] sector){
		switch(sector[0] & 0xFF){
			case 'R': {
				if(sector[1] == 'I' && sector[2] == 'F' && sector[3] == 'F') {
					if(sector[8] == 'W' && sector[8] == 'E' && sector[8] == 'B' && sector[8] == 'P') return "WEBP";
					else return "WAVESOUND(RIFF)";
				}
				break;
			}
			case 'W': {
				if(sector[1] == 'A' && sector[2] == 'V' && sector[3] == 'E') return "WAVESOUND";
	
				break;
			}
			case 255:{
				/*if((sector[1] & 0xF0) == 0xF0) {
					return "MP3";
				}*/
				if((sector[2] & 0xFF) == 0xFF && sector[6] == 'J' && sector[6] == 'F' && sector[6] == 'I' && sector[6] == 'F') {
					return "JPG";
				}
				
				break;
			}
			case 'I':{
				if(sector[1] == 'D' && sector[2] == '3') return "MP3";
				break;
			}
			case '‰':{
				if(sector[1] == 'P' && sector[2] == 'N' && sector[2] == 'G') return "PNG";
				break;
			}
			case '7':{
				if(sector[1] == 'z' && sector[2] == (byte)0xBC && sector[3] == (byte)0xAF 
						&& sector[4] == (byte)0x27 && sector[5] == (byte)0x1C) return "7Z";
				break;
			}
			case 'P':{
				if(sector[1] == 'K' && sector[2] == 3 && sector[2] == 4) return "ZIP";
				break;
			}
			case 'O':{
				if(sector[1] == 'g' && sector[2] == 'g') return "OGG";
				break;
			}
			case 'M':{
				if(sector[1] == 'Z') {
					int PEHeaderOffset = readIntLE(sector, 60);
					if(PEHeaderOffset > 0 && PEHeaderOffset + 4 < sector.length){
						if(sector[PEHeaderOffset] == 'P' && sector[PEHeaderOffset+1] == 'E' 
								&& sector[PEHeaderOffset+2] == 0 && sector[PEHeaderOffset+3] == 0){
							return "EXE";
						}
					}
					
				}
				break;
			}
			case '%':{
				if(sector[1] == 'P' && sector[2] == 'D' && sector[2] == 'F') return "PDF";
				break;
			}
			case 'G':{
				if(sector[1] == 'I' && sector[2] == 'F' && sector[3] == '8' && sector[5] == 'a') {
					if(sector[4] == '7' || sector[4] == '9') return "GIF";
				}
				break;
			}
			case '[':{//"[InternetShortcut]"
				if(sector[1] == 'I' && sector[2] == 'n' && sector[3] == 't' && sector[4] == 'e' 
						&& sector[5] == 'r' && sector[6] == 'n' && sector[7] == 'e' && sector[8] == 't'
						&& sector[9] == 'S' && sector[10] == 'h' && sector[11] == 'o' && sector[12] == 'r'
						&& sector[13] == 't' && sector[14] == 'c' && sector[15] == 'u' && sector[16] == 't'
						 && sector[17] == ']') return "URL";
				break;
			}
		}
		
		return null;
	}
	
	private static int readIntLE(byte[] buf, int offset){
		if(offset + 4 > buf.length) return 0;
		  int value = 0;
		  for(int i=0; i<4; i++){
		    value |= (buf[offset + i] & 0xFF) << (i * 8);
		  }
		  return value;
	}
}
