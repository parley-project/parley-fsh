package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Mv extends ShellCommand{


	enum MvOptions {f,i,n,h,v};

	static String name = "mv";
	static String USAGE =  "USAGE\n"
			+ "     mv [-f | -i | -n] [-hv] source target\n"
			+ "     mv [-f | -i | -n] [-v] source ... directory\n";

	static String help = "mv [-f | -i | -n] [-v] SOURCE TARGET   or   mv [-f | -i | -n] [-v] SOURCE ... DIRECTORY\n"
			+ "\tMove or rename files. Within one file system it renames; between file systems it copies, then\n"
			+ "\tremoves the source.\n"
			+ "\t-f   overwrite an existing file without asking\n"
			+ "\t-i   ask before overwriting an existing file\n"
			+ "\t-n   never overwrite an existing file\n"
			+ "\t-v   show each file as it is moved\n"
			+ "\tThe last of -f, -i and -n given wins.";

	public Mv() {
		super(name, help);
	}

	@Override
	public int process(ShellContext sc) throws IOException {
		int ret = 0;
		ShellArgument sa = parseArgs(sc, MvOptions.class);

		List<String> paths = sa.paths;
		List<MvOptions> options = new ArrayList<Mv.MvOptions>();
		
		//[-f | -i | -n] [-hv
		for(Object o : sa.options) {
			if (o instanceof MvOptions) {
				MvOptions op = (MvOptions) o;
				options.remove(op);
				options.add(op);
				switch (op) {
				case f:
					//The -f option overrides any previous -i or -n options.)
					options.remove(MvOptions.i);
					options.remove(MvOptions.n);
					break;
				case i:
					//(The -i option overrides any previous -f or -n options.)
					options.remove(MvOptions.f);
					options.remove(MvOptions.n);
					break;
				case n:
					//(The -n option overrides any previous -f or -i options.)
					options.remove(MvOptions.f);
					options.remove(MvOptions.i);
					break;
					
				default:
					// v and h are ok
					break;
				}
			}
		}
		
		List<FileSource> files = new ArrayList<>();

		for(String path : paths) {
			List<FileSource> kids = new ArrayList<>();

			// the shell expanded the patterns already: a * here is a name
			{
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

		List<Object> chosen = new ArrayList<>(options);
		FileSource dest = files.remove(files.size()-1);
		if(dest.isDirectory()) {
			for(FileSource file : files) {
				FileSource dest2= dest.getChild(file.getName());
				if( mv_or_copy(sc,file, dest2,chosen) != 0 ) {
					ret = 1;
				}
			}			
		} else {
			if(files.size()>1) {
				sc.stderr.println(name+": "+dest+" is not a directory");
				return 1;
			} 
			ret = mv_or_copy(sc,files.get(0),dest,chosen);
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
		int ret = 0;
		if( options.contains(MvOptions.n) && dest.exists()) {
			sc.stdout.println(dest.getAbsolutePath()+" not overwritten");
			return 1;
		}
		if( options.contains(MvOptions.i) && dest.exists()) {

			String str = promptAndGetReponse(sc, "overwrite "+dest+"? (y/n [n])");

			if( str == null || !str.trim().toLowerCase().startsWith("y")) {
				sc.stdout.println(dest+" not overwritten.");
				return 1;
			} 
		}


		boolean renamed = source.renameTo(us.bringardner.fsh.TreeFile.unwrap(dest));
		if( !renamed ) {
			// (copy lists each file it copies with -v, the source itself last)
			copy(sc,source,dest,options);
			// copy worked 
			ret = deleteIfExists(source);
		}

		if(ret == 0 && renamed && options.contains(MvOptions.v)) {
			sc.stdout.println(source.getAbsolutePath()+" -> "+dest);
		}

		return ret;
	}

	public  void copy(ShellContext sc,FileSource from, FileSource to,List<Object> options) throws IOException {
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
					copyStream(sc,in,out);		
				}
			}			
		}
		if( options.contains(MvOptions.v)) {
			sc.stdout.println(from.getAbsolutePath()+" -> "+to);
		}
	}



}
