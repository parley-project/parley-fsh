package us.bringardner.fsh.test.utilities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Random;
import java.util.Set;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;

public class ManageTestFiles {

	public static enum Permissions {
		UnDefined(' '),
		OWNER_READ('r'),
		OWNER_WRITE('w'),
		OWNER_EXECUTE('x'),

		GROUP_READ('r'),
		GROUP_WRITE('w'),
		GROUP_EXECUTE('x'),

		OTHERS_READ('r'),
		OTHERS_WRITE('w'),
		OTHERS_EXECUTE('x');

	    public final char label;

	    private Permissions(char label) {
	        this.label = label;
	    }
	    
	    public static Permissions find(String name) {
	    	Permissions ret = UnDefined;
	    	char id = name.charAt(0);
	    	for(Permissions p : values()) {
	    		if(id == p.label ) {
	    			return p;
	    		}
	    	}
	    	return ret;
	    }
	}
	
	public static void main(String[] args) throws IOException, ParseException {
		boolean capture = false;
		if( capture ) {
			captureAll();
		} else {
			restore();
		}

	}

	public static void restore() throws IOException, ParseException {
		for(String line : lines()) {
			restore(line);			
		}
	}

	/** TestFileData.txt's lines */
	private static String [] lines() throws IOException {
		try(	InputStream in = ManageTestFiles.class.getResourceAsStream("/TestFileData.txt")){
			return new String(in.readAllBytes()).split("\n");
		}
	}

	/**
	 * The test data's symbolic links (SymLink2X, a link to X beside it): each one is made again if it
	 * is missing or does not point at X. Cheap, so the tests do this every time they run.
	 */
	public static void restoreLinks() throws IOException {
		for(String line : lines()) {
			File file = new File(fileName(line.split("[,]")[0]));
			if( file.getName().startsWith("SymLink2")) {
				symbolicLink(file);
			}
		}
	}

	/** the path after the project directory */
	private static String fileName(String recorded) {
		// (/.../parley-fsh/TestFiles/... gives TestFiles/...; the data recorded before the folder was
		// renamed has /.../fsh/TestFiles/...)
		String project = recorded.contains("/parley-fsh/") ? "/parley-fsh/" : "/fsh/";
		int idx = recorded.indexOf(project);
		return recorded.substring(idx+project.length()).trim();
	}

	/** file, SymLink2X, as a link to X beside it, unless it already is one */
	private static void symbolicLink(File file) throws IOException {
		Path link = file.toPath();
		// relative (X, in the link's folder), so it still works when the project folder is renamed or moved
		Path target = Paths.get(file.getName().substring(8));
		if( Files.isSymbolicLink(link) && Files.readSymbolicLink(link).equals(target) && Files.exists(link)) {
			return;
		}
		// (Files, not file.exists(): that looks through the link, and a broken link is not deleted)
		Files.deleteIfExists(link);
		try {
			Files.createSymbolicLink(link, target);
		} catch (IOException | UnsupportedOperationException e) {
			if( FileSourceFactory.isWindows()) {
				throw new IOException("Can't create symbolic link to : "+file+" Windows MUST be in developer mode.\nSettings->System->Advanced : Toggle Developer mode", e);
			}
			throw new IOException("Can't create symbolic link to : "+file, e);
		}
		if( !Files.exists(link)) {
			throw new IOException("Can't create symbolic link to : "+file);
		}
	}

	public static void restore(String line) throws IOException, ParseException {
		//0 BjlFileSystemConsole/TouchTestFiles,
		//1 type=dir,
		//2 creationTime=03-14-2025 11:05:31,
		//3 lastAccessTime=03-14-2025 11:05:31,
		//4 lastModifiedTime=03-14-2025 11:05:31,
		//5 permissions=OWNER_READ:GROUP_READ:OWNER_WRITE:OTHERS_READ:OWNER_EXECUTE:GROUP_EXECUTE:OTHERS_EXECUTE:

		String []parts = line.split("[,]");
		String fileName = fileName(parts[0]);
		
		File file = new File(fileName);
		
		System.out.println("file="+file);
		if( file.getName().startsWith("HardLink2")) {
			if( file.exists()) {
				if( !file.delete()) {
					throw new IOException("Can't delete existing hard link test file: "+file);
				}
			}
			File dir = file.getParentFile();
			File target = new File(dir,file.getName().substring(9));
			
			Path path = Files.createLink(file.toPath(), target.toPath());
			if( path == null || !Files.exists(path)) {
				throw new IOException("Can't create hard link to : "+file);
			}
		} else if( file.getName().startsWith("Hard")) {
			if( file.exists()) {
				if( !file.delete()) {
					throw new IOException("Can't delete existing hard link test file: "+file);
				}
			}
			File dir = file.getParentFile();
			File target = new File(dir,file.getName().substring(4));
			
			Path path = Files.createLink(file.toPath(), target.toPath());
			if( path == null || !Files.exists(path)) {
				throw new IOException("Can't create hard link to : "+file);
			}			 	
		} else if( file.getName().startsWith("SymLink2")) {
			symbolicLink(file);
		} else {
			String str = parts[1].substring(parts[1].indexOf('=')+1);
			long create =  dateFmt.parse(str).getTime();

			str = parts[2].substring(parts[2].indexOf('=')+1);
			long lastAccess = dateFmt.parse(str).getTime();

			str = parts[3].substring(parts[3].indexOf('=')+1);
			long lastMod = dateFmt.parse(str).getTime();

			String tmp = parts[4].substring(parts[4].indexOf('=')+1);
			String perms [] = tmp.split(":");

			FileSource fs = FileSourceFactory.fileProxyFactory.createFileSource(file.getAbsolutePath());
			fs.setCreateTime(create);
			fs.setLastAccessTime(lastAccess);
			fs.setLastModifiedTime(lastMod);
			
			// exactly the permissions recorded, no more: setting them one at a time only ever added
			// (and OTHERS_READ and OTHERS_WRITE set OTHERS_EXECUTE), so the test files became
			// executable and git saw them as changed
			Set<PosixFilePermission> wanted = java.util.EnumSet.noneOf(PosixFilePermission.class);
			for(String p : perms) {
				p = p.trim();
				if( !p.isEmpty()) {
					wanted.add(PosixFilePermission.valueOf(p));
				}
			}
			if( Files.getFileAttributeView(file.toPath(), PosixFileAttributeView.class) != null ) {
				Files.setPosixFilePermissions(file.toPath(), wanted);
			} else {
				// Windows: what java.io.File can say
				fs.setOwnerReadable(wanted.contains(PosixFilePermission.OWNER_READ));
				fs.setOwnerWritable(wanted.contains(PosixFilePermission.OWNER_WRITE));
				fs.setOwnerExecutable(wanted.contains(PosixFilePermission.OWNER_EXECUTE));
			}
				
		}
	}

	public static void captureAll() throws IOException {
		File dir = new File(".").getCanonicalFile();
		File[] kids = dir.listFiles((f)->{
			return f.isDirectory() && f.getName().endsWith("TestFiles");
		});

		File dataFile = new File(dir,"TestFileData.txt");
		System.setOut(new PrintStream(dataFile));
		for(File kid : kids) {
			captureTestFileData(kid,true);
		}

	}

	public static final SimpleDateFormat dateFmt = new SimpleDateFormat("MM-dd-yyy HH:mm:ss");

	public static void captureTestFileData(File fileOrDir,boolean randomize) throws IOException {

		Random r = new Random();
		StringBuilder buf= new StringBuilder(fileOrDir.getAbsolutePath());
		PosixFileAttributeView view = Files.getFileAttributeView(fileOrDir.toPath(), PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
		PosixFileAttributes at = view.readAttributes();
		Calendar cal = Calendar.getInstance();

		cal.setTimeInMillis(at.creationTime().toMillis());
		cal.set(Calendar.HOUR_OF_DAY, r.nextInt(13));
		cal.set(Calendar.DAY_OF_MONTH, r.nextInt(29));
		buf.append(",creationTime="+dateFmt.format(cal.getTime().getTime()));

		cal.setTimeInMillis(at.lastAccessTime().toMillis());
		cal.set(Calendar.HOUR_OF_DAY, r.nextInt(13));
		cal.set(Calendar.DAY_OF_MONTH, r.nextInt(29));
		buf.append(",accessTime="+dateFmt.format(cal.getTime().getTime()));

		cal.setTimeInMillis(at.lastModifiedTime().toMillis());
		cal.set(Calendar.HOUR_OF_DAY, r.nextInt(13));
		cal.set(Calendar.DAY_OF_MONTH, r.nextInt(29));
		buf.append(",lastModifiedTime="+dateFmt.format(cal.getTime().getTime()));

		Set<PosixFilePermission> perm = at.permissions();
		buf.append(",permissions=");
		for(PosixFilePermission p : perm) {
			buf.append(p.name()+":");
		}
		System.out.println(buf);

		if(fileOrDir.isDirectory() ) {
			File [] kids = fileOrDir.listFiles();
			if( kids != null ) {
				for(File kid : kids) {
					captureTestFileData(kid,randomize);
				}

			}
		}


	}


}
