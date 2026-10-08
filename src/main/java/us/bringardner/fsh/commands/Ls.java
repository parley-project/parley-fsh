package us.bringardner.fsh.commands;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Ls extends ShellCommand {
	private enum LsArgument {a,C,d,g,G,h,l,L,Q,r,R,S,t,u,x,X,ONE;
		// -1 (an enum name can't be a digit); parseArgs uses find when there is one
		static LsArgument find(String name) {
			return name.equals("1") ? ONE : valueOf(name);
		}
	};


	static String name = "ls";
	static String help = "ls [-1aCdgGhlLQrRStuxX].. [--] [path].."
			+ "List information about the FILEs (the current directory by default).  Sort entries alphabetically if none of -ctuSUX is specified.\n"
			+ "\n"
			+ "       -1	list one entry per line\n"
			+ "       -a	do not ignore entries starting with .\n"
			+ "       -C	list entries by columns\n"
			+ "       -d	list directories themselves, not their contents\n"
			+ "       -g	like -l, but do not list owner\n"
			+ "       -G	in a long listing, don't print group names\n"
			+ "       -h	with -l, print sizes like 1K 234M 2G etc.\n"
			+ "       -l	use a long listing format\n"
			+ "       -L	folow symbolic link (show link instead)\n"
			+ "       -Q	enclose entry names in double quotes"
			+ "       -r	reverse order while sorting\n"
			+ "       -R	list subdirectories recursively\n"
			+ "       -S	sort by file size, largest first\n"
			+ "       -t	sort by time, newest first\n"
			+ "       -x	list entries by lines instead of by columns\n"
			+ "       -X	sort alphabetically by entry extension\n"
			+ "       --	the arguments after this are paths, even if they start with -\n"
			;



	static class LsContext {
		public LsContext(ShellContext ctx2) {
			ctx = ctx2;
		}
		ShellContext ctx;
		StringBuilder output = new StringBuilder();
	}



	public Ls() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		ShellArgument lsArgs = parseArgs(ctx, LsArgument.class);
		List<LsArgument> options = new ArrayList<Ls.LsArgument>();
		for(Object obj:lsArgs.options) {
			if (obj instanceof LsArgument) {
				options.add((LsArgument) obj);				
			}
		}

		List<String> paths = lsArgs.paths;		
		List<String> output = new ArrayList<>();
		if(paths.size() == 0) {
			FileSource cwd = ctx.console.getCurrentDirectory();
			if(options.contains(LsArgument.d)) {
				print(ctx,output, options, cwd);
			} else {
				FileSource [] kids = cwd.listFiles();
				sort(options, kids);
				for(FileSource file :kids) {
					if( !isHidden(file) || options.contains(LsArgument.a)) {
						print(ctx,output, options, file);
					}
				}
			}
		} else if( options.contains(LsArgument.R) && !options.contains(LsArgument.d)) {

			List<FileSource> list = new ArrayList<FileSource>();

			for(String arg : paths) {
				arg = arg.trim();
				if( !arg.isEmpty()) {
					list.addAll(getFiles(ctx, arg));				
				}
			}

			if( list.size()>0) {
				listRecursive(ctx,output, options, list.toArray(new FileSource[list.size()]));				
			}
		} else {
			return listOperands(ctx, options, paths);
		}
		if( !output.isEmpty()) {
			// Column 
			if(ctx.console.isInteractive && !options.contains(LsArgument.l) && !options.contains(LsArgument.g)
					&& !options.contains(LsArgument.ONE))  {
				if( options.contains(LsArgument.R)) {
					formatRecursive(ctx,output);
				} else {
					ctx.stdout.println(super.toColumns(ctx,output).trim());
				}
			} else {
				for(String line : output) {
					ctx.stdout.println(line);
				}
			}
		}

		return ret;
	}


	/**
	 * ls with paths, as bash does it: the files first (named as given), then each directory's
	 * contents under a "name:" heading. With a single path there is no heading. A missing path
	 * is reported and the others are still listed.
	 */
	private int listOperands(ShellContext ctx, List<LsArgument> options, List<String> paths) throws IOException {
		int ret = 0;
		int operands = 0;
		Map<FileSource,String> labels = new IdentityHashMap<>();
		List<FileSource> files = new ArrayList<>();
		List<FileSource> dirs = new ArrayList<>();
		for(String arg : paths) {
			arg = arg.trim();
			if( arg.isEmpty()) {
				continue;
			}
			// only * ? [ make a glob; a leading ~ is the home directory (getFiles treats ~ as a
			// glob and returned the contents of ~/dir)
			List<FileSource> found;
			if( isGlob(arg)) {
				found = getFiles(ctx, arg);
			} else {
				arg = expandTilde(ctx, arg);
				found = new ArrayList<>();
				found.add(ctx.console.createFileSource(arg));
			}
			if( found.isEmpty()) {
				operands++;
				ctx.stderr.println("ls: "+arg+": no such file or directory");
				ret = 1;
			}
			for(FileSource file : found) {
				operands++;
				String label = isGlob(arg) ? globLabel(ctx, arg, file) : arg;
				if( !file.exists()) {
					ctx.stderr.println("ls: "+label+": no such file or directory");
					ret = 1;
				} else {
					labels.put(file, label);
					if( file.isDirectory() && !options.contains(LsArgument.d)) {
						dirs.add(file);
					} else {
						files.add(file);
					}
				}
			}
		}

		boolean columns = useColumns(ctx, options);
		boolean first = true;
		if( !files.isEmpty()) {
			FileSource[] list = files.toArray(new FileSource[files.size()]);
			sortOperands(options, list, labels);
			List<String> out = new ArrayList<>();
			for(FileSource file : list) {
				print(ctx, out, options, file, labels.get(file));
			}
			printBlock(ctx, null, out, columns);
			first = false;
		}
		FileSource[] dirList = dirs.toArray(new FileSource[dirs.size()]);
		sortOperands(options, dirList, labels);
		for(FileSource dir : dirList) {
			if( !first ) {
				ctx.stdout.println();
			}
			FileSource[] kids = dir.listFiles();
			List<String> out = new ArrayList<>();
			if( kids != null ) {
				sort(options, kids);
				for(FileSource kid : kids) {
					print(ctx, out, options, kid, null);
				}
			}
			printBlock(ctx, operands > 1 ? labels.get(dir) : null, out, columns);
			first = false;
		}
		return ret;
	}

	/**
	 * How to show a file a glob found: the directory part of the pattern (if it has no
	 * wildcard) and the file's name; otherwise the path relative to the current directory
	 * for a relative pattern, or the full path.
	 */
	private static String globLabel(ShellContext ctx, String pattern, FileSource file) throws IOException {
		int slash = Math.max(pattern.lastIndexOf('/'), pattern.lastIndexOf('\\'));
		String dir = slash >= 0 ? pattern.substring(0, slash+1) : "";
		if( !isGlob(dir)) {
			return expandTilde(ctx, dir)+file.getName();
		}
		String path = file.getAbsolutePath();
		if( isRelative(pattern)) {
			String cwd = ctx.console.getCurrentDirectory().getAbsolutePath();
			char sep = file.getFileSourceFactory().getSeperatorChar();
			if( path.startsWith(cwd+sep)) {
				return path.substring(cwd.length()+1);
			}
		}
		return path;
	}

	private static boolean isGlob(String arg) {
		return arg.indexOf('*') >= 0 || arg.indexOf('?') >= 0 || arg.indexOf('[') >= 0;
	}

	private boolean useColumns(ShellContext ctx, List<LsArgument> options) {
		return ctx.console.isInteractive && !options.contains(LsArgument.l) && !options.contains(LsArgument.g)
				&& !options.contains(LsArgument.ONE);
	}

	private void printBlock(ShellContext ctx, String heading, List<String> lines, boolean columns) {
		if( heading != null ) {
			ctx.stdout.println(heading+":");
		}
		if( lines.isEmpty()) {
			return;
		}
		if( columns ) {
			ctx.stdout.println(toColumns(ctx, lines).trim());
		} else {
			for(String line : lines) {
				ctx.stdout.println(line);
			}
		}
	}

	private void formatRecursive(ShellContext ctx, List<String> output) {
		StringBuilder buf = new StringBuilder();
		List<String> tmp = new ArrayList<String>();
		for(String line : output) {
			if( line.endsWith(":")) {
				//  new folder
				String col = toColumns(ctx, tmp);
				buf.append(col);
				buf.append("\n\n");
				buf.append(line);
				buf.append("\n");
				tmp.clear();
			} else {
				tmp.add(line);
			}
		}
		if( !tmp.isEmpty()) {
			String col = toColumns(ctx, tmp);
			buf.append(col);			
		}
		ctx.stdout.println(buf.toString());
	}

	private void list(ShellContext ctx,List<String> output, List<LsArgument> options, FileSource[] files) throws IOException {
		sort( options, files);
		for(FileSource file : files) {
			print(ctx,output, options, file);			 
		}

	}

	private void listRecursive(ShellContext ctx,List<String> output,List<LsArgument> options, FileSource [] files1) throws IOException {
		sort( options, files1);
		for(FileSource file : files1) {
			print(ctx,output, options, file);	
		}
		for(FileSource file2 : files1) {
			if( file2.isDirectory()) {
				output.add("");			
				output.add(file2.getAbsolutePath()+":");
				FileSource [] kids = file2.listFiles();
				if( kids !=null && kids.length>0) {
					listRecursive(ctx,output, options, kids);
				}
			}
		}

	}

	/**
	 * Sort entries alphabetically if none of -ctuvSUX
	 * @param options
	 * @return
	 */
	private Comparator< FileSource> getComparator(List<LsArgument> options) {
		Comparator<FileSource> ret = null;
		for(LsArgument arg : options) {
			switch (arg) {
			case t:
				ret = (o1,o2)->{
					Date d1 = new Date(lastModified(o1));
					Date d2 = new Date(lastModified(o2));
					int val = d2.compareTo(d1);
					return val;
				};
				return ret;

			case u:
				ret = (o1,o2)->{
					Date d1 = new Date(lastAccess(o1));
					Date d2 = new Date(lastAccess(o2));
					int val = d2.compareTo(d1);
					return val;
				};

				return ret;

			case S:
				ret = (o1,o2)->{return (int)(length(o1)-length(o2));};
				return ret;

			case X:
				ret = (o1,o2)->{
					String s1 = getExtention(o1);
					String s2 = getExtention(o2);
					int val = s1.compareTo(s2);
					return val;
				};
				return ret;
			default:
				break;
			}
		}
		ret = (o1,o2)->{return o1.getName().compareTo(o2.getName());};

		return ret;
	}

	private String getExtention(FileSource file) {
		String ret = "";
		String name = file.getName();
		int idx = name.lastIndexOf('.');
		if( idx > 0 ) {
			ret = name.substring(idx+1);
		}
		return ret;
	}

	private long length(FileSource o1) {
		long ret = 0;
		try {
			ret = o1.length();
		} catch (IOException e) {
		}
		return ret;
	}

	private long lastAccess(FileSource o1) {
		long ret = 0;
		try {
			ret = o1.lastAccessTime();
		} catch (IOException e) {
		}
		return ret;
	}

	private long lastModified(FileSource o1) {
		long ret = 0;
		try {
			ret = o1.lastModified();
		} catch (IOException e) {
		}
		return ret;
	}

	/** operands by name are in the order of what was given (lib/glob/x before lib/sh/a), as ls sorts them */
	private void sortOperands(List<LsArgument> args, FileSource [] files, Map<FileSource,String> labels) {
		if( args.contains(LsArgument.t) || args.contains(LsArgument.S) || args.contains(LsArgument.X)) {
			sort(args, files);
			return;
		}
		java.util.Comparator<FileSource> byName = java.util.Comparator.comparing(f -> labels.getOrDefault(f, f.getName()));
		Arrays.sort(files, args.contains(LsArgument.r) ? byName.reversed() : byName);
	}

	private void sort(List<LsArgument> args, FileSource [] files) {
		Arrays.sort(files, getComparator(args));
		if( args.contains(LsArgument.r)) {
			FileSource [] tmp = new FileSource[files.length];
			for (int i = 0,i2=files.length-1; i < files.length ; i++, i2--) {
				tmp[i2] = files[i];
			}
			for (int idx = 0; idx < tmp.length; idx++) {
				files[idx] = tmp[idx];
			}
		}
	}

	private boolean isHidden(FileSource f) throws IOException {

		return f.isHidden() || f.getName().startsWith(".");
	}
	//      
	//prmStr linkStr   usrStr  crpStr    sizeStr  |dateStr     | nameStr
	//-rw-rw-r--    1     ec2-user ec2-user    2186     Feb  2 08:41 build.txt
	private  void print(ShellContext ctx, List<String> out,List<LsArgument> args, FileSource file) throws IOException {
		print(ctx, out, args, file, null);
	}

	/**
	 * @param label the name to show (a path as the user gave it), or null for the file's name
	 */
	private  void print(ShellContext ctx, List<String> out,List<LsArgument> args, FileSource file, String label) throws IOException {
		if( !file.exists()) {
			throw new IOException("ls: "+file+": no such file or directory");
			
		}
		if( args.contains(LsArgument.L)) {
			FileSource link = file.getLinkedTo();
			if( link !=null ) {
				file = link;
			}
		}
		// a hidden file is shown when it is named
		if(label == null && isHidden(file) && !args.contains(LsArgument.a)) {
			return;
		}
		if(!args.contains(LsArgument.l) && !args.contains(LsArgument.g)) {
			out.add(label != null ? label : file.getName());
		} else {
			String permStr = (file.isDirectory()?"d":"-")+formatPermission(file);
			String linkStr = formatLink(args,file);
			String userStr = formatUser(args,file);
			String groupStr = formatGroup(args,file);
			String sizeStr = formatSize(args,file);
			String timeStr = formatTime(args,file);
			String nameStr = label == null ? formatName(args,file) : args.contains(LsArgument.Q) ? "\""+label+"\"" : label;


			out.add(String.format("%s %s %s  %s %s  %s %s",permStr,linkStr,
					userStr,groupStr,sizeStr,timeStr,nameStr));

		}		

	}

	private String formatName(List<LsArgument> args, FileSource file) {
		String ret = file.getName();
		if( args.contains(LsArgument.Q)) {
			// (this quoted the command's name, so every entry showed as "ls")
			ret = "\""+ret+"\"";
		}
		return ret;
	}

	static long K = 1024;
	static long M = K*K;
	static long G = M*M;

	private String formatSize(List<LsArgument> args, FileSource file) throws IOException {
		long len = file.length();

		if(args.contains(LsArgument.h)) {
			long val = len;
			String post = "";

			if( val > G) {
				val = val / G;
				post = "GB";
			} else if( val > M) {
				val = val / M;
				post = "MB";
			} else if( val > G) {
				val = val / K;
				post = "KB";
			}

			return String.format("% 5d", val)+post;	
		} else {
			return String.format("% 5d", len);
		}

	}

	private String formatGroup(List<LsArgument> args, FileSource file) throws IOException {
		if(args.contains(LsArgument.G) ) {				
			return "";
		} 

		return file.getGroup().getName();
	}

	private String formatUser(List<LsArgument> args, FileSource file) throws IOException {
		if(args.contains(LsArgument.g) ) {				
			return "";
		} 


		return file.getOwner().getName();
	}

	private String formatLink(List<LsArgument> args, FileSource file) throws IOException {
		int cnt = 1;
		FileSource link = file.getLinkedTo();
		while(link !=null ) {
			cnt++;
			link = link.getLinkedTo();
		}

		return ""+cnt;
	}

	public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd yyyy");

	private String formatTime(List<LsArgument> args, FileSource file) throws IOException {
		long time = 0;
		if(args.contains(LsArgument.u) ) {
			time = file.lastAccessTime();
		} else {
			time = file.lastModified();
		}

		return DATE_FORMAT.format(new Date(time));
	}

	private String formatPermission(FileSource file) throws IOException {
		char[] perm = "---------".toCharArray();
		FileSource jdbc = file;
		if( jdbc.canOwnerRead() ) {
			perm[0] = 'r';
		}
		if( jdbc.canOwnerWrite() ) {
			perm[1] = 'w';
		}

		if( jdbc.canOwnerExecute() ) {
			perm[2] = 'x';
		}

		if( jdbc.canGroupRead() ) {
			perm[3] = 'r';
		}

		if( jdbc.canGroupWrite() ) {
			perm[4] = 'w';
		}

		if( jdbc.canGroupExecute() ) {
			perm[5] = 'x';
		}

		if( jdbc.canGroupRead() ) {
			perm[6] = 'r';
		}

		if( jdbc.canOtherWrite() ) {
			perm[7] = 'w';
		}

		if( jdbc.canGroupExecute() ) {
			perm[8] = 'x';
		}


		String str = new String(perm);

		return str;
	}

}
