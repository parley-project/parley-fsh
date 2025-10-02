package us.bringardner.shell.test;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;

import sun.misc.Signal;
import sun.misc.SignalHandler;
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
	
	public static void main1(String[] args) throws IOException, InterruptedException {
		 // Java signal handling works by registering SignalHandler
        // implementations for specific signals.
        CountDownLatch sigReceived = new CountDownLatch(1);

        // Register handlers for SIGINT and SIGTERM
        SignalHandler handler = signal -> {
            System.out.println();
            System.out.println(signal.getName());
            sigReceived.countDown();
        };
        
        

        
        Signal.handle(new Signal("INT"), handler);
        Signal.handle(new Signal("TERM"), handler);

        // This demonstrates a more realistic scenario of graceful shutdown.
        Thread signalWaiter = new Thread(() -> {
            try {
                sigReceived.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        signalWaiter.start();

        // The program will wait here until it gets the
        // expected signal (as indicated by the CountDownLatch
        // being decremented) and then exit.
        System.out.println("awaiting signal");
        try {
            signalWaiter.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("exiting");
    }

	public static void main(String[] args) throws IOException, InterruptedException {
	
		String tmp = "1) SIGHUP   2) SIGINT   3) SIGQUIT  4) SIGILL   5) SIGTRAP\n"
				+ " 6) SIGABRT  7) SIGBUS   8) SIGFPE   9) SIGKILL 10) SIGUSR1\n"
				+ "11) SIGSEGV 12) SIGUSR2 13) SIGPIPE 14) SIGALRM 15) SIGTERM\n"
				+ "16) SIGSTKFLT   17) SIGCHLD 18) SIGCONT 19) SIGSTOP 20) SIGTSTP\n"
				+ "21) SIGTTIN 22) SIGTTOU 23) SIGURG  24) SIGXCPU 25) SIGXFSZ\n"
				+ "26) SIGVTALRM   27) SIGPROF 28) SIGWINCH    29) SIGIO   30) SIGPWR\n"
				+ "31) SIGSYS  34) SIGRTMIN    35) SIGRTMIN+1  36) SIGRTMIN+2  37) SIGRTMIN+3\n"
				+ "38) SIGRTMIN+4  39) SIGRTMIN+5  40) SIGRTMIN+6  41) SIGRTMIN+7  42) SIGRTMIN+8\n"
				+ "43) SIGRTMIN+9  44) SIGRTMIN+10 45) SIGRTMIN+11 46) SIGRTMIN+12 47) SIGRTMIN+13\n"
				+ "48) SIGRTMIN+14 49) SIGRTMIN+15 50) SIGRTMAX-14 51) SIGRTMAX-13 52) SIGRTMAX-12\n"
				+ "53) SIGRTMAX-11 54) SIGRTMAX-10 55) SIGRTMAX-9  56) SIGRTMAX-8  57) SIGRTMAX-7\n"
				+ "58) SIGRTMAX-6  59) SIGRTMAX-5  60) SIGRTMAX-4  61) SIGRTMAX-3  62) SIGRTMAX-2\n"
				+ "63) SIGRTMAX-1  64) SIGRTMAX"
				;
		
		String [] parts = tmp.split("[ \t\n]");
		Map<Integer,String> map = new TreeMap<>();
		for (int idx = 0; idx < parts.length; idx++) {
			String val = parts[idx].trim();
			if( !val.isEmpty()) {
				if( !val.endsWith(")")) {
					String name = (parts[idx].substring(3));
					try {
						Signal s = new Signal(name);
						System.out.println(name+"="+s.getNumber());
						map.put(s.getNumber(), name);
					} catch (Exception e) {
						//System.err.println(name);
					}
				}
			}
		}
		
		for(Integer n :map.keySet()) {
			//System.out.print("\""+map.get(n)+"\",");
		}
		
		/*
	 String [] names = {"INT","TERM","SEGV", "BUS", "FPE", "PIPE", "ILL","HUP","KILL","CONT","STOP","TSTP"};
	
	
		for (String name : names) {
			Signal s = new Signal(name);
			System.out.println(name+"="+s.getNumber());
		}
	    */
	   
	}
	

}
