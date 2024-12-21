package me.kaigermany.opendiskdiver.linux;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.data.DriveInfo;

public class LinuxDrives {
	public static ArrayList<DriveInfo> listDrives() throws UnsupportedOperationException {
		try {
			//n: no head, b: raw byte values, o:order filter, --raw:unformated output format.
			Process p = new ProcessBuilder(
					"lsblk", "-b", "--raw", "-o", "NAME,SIZE,TYPE", "-n"
			).start();
			InputStream is = p.getInputStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
			byte[] buf = new byte[1024];
			int l;
			while ((l = is.read(buf)) != -1) baos.write(buf, 0, l);
			String data = new String(baos.toByteArray());
			ArrayList<DriveInfo> out = new ArrayList<DriveInfo>();
			for(String entry : data.split("\n")){
				String[] params = entry.split(" ");
				if(!params[2].equals("disk")){
					continue;
				}
				//out.add(new DriveInfo(params[0], "/sys/block/" + params[0], params[1]));
				out.add(new DriveInfo(params[0], "/dev/" + params[0], params[1]));
			}
			return out;
		} catch (Exception e) {
			throw new UnsupportedOperationException("Unable to execute PowerShell command", e);
		}
	}
}
