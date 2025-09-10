package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;

@TestMethodOrder(OrderAnnotation.class)
public abstract class AbstractConsoleTest {

	public static String fileDate;
	public static Console console;
	public static File testFilesDir;
	
		
	public static void setup(String home) throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		testFilesDir = new File(home).getCanonicalFile();

		System.setProperty("user.home", testFilesDir.getAbsolutePath());
		console = new Console();
	}
	
	
	public static void teardown() {
		
	}
	
	public static class ExecuteResult {
		int exitCode=0;		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();			
	}
	
	public static ExecuteResult executeCommand(String command,String stdIn,String ... args) {
	
		ExecuteResult ret = new ExecuteResult();
		Console console = new Console(args);
		console.setStdOut(new PrintStream(ret.bao));
		console.setStdErr(new PrintStream(ret.bae));
		console.setStdIn(new ByteArrayInputStream(stdIn.getBytes()));
		
		ret.exitCode=console.executeUsingAntlr(command);
	
		if(showError && ret.bae.size()!=0) {
			System.out.println(new String(ret.bae.toByteArray()));
		}
		return ret;
	}
	
	public static ExecuteResult executeCommand(String command,String stdIn,int exitCode) throws IOException {
		ExecuteResult ret = executeCommand(command, stdIn);
		assertEquals(exitCode, ret.exitCode);
		return ret;
	}
	
	public String executeCommand(String command) throws IOException {
		us.bringardner.shell.test.AbstractConsoleTest.ExecuteResult tmp = executeCommand(command, "");
		String ret = new String(tmp.bao.toByteArray());
		return ret;
	}
	
	public static boolean showError = true;
	public static ExecuteResult executeCommand(String command,String stdIn) throws IOException {
		
		ExecuteResult ret = new ExecuteResult();
		PrintStream out = (new PrintStream(ret.bao));
		PrintStream err = (new PrintStream(ret.bae));
		ByteArrayInputStream in = (new ByteArrayInputStream(stdIn.getBytes()));
		console.setStdIn(in);
		console.setStdOut(out);
		console.setStdErr(err);
		
		ret.exitCode=console.executeUsingAntlr(command);
		String errMsg = new String(ret.bae.toByteArray());
		if(showError && !errMsg.isEmpty()) {
			System.out.println(command);
			System.out.println(errMsg);
		}
		
		return ret;
	}

	
	public Path getLinkedTo(File target) {
		Path ret = null;
		try {

			Path path = target.toPath();
			BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
			if (view != null) {
				BasicFileAttributes at = view.readAttributes();
				if( at.isSymbolicLink()) {
					Path newPath = Files.readSymbolicLink(path);
					if( newPath != null) {
						ret = newPath;
					}

				}
			}

		} catch (IOException e) {
			//ignore here
		}

		return ret;
	}
}
