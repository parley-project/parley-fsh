package us.bringardner.fsh.commands;

/** sha256sum, modeled on the GNU command; it is a variant of {@link Checksum} (the command registry makes each command with a constructor that takes no arguments). */
public class Sha256sum extends Checksum {

	public Sha256sum() {
		super("sha256sum", "SHA-256");
	}
}
