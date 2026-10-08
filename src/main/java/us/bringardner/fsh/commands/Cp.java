package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

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
		ShellArgument sa = parseArgs(ctx, Arguments.class);

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
		boolean existed = to.exists();
		try(InputStream in = from.getInputStream()) {
			try(OutputStream out = to.getOutputStream()) {
				copyStream(ctx,in,out);
			}
		}		
		if( !existed && from instanceof us.bringardner.parley.files.fileproxy.FileProxy f
				&& to instanceof us.bringardner.parley.files.fileproxy.FileProxy t ) {
			// as cp: a new file gets the source's mode (less group and other write, as umask 022)
			try {
				java.util.Set<java.nio.file.attribute.PosixFilePermission> mode = java.nio.file.Files.getPosixFilePermissions(f.getTarget().toPath());
				mode.remove(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
				mode.remove(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
				java.nio.file.Files.setPosixFilePermissions(t.getTarget().toPath(), mode);
			} catch (UnsupportedOperationException e) {
			}
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
