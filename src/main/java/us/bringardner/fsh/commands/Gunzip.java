package us.bringardner.fsh.commands;

/** gunzip, modeled on the GNU command; it is a variant of {@link Gzip} (the command registry makes each command with a constructor that takes no arguments). */
public class Gunzip extends Gzip {

	public Gunzip() {
		super("gunzip");
	}
}
