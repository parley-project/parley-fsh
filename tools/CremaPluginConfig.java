import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * For a native image built with Crema (GraalVM -H:+RuntimeClassLoading): the methods, constructors
 * and fields in the image that plugin jars use, as reachability metadata (reflect-config.json), so
 * they are compiled into the image (a plugin's interpreted code can call only methods that are).
 *
 *   java tools/CremaPluginConfig.java OUTPUT.json CLASSPATH PLUGIN.jar ...
 *
 * CLASSPATH is the image's class path (what the plugins link against). Every method, constructor and
 * field a plugin class refers to (its constant pool) that is declared outside the plugins is listed,
 * at the class that declares it, with the overrides of it in the image's classes the plugin names, so
 * calls on those types find compiled code. Classes the plugins define are left out: they are loaded
 * at run time.
 */
public class CremaPluginConfig {

	public static void main(String[] args) throws Exception {
		if( args.length < 3 ) {
			System.err.println("usage: java tools/CremaPluginConfig.java OUTPUT.json CLASSPATH PLUGIN.jar ...");
			System.exit(2);
		}
		List<URL> image = new ArrayList<>();
		for(String p : args[1].split(File.pathSeparator)) {
			if( !p.isEmpty()) {
				image.add(new File(p).toURI().toURL());
			}
		}
		List<URL> plugins = new ArrayList<>();
		Set<String> pluginClasses = new TreeSet<>();
		Map<String, Set<String>> refs = new TreeMap<>(); // owner -> name:descriptor (fields: name only)
		for(int i = 2; i < args.length; i++) {
			plugins.add(new File(args[i]).toURI().toURL());
			try (JarFile jar = new JarFile(args[i])) {
				for(Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
					JarEntry je = e.nextElement();
					if( je.getName().endsWith(".class") && !je.getName().contains("META-INF")) {
						pluginClasses.add(je.getName().substring(0, je.getName().length()-6).replace('/', '.'));
						try (InputStream in = jar.getInputStream(je)) {
							constantPool(in.readAllBytes(), refs);
						}
					}
				}
			}
		}
		ClassLoader imageLoader = new URLClassLoader(image.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
		ClassLoader all = new URLClassLoader(plugins.toArray(new URL[0]), imageLoader);

		// class -> members to register
		Map<String, Set<String>> methods = new TreeMap<>();
		Map<String, Set<String>> fields = new TreeMap<>();
		Set<String> types = new TreeSet<>();
		int missing = 0;
		for(Map.Entry<String, Set<String>> e : refs.entrySet()) {
			String owner = e.getKey();
			Class<?> c;
			try {
				c = Class.forName(owner, false, all);
			} catch (ClassNotFoundException | LinkageError x) {
				System.err.println("not found: "+owner);
				missing++;
				continue;
			}
			if( !inPlugins(c, pluginClasses)) {
				types.add(c.getName());
			}
			for(String member : e.getValue()) {
				int colon = member.indexOf(':');
				if( colon < 0 ) {
					Field f = findField(c, member);
					if( f != null && !inPlugins(f.getDeclaringClass(), pluginClasses)) {
						fields.computeIfAbsent(f.getDeclaringClass().getName(), k -> new TreeSet<>()).add(f.getName());
					}
					continue;
				}
				String name = member.substring(0, colon);
				String desc = member.substring(colon+1);
				for(Executable x : find(c, name, desc, all)) {
					if( !inPlugins(x.getDeclaringClass(), pluginClasses)) {
						methods.computeIfAbsent(x.getDeclaringClass().getName(), k -> new TreeSet<>()).add(signature(x));
						types.add(x.getDeclaringClass().getName());
					}
				}
			}
		}
		// the image's supertypes of the plugins' classes: their methods are called on plugin objects
		for(String pc : pluginClasses) {
			try {
				Class<?> c = Class.forName(pc, false, all);
				for(Class<?> s = c.getSuperclass(); s != null; s = s.getSuperclass()) {
					if( !inPlugins(s, pluginClasses)) {
						addAll(s, methods, types);
					}
				}
			} catch (ClassNotFoundException | LinkageError x) {
				// (reported above if used)
			}
		}
		try (PrintStream out = new PrintStream(args[0], "UTF-8")) {
			out.println("[");
			boolean first = true;
			for(String t : types) {
				out.print(first ? "" : ",\n");
				first = false;
				out.print("  {\"name\": \""+t+"\"");
				Set<String> ms = methods.get(t);
				if( ms != null ) {
					out.print(", \"methods\": ["+String.join(", ", ms)+"]");
				}
				Set<String> fs = fields.get(t);
				if( fs != null ) {
					List<String> q = new ArrayList<>();
					for(String f : fs) {
						q.add("{\"name\": \""+f+"\"}");
					}
					out.print(", \"fields\": ["+String.join(", ", q)+"]");
				}
				out.print("}");
			}
			out.println("\n]");
		}
		int n = 0;
		for(Set<String> s : methods.values()) {
			n += s.size();
		}
		System.err.println(args[0]+": "+types.size()+" types, "+n+" methods and constructors, "
				+fields.values().stream().mapToInt(Set::size).sum()+" fields"+(missing > 0 ? ", "+missing+" classes not found" : ""));
	}

	private static boolean inPlugins(Class<?> c, Set<String> pluginClasses) {
		while( c.isArray()) {
			c = c.getComponentType();
		}
		return pluginClasses.contains(c.getName());
	}

	private static void addAll(Class<?> c, Map<String, Set<String>> methods, Set<String> types) {
		types.add(c.getName());
		Set<String> ms = methods.computeIfAbsent(c.getName(), k -> new TreeSet<>());
		try {
			for(Method m : c.getDeclaredMethods()) {
				if( !Modifier.isPrivate(m.getModifiers())) {
					ms.add(signature(m));
				}
			}
			for(Constructor<?> k : c.getDeclaredConstructors()) {
				if( !Modifier.isPrivate(k.getModifiers())) {
					ms.add(signature(k));
				}
			}
		} catch (LinkageError e) {
			// (a class whose members cannot be listed here)
		}
	}

	private static Field findField(Class<?> c, String name) {
		for(Class<?> k = c; k != null; k = k.getSuperclass()) {
			try {
				return k.getDeclaredField(name);
			} catch (NoSuchFieldException | LinkageError e) {
			}
			for(Class<?> i : k.getInterfaces()) {
				Field f = findField(i, name);
				if( f != null ) {
					return f;
				}
			}
		}
		return null;
	}

	/** the method or constructor name+desc resolves to from c, and the declarations it overrides */
	private static List<Executable> find(Class<?> c, String name, String desc, ClassLoader l) {
		List<Executable> ret = new ArrayList<>();
		Class<?>[] params;
		try {
			params = params(desc, l);
		} catch (ClassNotFoundException | LinkageError e) {
			return ret;
		}
		if( name.equals("<init>")) {
			try {
				ret.add(c.getDeclaredConstructor(params));
			} catch (NoSuchMethodException | LinkageError e) {
			}
			return ret;
		}
		if( name.equals("<clinit>")) {
			return ret;
		}
		Set<Class<?>> seen = new LinkedHashSet<>();
		collect(c, name, params, ret, seen);
		return ret;
	}

	private static void collect(Class<?> c, String name, Class<?>[] params, List<Executable> ret, Set<Class<?>> seen) {
		if( c == null || !seen.add(c)) {
			return;
		}
		try {
			ret.add(c.getDeclaredMethod(name, params));
		} catch (NoSuchMethodException | LinkageError e) {
		}
		collect(c.getSuperclass(), name, params, ret, seen);
		for(Class<?> i : c.getInterfaces()) {
			collect(i, name, params, ret, seen);
		}
		if( c.isInterface()) {
			collect(Object.class, name, params, ret, seen);
		}
	}

	private static Class<?>[] params(String desc, ClassLoader l) throws ClassNotFoundException {
		List<Class<?>> ret = new ArrayList<>();
		int i = 1;
		while( desc.charAt(i) != ')' ) {
			int start = i;
			while( desc.charAt(i) == '[' ) {
				i++;
			}
			if( desc.charAt(i) == 'L' ) {
				i = desc.indexOf(';', i);
			}
			i++;
			ret.add(type(desc.substring(start, i), l));
		}
		return ret.toArray(new Class<?>[0]);
	}

	private static Class<?> type(String d, ClassLoader l) throws ClassNotFoundException {
		switch (d.charAt(0)) {
			case 'Z': return boolean.class;
			case 'B': return byte.class;
			case 'C': return char.class;
			case 'S': return short.class;
			case 'I': return int.class;
			case 'J': return long.class;
			case 'F': return float.class;
			case 'D': return double.class;
			case 'V': return void.class;
			case 'L': return Class.forName(d.substring(1, d.length()-1).replace('/', '.'), false, l);
			default: return Class.forName(d.replace('/', '.'), false, l); // [...
		}
	}

	private static String signature(Executable x) {
		List<String> p = new ArrayList<>();
		for(Class<?> t : x.getParameterTypes()) {
			p.add("\""+t.getTypeName()+"\"");
		}
		String name = x instanceof Constructor ? "<init>" : x.getName();
		return "{\"name\": \""+name+"\", \"parameterTypes\": ["+String.join(", ", p)+"]}";
	}

	/** add the Methodref, InterfaceMethodref and Fieldref entries of a class file to refs */
	private static void constantPool(byte[] b, Map<String, Set<String>> refs) throws IOException {
		DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(b));
		in.readInt();
		in.readUnsignedShort();
		in.readUnsignedShort();
		int n = in.readUnsignedShort();
		String[] utf = new String[n];
		int[] classIdx = new int[n];
		int[][] ref = new int[n][];
		int[] refTag = new int[n];
		int[][] nat = new int[n][];
		for(int i = 1; i < n; i++) {
			int tag = in.readUnsignedByte();
			switch (tag) {
				case 1: utf[i] = in.readUTF(); break;
				case 3: case 4: in.readInt(); break;
				case 5: case 6: in.readLong(); i++; break;
				case 7: classIdx[i] = in.readUnsignedShort(); break;
				case 8: case 16: case 19: case 20: in.readUnsignedShort(); break;
				case 9: case 10: case 11:
					refTag[i] = tag;
					ref[i] = new int[] { in.readUnsignedShort(), in.readUnsignedShort() };
					break;
				case 12: nat[i] = new int[] { in.readUnsignedShort(), in.readUnsignedShort() }; break;
				case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
				case 17: case 18: in.readUnsignedShort(); in.readUnsignedShort(); break;
				default: throw new IOException("constant pool tag "+tag);
			}
		}
		for(int i = 1; i < n; i++) {
			if( ref[i] == null ) {
				continue;
			}
			String owner = utf[classIdx[ref[i][0]]];
			if( owner.startsWith("[")) {
				owner = "java/lang/Object"; // (array clone and the like)
			}
			int[] nt = nat[ref[i][1]];
			String member = refTag[i] == 9 ? utf[nt[0]] : utf[nt[0]]+":"+utf[nt[1]];
			refs.computeIfAbsent(owner.replace('/', '.'), k -> new TreeSet<>()).add(member);
		}
	}
}
