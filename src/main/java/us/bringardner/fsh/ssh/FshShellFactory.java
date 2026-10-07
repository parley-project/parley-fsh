package us.bringardner.fsh.ssh;

import java.io.InputStream;
import java.io.OutputStream;

import us.bringardner.parley.ssh.server.AbstractCommand;
import us.bringardner.parley.ssh.server.CommandEnvironment;
import us.bringardner.parley.ssh.server.ICommand;
import us.bringardner.parley.ssh.server.IShellFactory;
import us.bringardner.fsh.Console;

/**
 * fsh, the FileSource Shell, as the interactive shell of a parley-ssh server:
 * <pre>
 * server.setShellFactory(new FshShellFactory());
 * </pre>
 * or, with no code, with this jar on the class path and the server's ShellFactory property:
 * <pre>
 * us.bringardner.parley.ssh.server.SshServer.ShellFactory=us.bringardner.fsh.ssh.FshShellFactory
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
		// "exit" must end the session's console, never the server's JVM
		Console.exitJvm = false;
	}

	@Override
	public ICommand create(CommandEnvironment env) {
		return new AbstractCommand() {
			private volatile Console console;

			@Override
			protected int run(CommandEnvironment env, InputStream in, OutputStream out, OutputStream err) throws Exception {
				SshTerminal term = new SshTerminal(in, out, err, env.hasPty());
				Console c = new Console();
				console = c;
				c.setKeyboardReader(term);
				c.setStdIn(in);
				c.setStdOut(term.getStdOut());
				c.setStdErr(term.getStdErr());
				int ret = c.execute();
				if( ret != 0 || !c.isInteractive ) {
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
