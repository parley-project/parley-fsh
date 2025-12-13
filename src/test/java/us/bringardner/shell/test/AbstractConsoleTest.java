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
import java.util.List;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;
import us.bringardner.shell.job.IJob;

@TestMethodOrder(OrderAnnotation.class)
public abstract class AbstractConsoleTest {

	public enum OperatingSystem {UnKnown,Windows,Mac,Linux};
	
	public static OperatingSystem getOs() {
		OperatingSystem ret = OperatingSystem.UnKnown;
		String os = System.getProperty("os.name").toLowerCase();
		if( os.contains("win")) {
			ret = OperatingSystem.Windows;
		} else if (os.contains("mac")) {
			ret = OperatingSystem.Mac;
		} 	else if (os.contains("linux")) {
			ret = OperatingSystem.Linux;
		}
		return ret;
	}

	public static String fileDate;
	public static Console console;
	public static File testFilesDir;


	public static void setup(String home) throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		File tmp = new File(home).getCanonicalFile();
		testFilesDir = tmp;
		System.setProperty("user.home", testFilesDir.getAbsolutePath());
		console = new Console();
	}


	public static void teardown() {

	}

	public static class ExecuteResult {
		public int exitCode=0;		
		private ByteArrayOutputStream bao = new ByteArrayOutputStream();
		public int getExitCode() {
			return exitCode;
		}

		public ByteArrayOutputStream getBao() {
			return bao;
		}

		public ByteArrayOutputStream getBae() {
			return bae;
		}
		private ByteArrayOutputStream bae = new ByteArrayOutputStream();	

		public String toString() {
			return "Exitcode="+exitCode+"\n"
					+ "out="+(new String(bao.toByteArray()))+"\n"
					+ "err="+(new String(bae.toByteArray()))+"\n"
					;
		}
		
		public String getStdOut() {
			return new String(bao.toByteArray()).replaceAll("\r", "");
		}
		public String getStdErr() {
			return new String(bae.toByteArray()).replaceAll("\r", "");
		}
		
	}

public static ExecuteResult executeCommand(String [] args,String stdIn,int exitCode,String expectOut,String expectErr) throws IOException {
		
		Console console = new Console();
		
		ExecuteResult ret = new ExecuteResult();
		console.setStdOut(new PrintStream(ret.bao));
		console.setStdErr(new PrintStream(ret.bae));
		console.setStdIn(new ByteArrayInputStream(stdIn.getBytes()));
		
		ret.exitCode = console.execute(args);
		
		
		String out = ret.getStdOut();
		String err = ret.getStdErr();
		assertEquals(expectErr, err);
		assertEquals(expectOut, out);
		assertEquals(exitCode, ret.exitCode);
		
		return ret;
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

	public static ExecuteResult executeCommand(String command,String stdIn,int exitCode,String expectOut,String expectErr) throws IOException {
		ExecuteResult ret = executeCommand(command, stdIn,exitCode);
		assertEquals(expectOut, ret.getStdOut());
		assertEquals(expectErr, ret.getStdErr());
		
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
	public static boolean waitForJobs = false;

	public static ExecuteResult executeCommand(String command,String stdIn) throws IOException {

		ExecuteResult ret = new ExecuteResult();
		PrintStream out = (new PrintStream(ret.bao));
		PrintStream err = (new PrintStream(ret.bae));
		ByteArrayInputStream in = (new ByteArrayInputStream(stdIn.getBytes()));
		console.setStdIn(in);
		console.setStdOut(out);
		console.setStdErr(err);

		ret.exitCode=console.executeUsingAntlr(command);
		List<IJob> jobs = console.jobManager.getJobs();
		long start = System.currentTimeMillis();

	
		if( waitForJobs) {
			int id = -1;
			boolean done = false;
			int cnt = 0;
			while( !done ) {
				Thread.yield();
				long time = System.currentTimeMillis()-start;
				if( time > 2000) {
					System.out.println("Waiting for jobs to stop... id="+id);
					start = System.currentTimeMillis();
					if( ++cnt > 5) {
						System.out.println("Exit Waiting for jobs to stop...");
						break;
					}
				}

				done = true;
				id = -1;
				for(IJob job : jobs) {
					if( job.isRunning()) {
						id = job.getJobNumber();
						done = false;
					}
				}
			}
		}
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
