package us.bringardner.shell.commands;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Ls extends ShellCommand {
	private enum Argument {a,A,b,B,c,C,d,F,g,G,h,H,I,l,L,o,Q,r,R,S,t,u,U,x,X};


	static String name = "ls";
	static String help = "List file from a directory";



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
		List<Argument> largs = new ArrayList<>();
		List<String> paths = new ArrayList<>();

		for(int idx=0; idx < args.length; idx++ ) {
			String arg = args[idx].getValue(ctx).toString();
			if( arg.startsWith("-")) {
				arg = arg.substring(1);
				for(char c : arg.toCharArray()) {
					try {
						Argument a =Argument.valueOf(""+c);
						largs.add(a);
					} catch (Exception e) {
						throw new IOException("Unknown argument "+c);
					}
				}
			} else {
				paths.add(arg);
			}
		}

		if( !largs.contains(Argument.t)) {
			// these only effect sort by time so remove them to avoid confusion
			largs.remove(Argument.c);
			largs.remove(Argument.c);
		}

		List<String> out = new ArrayList<>();
		LsContext lsctx = new LsContext(ctx);
		if(paths.size() == 0) {
			List<FileSource> files = Arrays.asList(ctx.console.getCurrentDirectory().listFiles());
			sort(ctx,largs,files);
			for(FileSource file : files) { 
				print(out,lsctx,largs, file);
			}
		} else {
			for(String arg : paths) {
				arg = arg.trim();
				if( !arg.isEmpty()) {
					List<FileSource> files = getFiles(ctx, arg);
					sort(ctx,largs,files);
					for(FileSource file : files) {
						if( file.exists()) {
							list(out,lsctx,largs, file);
						} else {
							ctx.stderr.println("ls: "+file+" no such file or directory");
							return 1;
						}
					}
				}

			}
		}
		if( !out.isEmpty()) {
			// Column 
			if(ctx.console.isInteractive && !largs.contains(Argument.l) && !largs.contains(Argument.g))  {
				ctx.stdout.println(super.toColumns(ctx,out).trim());
			} else {
				for(String line : out) {
					ctx.stdout.println(line);
				}
			}
		}

		return ret;
	}


	/**
	 * Sort entries alphabetically if none of -ctuvSUX
	 * @param args
	 * @return
	 */
	private Comparator< FileSource> getComparator(List<Argument> args) {
		Comparator<FileSource> ret = null;
		for(Argument arg : args) {
			switch (arg) {
			case c:
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

	private void sort(ShellContext ctx, List<Argument> args, List<FileSource> files) {
		if(args.contains(Argument.U)) {
			// not sort
			return;
		}


		Collections.sort(files, getComparator(args));
		if( args.contains(Argument.r)) {
			Collections.reverse(files);
		}

	}

	private void list(List<String> out,LsContext ctx, List<Argument> args, FileSource dir) throws IOException {
		if( dir.isDirectory() && !args.contains(Argument.d)) {
			FileSource[] kids = dir.listFiles();
			if( kids != null ) {
				boolean recursive = args.contains(Argument.R);
				boolean showAll = args.contains(Argument.a);
				for(FileSource f : kids) {
					if(!isHidden(f) || showAll) {
						print(out,ctx,args,f);
						if( recursive && f.isDirectory()) {
							list(out,ctx, args, f);
						}
					}
				}
			}
		} else {
			print(out,ctx,args,dir);
		}
	}

	private boolean isHidden(FileSource f) throws IOException {

		return f.isHidden() || f.getName().startsWith(".");
	}
	//      
	//prmStr linkStr   usrStr  crpStr    sizeStr  |dateStr     | nameStr
	//-rw-rw-r--    1     ec2-user ec2-user    2186     Feb  2 08:41 build.txt
	private  void print(List<String> out,LsContext ctx,List<Argument> args, FileSource file) throws IOException {
		if( args.contains(Argument.L)) {
			FileSource link = file.getLinkedTo();
			if( link !=null ) {
				file = link;
			}
		}
		if(isHidden(file) && !args.contains(Argument.a)) {
			return;
		}
		if(!args.contains(Argument.l) && !args.contains(Argument.g)) {
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

	private String formatName(List<Argument> args, FileSource file) {
		String ret = file.getName();
		if( args.contains(Argument.Q)) {
			ret = "\""+name+"\"";
		}
		return ret;
	}

	static long K = 1024;
	static long M = K*K;
	static long G = M*M;

	private String formatSize(List<Argument> args, FileSource file) throws IOException {
		long len = file.length();

		if(args.contains(Argument.h)) {
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

	private String formatGroup(List<Argument> args, FileSource file) throws IOException {
		if(args.contains(Argument.G) || args.contains(Argument.o)) {				
			return "";
		} 

		return file.getGroup().getName();
	}

	private String formatUser(List<Argument> args, FileSource file) throws IOException {
		if(args.contains(Argument.g) ) {				
			return "";
		} 


		return file.getOwner().getName();
	}

	private String formatLink(List<Argument> args, FileSource file) throws IOException {
		int cnt = 1;
		FileSource link = file.getLinkedTo();
		while(link !=null ) {
			cnt++;
			link = link.getLinkedTo();
		}

		return ""+cnt;
	}

	public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd yyyy");

	private String formatTime(List<Argument> args, FileSource file) throws IOException {
		long time = 0;
		if(args.contains(Argument.u) ) {
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
