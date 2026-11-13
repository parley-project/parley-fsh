package us.bringardner.fsh.commands;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** uname, modeled on the GNU uname: facts about the system. */
public class Uname extends ShellCommand{
	static String name = "uname";
	static String help = "uname [-asnrvmpo]\n"
			+ "\tPrint facts about the system (the kernel name with no option).\n"
			+ "\t-a, --all               all of them\n"
			+ "\t-s, --kernel-name       the operating system\n"
			+ "\t-n, --nodename          the network name of this computer\n"
			+ "\t-r, --kernel-release    the release\n"
			+ "\t-v, --kernel-version    the version\n"
			+ "\t-m, --machine           the hardware, like x86_64\n"
			+ "\t-p, --processor         the same\n"
			+ "\t-o, --operating-system  the operating system (the same as -s here)";

	public Uname() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "asnrvmpo", "", false, "all:a", "kernel-name:s", "nodename:n", "kernel-release:r",
				"kernel-version:v", "machine:m", "processor:p", "operating-system:o");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( !a.operands.isEmpty()) {
			ctx.stderr.println("uname: extra operand '"+a.operands.get(0)+"'");
			return 1;
		}
		String os = System.getProperty("os.name");
		String arch = System.getProperty("os.arch");
		if( arch.equals("amd64")) {
			arch = "x86_64";
		} else if( arch.equals("x86")) {
			arch = "i686";
		}
		String node;
		try {
			node = InetAddress.getLocalHost().getHostName();
		} catch (IOException e) {
			node = "localhost";
		}
		boolean all = a.has('a');
		boolean none = !(all || a.has('s') || a.has('n') || a.has('r') || a.has('v') || a.has('m') || a.has('p') || a.has('o'));
		List<String> parts = new ArrayList<>();
		if( all || none || a.has('s')) {
			parts.add(os);
		}
		if( all || a.has('n')) {
			parts.add(node);
		}
		if( all || a.has('r')) {
			parts.add(System.getProperty("os.version"));
		}
		if( all || a.has('v')) {
			parts.add(os+" "+System.getProperty("os.version"));
		}
		if( all || a.has('m')) {
			parts.add(arch);
		}
		if( all || a.has('p')) {
			parts.add(arch);
		}
		if( all || a.has('o')) {
			parts.add(os);
		}
		ctx.stdout.println(String.join(" ", parts));
		ctx.stdout.flush();
		return 0;
	}
}
