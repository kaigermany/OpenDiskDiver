package me.kaigermany.opendiskdiver.probe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import me.kaigermany.opendiskdiver.data.Reader;
import me.kaigermany.opendiskdiver.data.fat.FatReader;
import me.kaigermany.opendiskdiver.data.ntfs.NtfsReader;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;

public class Probe {
	static{
		Probe.regiterProbeTester(new ProbeFunction() {
			@Override
			public String getName() { return "NTFS"; }

			@Override
			public Reader getReader() { return new NtfsReader(); }
			
			@Override
			public float probe(byte[] sampleData) throws Throwable {
				long Signature = ByteArrayUtils.read64(sampleData, 3);
				return Signature == 0x202020205346544EL ? 1 : 0;
			}
		});
		
		Probe.regiterProbeTester(new ProbeFunction() {
			@Override
			public String getName() { return "FAT"; }

			@Override
			public Reader getReader() { return new FatReader(); }
			
			@Override
			public float probe(byte[] sampleData) throws Throwable {
				return FatReader.isFatFormated(sampleData) ? 0.99F : 0;
			}
		});
		
		Probe.regiterProbeTester(new ProbeFunction() {
			@Override
			public String getName() { return "___EMPTY___"; }

			@Override
			public Reader getReader() { return null; }
			
			@Override
			public float probe(byte[] sampleData) throws Throwable {
				return ByteArrayUtils.isEmptySector(sampleData) ? 1 : 0;
			}
		});
		
		Probe.regiterProbeTester(new ProbeFunction() {
			@Override
			public String getName() { return "EXT"; }

			@Override
			public Reader getReader() { return null; }
			
			@Override
			public float probe(byte[] sampleData) throws Throwable {
				return ByteArrayUtils.isEmptySector(sampleData) ? 1.01F : 0;
			}
		});
	}
	
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
