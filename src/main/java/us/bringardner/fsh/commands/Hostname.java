package us.bringardner.fsh.commands;

import java.io.IOException;
import java.net.InetAddress;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** hostname: the name of this computer (it can't be changed here). */
public class Hostname extends ShellCommand{
	static String name = "hostname";
	static String help = "hostname [-sfi]\n"
			+ "\tPrint the name of this computer.\n"
			+ "\t-s, --short   the name up to the first dot\n"
			+ "\t-f, --fqdn    the fully qualified name\n"
			+ "\t-i, --ip-address   its address";

	public Hostname() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "sfi", "", false, "short:s", "fqdn:f", "long:f", "ip-address:i");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( !a.operands.isEmpty()) {
			ctx.stderr.println("hostname: setting the name is not supported");
			return 1;
		}
		String host;
		InetAddress addr = null;
		try {
			addr = InetAddress.getLocalHost();
			host = addr.getHostName();
		} catch (IOException e) {
			host = System.getenv("COMPUTERNAME") != null ? System.getenv("COMPUTERNAME") : System.getenv("HOSTNAME");
			if( host == null ) {
				host = "localhost";
			}
		}
		if( a.has('i')) {
			host = addr == null ? "127.0.0.1" : addr.getHostAddress();
		} else if( a.has('f') && addr != null ) {
			host = addr.getCanonicalHostName();
		} else if( a.has('s') && host.contains(".")) {
			host = host.substring(0, host.indexOf('.'));
		}
		ctx.stdout.println(host);
		ctx.stdout.flush();
		return 0;
	}
}
