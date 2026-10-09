package us.bringardner.fsh;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A map whose entries come out in the order bash's hashlib keeps them (bash's hash_string, a new
 * entry at the head of its bucket, buckets in order, grown four times over at twice as many
 * entries as buckets): what bash lists from its tables (complete -p) comes out in bash's order.
 */
public class BashHashTable<V> extends AbstractMap<String,V> {

	private static final class Item<V> {
		final String key;
		final int hash;
		V value;
		Item<V> next;

		Item(String key, int hash, V value) {
			this.key = key;
			this.hash = hash;
			this.value = value;
		}
	}

	private Item<V>[] buckets;
	private int size;

	@SuppressWarnings("unchecked")
	public BashHashTable(int nbuckets) {
		buckets = new Item[nbuckets];
	}

	/** bash's hash_string (FNV-1 with bash's multiply) */
	static int hash(String s) {
		int i = (int) 2166136261L;
		for(byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
			i += (i<<1) + (i<<4) + (i<<7) + (i<<8) + (i<<24);
			// (char is signed in bash's C)
			i ^= b;
		}
		return i;
	}

	private Item<V> find(String key) {
		int h = hash(key);
		for(Item<V> it = buckets[h & (buckets.length-1)]; it != null; it = it.next) {
			if( it.hash == h && it.key.equals(key)) {
				return it;
			}
		}
		return null;
	}

	@Override
	public V get(Object key) {
		Item<V> it = key instanceof String s ? find(s) : null;
		return it == null ? null : it.value;
	}

	@Override
	public boolean containsKey(Object key) {
		return key instanceof String s && find(s) != null;
	}

	@Override
	public V put(String key, V value) {
		Item<V> it = find(key);
		if( it != null ) {
			V old = it.value;
			it.value = value;
			return old;
		}
		if( size >= buckets.length*2 ) {
			rehash(buckets.length*4);
		}
		int h = hash(key);
		Item<V> n = new Item<>(key, h, value);
		int b = h & (buckets.length-1);
		n.next = buckets[b];
		buckets[b] = n;
		size++;
		return null;
	}

	@SuppressWarnings("unchecked")
	private void rehash(int nsize) {
		Item<V>[] old = buckets;
		buckets = new Item[nsize];
		for(Item<V> head : old) {
			Item<V> next;
			for(Item<V> it = head; it != null; it = next) {
				next = it.next;
				int b = it.hash & (nsize-1);
				it.next = buckets[b];
				buckets[b] = it;
			}
		}
	}

	@Override
	public V remove(Object key) {
		if( !(key instanceof String s)) {
			return null;
		}
		int h = hash(s);
		int b = h & (buckets.length-1);
		Item<V> prev = null;
		for(Item<V> it = buckets[b]; it != null; prev = it, it = it.next) {
			if( it.hash == h && it.key.equals(s)) {
				if( prev == null ) {
					buckets[b] = it.next;
				} else {
					prev.next = it.next;
				}
				size--;
				return it.value;
			}
		}
		return null;
	}

	@Override
	public void clear() {
		java.util.Arrays.fill(buckets, null);
		size = 0;
	}

	@Override
	public int size() {
		return size;
	}

	/** a live view (each iteration walks the table as it is then) */
	@Override
	public Set<Map.Entry<String,V>> entrySet() {
		return new java.util.AbstractSet<Map.Entry<String,V>>() {
			@Override
			public java.util.Iterator<Map.Entry<String,V>> iterator() {
				List<Map.Entry<String,V>> now = new ArrayList<>();
				for(Item<V> head : buckets) {
					for(Item<V> it = head; it != null; it = it.next) {
						now.add(new SimpleEntry<>(it.key, it.value));
					}
				}
				java.util.Iterator<Map.Entry<String,V>> walk = now.iterator();
				return new java.util.Iterator<Map.Entry<String,V>>() {
					private Map.Entry<String,V> last;

					@Override
					public boolean hasNext() {
						return walk.hasNext();
					}

					@Override
					public Map.Entry<String,V> next() {
						return last = walk.next();
					}

					@Override
					public void remove() {
						BashHashTable.this.remove(last.getKey());
					}
				};
			}

			@Override
			public int size() {
				return size;
			}
		};
	}

	public List<V> valuesInOrder() {
		List<V> ret = new ArrayList<>();
		for(Map.Entry<String,V> e : entrySet()) {
			ret.add(e.getValue());
		}
		return ret;
	}
}
