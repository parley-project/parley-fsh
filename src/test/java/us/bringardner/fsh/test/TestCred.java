package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.commands.CredentialPrompts;
import us.bringardner.parley.files.credentials.CommandCredentialProvider;

/** The cred builtin, and keeping plain passwords out of the history. */
public class TestCred extends AbstractConsoleTest {

	@TempDir
	Path dir;

	@BeforeAll
	public static void beforeAll() throws IOException {
		setup("ConnectTestFiles");
	}

	@AfterEach
	public void cmdOff() {
		CommandCredentialProvider.setEnabled(false);
	}

	private Path ownerOnlyFile(String content) throws IOException {
		Path f = dir.resolve("pw");
		Files.write(f, content.getBytes(StandardCharsets.UTF_8));
		if( getOs() != OperatingSystem.Windows ) {
			Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-------"));
		}
		return f;
	}

	@Test
	public void providersListsTheBuiltInStores() throws IOException {
		ExecuteResult res = executeCommand("cred providers", "");
		assertEquals(0, res.exitCode, res.getStdErr());
		String out = res.getStdOut();
		for(String scheme : new String[] {"env", "file", "cmd", "prompt", "keystore"}) {
			assertTrue(out.contains(scheme+"\t"), out);
		}
		assertTrue(out.contains("cmd\tunavailable"), "cmd is off by default:\n"+out);
	}

	@Test
	public void getSaysFoundAndNeverPrintsTheSecret() throws IOException {
		Path f = ownerOnlyFile("s3cret-value\n");
		ExecuteResult res = executeCommand("cred get file:"+f, "");
		assertEquals(0, res.exitCode, res.getStdErr());
		assertEquals("@file:"+f+": found", res.getStdOut().trim());
		assertFalse((res.getStdOut()+res.getStdErr()).contains("s3cret"));

		res = executeCommand("cred get @file:"+dir.resolve("missing"), "");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdErr().contains("doesn't exist"), res.getStdErr());
	}

	@Test
	public void setNeverTakesTheSecretAsAnArgument() throws IOException {
		ExecuteResult res = executeCommand("cred set keychain:parley/x hunter2", "");
		assertEquals(2, res.exitCode);
		assertTrue(res.getStdErr().contains("never given as an argument"), res.getStdErr());
	}

	@Test
	public void setNeedsSomeoneToAsk() throws IOException {
		ExecuteResult res = executeCommand("cred set file:"+dir.resolve("new"), "typed\ntyped\n");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdErr().contains("needs an interactive shell"), res.getStdErr());
		assertFalse(Files.exists(dir.resolve("new")));
	}

	@Test
	public void rmRemovesAndBadReferencesAreRefused() throws IOException {
		Path f = ownerOnlyFile("x");
		ExecuteResult res = executeCommand("cred rm file:"+f, "");
		assertEquals(0, res.exitCode, res.getStdErr());
		assertFalse(Files.exists(f));

		res = executeCommand("cred get notareference", "");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdErr().contains("isn't a reference"), res.getStdErr());
	}

	@Test
	public void cmdIsOffUntilEnabled() throws IOException {
		assumeTrue(getOs() != OperatingSystem.Windows);
		ExecuteResult res = executeCommand("cred get \"cmd:echo hi\"", "");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdErr().contains("is off"), res.getStdErr());

		res = executeCommand("cred enable cmd", "");
		assertEquals(0, res.exitCode, res.getStdErr());
		res = executeCommand("cred get \"cmd:echo hi\"", "");
		assertEquals(0, res.exitCode, res.getStdErr());
	}

	@Test
	public void testConnectsAndDisconnects() throws IOException {
		ExecuteResult res = executeCommand("cred test memory", "");
		assertEquals(0, res.exitCode, res.getStdErr());
		assertEquals("memory: connected", res.getStdOut().trim());
	}

	@Test
	public void aPromptInAScriptFailsAtOnce() throws IOException {
		ExecuteResult res = executeCommand("cred get prompt:", "");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdErr().contains("no way to ask"), res.getStdErr());
	}

	@Test
	public void linesWithAPlainPasswordAreKeptOutOfTheHistory() {
		assertTrue(CredentialPrompts.holdsPlainSecret("connect sftp host=h user=u password=hunter2 /x"));
		assertTrue(CredentialPrompts.holdsPlainSecret("cd /; connect ftp \"password=a b\" /f"));
		assertTrue(CredentialPrompts.holdsPlainSecret("connect jdbc jdbcPassword=pw /db"), "the factory names its secrets");
		assertTrue(CredentialPrompts.holdsPlainSecret("connect sftp password=@@literal /x"), "@@ is a literal @");
		assertTrue(CredentialPrompts.holdsPlainSecret("cred set keychain:parley/x hunter2"));

		assertFalse(CredentialPrompts.holdsPlainSecret("connect sftp host=h password=@keychain:parley/sftp /x"));
		assertFalse(CredentialPrompts.holdsPlainSecret("connect sftp host=h password=$PW /x"));
		assertFalse(CredentialPrompts.holdsPlainSecret("connect sftp host=h password= /x"));
		assertFalse(CredentialPrompts.holdsPlainSecret("connect memory name=password /m"));
		assertFalse(CredentialPrompts.holdsPlainSecret("cred set keychain:parley/x"));
		assertFalse(CredentialPrompts.holdsPlainSecret("echo connect password=x"));
	}
}
