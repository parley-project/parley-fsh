package us.bringardner.fsh.commands;

/** readlink, modeled on the GNU readlink; it is the other half of {@link Realpath}. */
public class Readlink extends Realpath {

	public Readlink() {
		super(true);
	}
}
