package us.bringardner.shell.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetAddress;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.shell.Console;

@TestMethodOrder(OrderAnnotation.class)
public class TestPrompt extends AbstractConsoleTest {


	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
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
		expect = "-"+Console.VERSION+"$ ";
		val = console.getPrompt(1);
		assertEquals(expect, val);
	}
	
	@Test
	@Order(2)
	public void testPropmtPS1_02() throws IOException, ParseException {
		int aval = 'A';
		String oval = Integer.toOctalString(aval);
		
		executeCommand("pwd");
		
		SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS z");
		
		
		Date date = fmt.parse("09/07/2025 13:22:15.123 EDT");
		
		Map<String,String> expect = new TreeMap<>();
		expect.put("\\a", ""+((char)7));
		expect.put("\\e", ""+((char)27));
		expect.put("\\n", "\n");
		expect.put("\\r", "\r");
		expect.put("\\s", "");
		expect.put("\\h", InetAddress.getLocalHost().getHostName());
		expect.put("\\H", InetAddress.getLocalHost().getHostName());
		expect.put("\\\\", "\\");
		expect.put("\\\\ ", "\\ ");
		expect.put("\\d", "Sun Sep 07");
		expect.put("\\j", "0");
		expect.put("\\l", "fssh");
		expect.put("\\t", "13:22:15");
		expect.put("\\T", "01:22:15");
		expect.put("\\@", "01:22:15 PM");
		expect.put("\\A", "13:22");
		expect.put("\\u", System.getProperty("user.name"));
		expect.put("\\v", Console.VERSION);
		expect.put("\\V", Console.VERSION);
		//PROMPT_DIRTRIM
		expect.put("\\w", "~/TestFiles");
		expect.put("\\W", "~/TestFiles");
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
				
				
			;

		String [] lines = tmp.split("\n");
		for (int idx = 0; idx < lines.length; idx++) {
			String val = lines[idx];
			String actual = console.expandPrompt(val,date);
			//System.out.println(val+"="+actual+" ("+lines[++idx]+")"); 
			String exp = expect.get(val);
			if( exp !=null ) {
				assertEquals(exp, actual,"val="+val);
			}
		}
	}


	@Test
	@Order(3)
	public void testPropmtPS1_03() throws IOException, ParseException {
		SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS z");
		Date date = fmt.parse("09/07/2025 13:22:15.123 EDT");
		
		Map<String,String> expect = new TreeMap<>();
		expect.put("%a", "Sun");
		expect.put("%A", "Sunday");
		expect.put("%b", "Sep");
		expect.put("%B", "September");
		expect.put("%c", "Sun Sep 07 13:22:15 2025");
		expect.put("%d", "07");
		expect.put("%H", "13");
		expect.put("%I", "01");
		expect.put("%j", "250");
		expect.put("%m", "09");
		expect.put("%M", "22");
		expect.put("%p", "PM");
		expect.put("%S", "15");
		// TODO: Sunday = 0 in strftime
		expect.put("%w", "07");
		expect.put("%W", "037");
		
		expect.put("%x", "09/07/25");
		expect.put("%X", "13:22:15");
		expect.put("%y", "25");
		expect.put("%Y", "2025");
		expect.put("%Z", "EDT");
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
				assertEquals(exp, actual," key="+key);
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
