package us.bringardner.shell.commands;

import java.io.IOException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Touch extends ShellCommand{

	private enum Arguments {A, a, c, h, m,r,t,d};

	static String name = "touch";
	static String help = ""
			+ "The touch utility sets the modification and access times of files.\n"
			+ "If any file does not exist, it is created with default permissions.\n"
			+ "\n"
			+ "Usage: touch [-A [-][[hh]mm]SS] [-achm] [-r file] [-t [[CC]YY]MMDDhhmm[.SS]] [-d YYYY-MM-DDThh:mm:SS[.frac][tz]] file ..."
			+ "\tsee 'man touch' for more information";

	private static interface ArgumentProcessor {
		/**
		 * Process the arguments for a specific option
		 * 
		 * @param argument 
		 * @param ctx
		 * @param idx the current index into ctx.args
		 * @return the index into ctx.args reflecting any changes made by the processor
		 * @throws IOException 
		 */
		int process(TouchArguments argument,ShellContext ctx,int idx) throws IOException ;
	}

	private static class Action_A implements ArgumentProcessor {

		@Override
		public int process(TouchArguments ret, ShellContext ctx, int idx) throws IOException {
			//[-A [-][[hh]mm]SS]

			boolean negate = false;
			String val = ctx.args[++idx];
			if( val.length()==7) {
				String tmp = val.substring(0,1);
				val = val.substring(1);
				if( !tmp.equals("-")) {
					throw new IOException("Invalid char = "+tmp);
				}
				negate = true;
			}
			int hh = 0, mm = 0;
			if( val.length()==6) {
				String tmp = val.substring(0,2);
				val = val.substring(2);
				hh = Integer.parseInt(tmp);
			}
			if( val.length()==4) {
				String tmp = val.substring(0,2);
				val = val.substring(2);
				mm = Integer.parseInt(tmp);
			}
			int SS = Integer.parseInt(val);
			ret.parameters.put(Arguments.A, Arrays.asList(negate,hh,mm,SS));

			return idx;
		}

	}


	private static class Action_r implements ArgumentProcessor {

		@Override
		public int process(TouchArguments ret, ShellContext ctx, int idx) throws IOException {
			//[-r file]
			//Use the access and modifications times from the specified file instead of the current time of day.

			String path = ctx.args[++idx];
			List<FileSource> file = getFiles(ctx, path);
			ret.parameters.put(Arguments.r, Arrays.asList(file));
			return idx;
		}

	}

	public static class Action_t implements ArgumentProcessor {
		//[-t [[CC]YY]MMDDhhmm[.SS]]
		String dateFormats [] = {
				"yyyyMMddhhmm.ss" 
				, "yyMMddhhmm.ss" 
				, "MMddhhmm.ss"
				, "yyyyMMddhhmm" 
				, "yyMMddhhmm" 
				, "MMddhhmm"
		}
		;

		@Override
		public int process(TouchArguments ret, ShellContext ctx, int idx) throws IOException {
			String val = ctx.args[++idx];
			SimpleDateFormat sdf = null;
			for(String fmt : dateFormats) {
				if( val.length()==fmt.length()) {
					sdf = new SimpleDateFormat(fmt);
					break;
				}
			}

			if( sdf == null ) {
				throw new IOException("Invalid date format "+val);
			}

			try {
				Date date = sdf.parse(val);
				ret.parameters.put(Arguments.t, Arrays.asList(date.getTime()));
			} catch (ParseException e) {
				throw new IOException("Invalid date format = "+val);
			}


			return idx;
		}

	}

	public static class Action_d implements ArgumentProcessor {
		//     idx +1  +2  +3 +4+5 +6 +7
		//[-d YYYY-MM-DDThh : mm : SS[tz]]
		
		@Override
		public int process(TouchArguments ret, ShellContext ctx, int idx) throws IOException {
			//  the date parses as a series of signed numbers
			//   Z ( time zone)?? parses as path
			// idx should be 2
			StringBuilder tmp = new StringBuilder();
			int idx2 = idx+1;
			for(;idx2<=4; idx2++) {
				tmp.append(ctx.args[idx2]);
			}
			tmp.append(' ');
			for(;idx2<ctx.args.length-1; idx2++) {
				tmp.append(ctx.args[idx2]);
			}
			
			String tm2 = tmp.toString();
			
			String val = tm2.replace("T","");
			
			boolean gmt = false;
			if( val.endsWith("Z")) {
				gmt = true;
				val = val.substring(0,val.length()-1);
			}
			
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			
			try {
				if( gmt ) {
					sdf.setTimeZone(TimeZone.getTimeZone("GMT"));	
				}
				Date date = sdf.parse(val);
				ret.parameters.put(Arguments.d, Arrays.asList(date.getTime()));
			} catch (ParseException e) {
				throw new IOException("Invalid date format "+e);
			}

			return idx2-1;
		}

	}



	private static class TouchArguments {		
		List<Object> options = new ArrayList<>();
		List<String> paths  = new ArrayList<>();
		Map<Object,List<Object>> parameters = new HashMap<>();
	}

	static Map<Object,ArgumentProcessor> actions = new HashMap<>();
	static {
		actions.put(Arguments.A, new Action_A());
		actions.put(Arguments.r, new Action_r());
		actions.put(Arguments.t, new Action_t());
		actions.put(Arguments.d, new Action_d());
	}

	public Touch() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;


		TouchArguments targs = parserArguments(ctx, Arguments.class, actions);

		List<Arguments> options = new ArrayList<>();
		for(Object o : targs.options) {
			if (o instanceof Arguments) {
				options.add((Arguments) o);				
			}
		}

		List<String> paths = targs.paths;
		if( paths.size() ==0) {
			ctx.stdout.println(help);
			return -1;
		}

		for(String path : paths) {
			List<FileSource> files = getFiles(ctx, path);
			for(FileSource file : files) {
				touch(file,targs);
			}
		}


		return ret;
	}

	@SuppressWarnings("unchecked")
	private void touch(FileSource file, TouchArguments targs) throws IOException {
		if( !file.exists()) {
			if( targs.options.contains(Arguments.c) || targs.options.contains(Arguments.A) || targs.options.contains(Arguments.h)) {
				//  don't create file
				return;
			}
			file.createNewFile();
			if( !file.exists()) {
				throw new IOException("Could not create file "+file);
			}
		}

		if( targs.options.contains(Arguments.r)) {
			List<Object> list = targs.parameters.get(Arguments.r);
			if( list == null ) {
				throw new IOException("No parameter list");
			}

			if( list.size() !=1 ) {
				throw new IOException("One and only one file expected. Got "+list.size());
			}
			List<FileSource> fsl = (List<FileSource>) list.get(0);
			if( fsl.size() !=1 ) {
				throw new IOException("One and only one file expected. Got "+fsl.size());
			}
			FileSource fs = fsl.get(0);

			if( targs.options.contains(Arguments.a) || !targs.options.contains(Arguments.m)) {
				file.setLastAccessTime(fs.lastAccessTime());
			}
			
			if( targs.options.contains(Arguments.m) || !targs.options.contains(Arguments.a)) {
				file.setLastModifiedTime(fs.lastModified());
			}


		} else if( targs.options.contains(Arguments.A)) {
			// adjust by amount
			List<Object> values = targs.parameters.get(Arguments.A);
			if( values == null || values.size()!= 4) {
				throw new IOException("Invalid args for -A="+values);
			}
			boolean negate = (boolean) values.get(0);
			//negate,hh,mm,SS
			int hh = (int) values.get(1);
			int mm = (int) values.get(2);
			int ss = (int) values.get(3);
			
			if( targs.options.contains(Arguments.a) || !targs.options.contains(Arguments.m)) {
				long ad = adjustTimeForA(file.lastAccessTime(),negate,hh,mm,ss);				
				file.setLastAccessTime(ad);
			}
			
			if( targs.options.contains(Arguments.m) || !targs.options.contains(Arguments.a)) {
				long md = adjustTimeForA(file.lastModified(),negate,hh,mm,ss);
				file.setLastModifiedTime(md);
			}
			

 		} else if( targs.options.contains(Arguments.d)) {
			// adjust by amount
			List<Object> values = targs.parameters.get(Arguments.d);
			if( values == null || values.size()!= 1) {
				throw new IOException("Invalid args for -d="+values);
			}
			long time  = (long) values.get(0);
			if( targs.options.contains(Arguments.a) || !targs.options.contains(Arguments.m)) {
				file.setLastAccessTime(time);
			}
			
			if( targs.options.contains(Arguments.m) || !targs.options.contains(Arguments.a)) {
				file.setLastModifiedTime(time);
			}
			

		} else if( targs.options.contains(Arguments.t)) {
			// adjust by amount
			List<Object> values = targs.parameters.get(Arguments.t);
			if( values == null || values.size()!= 1) {
				throw new IOException("Invalid args for -t="+values);
			}
			long time  = (long) values.get(0);
			if( targs.options.contains(Arguments.a) || !targs.options.contains(Arguments.m)) {
				file.setLastAccessTime(time);
			}
			
			if( targs.options.contains(Arguments.m) || !targs.options.contains(Arguments.a)) {
				file.setLastModifiedTime(time);
			}
			

		}




	}

	private long adjustTimeForA(long originalTime, boolean negate, int hh, int mm, int ss) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(originalTime);
		if( hh !=0 ) {
			cal.add(Calendar.HOUR, negate?-hh:hh);
		}
		if( mm !=0 ) {
			cal.add(Calendar.MINUTE, negate?-mm:mm);
		}
		if( ss !=0 ) {
			cal.add(Calendar.SECOND, negate?-ss:ss);
		}

		return cal.getTimeInMillis();
	}

	protected TouchArguments parserArguments(ShellContext ctx,Class<?> cls,Map<Object,ArgumentProcessor> actions) throws IOException {
		TouchArguments ret = new TouchArguments();
		if( ctx.args.length>1) {
			try {
				Method m = cls.getDeclaredMethod("valueOf", String.class);
				m.setAccessible(true);
				for(int idx=1; idx < ctx.args.length; idx++ ) {
					String arg = ctx.args[idx].trim();
					if( arg.startsWith("-")) {
						arg = arg.substring(1);
						for(char c : arg.toCharArray()) {
							try {
								Object a = m.invoke(null, ""+c);
								ret.options.add(a);
								ArgumentProcessor action = actions.get(a);
								if( action != null ) {
									idx = action.process(ret, ctx, idx);
								}
							} catch (Exception e) {
								throw new IOException("Unknown argument "+c);
							}
						}
					} else {
						ret.paths.add(arg);
					}
				}
			} catch (NoSuchMethodException | SecurityException e) {
				throw new IOException(e);
			}
		}
		return ret;
	}

	protected TouchArguments parserArguments1(ShellContext ctx,Class<?> cls) throws IOException {
		TouchArguments ret = new TouchArguments();
		if( ctx.args.length>0) {
			try {
				Method m = cls.getDeclaredMethod("valueOf", String.class);
				m.setAccessible(true);
				for(int idx=1; idx < ctx.args.length; idx++ ) {
					String arg = ctx.args[idx].trim();
					if( arg.startsWith("-")) {
						arg = arg.substring(1);
						for(char c : arg.toCharArray()) {
							try {
								Object a = m.invoke(null, ""+c);
								ret.options.add(a);
								if( a.equals(Arguments.A)) {
									//[-A [-][[hh]mm]SS]
									List<Object> p = new ArrayList<>();
									String val = ctx.args[idx++];
									p.add(val);
									if( val.equals("-")) {
										p.add(ctx.args[idx++]);
									}
									ret.parameters.put(arg, p);
								} else if( a.equals(Arguments.r)) {
									//[-r file]
									ret.parameters.put(arg, Arrays.asList(ctx.args[idx++]));
								} else if( a.equals(Arguments.t)) {
									//[-t [[CC]YY]MMDDhhmm[.SS]]
									ret.parameters.put(arg, Arrays.asList(ctx.args[idx++]));
								} else if( a.equals(Arguments.d)) {
									//[-d YYYY-MM-DDThh:mm:SS[.frac][tz]]
									List<Object> p = new ArrayList<>();
									String val = ctx.args[idx++];
									p.add(val);
									if( val.equals("Z")) {
										p.add(ctx.args[idx++]);
									}
									ret.parameters.put(arg, p);
								} 
							} catch (Exception e) {
								throw new IOException("Unknown argument "+c);
							}
						}
					} else {
						ret.paths.add(arg);
					}
				}
			} catch (NoSuchMethodException | SecurityException e) {
				throw new IOException(e);
			}
		}
		return ret;
	}

}
