package us.bringardner.shell.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Cp extends ShellCommand{
	private enum Arguments {R,H,L,P,f,i,n,a,c,l,p,S,s,v,x};

	static String name = "cp";
	static String help = ""
			+ "the cp utility copies the contents of the source_file to the target_file.\n"
			+ "usage: cp [-R [-H | -L | -P]] [-fi | -n] [-aclpSsvXx] source_file target_file\n"
			+ "       cp [-R [-H | -L | -P]] [-fi | -n] [-aclpSsvXx] source_file ... target_directory"
			+ "\t see 'man cp' for more information";

	public Cp() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		ShellArgument sa = parserArgs(ctx, Arguments.class);

		List<Arguments> options = new ArrayList<>();
		for(Object o : sa.options) {
			if (o instanceof Arguments) {
				options.add((Arguments) o);				
			}
		}
		List<String> paths = sa.paths;
		List<FileSource> files = new ArrayList<>();

		for(String path : paths) {
			List<FileSource> kids = getFiles(ctx, path);

			//If the source_file ends in a /, the contents of the directory are copied rather than the directory itself.  

			if(options.contains(Arguments.R) && kids.size() == 1 && path.endsWith("/")) {
				kids =Arrays.asList(kids.get(0).listFiles());
			}

			files.addAll(kids);
		}

		if( files.size() < 1) {
			ctx.stdout.println(help);
			return -1;
		}

		FileSource lastFile = files.removeLast();
		if( lastFile.isDirectory()) {
			ctx.setValue(ROOT_FACTORY_ID, lastFile.getFileSourceFactory().getTypeId());
			copyToDir(ctx,options,files,lastFile);
		} else {
			if( files.size()> 1) {
				ctx.stdout.println(lastFile+" is not a valid directory.");
				return -1;
			}
			copyFileToFile(ctx,options,files.getFirst(),lastFile);
		}

		return ret;
	}

	private void copyFileToFile(ShellContext ctx, List<Arguments> options, FileSource from, FileSource to) throws IOException {
		try(InputStream in = from.getInputStream()) {
			try(OutputStream out = to.getOutputStream()) {
				copySteam(ctx,options,in,out);
			}
		}		
	}


	private void copySteam(ShellContext ctx, List<Arguments> options, InputStream in, OutputStream out) throws IOException {
		byte [] buffer = new byte[1024*10];
		int got = in.read(buffer);
		while( got >=0 ) {
			if( got > 0 ) {
				out.write(buffer, 0, got);
			}
			got = in.read(buffer);
		}
	}

	private void copyToDir(ShellContext ctx, List<Arguments> options, List<FileSource> files, FileSource dir) throws IOException {
		if( !dir.exists()) {
			if( !dir.mkdirs()) {
				throw new IOException("Can't create diretory "+dir);
			}
		}

		for(FileSource file : files) {
			FileSource destination = dir.getChild(file.getName());
			if( file.isDirectory() ) {
				if(options.contains(Arguments.R)) {
					if(options.contains(Arguments.x)) {
						if( !ctx.getValue(ROOT_FACTORY_ID, "").equals(file.getFileSourceFactory().getTypeId())) {
							continue;
						}
					}					
					copyToDir(ctx, options,Arrays.asList(file.listFiles()), destination);
				}
			} else {
				copyFileToFile(ctx, options, file, destination);
			}
		}
	}

}
