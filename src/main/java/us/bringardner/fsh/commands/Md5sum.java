package us.bringardner.fsh.commands;

/** md5sum, modeled on the GNU command; it is a variant of {@link Checksum} (the command registry makes each command with a constructor that takes no arguments). */
public class Md5sum extends Checksum {

	public Md5sum() {
		super("md5sum", "MD5");
	}
}
