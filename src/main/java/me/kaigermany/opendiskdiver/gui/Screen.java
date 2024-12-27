package me.kaigermany.opendiskdiver.gui;

import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;

public class Screen {
	public static final String BLACK = "Black";
	public static final String DARKBLUE = "DarkBlue";
	public static final String DARKGREEN = "DarkGreen";
	public static final String DARKCYAN = "DarkCyan";
	public static final String DARKRED = "DarkRed";
	public static final String DARKMAGENTA = "DarkMagenta";
	public static final String DARKYELLOW = "DarkYellow";
	public static final String GRAY = "Gray";
	public static final String DARKGRAY = "DarkGray";
	public static final String BLUE = "Blue";
	public static final String GREEN = "Green";
	public static final String CYAN = "Cyan";
	public static final String RED = "Red";
	public static final String MAGENTA = "Magenta";
	public static final String YELLOW = "Yellow";
	public static final String WHITE = "White";
	
	public char[] map;
	public String[] foregroundColorMap;
	public String[] backgroundColorMap;
	public int w;
	public int h;
	ConsoleInterface console;
	
	public String backgroundColor = BLACK;
	
	public Screen(int w, int h, ConsoleInterface console){
		this.console = console;
		resize(w, h);
	}
	
	public void resize(int w, int h){
		this.w = w;
		this.h = h;
		map = new char[w*h];
		foregroundColorMap = new String[w*h];
		backgroundColorMap = new String[w*h];
		clear();
	}
	
	public void printText(){
		console.clear();
		String lastFColor = null;
		String lastBColor = null;
		StringBuilder sb = new StringBuilder();
		for(int y=0; y<h; y++){
			for(int x=0; x<w; x++){
				int pos = x + (y*w);
				{
					String targetFColor = foregroundColorMap[pos];
					String targetBColor = backgroundColorMap[pos];
					boolean updateF = !targetFColor.equals(lastFColor);
					boolean updateB = !targetBColor.equals(lastBColor);
					
					if(updateF || updateB){
						String text = sb.toString();
						sb = new StringBuilder();
						if(text.length() > 0){
							console.write(text);
						}
					}
					
					if(updateF){
						console.setColor(targetFColor, false);
						lastFColor = targetFColor;
					}
					if(updateB){
						console.setColor(targetBColor, true);
						lastBColor = targetBColor;
					}
				}
				sb.append(map[x + (y*w)]);
			}
			sb.append('\n');
		}
		console.write(sb.toString());
		console.setColor(backgroundColor, true);
	}
	
	public void setBackgroundColor(String color){
		backgroundColor = color;
	}
	
	public void clear(){
		for(int i=0; i<w*h; i++) {
			map[i] = ' ';
			foregroundColorMap[i] = WHITE;
			backgroundColorMap[i] = backgroundColor;
		}
	}
	
	public void write(String text, int x, int y, String foregroundColor, String backgroundColor){
		if(x >= 0 && y >= 0 && x < w & y < h) {
			char[] arr = text.toCharArray();
			int l = Math.min(arr.length, w - x);
			for(int i=0; i<l; i++){
				int pos = i + x + (y * w);
				map[pos] = arr[i];
				foregroundColorMap[pos] = foregroundColor;
				backgroundColorMap[pos] = backgroundColor;
			}
		}
	}
	
	public void writeChar(char c, int x, int y, String foregroundColor, String backgroundColor){
		if(x >= 0 && y >= 0 && x < w & y < h) {
			int pos = x + (y * w);
			map[pos] = c;
			foregroundColorMap[pos] = foregroundColor;
			backgroundColorMap[pos] = backgroundColor;
		}
	}
	
	public void close(){
		console.close();
	}
}