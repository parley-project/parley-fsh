package us.bringardner.fsh;

/**
 * An argument of a builtin command: a word after expansion.
 */
public class Argument {

	private final String value;

	public Argument(String value) {
		this.value = value;
	}

	/** the word (ctx is not needed: the word is already expanded) */
	public Object getValue(ShellContext ctx) {
		return value;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Argument a && value.equals(a.value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return value;
	}
}
