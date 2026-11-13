package us.bringardner.fsh.commands;

/** zcat, modeled on the GNU command; it is a variant of {@link Gzip} (the command registry makes each command with a constructor that takes no arguments). */
public class Zcat extends Gzip {

	public Zcat() {
		super("zcat");
	}
}
