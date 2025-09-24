package us.bringardner.shell.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Connect extends ShellCommand{
	static String name = "connect";
	static String help = "Open connection to a FileSourceFactory\n"
			+ "USAGE: connect factory_id [ [-f=property_file]|[name=val ...]] mount_point\n"
			+ "\targuments may also be provided on stdin.";


	public Connect() {
		super(name, help);
	}


	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		if( ctx.args.length < 3) {
			ctx.stdout.println( help);
			ret = -1;
		} else {

			//put in a list to make it easier to manage
			List<String> args = new ArrayList<>();
			for(String arg : ctx.args) {
				args.add(arg);
			}

			args.removeFirst();
			String fid = args.removeFirst();
			FileSourceFactory tmp = FileSourceFactory.getFileSourceFactory(fid);
			if( tmp == null ) {
				ctx.stdout.println("Unknown factory id="+fid);
				ret = -1;
			} else {
				if( !tmp.isConnected()) {
					String mountPoint = args.removeLast().trim();
					if( mountPoint.isEmpty()) {
						throw new IOException("No valid mount point");
					}
					FileSource mount =  ctx.console.getMountFactory().createFileSource(mountPoint);

					if( mount.exists()) {
						throw new IOException(mountPoint+" already exists.");
					}

					Properties props = tmp.getConnectProperties();
					if( props != null && props.size()>0) {

						for(String arg : args) {
							processArg(ctx,arg,props);
						}

						if( tmp.connect(props)) {
							if(!ctx.console.mount(tmp, mountPoint)) {
								throw new IOException("Can't mount "+tmp.getTypeId()+" to "+mountPoint);
							}
							ctx.stdout.println(tmp.getTypeId()+" connected as "+mountPoint);
						} else {
							ctx.stdout.println(tmp.getTypeId()+" could not connect.");
						}


					} else {
						ctx.stdout.println("Already connected");
						ret = -1;
					}
				}
			}
		}



			return ret;
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

				FileSource file = files.removeFirst();
				if( !file.exists() || file.length() <=0 ) {
					throw new IOException("Can't load properies. "+file+" does not exists or is empty");
				}
				InputStream pin = file.getInputStream();
				try {
					props.load(pin);
				} finally {
					pin.close();
				}			
			} else {
				String  [] parts=arg.split("=");
				if( parts.length != 2) {
					throw new IOException("Invalid arg = "+arg);
				}
				props.setProperty(parts[0].trim(), parts[1].trim());
			}

		}


	}
