package us.bringardner.fsh.commands;

/** sha1sum, modeled on the GNU command; it is a variant of {@link Checksum} (the command registry makes each command with a constructor that takes no arguments). */
public class Sha1sum extends Checksum {

	public Sha1sum() {
		super("sha1sum", "SHA-1");
	}
}
