package us.bringardner.fsh.commands;

/** sha512sum, modeled on the GNU command; it is a variant of {@link Checksum} (the command registry makes each command with a constructor that takes no arguments). */
public class Sha512sum extends Checksum {

	public Sha512sum() {
		super("sha512sum", "SHA-512");
	}
}
