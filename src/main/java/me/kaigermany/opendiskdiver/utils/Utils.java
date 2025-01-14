package me.kaigermany.opendiskdiver.utils;

import java.util.ArrayList;

public class Utils {
	public static String toHumanReadableFileSize(long bytes){
		if(bytes < 1024){
			return bytes + " B";
		}
		String[] sizeUnits = new String[] {"B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "BB"};

        int steps = 0;
        long beforeComma = bytes;
        long afterComma = bytes * 1000;
        
        while(beforeComma / 1000 > 0) {
            beforeComma /= 1024;
            afterComma /= 1024;
            steps++;
        }
        
        afterComma = (afterComma - beforeComma * 1000);
        
        try{
	        char[] a = new char[7];
	        int wp = 0;
	        a[4] = (char)(beforeComma / 100);
	        a[5] = (char)((beforeComma / 10) % 10);
	        a[6] = (char)(beforeComma % 10);
	        int rp = 4;
	        if(a[rp] == 0) {
	        	rp++;
	            if(a[rp] == 0) rp++;
	        }
	        for(int i=rp; i<7; i++){
	        	a[wp++] = (char)('0' + a[i]);
	        }
	        rp -= 4;
	        rp = 3 - rp;
	        if(rp != 3){
	        	a[wp++] = ',';
		        for(int i=rp; i<3; i++){
		        	a[wp++] = (char)('0' + (char)(afterComma / 100) % 10);
		        	afterComma *= 10;
		        }
	        }
        	a[wp++] = ' ';
	        String text = sizeUnits[steps];
	        for(int i=0; i<text.length(); i++) {
	        	a[wp++] = text.charAt(i);
	        }
	        return new String(a, 0, wp);
        }catch(Exception e){
        	e.printStackTrace();
        	return "";
        }
	}
	

	public static String[] renderStylizedTable(String title, ArrayList<String[]> table, boolean[] alignRight){
		String[] out = new String[table.size() + (title != null ? 1 : 0)];
		int wp = 0;
		for(String[] row : table){
			if(row == null){
				throw new IllegalArgumentException("table must not contain null objects!");
			}
		}
		//extract max length
		int[] strLenMap = new int[table.get(0).length];
		for(String[] row : table){
			if(strLenMap.length != row.length){
				throw new IllegalArgumentException("expected table width: " + strLenMap.length + " but found entry width: " + row.length);
			}
			for(int i=0; i<row.length; i++){
				strLenMap[i] = Math.max(strLenMap[i], row[i].length());
			}
		}
		if(alignRight == null){
			alignRight = new boolean[strLenMap.length];
			for(int i=0; i<strLenMap.length; i++){
				alignRight[i] = true;
			}
		}
		int fullRowLen = 0;
		for(int i=0; i<strLenMap.length; i++){
			fullRowLen += strLenMap[i];//count max width
		}
		fullRowLen += strLenMap.length - 1;//include separators
		StringBuilder sb = new StringBuilder(fullRowLen);//allocate max predicted space
		if(title != null){//build title
			String title2 = " " + title + ": ";
			fullRowLen -= title2.length();
			if(fullRowLen > 1){//try to smoothly fit into table design
				for(int i=0; i<fullRowLen/2; i++){
					sb.append('=');
				}
				sb.append(title2);
				for(int i=fullRowLen/2; i<fullRowLen; i++){
					sb.append('=');
				}
				out[wp++] = sb.toString();
			} else {
				out[wp++] = title + ":";
			}
		}
		
		//build every line
		for(String[] row : table){
			sb.setLength(0);//clear StringBuilder
			for(int i=0; i<row.length; i++){
				if(i > 0){
					sb.append(' ');
				}
				int lenDiff = strLenMap[i] - row[i].length();
				if(alignRight[i]){
					for(int ii=0; ii<lenDiff; ii++){
						sb.append(' ');
					}
					sb.append(row[i]);
				} else {
					sb.append(row[i]);
					for(int ii=0; ii<lenDiff; ii++){
						sb.append(' ');
					}
				}
			}
			out[wp++] = sb.toString();
		}
		
		return out;
	}
}
