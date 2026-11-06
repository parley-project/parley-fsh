package us.bringardner.fsh.ssh;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import us.bringardner.parley.net.server.FileUserRegistry;

/**
 * The test user for the embedded SSH servers: parley-ssh 2.0.0 logs in only users its parley-iam
 * registry knows, and the registry checks the password and answers the shell and exec permissions.
 */
final class TestUsers {

	private TestUsers() {
	}

	/** @return a registry with alice (password secret) holding shell and exec */
	static FileUserRegistry alice() throws IOException {
		File users = Files.createTempFile("fsh-ssh-test-users", ".txt").toFile();
		users.deleteOnExit();
		Files.write(users.toPath(), "alice, secret, shell|exec\n".getBytes(StandardCharsets.UTF_8));
		FileUserRegistry registry = new FileUserRegistry();
		registry.initialize(users);
		return registry;
	}
}
