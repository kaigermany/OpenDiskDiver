package me.kaigermany.opendiskdiver.probe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

public class Probe {
	private static ArrayList<ProbeFunction> probeFunctions = new ArrayList<ProbeFunction>();

	public static void regiterProbeTester(ProbeFunction func){
		probeFunctions.add(func);
	}
	
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
}
