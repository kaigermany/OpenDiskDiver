package me.kaigermany.opendiskdiver.gui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CmdGui {
	public static int listSelectBlocking(String[] entries){
		System.out.println("Please select one option by enter its number(1 - "+entries.length+"):");
		for(int i=0; i<entries.length; i++){
			System.out.println(String.valueOf(i + 1) + " \t" + entries[i]);
		}
		while(true){
			try{
				String text = readLine().trim();
				int n = Integer.parseInt(text) - 1;
				if(n < 0 || n >= entries.length) throw new Exception();
				return n;
			}catch(Exception ignored){
				//ignored.printStackTrace();
			}
			System.out.println("Invalid number, please enter a number between 1 and "+entries.length+".");
		}
	}
	
	public static File askForFilePathBlocking() throws IOException {
		System.out.println("Please enter a valid file path:");
		while(true){
			String text = readLine().trim();
			File file = new File(text);
			if(file.exists()) return file;
			System.out.println("File not found! Please enter a new valid file path:");
		}
	}
	
	public static String readLine() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
		int chr;
		while(true){
			chr = System.in.read();
			if(chr == '\n'){
				break;
			}
			baos.write(chr);
		}
		return new String(baos.toByteArray(), StandardCharsets.UTF_8);
	}
}
