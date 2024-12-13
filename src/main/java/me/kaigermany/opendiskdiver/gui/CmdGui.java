package me.kaigermany.opendiskdiver.gui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CmdGui {
	public static int listSelectBlocking(String[] entries){
		System.out.println("Please select one option by enter its number(1 - "+entries.length+"):");
		for(String row : entries){
			System.out.println(row);
		}
		while(true){
			try{
				String text = readLine().trim();
				return Integer.parseInt(text);
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
	
	private static String readLine() throws IOException {
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
