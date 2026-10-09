package us.bringardner.fsh.exec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.bringardner.fsh.syntax.Ast;
import us.bringardner.fsh.syntax.Parser;

/**
 * The line numbers of the commands in a $( ), as bash has them: bash reads the commands again
 * from the text it prints them as (one command a line, no blank lines or comments), counting from
 * the line the $( ) ends on. That text is read here only for its line numbers, which the commands
 * as they were read then take (if both are the same commands); what runs is what was read.
 */
final class SubstitutionLines {

	private SubstitutionLines() {
	}

	/** the substitutions whose lines are bash's already */
	private static final Set<Ast.Sequence> done = Collections.synchronizedSet(Collections.newSetFromMap(new java.util.WeakHashMap<>()));

	/**
	 * Give body's commands bash's line numbers.
	 * @param printed the commands as bash prints them in a $( )
	 * @param text the text between $( and ), as written
	 */
	static void renumber(Ast.Sequence body, String printed, String text) {
		if( body == null || !done.add(body)) {
			return;
		}
		try {
			int first = firstCommand(text);
			int endLine = body.line+(int) text.substring(first).chars().filter(c -> c == '\n').count();
			Ast.Sequence again = Parser.parseWithoutAliases(printed, endLine);
			List<Ast.Node> from = nodes(again);
			List<Ast.Node> to = nodes(body);
			if( from.size() != to.size()) {
				return;
			}
			for (int i = 0; i < from.size(); i++) {
				if( from.get(i).getClass() != to.get(i).getClass()) {
					return;
				}
			}
			for (int i = 0; i < from.size(); i++) {
				to.get(i).line = from.get(i).line;
			}
		} catch (Exception | StackOverflowError e) {
			// (as they were read)
		}
	}

	/** where the first command is in text: after blanks, newlines and comments */
	private static int firstCommand(String text) {
		int i = 0;
		while( i < text.length()) {
			char c = text.charAt(i);
			if( c == ' ' || c == '\t' || c == '\n' ) {
				i++;
			} else if( c == '#' ) {
				while( i < text.length() && text.charAt(i) != '\n' ) {
					i++;
				}
			} else {
				break;
			}
		}
		return i;
	}

	/** the nodes of a tree, in the order of its fields */
	private static List<Ast.Node> nodes(Object root) throws IllegalAccessException {
		List<Ast.Node> ret = new ArrayList<>();
		walk(root, ret, new IdentityHashMap<>());
		return ret;
	}

	private static void walk(Object o, List<Ast.Node> out, Map<Object, Boolean> seen) throws IllegalAccessException {
		if( o == null || o instanceof String || o instanceof Number || o instanceof Character || o instanceof Boolean
				|| o.getClass().isEnum() || seen.put(o, true) != null ) {
			return;
		}
		if( o instanceof Ast.Node n ) {
			out.add(n);
		}
		if( o instanceof Iterable<?> list ) {
			for(Object e : list) {
				walk(e, out, seen);
			}
			return;
		}
		if( o instanceof Object[] array ) {
			for(Object e : array) {
				walk(e, out, seen);
			}
			return;
		}
		if( o.getClass().isRecord()) {
			for(RecordComponent rc : o.getClass().getRecordComponents()) {
				try {
					walk(rc.getAccessor().invoke(o), out, seen);
				} catch (ReflectiveOperationException e) {
					throw new IllegalAccessException(e.getMessage());
				}
			}
			return;
		}
		if( !o.getClass().getName().startsWith("us.bringardner.fsh.syntax.")) {
			return;
		}
		List<Field> fields = new ArrayList<>();
		for(Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			for(Field f : c.getDeclaredFields()) {
				if( !Modifier.isStatic(f.getModifiers())) {
					fields.add(f);
				}
			}
		}
		// (the same order for both trees)
		fields.sort((a, b) -> (a.getDeclaringClass().getName()+"."+a.getName()).compareTo(b.getDeclaringClass().getName()+"."+b.getName()));
		for(Field f : fields) {
			f.setAccessible(true);
			walk(f.get(o), out, seen);
		}
	}
}
