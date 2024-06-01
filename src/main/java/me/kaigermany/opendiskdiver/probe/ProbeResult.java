package me.kaigermany.opendiskdiver.probe;

import java.util.ArrayList;
import java.util.HashMap;

public class ProbeResult {
	public HashMap<ProbeFunction, Float> results;
	public ArrayList<ProbeFunction> sortedResults;

	public ProbeResult(HashMap<ProbeFunction, Float> results, ArrayList<ProbeFunction> sortedResults) {
		this.results = results;
		this.sortedResults = sortedResults;
	}
	
	public ArrayList<ProbeFunction> getSortedResults(){
		return sortedResults;
	}
	
	public float getProbeFunctionScore(ProbeFunction func){
		Float f = results.get(func);
		return f == null ?  0 : f;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(4096).append("ProbeResult: ").append(sortedResults.size()) .append(" possible type");

		if (sortedResults.size() == 0) return sb.append("s!").toString();

		if (sortedResults.size() > 1) sb.append('s');

		sb.append(": [");
		for (ProbeFunction func : sortedResults) {
			int score = (int) (results.get(func) * 100F);
			sb.append("\n\t").append(func.getName()).append(": ").append(score).append('%');
		}
		return sb.append("\n]").toString();
	}
}