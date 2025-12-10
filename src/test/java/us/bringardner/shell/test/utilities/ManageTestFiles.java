package us.bringardner.shell.test.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;

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
		String [] lines = null;
		try(	InputStream in = ManageTestFiles.class.getResourceAsStream("/TestFileData.txt")){
			lines = new String(in.readAllBytes()).split("\n");
		}

		for(String line : lines) {
			restore(line);			
		}
	}

	public static void restore(String line) throws IOException, ParseException {
		//0 BjlFileSystemConsole/TouchTestFiles,
		//1 type=dir,
		//2 creationTime=03-14-2025 11:05:31,
		//3 lastAccessTime=03-14-2025 11:05:31,
		//4 lastModifiedTime=03-14-2025 11:05:31,
		//5 permissions=OWNER_READ:GROUP_READ:OWNER_WRITE:OTHERS_READ:OWNER_EXECUTE:GROUP_EXECUTE:OTHERS_EXECUTE:

		if( line.startsWith("/Volumes/Data/eclipse-git/BjlShell/TestFiles/SymLink2Folder01")) {
			System.out.println("here");
		}
		String []parts = line.split("[,]");
		int idx = parts[0].indexOf("BjlShell");
		String fileName = parts[0].substring(idx+9).trim();
		
		File file = new File(fileName).getCanonicalFile();
		
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
			if( file.exists()) {
				if( !file.delete()) {
					throw new IOException("Can't delete existing symbolic link test file: "+file);
				}
			}
			File dir = file.getParentFile();
			File target = new File(dir,file.getName().substring(8));
			
			Path path = Files.createSymbolicLink(file.toPath(),target.toPath());
			if( path == null || !Files.exists(path)) {
				if( FileSourceFactory.isWindows()) {
					throw new IOException("Can't create symbolic link to : "+file+" Windoes MUST be in developer mode.\nSettings->System->Advanced : Toggle Developer mode");
				} else {
					throw new IOException("Can't create symbolic link to : "+file);
				}
			}
		} else {
			String str = parts[1].substring(parts[1].indexOf('=')+1);
			long create =  dateFmt.parse(str).getTime();

			str = parts[2].substring(parts[2].indexOf('=')+1);
			long lastAccess = dateFmt.parse(str).getTime();

			str = parts[3].substring(parts[3].indexOf('=')+1);
			long lastMod = dateFmt.parse(str).getTime();

			String tmp = parts[4].substring(parts[4].indexOf('=')+1);
			String perms [] = tmp.split(":");

			FileSource fs = FileSourceFactory.getDefaultFactory().createFileSource(file.getAbsolutePath());
			fs.setCreateTime(create);
			fs.setLastAccessTime(lastAccess);
			fs.setLastModifiedTime(lastMod);
			
			for(String p : perms) {
				p = p.trim();
				if( p.isEmpty()) {
					continue;
				}

				Permissions perm = Permissions.valueOf(p);
					switch (perm) {
					case OWNER_READ:fs.setOwnerReadable(true);break;
					case OWNER_WRITE:fs.setOwnerWritable(true);break;
					case OWNER_EXECUTE:fs.setOwnerExecutable(true);break;
					case GROUP_READ:fs.setGroupReadable(true);break;
					case GROUP_WRITE:fs.setGroupWritable(true);break;
					case GROUP_EXECUTE:fs.setGroupExecutable(true);break;
					case OTHERS_READ:fs.setOtherExecutable(true);break;
					case OTHERS_WRITE:fs.setOtherExecutable(true);break;
					case OTHERS_EXECUTE:fs.setOtherExecutable(true);break;
					default:
						throw new IllegalArgumentException("Unexpected value: " + perm);
					}
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
