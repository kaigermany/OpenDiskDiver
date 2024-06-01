package me.kaigermany.opendiskdiver.probe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import me.kaigermany.opendiskdiver.data.Reader;

public class Probe {
	private static ArrayList<ProbeFunction> probeFunctions = new ArrayList<ProbeFunction>();
	
	public static ProbeResult detectType(byte[] probeBytes) {
		HashMap<ProbeFunction, Float> results = new HashMap<ProbeFunction, Float>(probeFunctions.size());
		for(ProbeFunction func : probeFunctions) {
			try{
				float score = func.probe(probeBytes);
				if(score > 0){
					results.put(func, score);
				}
			}catch(Throwable t){
				//in exception case we assume the score 0.0F and so we skip it.
			}
		}
		return new ProbeResult(results, toSortedList(results));
	}
	
	private static ArrayList<ProbeFunction> toSortedList(HashMap<ProbeFunction, Float> results){
		ArrayList<ProbeFunction> list = new ArrayList<ProbeFunction>(results.keySet());
		list.sort(new Comparator<ProbeFunction>() {//sort: biggest values first.
			@Override
			public int compare(ProbeFunction o1, ProbeFunction o2) {
				return -Float.compare(results.get(o1), results.get(o2));
			}
		});
		return list;
	}
	
	public static void regiterProbeTester(ProbeFunction func){
		probeFunctions.add(func);
	}
	
	
	public static class ProbeResult {
		HashMap<ProbeFunction, Float> results;
		ArrayList<ProbeFunction> sortedResults;
		
		public ProbeResult(HashMap<ProbeFunction, Float> results, ArrayList<ProbeFunction> sortedResults) {
			this.results = results;
			this.sortedResults = sortedResults;
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder(4096).append("ProbeResult: ").append(sortedResults.size()).append(" possible type");
			
			if(sortedResults.size() == 0) return sb.append("s!").toString();
			
			if(sortedResults.size() > 1) sb.append('s');
			
			sb.append(": [");
			for(ProbeFunction func : sortedResults){
				int score = (int)(results.get(func) * 100F);
				sb.append("\n\t").append(func.getName()).append(": ").append(score).append('%');
			}
			return sb.append("\n]").toString();
		}
	}

	public static interface ProbeFunction {
		float probe(byte[] sampleData) throws Throwable;
		String getName();
		Reader getReader();
	}
}
