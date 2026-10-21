package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.commands.Help;

/**
 * COMMANDS.md is made from the builtins' help text. This fails when the file is out of date;
 * -Dparley.fsh.writeCommandsDoc=true writes it instead.
 */
public class TestCommandsDoc {

	@Test
	public void commandsDocIsCurrent() throws IOException {
		Path doc = Path.of("COMMANDS.md");
		String made = Help.markdown();
		if( Boolean.getBoolean("parley.fsh.writeCommandsDoc")) {
			Files.writeString(doc, made, StandardCharsets.UTF_8);
			return;
		}
		String have = Files.exists(doc) ? Files.readString(doc, StandardCharsets.UTF_8) : "";
		assertEquals(made, have.replace("\r\n", "\n"), "COMMANDS.md is out of date; run"
				+ " mvn test -Dtest=TestCommandsDoc -Dparley.fsh.writeCommandsDoc=true");
	}
}
