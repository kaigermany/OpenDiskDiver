package me.kaigermany.opendiskdiver.windows.console;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class ConsoleInterface {
	private File console;
	private Process p;
	private DataOutputStream dos;
	private DataInputStream dis;
	
	public ConsoleInterface(){
		//prepare C# console file
		File libPath = new File(System.getProperty("java.io.tmpdir"), "OpenDiskDiver");
		if(!libPath.exists()) libPath.mkdirs();
		console = new File(libPath, "SmartConsole.exe");
		if(!console.exists()) {
			try {
				//InputStream is = ConsoleInterface.class.getResourceAsStream("/me/kaigermany/dynamicconsole/SmartConsole.exe");
				InputStream is = ConsoleInterface.class.getResourceAsStream("SmartConsole.exe");
				FileOutputStream fos = new FileOutputStream(console);
				int l;
				byte[] a = new byte[1024];
				while((l = is.read(a)) > 0) fos.write(a, 0, l);
				fos.close();
			}catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		//find unused port for communication
		Random r = new Random(System.currentTimeMillis());
		int port;
		Socket socket = null;
		ServerSocket ss = null;
		while(true){
			port = r.nextInt() & 0xFFFF;
			if(port < 1024) continue;	//System reserved.
			try{
				ss = new ServerSocket(port);
				break;
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
		//launch console
		final String portStr = String.valueOf(port);
		new Thread(new Runnable(){
			@Override
			public void run() {
				try{
					p = new ProcessBuilder("cmd.exe", "/c", "start", console.getAbsolutePath(), portStr).start();
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}).start();
		
		//handle pipes
		try{
			socket = ss.accept();
			ss.close();
			dos = new DataOutputStream(socket.getOutputStream());
			dis = new DataInputStream(socket.getInputStream());
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		close();	//ensure correct exit handling of the console subprocess.
	}
	
	public void close(){
		synchronized (this) {
			if(p != null){
				try {
					dos.write('X');
					dos.flush();
					//right here the console process should exit by itself.
				} catch (IOException e) {
					e.printStackTrace();
					//on connection-loss etc. just task-kill it.
					p.destroyForcibly();
				}
				p = null;
			}
		}
	}
	
	public Pair<Integer, String> readKey(){
		try {
			dos.write('R');
			dos.flush();
			int key = dis.read();
			int strLen = dis.read();
			byte[] str = new byte[strLen];
			dis.readFully(str);
			return new Pair<Integer, String>(key, new String(str));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public void write(String str){
		try {
			dos.write('W');
			//byte[] raw = str.getBytes();
			char[] text = str.toCharArray();
			byte[] raw = new byte[text.length];
			for(int i=0; i<text.length; i++) raw[i] = (byte)(text[i] & 0xFF);
			dos.writeInt(raw.length);
			dos.write(raw);
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void setColor(String colorName, boolean isBackgroundColor) {
		try {
			dos.write('C');
			dos.write(isBackgroundColor ? 'B' : 'F');
			byte[] temp = colorName.getBytes();
			dos.write(temp.length);
			dos.write(temp);
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void clear(){
		try {
			dos.write('N');
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public boolean hasNextKey(){
		try {
			dos.write('T');
			dos.flush();
			return dis.read() == 1;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static class Pair<T1, T2> {
		private T1 obj1;
		private T2 obj2;
		public Pair(T1 obj1, T2 obj2){
			this.obj1 = obj1;
			this.obj2 = obj2;
		}
		
		public T1 getFirst(){
			return obj1;
		}
		
		public T2 getSecond(){
			return obj2;
		}
		
		@Override
		public String toString(){
			return "{" + obj1 + " <=> " + obj2 + "}";
		}
	}
}
