package us.bringardner.shell.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Mv extends ShellCommand{


	enum MvOptions {f,i,n,h,v};

	static String name = "mv";
	static String USAGE =  "USAGE\n"
			+ "     mv [-f | -i | -n] [-hv] source target\n"
			+ "     mv [-f | -i | -n] [-v] source ... directory\n";

	static String help = ""
			+ ""
			+ "     mv – move files\n"
			+ "\n"
			+ "SYNOPSIS\n"
			+ "     mv [-f | -i | -n] [-hv] source target\n"
			+ "     mv [-f | -i | -n] [-v] source ... directory\n"
			+ "\n"
			+ "DESCRIPTION\n"
			+ "     In its first form, the mv utility renames the file named by the source operand to the destination path named by the target operand.  This form is assumed when the last operand does\n"
			+ "     not name an already existing directory.\n"
			+ "\n"
			+ "     In its second form, mv moves each file named by a source operand to a destination file in the existing directory named by the directory operand.  The destination path for each operand\n"
			+ "     is the pathname produced by the concatenation of the last operand, a slash, and the final pathname component of the named file.\n"
			+ "\n"
			+ "     The following options are available:\n"
			+ "\n"
			+ "     -f      Do not prompt for confirmation before overwriting the destination path.  (The -f option overrides any previous -i or -n options.)\n"
			+ "\n"
			+ "     -h      If the target operand is a symbolic link to a directory, do not follow it.  This causes the mv utility to rename the file source to the destination path target rather than\n"
			+ "             moving source into the directory referenced by target.\n"
			+ "\n"
			+ "     -i      Cause mv to write a prompt to standard error before moving a file that would overwrite an existing file.  If the response from the standard input begins with the character ‘y’\n"
			+ "             or ‘Y’, the move is attempted.  (The -i option overrides any previous -f or -n options.)\n"
			+ "\n"
			+ "     -n      Do not overwrite an existing file.  (The -n option overrides any previous -f or -i options.)\n"
			+ "\n"
			+ "     -v      Cause mv to be verbose, showing files after they are moved.\n"
			+ "\n"
			+ "     It is an error for the source operand to specify a directory if the target exists and is not a directory.\n"
			+ "\n"
			+ "     If the destination path does not have a mode which permits writing, mv prompts the user for confirmation as specified for the -i option.\n"
			+ "\n"
			+ "     As the rename(2) call does not work across file systems, mv uses cp(1) and rm(1) to accomplish the move.  The effect is equivalent to:\n"
			+ "\n"
			+ "           rm -f destination_path && \\\n"
			+ "           cp -pRP source_file destination && \\\n"
			+ "           rm -rf source_file\n"
			+ "\n"
			+ "EXIT STATUS\n"
			+ "     The mv utility exits 0 on success, and >0 if an error occurs.\n"
			+ "\n"
			+ "     The command \"mv dir/afile dir\" will abort with an error message.";

	public Mv() {
		super(name, help);
	}

	@Override
	public int process(ShellContext sc) throws IOException {
		int ret = 0;
		ShellArgument sa = parserArgs(sc, MvOptions.class);

		List<String> paths = sa.paths;

		List<FileSource> files = new ArrayList<>();

		for(String path : paths) {
			List<FileSource> kids = new ArrayList<>();

			if( hasWildcard(path)) {
				kids.addAll(getFiles(sc, path));
			} else {
				FileSource cwd = sc.console.getCurrentDirectory();
				if( isRelative(path)) {
					kids.add(cwd.getChild(path));
				} else {
					kids.add(sc.console.createFileSource(path));					
				}
			}

			files.addAll(kids);
		}

		if( files.size() < 2) {
			sc.stderr.println( USAGE);			
			return 1;
		}

		FileSource dest = files.removeLast();
		if(dest.isDirectory()) {
			for(FileSource file : files) {
				FileSource dest2= dest.getChild(file.getName());
				if((ret=mv_or_copy(sc,file, dest2,sa.options))!=0) {
					break;
				}
			}			
		} else {
			if(files.size()>1) {
				sc.stderr.println( USAGE);			
				return 1;
			} 
			ret = mv_or_copy(sc,files.getFirst(),dest,sa.options);
		}

		return ret;
	}

	public static int deleteIfExists(FileSource file) throws IOException {
		if( file.exists()) {
			if(file.isDirectory()) {
				FileSource[] kids = file.listFiles();
				if( kids !=null) {
					for(FileSource kid : kids) {
						int ret = deleteIfExists(kid);
						if( ret !=0) {
							return ret;
						}
					}
				}
			}
			if(!file.delete()) {
				return 1;
			}
		}
		
		return 0;
	}
	
	private int mv_or_copy(ShellContext sc,FileSource source, FileSource dest, List<Object> options) throws IOException {
		if( !source.exists()) {
			sc.stderr.println(source.getAbsolutePath()+" does not exist.");
			return 1;
		}
		if( !options.contains(MvOptions.f) && dest.exists()) {

		}
		if( !source.renameTo(dest)) {
			copy(sc,source,dest,options);
			// copy worked 
			return deleteIfExists(source);
		}

		return 0;
	}

	public static void copy(ShellContext sc,FileSource from, FileSource to,List<Object> options) throws IOException {
		FileSource parent = to.getParentFile();
		if( parent != null && !parent.exists()) {
			parent.mkdirs();
		} 
		if( from.isDirectory()) {
			FileSource [] kids = from.listFiles();
			if( kids != null ) {
				for(FileSource f : kids) {
					copy(sc,f,to.getChild(f.getName()),options);
				}
			}
		} else {
			try(InputStream in = from.getInputStream()) {
				try(OutputStream out = to.getOutputStream()) {
					copy(sc,in,out);		
				}
			}			
		}
	}

	public static void copy(ShellContext sc,InputStream in, OutputStream out) throws IOException {
		// use a small buffer to get multiple reads 
		byte [] data = new byte[1024*10];
		int got = 0;

		while( (got=in.read(data)) >= 0) {
			if( got > 0 ) {
				out.write(data,0,got);
			}
			if(sc.getException() !=null) {
				throw new IOException(sc.getException());
			}
			while(sc.isPaused()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
		}
	}

}
