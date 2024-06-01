package me.kaigermany.opendiskdiver.probe;

import me.kaigermany.opendiskdiver.data.Reader;

public interface ProbeFunction {
	String getName();
	Reader getReader();
	float probe(byte[] sampleData) throws Throwable;
}