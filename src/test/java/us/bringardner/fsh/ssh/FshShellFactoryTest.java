package us.bringardner.fsh.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.ssh.client.ClientSession;
import us.bringardner.parley.ssh.client.HostKeyVerifiers;
import us.bringardner.parley.ssh.client.PasswordAuth;
import us.bringardner.parley.ssh.client.SessionChannel;
import us.bringardner.parley.ssh.client.SshClient;
import us.bringardner.parley.ssh.server.HostKeyProviders;
import us.bringardner.parley.ssh.server.SshPrincipal;
import us.bringardner.parley.ssh.server.SshServer;

/**
 * fsh over SSH: an interactive login with a pty (keys typed one at a time, echoed back)
 * and one without (lines piped in).
 */
public class FshShellFactoryTest {

	private static SshServer server;
	private static SshClient client;

	@BeforeAll
	public static void start() throws Exception {
		System.setProperty("java.awt.headless", "true");
		server = new SshServer(0);
		server.setHostKeyProvider(HostKeyProviders.ephemeral());
		server.setPasswordAuthenticator((user, pw, ctx) -> "secret".equals(new String(pw)) ? new SshPrincipal(user) : null);
		server.setShellFactory(new FshShellFactory());
		server.startAndWait(5000);
		client = new SshClient();
		client.setHostKeyVerifier(HostKeyVerifiers.acceptAll());
	}

	@AfterAll
	public static void stop() throws Exception {
		client.close();
		server.stop(5000, false);
	}

	private static ClientSession login() throws Exception {
		ClientSession s = client.connectAndWait("localhost", server.getLocalPort());
		s.authenticateAndWait("alice", new PasswordAuth("secret"));
		return s;
	}

	/** Read until the text shows up */
	private static String readUntil(InputStream in, String want, long timeoutMs) throws Exception {
		ByteArrayOutputStream got = new ByteArrayOutputStream();
		long end = System.currentTimeMillis()+timeoutMs;
		while( System.currentTimeMillis() < end ) {
			if( in.available() > 0 ) {
				got.write(in.read());
				if( got.toString(StandardCharsets.UTF_8).contains(want) ) {
					return got.toString(StandardCharsets.UTF_8);
				}
			} else {
				Thread.sleep(10);
			}
		}
		throw new AssertionError("'"+want+"' not seen in: "+got.toString(StandardCharsets.UTF_8));
	}

	@Test
	public void interactiveWithPty() throws Exception {
		ClientSession s = login();
		SessionChannel ch = s.openSession();
		ch.requestPty("xterm-256color", 100, 30);
		ch.shell();
		OutputStream keys = ch.getOutputStream();
		InputStream screen = ch.getInputStream();
		// Typed one key at a time, with a typo fixed by backspace, then Enter (CR)
		for (byte b : "echo hello-from-fsx".getBytes(StandardCharsets.UTF_8)) {
			keys.write(b);
			keys.flush();
		}
		keys.write(0x7f);
		keys.write("h\r".getBytes(StandardCharsets.UTF_8));
		keys.flush();
		String shown = readUntil(screen, "hello-from-fsh\r\n", 15000);
		assertTrue(shown.contains("echo hello-from-fs"), "the keys are echoed: "+shown);
		keys.write("exit\r".getBytes(StandardCharsets.UTF_8));
		keys.flush();
		assertTrue(ch.waitForClose(15, TimeUnit.SECONDS), "exit ends the session's shell");
		assertTrue(server.isRunning(), "and not the server");
		s.close();
	}

	@Test
	public void linesWithoutPty() throws Exception {
		ClientSession s = login();
		SessionChannel ch = s.openSession();
		ch.shell();
		OutputStream o = ch.getOutputStream();
		o.write("echo plain-line\nexit 3\n".getBytes(StandardCharsets.UTF_8));
		o.flush();
		String out = readUntil(ch.getInputStream(), "plain-line", 15000);
		assertTrue(out.contains("plain-line"), out);
		assertTrue(ch.waitForClose(15, TimeUnit.SECONDS));
		assertEquals(3, ch.getExitStatus(), "the shell's exit status");
		s.close();
	}

	/** Read until the channel closes */
	private static String readAll(SessionChannel ch) throws Exception {
		assertTrue(ch.waitForClose(30, TimeUnit.SECONDS), "the session ends");
		return new String(ch.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}

	@Test
	public void inputQueuedBehindExternalCommandWithoutPty() throws Exception {
		ClientSession s = login();
		SessionChannel ch = s.openSession();
		ch.shell();
		OutputStream o = ch.getOutputStream();
		o.write("whoami\nhostname\necho three\necho four\nexit\n".getBytes(StandardCharsets.UTF_8));
		o.flush();
		String out = readAll(ch);
		assertTrue(out.contains(System.getProperty("user.name")), out);
		assertTrue(out.contains("three"), "a line after an external command is not lost: "+out);
		assertTrue(out.contains("four"), out);
		s.close();
	}

	@Test
	public void burstBehindExternalCommandWithPty() throws Exception {
		ClientSession s = login();
		SessionChannel ch = s.openSession();
		ch.requestPty("xterm-256color", 100, 30);
		ch.shell();
		OutputStream keys = ch.getOutputStream();
		keys.write("pwd\rwhoami\recho mid-burst\recho after-burst\rexit\r".getBytes(StandardCharsets.UTF_8));
		keys.flush();
		String out = readAll(ch);
		assertTrue(out.contains(System.getProperty("user.name")), out);
		assertTrue(out.contains("mid-burst\r\n"), "the lines queued behind an external command run: "+out);
		assertTrue(out.contains("after-burst\r\n"), out);
		s.close();
	}

	@Test
	public void spacedLinesWithPty() throws Exception {
		ClientSession s = login();
		SessionChannel ch = s.openSession();
		ch.requestPty("xterm-256color", 100, 30);
		ch.shell();
		OutputStream keys = ch.getOutputStream();
		for (String line : new String[] {"whoami", "echo right-after", "echo and-more", "exit"}) {
			keys.write((line+"\r").getBytes(StandardCharsets.UTF_8));
			keys.flush();
			Thread.sleep(700);
		}
		String out = readAll(ch);
		assertTrue(out.contains("right-after\r\n"), "the line right after an external command runs: "+out);
		assertTrue(out.contains("and-more\r\n"), out);
		s.close();
	}
}
