package us.bringardner.fsh;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class DataStream {
	private final BlockingQueue<Integer> queue=new LinkedBlockingDeque<>();

	
	public void write(int i) throws IOException {
		queue.add(i);
	}

	public void write(byte[] data)  throws IOException {
		write(data, 0, data.length);
	}

	public void write(byte [] data, int start, int length) throws IOException {
		for (int idx = start; idx < length; idx++) {
			queue.add((int)(data[idx]));
		}			
	}

	public int read() {
		return queue.poll();
	}

	public int read(byte[] data,int start, int length) {
		int ret = read();			
		return ret;
	}

	public int read(byte [] data) {
		return read(data,0,data.length);
	}

}
