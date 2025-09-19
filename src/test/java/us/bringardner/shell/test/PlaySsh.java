package us.bringardner.shell.test;

import java.io.IOException;
import java.util.Arrays;

import us.bringardner.shell.Console;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.CommandStatement;

public class PlaySsh {

	/*
cd /Volumes/Data/Applications/ExecutableJars/AWS
usr=ec2-user
svr1=ec2-54-69-95-39.us-west-2.compute.amazonaws.com
svr=bringardner.us

Echo "ssh -X -i dns2rebuild.pem $usr@$svr1"
ssh -X -i dns2rebuild.pem $usr@$svr1


	 */
	
	public static void execute() throws InterruptedException {
		try {
			
			//ProcessBuilder b = new ProcessBuilder().command("sh ssh  -i /Volumes/Data/Applications/ExecutableJars/AWS/dns2rebuild.pem  ec2-user@ec2-54-69-95-39.us-west-2.compute.amazonaws.com"); // macOS
			ProcessBuilder b= new ProcessBuilder("open", "-a", "Terminal", "ssh","-i","/Volumes/Data/Applications/ExecutableJars/AWS/dns2rebuild.pem","ec2-user@ec2-54-69-95-39.us-west-2.compute.amazonaws.com"); // ma
			//ProcessBuilder b = new ProcessBuilder("open", "-an", "Terminal");
			//ProcessBuilder b = new ProcessBuilder("open", "-an", "ssh");
			//ProcessBuilder b = new ProcessBuilder().command("ssh","-X","tony@test.bringardner.us"); // macOS
			//ProcessBuilder b = new ProcessBuilder().command("open", "-a", "Ssh","-X","tony@test.bringardner.us"); // macOS
			Process p = b.start();
			int exit = p.waitFor();
			System.out.println("exit = "+exit);
		} catch (IOException e2) {
			e2.printStackTrace();
		}

	}
	
	public static void main(String[] args) throws IOException, InterruptedException {
		execute();
		/*
		Console console = new Console();
		ShellContext ctx = new ShellContext(console);
		String [] cmd = {
				"ssh" ,"-X","tony@test.bringardner.us"
		};
		
		int exit = CommandStatement.execute(Arrays.asList(cmd), ctx);
		System.out.println("exit = "+exit);
		*/
	}

}
