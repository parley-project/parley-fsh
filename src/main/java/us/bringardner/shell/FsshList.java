package us.bringardner.shell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

public class FsshList implements List<Object> {
	public static class ListEntry {
		int index;
		Object value;
		
		public ListEntry() {
			
		}
		public ListEntry(int idx,Object value) {
			index = idx;
			this.value = value;
		}
		
	}
	
	private Map<Integer,ListEntry> entries = new TreeMap<>();

	
	public FsshList() {
		
	}

	public String toString() {
		return getValuesAsString();
		
	}

	public Map<Integer, ListEntry> getEntries() {
		return Collections.unmodifiableMap(entries);
	}

	public String getIndexesAsString() {
		StringBuilder buf = new StringBuilder();
		for(Integer o : entries.keySet()) {
			if( !buf.isEmpty()) {
				buf.append(' ');
			}
			buf.append(""+o);
		}	
		return buf.toString();
	}
	
	public List<Integer> getIndexes() {
		List<Integer> ret = new ArrayList<>();
		for(int idx : entries.keySet()) {
			ret.add(idx);
		}
		return ret;
	}

	public String getValuesAsString() {
		StringBuilder buf = new StringBuilder();
		for(ListEntry o : entries.values()) {
			if( !buf.isEmpty()) {
				buf.append(' ');
			}
			buf.append(""+o.value);
		}
		return buf.toString();
	}
	
	public List<Object> getValues() {
		List<Object> ret = new ArrayList<>();
		for(ListEntry val : entries.values()) {
			ret.add(val.value);
		}
		return ret;
	}

	@Override
	public int size() {
		return entries.size();
	}


	@Override
	public boolean isEmpty() {
		return entries.isEmpty();
	}


	@Override
	public boolean contains(Object o) {
		for(ListEntry le : entries.values()) {
			if( le.equals(o)) {
				return true;
			}
		}
		return false;
	}


	@Override
	public Iterator<Object> iterator() {
		// the values in index order (an array may have holes: a[0] and a[5])
		Iterator<ListEntry> it = new ArrayList<>(entries.values()).iterator();
		return new Iterator<Object>() {
			@Override
			public Object next() {
				return it.next().value;
			}
			
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}
		};
	}


	@Override
	public Object[] toArray() {
		return toArray(new Object[size()]);
	}


	@SuppressWarnings("unchecked")
	@Override
	public Object[] toArray(Object[] a) {
		int idx = 0;
		for(ListEntry e : entries.values()) {
			if( idx < a.length ) {
				a[idx++] = e.value;
			}
		}
		return a;
	}


	@Override
	public boolean add(Object e) {
		// after the highest index (the array may have holes: a[5]=x; a+=(y) puts y at 6)
		int next = entries.isEmpty() ? 0 : ((TreeMap<Integer,ListEntry>) entries).lastKey()+1;
		ListEntry le = new ListEntry(next, e);
		entries.put(le.index, le);
		return true;
	}


	@Override
	public boolean remove(Object o) {
		int idx= indexOf(o);
		if( idx >=0) {
			return entries.remove(idx) != null;
		}
		return false;
	}


	@Override
	public boolean containsAll(Collection<?> c) {
		throw new RuntimeException(" Not implemented in FsshList");
	}


	@Override
	public boolean addAll(Collection<?> c) {
		for(Object o : c) {
			add(o);
		}
		return !c.isEmpty();
	}


	@Override
	public boolean addAll(int index, Collection<?> c) {
		throw new RuntimeException(" Not implemented in FsshList");
	}


	@Override
	public boolean removeAll(Collection<?> c) {
		throw new RuntimeException(" Not implemented in FsshList");
	}


	@Override
	public boolean retainAll(Collection<?> c) {
		throw new RuntimeException(" Not implemented in FsshList");
	}


	@Override
	public void clear() {
		entries.clear();		
	}


	@Override
	public Object get(int index) {
		ListEntry ret = entries.get(index);
		if( ret != null ) {
			return ret.value;
		}
		return null;
	}


	@Override
	public Object set(int index, Object element) {
		ListEntry le = new ListEntry(index, element);
		entries.put(index, le);
		return element;
	}


	@Override
	public void add(int index, Object element) {
		set(index, element);		
	}


	@Override
	public Object remove(int index) {
		ListEntry ret = entries.remove(index);
		if(ret !=null) {
			return ret.value;
		}
		return null;
	}


	@Override
	public int indexOf(Object o) {
		for(ListEntry le : entries.values()) {
			if(le.value.equals(o)) {
				return le.index;
			}
		}
		return -1;
	}


	@Override
	public int lastIndexOf(Object o) {
		throw new RuntimeException("lastIndexOf Not implemented in FsshList");
	}


	@Override
	public ListIterator<Object> listIterator() {
		throw new RuntimeException(" Not implemented in FsshList");
	}


	@Override
	public ListIterator<Object> listIterator(int index) {
		throw new RuntimeException(" Not implemented in FsshList");
	}


	@Override
	public List<Object> subList(int fromIndex, int toIndex) {
		throw new RuntimeException(" Not implemented in FsshList");
	}
	
}
