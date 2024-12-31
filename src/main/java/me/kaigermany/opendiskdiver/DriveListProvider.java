package me.kaigermany.opendiskdiver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.utils.Platform;

public class DriveListProvider {
	public static ArrayList<DriveInfo> listDrives() throws UnsupportedOperationException {
		return Platform.isWindows() ? listDrivesWindows() : listDrivesLinux();
	}
	
	private static ArrayList<DriveInfo> listDrivesLinux() throws UnsupportedOperationException {
		try {
			//n: no head, b: raw byte values, o:order filter, --raw:unformated output format.
			String[] lines = processResultLines("lsblk", "-b", "--raw", "-o", "NAME,SIZE,TYPE", "-n");
			ArrayList<DriveInfo> out = new ArrayList<DriveInfo>();
			for(String entry : lines){
				String[] params = entry.split(" ");
				if(!params[2].equals("disk")){
					continue;
				}
				//out.add(new DriveInfo(params[0], "/sys/block/" + params[0], params[1]));
				out.add(new DriveInfo(params[0], "/dev/" + params[0], params[1]));
			}
			return out;
		} catch (Exception e) {
			throw new UnsupportedOperationException("Unable to execute lsblk command", e);
		}
	}
	
	private static ArrayList<DriveInfo> listDrivesWindows() throws UnsupportedOperationException {
		try {
			String[] a = processResultLines("powershell", "-Command", 
					"Get-WmiObject Win32_DiskDrive | ForEach-Object { \\\"$($_.Caption)`n$($_.DeviceID)`n$($_.Size)\\\" }");
			
			ArrayList<DriveInfo> out = new ArrayList<DriveInfo>(a.length / 3);
			for (int i = 0; i < a.length; i += 3) {
				out.add(new DriveInfo(a[i], a[i + 1], a[i + 2].trim()));
			}
			
			//find correct sizes:
			a = processResultLines("powershell", "-Command", 
					"Get-Disk | Select-Object Number, Size | ForEach-Object { \\\"$($_.Number)`n$($_.Size)\\\" }");
			HashMap<String, DriveInfo> driveMap = new HashMap<>(out.size() * 2);
			for(DriveInfo di : out){
				driveMap.put(di.path, di);
			}
			for (int i = 0; i < a.length; i += 2) {
				String path = "\\\\.\\PHYSICALDRIVE" + a[i];
				long exactSize = Long.parseLong(a[i+1].trim());
				DriveInfo di = driveMap.get(path);
				if(di.size < exactSize){
					DriveInfo updatedInfo = new DriveInfo(di.name, path, exactSize);
					out.set(out.indexOf(di), updatedInfo);
				}
			}
			return out;
		} catch (Exception e) {
			throw new UnsupportedOperationException("Unable to execute PowerShell command", e);
		}
	}
	
	private static String[] processResultLines(String... programStartArguments) throws IOException {
		Process p = new ProcessBuilder(programStartArguments).start();
		InputStream is = p.getInputStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
		byte[] buf = new byte[1024];
		int l;
		while ((l = is.read(buf)) != -1) baos.write(buf, 0, l);
		String data = new String(baos.toByteArray());
		return data.split("\n");
	}
}
