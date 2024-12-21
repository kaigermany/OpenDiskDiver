package me.kaigermany.opendiskdiver.utils;

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
}
