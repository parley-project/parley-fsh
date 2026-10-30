package us.bringardner.fsh.ssh;

import java.io.InputStream;
import java.io.OutputStream;

import us.bringardner.parley.ssh.server.AbstractCommand;
import us.bringardner.parley.ssh.server.CommandEnvironment;
import us.bringardner.parley.ssh.server.ICommand;
import us.bringardner.parley.ssh.server.IShellFactory;
import us.bringardner.fsh.Console;
import us.bringardner.fsh.exec.PipeInput;

/**
 * fsh, the FileSource Shell, as the interactive shell of a parley-ssh server:
 * <pre>
 * server.setShellFactory(new FshShellFactory());
 * </pre>
 * or, with no code, with this jar on the class path and the server's shellFactory property:
 * <pre>
 * parley.ssh.shellFactory=us.bringardner.fsh.ssh.FshShellFactory
 * </pre>
 * Each login gets its own Console on an {@link SshTerminal}.
 * <p>
 * <b>The shell works as the account the server runs as</b>: it starts in that account's home
 * directory with its environment and history file, and can read, change and connect to what
 * that account can, whoever logged in. Give it only to users who may do all of that (with an
 * access control list, they need the "shell" permission).
 *
 * @author Tony Bringardner
 */
public class FshShellFactory implements IShellFactory {

	public FshShellFactory() {
		// the server's OS signals (TERM from a service manager, INT, HUP) stay the server's: no
		// session's shell takes them. Set before Console loads (a compile-time constant, so this
		// line doesn't load it)
		if( System.getProperty(Console.PROP_OS_SIGNALS) == null ) {
			System.setProperty(Console.PROP_OS_SIGNALS, "false");
		}
		// "exit" must end the session's console, never the server's JVM
		Console.exitJvm = false;
	}

	@Override
	public ICommand create(CommandEnvironment env) {
		return new AbstractCommand() {
			private volatile Console console;

			@Override
			protected int run(CommandEnvironment env, InputStream channelIn, OutputStream out, OutputStream err) throws Exception {
				// shared with the programs the shell runs, as bash shares its input with them: a program is
				// given only what it reads and the rest goes back to the shell (without this, the copier that
				// feeds a program blocks in read() and takes the lines queued behind it)
				InputStream in = new PipeInput(channelIn);
				SshTerminal term = new SshTerminal(in, out, err, env.hasPty());
				Console c = new Console();
				console = c;
				c.setKeyboardReader(term);
				c.setStdIn(in);
				c.setStdOut(term.getStdOut());
				c.setStdErr(term.getStdErr());
				// as sshd starts bash: an interactive login shell on a terminal; without one, the
				// commands come from the input
				int ret = env.hasPty() ? c.execute("-i", "-l") : c.execute();
				if( !c.readsTypedCommands()) {
					return ret;
				}
				c.setName("ssh-shell-"+env.getUser());
				c.setDaemon(true);
				c.start();
				while( c.isAlive() ) {
					c.join(1000);
				}
				return c.getLastExitCode();
			}

			@Override
			public void destroy() {
				Console c = console;
				if( c != null ) {
					Console.exit(c, 255);
				}
				super.destroy();
			}
		};
	}
}
