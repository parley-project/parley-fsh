package us.bringardner.shell.test;

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

public class ManageTestFiles {

	public static void main(String[] args) throws IOException, ParseException {
		boolean capture = false;
		if( capture ) {
			captureAll();
		} else {
			restore();
		}

	}

	public static void restore() throws IOException, ParseException {
		File dir = new File(".").getCanonicalFile();
		File dataFile = new File(dir,"TestFileData.txt");
		InputStream in = new FileInputStream(dataFile);
		String [] lines = new String(in.readAllBytes()).split("\n");
		in.close();
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

		String []parts = line.split("[,]");
		File file = new File(parts[0]);
		String str = parts[1].substring(parts[1].indexOf('=')+1);
		long create =  dateFmt.parse(str).getTime();

		str = parts[2].substring(parts[2].indexOf('=')+1);
		long lastAccess = dateFmt.parse(str).getTime();

		str = parts[3].substring(parts[3].indexOf('=')+1);
		long lastMod = dateFmt.parse(str).getTime();

		String tmp = parts[4].substring(parts[4].indexOf('=')+1);
		String perms [] = tmp.split(":");

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
			
			Path path = Files.createSymbolicLink(file.toPath(), target.toPath());
			if( path == null || !Files.exists(path)) {
				throw new IOException("Can't create symbolic link to : "+file);
			}
		} else {
			

			PosixFileAttributeView view = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
			view.setTimes(FileTime.fromMillis(lastMod), FileTime.fromMillis(lastAccess),FileTime.fromMillis(create));

			Set<PosixFilePermission> perm = new HashSet<>();
			for(String p : perms) {
				perm.add(PosixFilePermission.valueOf(p));	
			}
			view.setPermissions(perm);	
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
