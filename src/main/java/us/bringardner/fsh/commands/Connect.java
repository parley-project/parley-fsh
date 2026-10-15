package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Connect extends ShellCommand{
	static String name = "connect";
	static String help = "connect FACTORY [-f FILE] [name=value ...] [-] MOUNT_POINT\n"
			+ "\tConnect to a file system and mount it at MOUNT_POINT (a path such as /prod), where fsh's file\n"
			+ "\tbuiltins (ls, cp, mv, rm, find, ...) can use it. FACTORY is the file system's id, such as memory,\n"
			+ "\tsftp, ftp or jdbc (jdbc when fsh is built with it).\n"
			+ "\tSettings, applied in order (a later one replaces an earlier one):\n"
			+ "\t-f FILE, -fFILE, -f=FILE   name=value lines from a properties file\n"
			+ "\tname=value                 one setting\n"
			+ "\t-                          one line of name=value settings read from standard input\n"
			+ "\tA secret setting such as password can name its secret instead of holding it: @env:NAME,\n"
			+ "\t@file:PATH, @keychain:SERVICE/ACCOUNT, @keystore:NAME, @prompt: or @cmd:COMMAND (see cred).\n"
			+ "\tOn success it prints \"FACTORY connected as MOUNT_POINT\"; disconnect undoes it.\n"
			+ "\tExamples:\n"
			+ "\tconnect memory /mem\n"
			+ "\tconnect sftp host=example.com user=deploy password=@keychain:parley/sftp/deploy@example.com /prod\n"
			+ "\tconnect ftp -f ~/.parley/ftp.properties /ftp";


	public Connect() {
		super(name, help);
	}


	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean readStdin = false;
		// -f FILE, -fFILE and -f=FILE all become -f=FILE
		List<String> args = new ArrayList<>();
		for(int idx = 0; idx < this.args.length; idx++) {
			String arg = ""+this.args[idx].getValue(ctx);
			if( arg.equals("-f")) {
				if( idx+1 >= this.args.length ) {
					ctx.stderr.println(name+": -f needs a properties file");
					return 2;
				}
				args.add("-f="+this.args[++idx].getValue(ctx));
			} else if( arg.startsWith("-f") && !arg.startsWith("-f=")) {
				args.add("-f="+arg.substring(2));
			} else if( arg.equals("-")) {
				readStdin = true;
			} else {
				args.add(arg);
			}
		}
		if( args.size() < 2 ) {
			ctx.stderr.println("usage: "+help.split("\n", 2)[0]);
			return 2;
		}

		String fid = args.remove(0);
		String mountPoint = args.remove(args.size()-1).trim();
		if( !mountPoint.startsWith("/") || mountPoint.equals("/")) {
			ctx.stderr.println(name+": the mount point must be a path such as /prod, not '"+mountPoint+"'");
			return 2;
		}
		FileSourceFactory factory = FileSourceFactory.getFileSourceFactory(fid);
		if( factory == null ) {
			ctx.stderr.println(name+": unknown file system '"+fid+"'; the known ones are "
					+String.join(", ", FileSourceFactory.getRegisterdFactories()));
			return 1;
		}
		if( factory.isConnected()) {
			ctx.stderr.println(name+": "+fid+" is already connected");
			return 1;
		}
		Properties props = factory.getConnectProperties();
		if( props == null || props.isEmpty()) {
			ctx.stderr.println(name+": "+fid+" has no connection settings, so it can't be connected");
			return 1;
		}
		for(String arg : args) {
			processArg(ctx, arg, props);
		}
		if( readStdin ) {
			String line = readLine(ctx.stdin);
			if( line != null && !line.isBlank()) {
				// (name=value settings separated by any white space)
				for(String arg : line.trim().split("\\s+")) {
					processArg(ctx, arg, props);
				}
			}
		}
		// a setting may name its secret by reference (password=@keychain:...); @prompt: asks on
		// this shell's terminal
		if( !CredentialPrompts.with(ctx, () -> factory.connect(props))) {
			ctx.stderr.println(name+": "+fid+" could not connect");
			return 1;
		}
		FileSource[] roots = factory.listRoots();
		String problem = null;
		if( roots == null || roots.length != 1 ) {
			problem = fid+" has "+(roots == null ? 0 : roots.length)+" roots; it can be mounted only with one";
		} else if( !ctx.console.mount(roots[0], mountPoint)) {
			problem = "can't mount "+fid+" at "+mountPoint+" (is something already connected there?)";
		}
		if( problem != null ) {
			// (don't leave a connection open that nothing can use)
			try {
				factory.disConnect();
			} catch (IOException e) {
				// the mount failing is what matters
			}
			ctx.stderr.println(name+": "+problem);
			return 1;
		}
		ctx.stdout.println(factory.getTypeId()+" connected as "+mountPoint);
		return 0;
	}

	private void processArg(ShellContext ctx,String arg, Properties props) throws IOException {
		if( arg.startsWith("-f=")) {
			String fileName=arg.substring(3).trim();
			List<FileSource> files = getFiles(ctx, fileName);
			if( files==null || files.size() == 0 ) {
				throw new IOException("No property file after evauating "+fileName);
			}
			if( files.size() > 1 ) {
				throw new IOException("Too many property files after evaluating  "+fileName);
			}

			FileSource file = files.remove(0);
			if( !file.exists() || file.length() <=0 ) {
				throw new IOException("can't load settings: "+file+" doesn't exist or is empty");
			}
			InputStream pin = file.getInputStream();
			try {
				props.load(pin);
			} finally {
				pin.close();
			}			
		} else {
			// limit 2: a value (a generated password, a JDBC url) may itself contain '='
			String  [] parts=arg.split("=",2);
			if( parts.length != 2 || parts[0].isBlank()) {
				// only the name: the value may be a password
				throw new IOException("Invalid arg (expected name=value): "+parts[0]);
			}
			props.setProperty(parts[0].trim(), parts[1].trim());
		}

	}


}
