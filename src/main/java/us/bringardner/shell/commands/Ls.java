package us.bringardner.shell.commands;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Ls extends ShellCommand {
	private enum LsArgument {a,C,d,g,G,h,l,L,Q,r,R,S,t,u,x,X};


	static String name = "ls";
	static String help = "ls [a,C,d,g,G,h,l,L,Q,r,R,S,t,u,x,X].. [path].."
			+ "List information about the FILEs (the current directory by default).  Sort entries alphabetically if none of -ctuSUX is specified.\n"
			+ "\n"
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
				print(output, options, cwd);
			} else {
				FileSource [] kids = cwd.listFiles();
				sort(options, kids);
				for(FileSource file :kids) {
					if( !isHidden(file) || options.contains(LsArgument.a)) {
						print(output, options, file);
					}
				}
			}
		} else {

			List<FileSource> list = new ArrayList<FileSource>();

			for(String arg : paths) {
				arg = arg.trim();
				if( !arg.isEmpty()) {
					list.addAll(getFiles(ctx, arg));				
				}
			}

			if( list.size()>0) {
				FileSource[] files= list.toArray(new FileSource[list.size()]);
				if( !options.contains(LsArgument.d) && options.contains(LsArgument.R) ) {
					listRecursive(ctx,output, options, files);				
				} else {
					list(output, options, files);					
				}
			}
		}
		if( !output.isEmpty()) {
			// Column 
			if(ctx.console.isInteractive && !options.contains(LsArgument.l) && !options.contains(LsArgument.g))  {
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

	private void list(List<String> output, List<LsArgument> options, FileSource[] files) throws IOException {
		sort( options, files);
		for(FileSource file : files) {
			print(output, options, file);			 
		}

	}

	private void listRecursive(ShellContext ctx,List<String> output,List<LsArgument> options, FileSource [] files1) throws IOException {
		sort( options, files1);
		for(FileSource file : files1) {
			print(output, options, file);	
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

	private void sort(List<LsArgument> args, FileSource [] files) {
		Arrays.sort(files, getComparator(args));
		if( args.contains(LsArgument.r)) {
			for (int i = 0; i < files.length / 2; i++) {
				FileSource temp = files[i];
				files[i] = files[files.length - 1 - i];
				files[files.length - 1 - i] = temp;
			}
		}
	}

	private boolean isHidden(FileSource f) throws IOException {

		return f.isHidden() || f.getName().startsWith(".");
	}
	//      
	//prmStr linkStr   usrStr  crpStr    sizeStr  |dateStr     | nameStr
	//-rw-rw-r--    1     ec2-user ec2-user    2186     Feb  2 08:41 build.txt
	private  void print(List<String> out,List<LsArgument> args, FileSource file) throws IOException {

		if( args.contains(LsArgument.L)) {
			FileSource link = file.getLinkedTo();
			if( link !=null ) {
				file = link;
			}
		}
		if(isHidden(file) && !args.contains(LsArgument.a)) {
			return;
		}
		if(!args.contains(LsArgument.l) && !args.contains(LsArgument.g)) {
			out.add(file.getName());
		} else {
			String permStr = (file.isDirectory()?"d":"-")+formatPermission(file);
			String linkStr = formatLink(args,file);
			String userStr = formatUser(args,file);
			String groupStr = formatGroup(args,file);
			String sizeStr = formatSize(args,file);
			String timeStr = formatTime(args,file);
			String nameStr = formatName(args,file);


			out.add(String.format("%s %s %s  %s %s  %s %s",permStr,linkStr,
					userStr,groupStr,sizeStr,timeStr,nameStr));

		}		

	}

	private String formatName(List<LsArgument> args, FileSource file) {
		String ret = file.getName();
		if( args.contains(LsArgument.Q)) {
			ret = "\""+name+"\"";
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
