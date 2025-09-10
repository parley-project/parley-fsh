package us.bringardner.shell.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Random;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

public class TestTouch extends AbstractConsoleTest {

	enum TimeType {Access,Modification,Both}

	static File targetDir;

	@BeforeAll
	public static void beforeAll() throws IOException {

	targetDir = new File("target").getCanonicalFile();
	setup(targetDir.getAbsolutePath());
}

@AfterAll
public static void afterAll() {
	teardown();
}


@Test
@Order(1)
public void touchPlain() throws IOException {
	String fileName = "TouchMePlain.tst";
	File file = new File(targetDir,fileName);
	if( file.exists()) {
		if( !file.delete()) {
			throw new IOException("Can't delete "+file);
		}
	}

	String cmd = "touch "+file.getName();
	ExecuteResult result = executeCommand(cmd, "");
	int exitCode = result.exitCode;
	if( exitCode!=0) {
		throw new IOException("Touch did not exit cleanly "+exitCode);
	}
	if( !file.exists()) {
		throw new IOException("Touch did not create "+file);		
	}		
}

@Test
@Order(4)
public void touch_r_then_A() throws IOException {
	String fileName = "TouchMe_A.tst";

	File sourceFile = new File("TouchTestFiles/AbcFileA.js").getCanonicalFile();
	if( !sourceFile.exists()) {
		throw new IOException("Source file "+sourceFile.getAbsolutePath()+" does not exists");		
	}

	File touchFile = new File(targetDir,fileName);
	if( touchFile.exists()) {
		if( !touchFile.delete()) {
			throw new IOException("Can't delete "+touchFile);
		}
	}

	String cmd = "touch -r ../TouchTestFiles/AbcFileA.js "+touchFile.getName();
	ExecuteResult result = executeCommand(cmd, "");
	int exitCode = result.exitCode;
	if( exitCode!=0) {
		throw new IOException("Touch did not exit cleanly "+exitCode);
	}
	if( !touchFile.exists()) {
		throw new IOException("Touch did not create "+touchFile);
	}		
	long touchTime = touchFile.lastModified();
	long sourceTime = sourceFile.lastModified();
	if( touchTime != sourceTime) {
		throw new IOException("Touch mod times don't match. source="+sourceTime+" touch="+touchTime);
	}

	touchTime = getLastAccess(touchFile);				
	sourceTime = getLastAccess(sourceFile);

	if( touchTime != sourceTime) {
		throw new IOException("Touch access times don't match. source="+sourceTime+" touch="+touchTime);
	}
	/*
 The argument is of the form “[-][[hh]mm]SS” where each pair of letters represents the following:

                   -       Make the adjustment negative: the new time stamp is set to be before the old one.
                   hh      The number of hours, 	from 00 to 99.
                   mm      The number of minutes, 	from 00 to 59.
                   SS      The number of seconds, 	from 00 to 59.
	 */
	// Add 10 seconds to modification time
	execute_A(touchFile,false,0,0,10,TimeType.Both);
	execute_A(touchFile,false,0,2,0,TimeType.Both);
	execute_A(touchFile,false,3,0,0,TimeType.Both);
	execute_A(touchFile,true,0,0,10,TimeType.Both);
	execute_A(touchFile,true,0,2,0,TimeType.Both);
	execute_A(touchFile,true,3,0,0,TimeType.Both);


	execute_A(touchFile,false,0,0,10,TimeType.Access);
	execute_A(touchFile,false,0,2,0,TimeType.Access);
	execute_A(touchFile,false,3,0,0,TimeType.Access);
	execute_A(touchFile,true,0,0,10,TimeType.Access);
	execute_A(touchFile,true,0,2,0,TimeType.Access);
	execute_A(touchFile,true,3,0,0,TimeType.Access);

	execute_A(touchFile,false,0,0,10,TimeType.Modification);
	execute_A(touchFile,false,0,2,0,TimeType.Modification);
	execute_A(touchFile,false,3,0,0,TimeType.Modification);
	execute_A(touchFile,true,0,0,10,TimeType.Modification);
	execute_A(touchFile,true,0,2,0,TimeType.Modification);
	execute_A(touchFile,true,3,0,0,TimeType.Modification);

	Random r = new Random();

	int cnt = 0;
	while( ++cnt < 10) {
		int h = r.nextInt(99);
		int m = r.nextInt(59);
		int s = r.nextInt(59);
		boolean negate = r.nextBoolean();
		execute_A(touchFile,negate,h,m,s,TimeType.Both);
	}

}

static final long SEC = 1000;
static final long MI = 60*SEC;
static final long HR = MI*60;

private void execute_A(File touchFile,boolean negate ,int hr,int min,int sec, TimeType type) throws IOException {

	long startMTime = -1;
	long startATime = -1;
	startMTime = touchFile.lastModified();
	startATime = getLastAccess(touchFile);

	String argStr = String.format("%02d%02d%02d", hr,min,sec);
	long delta = (HR*hr)+(MI*min)+(SEC*sec);
	if( negate ) {
		argStr = "-"+argStr;
		delta *= -1;
	}


	long expectedMTime = startMTime+delta;
	long expectedATime = startATime+delta;

	String typeStr = (type == null || type == TimeType.Both) ? "":type==TimeType.Modification?"m":"a";
	String cmd = String.format("touch -%sA %s %s ",typeStr,argStr,touchFile.getName());
	ExecuteResult result = executeCommand(cmd, "");
	int exitCode = result.exitCode;
	if( exitCode!=0) {
		throw new IOException("'"+cmd+"' did not exit cleanly. exitCode="+exitCode);
	}

	long touchedMTime = touchFile.lastModified();
	long touchedATime = getLastAccess(touchFile);


	if( (type == null || type == TimeType.Both ) || type == TimeType.Modification) {
		if( touchedMTime != expectedMTime) {
			throw new IOException("'"+cmd+"' M times don't match. start="+startMTime+" delta="+delta+" touched="+touchedMTime);
		}

	}
	if( (type == null || type == TimeType.Both ) || type == TimeType.Access) {
		if( touchedATime != expectedATime) {
			throw new IOException("'"+cmd+"' A times don't match. start="+startATime+" delta="+delta+" touched="+touchedATime);
		}

	}

}

@Test
@Order(3)
public void touch_r_a() throws IOException {
	String fileName = "TouchMe_a.tst";

	File sourceFile = new File("TouchTestFiles/AbcFileA.js").getCanonicalFile();
	if( !sourceFile.exists()) {
		throw new IOException("Source file "+sourceFile.getAbsolutePath()+" does not exists");		
	}

	File touchFile = new File(targetDir,fileName);
	if( touchFile.exists()) {
		if( !touchFile.delete()) {
			throw new IOException("Can't delete "+touchFile);
		}
	}

	String cmd = "touch -ar ../TouchTestFiles/AbcFileA.js "+touchFile.getName();
	ExecuteResult result = executeCommand(cmd, "");
	int exitCode = result.exitCode;
	if( exitCode!=0) {
		throw new IOException("Touch did not exit cleanly "+exitCode);
	}
	if( !touchFile.exists()) {
		throw new IOException("Touch did not create "+touchFile);
	}		
	long touchTime = touchFile.lastModified();
	long sourceTime = sourceFile.lastModified();
	if( touchTime == sourceTime) {
		throw new IOException("Touch mod times must not match. source="+sourceTime+" touch="+touchTime);
	}

	touchTime = getLastAccess(touchFile);				
	sourceTime = getLastAccess(sourceFile);

	if( touchTime != sourceTime) {
		throw new IOException("Touch access times don't match. source="+sourceTime+" touch="+touchTime);
	}

}

@Test
@Order(2)
public void touch_r_m() throws IOException {
	String fileName = "TouchMe_m.tst";

	File sourceFile = new File("TouchTestFiles/AbcFileA.js").getCanonicalFile();
	if( !sourceFile.exists()) {
		throw new IOException("Source file "+sourceFile.getAbsolutePath()+" does not exists");		
	}

	File touchFile = new File(targetDir,fileName);
	if( touchFile.exists()) {
		if( !touchFile.delete()) {
			throw new IOException("Can't delete "+touchFile);
		}
	}

	String cmd = "touch -mr ../TouchTestFiles/AbcFileA.js "+touchFile.getName();
	ExecuteResult result = executeCommand(cmd, "");
	int exitCode = result.exitCode;
	if( exitCode!=0) {
		throw new IOException("Touch did not exit cleanly "+exitCode);
	}
	if( !touchFile.exists()) {
		throw new IOException("Touch did not create "+touchFile);
	}		
	long touchTime = touchFile.lastModified();
	long sourceTime = sourceFile.lastModified();
	if( touchTime != sourceTime) {
		throw new IOException("Touch mod times do not match. source="+sourceTime+" touch="+touchTime);
	}

	touchTime = getLastAccess(touchFile);				
	sourceTime = getLastAccess(sourceFile);

	if( touchTime == sourceTime) {
		throw new IOException("Touch access times must not match. source="+sourceTime+" touch="+touchTime);
	}

}

private long getLastAccess(File target) throws IOException {
	BasicFileAttributes attrs = Files.readAttributes(target.toPath(), BasicFileAttributes.class);
	FileTime time = attrs.lastAccessTime();
	return time.toMillis();
}

static class DateConfig {
	String dateStr;
	SimpleDateFormat fmt;

	public DateConfig(SimpleDateFormat fmt,String dateStr) {
		this.fmt = fmt;
		this.dateStr = dateStr;
	}		
}

static DateConfig [] d_dateConfigs = {
		/*
		 * milliseconds and Z are the only optional
		 * YYYY-MM-DDThh:mm:SS[tz]
		 */
		new DateConfig(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"), "2020-07-23T06:30:10Z"),
		new DateConfig(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), "2020-07-23 06:30:10Z"),
		new DateConfig(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), "2020-07-23 06:30:10"),
		
		
		new DateConfig(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"), "2020-07-23T06:30:10"),
		
};

@Test
@Order(5)
public void touch_d() throws IOException, ParseException {
	String fileName = "TouchMe_d.tst";


	File touchFile = new File(targetDir,fileName);
	if( touchFile.exists()) {
		if( !touchFile.delete()) {
			throw new IOException("Can't delete "+touchFile);
		}
	}

	// java file times do not support millis

	for (int idx = 0; idx < d_dateConfigs.length; idx++) {

		String dateStr = d_dateConfigs[idx].dateStr;

		String cmd = String.format("touch -d %s %s",dateStr,touchFile.getName());
		ExecuteResult result = executeCommand(cmd, "");
		int exitCode = result.exitCode;
		if( exitCode!=0) {
			throw new IOException("Touch did not exit cleanly "+exitCode+" for "+dateStr);
		}

		if( !touchFile.exists()) {
			throw new IOException("Touch did not create "+touchFile);
		}		
		long touchMTime = touchFile.lastModified();
		long touchATime = getLastAccess(touchFile);
		SimpleDateFormat fmt = d_dateConfigs[idx].fmt;
		if( dateStr.endsWith("Z")) {
			fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
			dateStr = dateStr.substring(0,dateStr.length()-1);
		}
		long expected = fmt.parse(dateStr).getTime();

		if( touchMTime != expected) {
			throw new IOException("m time wrong expect="+expected+" actual="+touchMTime+" idx="+idx);
		}
		if( touchATime != expected) {
			throw new IOException("a time wrong expect="+expected+" actual="+touchATime+" idx="+idx);
		}
	}
}


static DateConfig [] t_dateConfigs = {
		//[-t [[CC]YY]MMDDhhmm[.SS]] 
		new DateConfig(new SimpleDateFormat("yyyyMMddHHmm.ss"), "202007230630.10"),
		new DateConfig(new SimpleDateFormat("yyMMddHHmm.ss"), "2107230640.10"),
		new DateConfig(new SimpleDateFormat("MMddHHmm.ss"), "07230640.20"),
		new DateConfig(new SimpleDateFormat("MMddHHmm"), "07230650"),
		new DateConfig(new SimpleDateFormat("yyyyMMddHHmm"), "202307230630"),
		new DateConfig(new SimpleDateFormat("yyMMddHHmm"), "2107230640"),
		
		
};

@Test
@Order(6)
public void touch_t() throws IOException, ParseException {
	String fileName = "TouchMe_t.tst";


	File touchFile = new File(targetDir,fileName);
	if( touchFile.exists()) {
		if( !touchFile.delete()) {
			throw new IOException("Can't delete "+touchFile);
		}
	}

	for (int idx = 0; idx < t_dateConfigs.length; idx++) {

		String dateStr = t_dateConfigs[idx].dateStr;
		SimpleDateFormat fmt = t_dateConfigs[idx].fmt;
		long expected = fmt.parse(dateStr).getTime();

		String cmd = String.format("touch -t %s %s",dateStr,touchFile.getName());
		ExecuteResult result = executeCommand(cmd, "");
		int exitCode = result.exitCode;
		if( exitCode!=0) {
			throw new IOException("Touch did not exit cleanly "+exitCode+" for "+dateStr);
		}

		if( !touchFile.exists()) {
			throw new IOException("Touch did not create "+touchFile);
		}		
		long touchMTime = touchFile.lastModified();
		long touchATime = getLastAccess(touchFile);
		
		if( touchMTime != expected) {
			throw new IOException("m time wrong expect="+expected+" actual="+touchMTime+" idx="+idx);
		}
		if( touchATime != expected) {
			throw new IOException("a time wrong expect="+expected+" actual="+touchATime+" idx="+idx);
		}
	}
}

}
