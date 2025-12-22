package us.bringardner.shell.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetAddress;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.shell.Console;
import us.bringardner.shell.Console.Prompt;

@TestMethodOrder(OrderAnnotation.class)
public class TestPrompt extends AbstractConsoleTest {


	private static Calendar cal;
	private static Date date;
	private static SimpleDateFormat Dow_Mon_Day = new SimpleDateFormat("EEE MMM dd");
	@BeforeAll
	public static void beforeAll() throws IOException, ParseException {
		AbstractConsoleTest.setup("TestFiles");
		
		SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS Z");
		
		
		date = fmt.parse("09/07/2025 13:22:15.123 EDT");
		cal = Calendar.getInstance();
		cal.setTime(date);
		

	}


	@Test
	@Order(1)
	public void testPropmtPS1_01() throws IOException {
		Object obj = console.getVariable(Console.VARIABLE_PS1);
		assertNotNull(obj);
		String val = (String)obj;
		String expect = "\\s-\\v\\$ ";
		assertEquals(expect, val);
		//-bash-4.2$
		expect = "fssh-"+Console.VERSION+"$ ";		
		val = console.getPrompt(Prompt.Primary);
		assertEquals(expect, val);
	}
	
	@Test
	@Order(2)
	public void testPropmtPS1_02() throws IOException, ParseException {
		int aval = 'A';
		String oval = Integer.toOctalString(aval);
		
		executeCommand("pwd");
				
		boolean isWin = getOs() == OperatingSystem.Windows;
		
		Map<String,String> expect = new TreeMap<>();
		expect.put("\\a", ""+((char)7));
		expect.put("\\e", ""+((char)27));
		expect.put("\\n", "\n");
		expect.put("\\r", "\r");
		expect.put("\\s", "fssh");
		expect.put("\\h", InetAddress.getLocalHost().getHostName());
		expect.put("\\H", InetAddress.getLocalHost().getHostName());
		expect.put("\\\\", "\\");
		expect.put("\\\\ ", "\\ ");
		expect.put("\\d", Dow_Mon_Day.format(date));
		expect.put("\\j", "0");
		expect.put("\\l", "fssh");
		expect.put("\\t", String.format("%02d:%02d:%02d",
				cal.get(Calendar.HOUR_OF_DAY),
				cal.get(Calendar.MINUTE),
				cal.get(Calendar.SECOND)
				));
		expect.put("\\T", String.format("%02d:%02d:%02d",
				cal.get(Calendar.HOUR),
				cal.get(Calendar.MINUTE),
				cal.get(Calendar.SECOND)
				));
		expect.put("\\@", String.format("%02d:%02d:%02d %s",
				cal.get(Calendar.HOUR),
				cal.get(Calendar.MINUTE),
				cal.get(Calendar.SECOND),
				cal.get(Calendar.HOUR_OF_DAY) < 12 ? "AM":"PM"
				));
		expect.put("\\A", String.format("%02d:%02d",
				cal.get(Calendar.HOUR_OF_DAY),
				cal.get(Calendar.MINUTE)
				));
		expect.put("\\u", System.getProperty("user.name"));
		expect.put("\\v", Console.VERSION);
		expect.put("\\V", Console.VERSION);
		//PROMPT_DIRTRIM
	
		expect.put("\\w",isWin?"~\\TestFiles": "~/TestFiles");
		expect.put("\\W",isWin?"~\\TestFiles": "~/TestFiles");
		expect.put("\\!", "0");
		expect.put("\\#", "0");
		expect.put("\\$", "$");
		expect.put("\\"+oval, "A");
		
		String tmp = 
				"\\a\n"
				+ "A bell character.\n"
				+"\\d\n"
				+ "The date, in \"Weekday Month Date\" format (e.g., \"Tue May 26\").\n"
				+ "\\e\n"
				+ "An escape character.\n"
				+ "\\h\n"
				+ "The hostname, up to the first ‘.’.\n"
				+ "\\H\n"
				+ "The hostname.\n"
				+ "\\j\n"
				+ "The number of jobs currently managed by the shell.\n"
				+ "\\l\n"
				+ "The basename of the shell’s terminal device name (e.g., \"ttys0\").\n"
				+ "\\n\n"
				+ "A newline.\n"
				+ "\\r\n"
				+ "A carriage return.\n"
				+ "\\s\n"
				+ "The name of the shell: the basename of $0 (the portion following the final slash).\n"
				+ "\\t\n"
				+ "The time, in 24-hour HH:MM:SS format.\n"
				+ "\\T\n"
				+ "The time, in 12-hour HH:MM:SS format.\n"
				+ "\\@\n"
				+ "The time, in 12-hour am/pm format.\n"
				+ "\\A\n"
				+ "The time, in 24-hour HH:MM format.\n"
				+ "\\u\n"
				+ "The username of the current user.\n"
				+ "\\v\n"
				+ "The Bash version (e.g., 2.00).\n"
				+ "\\V\n"
				+ "The Bash release, version + patchlevel (e.g., 2.00.0).\n"
				+ "\\w\n"
				+ "The value of the PWD shell variable ($PWD), with $HOME abbreviated with a tilde (uses the $PROMPT_DIRTRIM variable).\n"
				+ "\\W\n"
				+ "The basename of $PWD, with $HOME abbreviated with a tilde.\n"
				+ "\\!\n"
				+ "The history number of this command.\n"
				+ "\\#\n"
				+ "The command number of this command.\n"
				+ "\\$\n"
				+ "If the effective uid is 0, #, otherwise $.\n"
				+ "\\"+oval+"\n"
				//+ "\\nnn\n"
				+ "The character whose ASCII code is the octal value nnn.\n"
				+ "\\\\\n"
				+ "A backslash.\n"
				+ "\\\\ \n"
				+ "A backslash space.\n"
				
				// 3837 attiuc dr. powell
			;

		String [] lines = tmp.split("\n");
		for (int idx = 0; idx < lines.length; idx++) {
			String val = lines[idx];
			String actual = console.expandPrompt(val,date);
		
			//System.out.println("idx="+idx+" val="+ val+"="+actual+" ("+lines[++idx]+")"); 
			String exp = expect.get(val);
			if( exp !=null ) {
				assertEquals(exp, actual,"val="+val);
			}
		}
	}


	@Test
	@Order(3)
	public void testPropmtPS1_03() throws IOException, ParseException {
		SimpleDateFormat fmt = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");
		
		Map<String,String> expect = new TreeMap<>();
		expect.put("%a", "Sun");
		expect.put("%a", new SimpleDateFormat("E").format(date));
		expect.put("%A", new SimpleDateFormat("EEEE").format(date));
		expect.put("%b", new SimpleDateFormat("MM").format(date));
		expect.put("%b", new SimpleDateFormat("MMM").format(date));
		expect.put("%c", fmt.format(date));
		
		expect.put("%d", "07");		
		expect.put("%H", "13");
		expect.put("%H", new SimpleDateFormat("HH").format(date));
		expect.put("%I", "01");
		expect.put("%I", new SimpleDateFormat("hh").format(date));
		expect.put("%j", "250");
		expect.put("%m", "09");
		expect.put("%M", "22");
		expect.put("%p", "PM");
		expect.put("%S", "15");
		// TODO: Sunday = 0 in strftime
		expect.put("%w", "07");
		expect.put("%W", "037");
		
		expect.put("%x", new SimpleDateFormat("MM/dd/yy").format(date));//"09/07/25");
		expect.put("%X", new SimpleDateFormat("HH:mm:ss").format(date));
		expect.put("%y", new SimpleDateFormat("yy").format(date));
		expect.put("%Y", new SimpleDateFormat("yyyy").format(date));
		expect.put("%Z", new SimpleDateFormat("z").format(date));
		expect.put("%%", "%");
		
		String tmp = 
				//"\\a\n"
				//+ "A bell character.\n"
				""
				+ "%a\nAbbreviated weekday name	Sun\n"
				+ "%A\nFull weekday name	Sunday\n"
				+ "%b\nAbbreviated month name	Mar\n"
				+ "%B\nFull month name	March\n"
				+ "%c\nDate and time representation	Sun Aug 19 02:56:02 2012\n"
				+ "%d\nDay of the month (01-31)	19\n"
				+ "%H\nHour in 24h format (00-23)	14\n"
				+ "%I\nHour in 12h format (01-12)	05\n"
				+ "%j\nDay of the year (001-366)	231\n"
				+ "%m\nMonth as a decimal number (01-12)	08\n"
				+ "%M\nMinute (00-59)	55\n"
				+ "%p\nAM or PM designation	PM\n"
				+ "%S\nSecond (00-61)	02\n"
				+ "%U\nWeek number with the first Sunday as the first day of week one (00-53)	33\n"
				+ "%w\nWeekday as a decimal number with Sunday as 0 (0-6)	4\n"
				+ "%W\nWeek number with the first Monday as the first day of week one (00-53)	34\n"
				+ "%x\nDate representation	08/19/12\n"
				+ "%X\nTime representation	02:50:06\n"
				+ "%y\nYear, last two digits (00-99)	01\n"
				+ "%Y\nYear	2012\n"
				+ "%Z\nTimezone name or abbreviation	CDT\n"
				+ "%%\nA % sign	%"
				;
			;
	
		String [] lines = tmp.split("\n");
		for (int idx = 0; idx < lines.length; idx++) {
			String key = lines[idx++];
			String val = "\\D{"+key+"}";
			//System.out.println(val+" ("+lines[++idx]+")");
			String actual = console.expandPrompt(val,date);
			//System.out.println(val+"="+actual+" ("+lines[++idx]+")"); 
			String exp = expect.get(key);
			if( exp !=null ) {
				assertEquals(exp, actual,"idx="+idx+" key="+key);
			}
		}
	}
	
	@Test
	@Order(4)
	public void testPropmtPS1_04() throws IOException {
		
		String codes = "¢©®";
		String prompt = "\\["+codes+"]";
		String actual = console.expandPrompt(prompt,null);
		assertEquals(codes, actual);
		
	}
}
