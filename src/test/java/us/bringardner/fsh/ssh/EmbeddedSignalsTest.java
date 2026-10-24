package us.bringardner.fsh.ssh;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.parley.ssh.client.ClientSession;
import us.bringardner.parley.ssh.client.HostKeyVerifiers;
import us.bringardner.parley.ssh.client.PasswordAuth;
import us.bringardner.parley.ssh.client.SessionChannel;
import us.bringardner.parley.ssh.client.SshClient;
import us.bringardner.parley.ssh.server.HostKeyProviders;
import us.bringardner.parley.ssh.server.SshPrincipal;
import us.bringardner.parley.ssh.server.SshServer;

/**
 * An SSH server whose sessions are fsh still stops on SIGTERM, as a service manager stops it:
 * fsh doesn't take the server's OS signals ({@link Console#PROP_OS_SIGNALS}). The server runs in
 * a process of its own ({@link #main(String[])}), with one session's shell started in it.
 */
public class EmbeddedSignalsTest {

	/** The server process: start, run one session's fsh, say "ready", then wait to be stopped */
	public static void main(String[] args) throws Exception {
		System.setProperty("java.awt.headless", "true");
		SshServer server = new SshServer(0);
		server.setHostKeyProvider(HostKeyProviders.ephemeral());
		server.setPasswordAuthenticator((user, pw, ctx) -> "secret".equals(new String(pw)) ? new SshPrincipal(user) : null);
		server.setShellFactory(new FshShellFactory());
		server.startAndWait(10000);
		try (SshClient client = new SshClient()) {
			client.setHostKeyVerifier(HostKeyVerifiers.acceptAll());
			ClientSession s = client.connectAndWait("localhost", server.getLocalPort());
			s.authenticateAndWait("alice", new PasswordAuth("secret"));
			SessionChannel ch = s.openSession();
			ch.shell();
			ch.getOutputStream().write("echo hi\nexit\n".getBytes(StandardCharsets.UTF_8));
			ch.getOutputStream().flush();
			ch.waitForClose(10, TimeUnit.SECONDS);
		}
		System.out.println("ready "+System.getProperty(Console.PROP_OS_SIGNALS));
		System.out.flush();
		// (a non-daemon thread: only a signal ends this process)
		Thread.sleep(Long.MAX_VALUE);
	}

	@Test
	public void sigtermStopsTheServer() throws Exception {
		assumeTrue(File.separatorChar == '/', "SIGTERM: not on Windows");
		ProcessBuilder pb = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(),
				"-cp", System.getProperty("java.class.path"), EmbeddedSignalsTest.class.getName()).redirectErrorStream(true);
		Process p = pb.start();
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
			StringBuilder out = new StringBuilder();
			String line;
			String ready = null;
			while( (line = r.readLine()) != null ) {
				out.append(line).append('\n');
				if( line.startsWith("ready") ) {
					ready = line;
					break;
				}
			}
			assertTrue(ready != null, out.toString());
			p.destroy();	// SIGTERM
			assertTrue(p.waitFor(20, TimeUnit.SECONDS), "SIGTERM stops a server whose sessions are fsh");
			assertTrue(ready.equals("ready false"), "the factory turns fsh's signals off: "+ready);
		} finally {
			p.destroyForcibly();
		}
	}
}
