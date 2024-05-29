package me.kaigermany.opendiskdiver.windows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.data.DriveInfo;

public class WindowsDrives {
	public static ArrayList<DriveInfo> listDrives() throws UnsupportedOperationException {
		try {
			Process p = new ProcessBuilder(
					"powershell", "-Command", 
					"Get-WmiObject Win32_DiskDrive | ForEach-Object { \\\"$($_.Caption)`n$($_.DeviceID)`n$($_.Size)\\\" }"
			).start();
			
			InputStream is = p.getInputStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
			byte[] buf = new byte[1024];
			int l;
			while ((l = is.read(buf)) != -1) baos.write(buf, 0, l);
			String data = new String(baos.toByteArray());
			String[] a = data.split("\n");
			ArrayList<DriveInfo> out = new ArrayList<DriveInfo>(a.length / 3);
			for (int i = 0; i < a.length; i += 3) {
				out.add(new DriveInfo(a[i], a[i + 1], a[i + 2].trim()));
			}
			return out;
		} catch (Exception e) {
			//e.printStackTrace();
			throw new UnsupportedOperationException("Unable to execute PowerShell command", e);
			//data = "";
		}
	}
}
