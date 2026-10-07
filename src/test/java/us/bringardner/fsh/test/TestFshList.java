package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import us.bringardner.fsh.FshList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class TestFshList {

	@Test
	public void testList01() {
		FshList list = new FshList();
		String string1 = "0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h";
		String obj = "Here";
		String string2 = string1+" "+obj;
		String [] data1 = string1.split(" ");

		for(String s : data1) {
			list.add(s);
		}

		for (int idx = 0; idx < data1.length; idx++) {
			Object val = list.get(idx);
			assertEquals(data1[idx], val);
		}


		list.set(50, obj);
		Object val = list.get(50);
		assertEquals(obj, val);


		List<Object> values = list.getValues();
		assertEquals(list.size(), values.size());
		StringBuilder buf = new StringBuilder();
		for(Object o : values) {
			if( !buf.isEmpty()) {
				buf.append(' ');
			}
			buf.append(""+o);
		}
		//System.out.println(buf.toString());
		assertEquals(string2, buf.toString());
		assertEquals(string2, list.getValuesAsString());

		List<Integer> indexes = list.getIndexes();
		assertEquals(list.size(), indexes.size());
		buf = new StringBuilder();
		for(Integer o : indexes) {
			if( !buf.isEmpty()) {
				buf.append(' ');
			}
			buf.append(""+o);
		}
		//System.out.println(buf.toString());
		String expect = "0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 50";
		assertEquals(expect, buf.toString());
		assertEquals(expect, list.getIndexesAsString());


	}

	@Test
	public void testList02() {
		FshList list = new FshList();
		List<Integer> indexes = new ArrayList<>();
		
		int cnt = 0;
		Random r = new Random();
		
		while( cnt++ < 100) {
			int idx = r.nextInt(1000);
			Object val = "Val"+idx;
			if(idx % 4 == 0 ) {
				val = idx;
			}
			if( !indexes.contains(idx) ) {
				indexes.add(idx);
			}
			// this overwrites dups
			list.set(idx, val);
			assertEquals(val, list.get(idx),"cnt="+cnt);
			Collections.sort(indexes);
			StringBuilder buf = new StringBuilder();
			for(Integer i : indexes) {
				if( !buf.isEmpty()) {
					buf.append(' ');
				}
				buf.append(""+i);
			}
			
			String tmp = list.getIndexesAsString();
			assertEquals(buf.toString(), tmp);
		}
	}
}
