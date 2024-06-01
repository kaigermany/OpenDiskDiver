package me.kaigermany.opendiskdiver.data.ntfs;

import java.util.ArrayList;

public class NtfsStream {
	public final int Type;
	
	public long Clusters; // Total number of clusters.
	public long Size; // Total number of bytes.
	public String Name;
	
	private ArrayList<Fragment> raw_fragments = new ArrayList<Fragment>(10);
	private long[] fragments;
	
	private boolean debug = false;

	public NtfsStream(String name, int type, long size) {
		Name = name;
		Type = type;
		Size = size;
		fragments = new long[0];
	}

	public long[] getFragments() {
		if(fragments == null) applyFragmentsImpl();
		return fragments.clone();
	}

	public void addFragment(long lcn, long nextVcn) {
		raw_fragments.add(new Fragment(lcn, nextVcn));
	}

	public void applyFragments() {
		applyFragmentsImpl();
		this.raw_fragments = null;
	}

	private void applyFragmentsImpl() {
		int len = raw_fragments.size();
		if (len == 0)
			return;

		long[] out = new long[(len + 1) * 2];
		int wp = 0;
		out[wp++] = 0;
		for (Fragment f : raw_fragments) {
			out[wp++] = f.Lcn;
			out[wp++] = f.NextVcn;
			if (debug)
				System.out.println("applyFragments: f.Lcn=" + f.Lcn + ", f.NextVcn=" + f.NextVcn);
		}
		out[out.length - 1] = out[out.length - 3] + (out[out.length - 2] - out[out.length - 4]);
		this.fragments = out;
	}

	public static class Fragment {
		public final long Lcn; // Logical cluster number, location on disk.
		public final long NextVcn; // Virtual cluster number of next fragment.

		public Fragment(long lcn, long nextVcn) {
			Lcn = lcn;
			NextVcn = nextVcn;
		}

		@Override
		public String toString() {
			return "{Lcn=" + Lcn + ", NextVcn=" + NextVcn + "}";
		}
	}
}
