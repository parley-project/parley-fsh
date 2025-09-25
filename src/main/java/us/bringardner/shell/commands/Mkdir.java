package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Mkdir extends ShellCommand{
	static String name = "mkdir";
	// physical 
	static String help = " mkdir – make directories\n"
			+ "\n"
			+ "SYNOPSIS\n"
			+ "     mkdir [-pv] [-m mode] directory_name ...\n"
			+ "\n"
			+ "     The options are as follows:\n"
			+ "\n"
			+ "     -m mode        Set the file permission bits of the final created directory to the specified mode.  The mode argument can be in any of the formats specified to the chmod(1) command.  If a\n"
			+ "                    symbolic mode is specified, the operation characters ‘+’ and ‘-’ are interpreted relative to an initial mode of “a=rwx”.\n"
			+ "\n"
			+ "     -p             Create intermediate directories as required.  If this option is not specified, the full path prefix of each operand must already exist.  On the other hand, with this option\n"
			+ "                    specified, no error will be reported if a directory given as an operand already exists.  Intermediate directories are created with permission bits of “rwxrwxrwx” (0777) as\n"
			+ "                    modified by the current umask, plus write and search permission for the owner.\n"
			+ "\n"
			+ "     -v             Be verbose when creating directories, listing them as they are created.\n"
			+ "\n"
			+ "     The user must have write permission in the parent directory.\n"
			+ "";

	public Mkdir() {
		super(name, help);
	}

	enum MkdirArgs {m,p,v}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;

		ShellArgument ops = parserArgs(ctx, MkdirArgs.class);
		boolean mkdirs = ops.options.contains(MkdirArgs.p);
		boolean verbose = ops.options.contains(MkdirArgs.v);
		for(String path : ops.paths) {
			List<FileSource> dirs = getFiles(ctx, path);
			if( dirs==null || dirs.size()==0) {
				ctx.stderr.println("mkdir: no such directory: "+path);
				return -1;
			}
			for(FileSource dir : dirs) {
				if(mkdirs ) {
					ret = dir.mkdirs()?0:1;
				} else {
					ret = dir.mkdir()?0:1;
				}
				if( ret != 0 ) {
					ctx.stderr.println("mkdir: could not create directory for "+path);
					return ret;
				}
				if( verbose ) {
					ctx.stdout.println("mkdir: "+dir);
				}
			}
		}
		
		return ret;
	}



}
