package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestSelectStatement extends AbstractConsoleTest {

	
	
	@BeforeAll
	public static void setup() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		if(!testFilesDir.exists() ) {
			System.out.println(testFilesDir.getAbsolutePath()+" does not exists");
		}
	}
	

	@Test
	public void testSelectStatent01() throws Exception{
		String cmd = 
				 "select fname in 'one       ' two three four five;\n"
				 + "do\n"
				 + "	echo \"you picked $fname ($REPLY)\"\n"
				 + "	break;\n"
				 + "done\n"
				;
		
		
		String expectOut = 	"you picked two (2)\n";
		// as bash lays out the menu (on standard error)
		String expectErr = 
				  "1) one       \n"
				+ "2) two\n"
				+ "3) three\n"
				+ "4) four\n"
				+ "5) five\n";
		String stdin = "2\n";
		showError=false;
		ExecuteResult res = executeCommand(cmd,stdin);
		showError=true;
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		// the prompt (#?) is on standard error, as in bash, between the menus
		assertTrue(err.contains("#? "), err);
		assertEquals(expectErr, err.replace("#? ", ""));
	}

	@Test
	public void testSelectStatent02() throws Exception{
		String cmd = 
				 "select fname in 'one       ' two three four five;\n"
				 + "do\n"
				 + "	echo you picked $fname \"($REPLY)\"\n"
				 + "	break;\n"
				 + "done\n"
				;
		
		
		String expectOut = 	"you picked two (2)\n";
		// as bash lays out the menu (on standard error)
		String expectErr = 
				  "1) one       \n"
				+ "2) two\n"
				+ "3) three\n"
				+ "4) four\n"
				+ "5) five\n"
				+ "1) one       \n"
				+ "2) two\n"
				+ "3) three\n"
				+ "4) four\n"
				+ "5) five\n";
		String stdin = "\n2\n";
		showError=false;
		ExecuteResult res = executeCommand(cmd,stdin);
		showError=true;
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		// the prompt (#?) is on standard error, as in bash, between the menus
		assertTrue(err.contains("#? "), err);
		assertEquals(expectErr, err.replace("#? ", ""));
	}
	
	@Test
	public void testSelectStatent03() throws Exception{
		String cmd = 
				 "select fname in 'one       ' two three four five;\n"
				 + "do\n"
				 + "	echo \"you picked $fname ($REPLY)\"\n"
				 + "	break;\n"
				 + "done\n"
				;
		
		
		String expectOut = 	"you picked  (test)\n";
		// as bash lays out the menu (on standard error)
		String expectErr = 
				  "1) one       \n"
				+ "2) two\n"
				+ "3) three\n"
				+ "4) four\n"
				+ "5) five\n";
		String stdin = "test\n";
		showError=false;
		ExecuteResult res = executeCommand(cmd,stdin);
		showError=true;
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		// the prompt (#?) is on standard error, as in bash, between the menus
		assertTrue(err.contains("#? "), err);
		assertEquals(expectErr, err.replace("#? ", ""));
	}

	@Test
	public void testSelectStatent04() throws Exception{
		String cmd = 
				 "select fname in *;\n"
				 + "do\n"
				 + "	echo you picked $fname \\($REPLY\\)\n"
				 + "	break;\n"
				 + "done\n"
				;
		
		
		String expectOut = 	"you picked AbcFile.php (2)\n";
		// as bash lays out the menu (on standard error)
		String expectErr = 
				  "1) AbcFile.js\t\t 3) AbcFile.properties\t  5) Hotel California.txt\n"
				+ "2) AbcFile.php\t\t 4) Folder01\t\t  6) SymLink2Folder01\n";
		String stdin = "2\n";
		
		showError=false;
		ExecuteResult res = executeCommand(cmd,stdin);
		showError=true;
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		assertTrue(err.contains("#? "), err);
		assertEquals(expectErr.trim(), err.replace("#? ", "").trim());
	}
	
	@Test
	public void testSelectStatent05() throws Exception{
		String cmd = 
				 "select fname in Folder01/*;\n"
				 + "do\n"
				 + "	echo \"you picked $fname ($REPLY)\"\n"
				 + "	break;\n"
				 + "done\n"
				;
		if(getOs()== OperatingSystem.Windows) {
			cmd = 	cmd.replaceAll("/", "\\\\");
		}
		
		//System.out.println(cmd);
		String expectOut = 	"you picked Folder01/AbcFile.properties (2)\n";
		if(getOs()== OperatingSystem.Windows) {
			expectOut = 	expectOut.replaceAll("/", "\\\\");
		}
		// as bash lays out the menu (on standard error)
		String expectErr = 
				  "1) Folder01/AbcFile.php\t\t  4) Folder01/Folder01def.2\n"
				+ "2) Folder01/AbcFile.properties\t  5) Folder01/Hotel California.txt\n"
				+ "3) Folder01/Folder01abc.1\n";
		String stdin = "2\n";
		
		showError=false;
		ExecuteResult res = executeCommand(cmd,stdin);
		showError=true;
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		// the prompt (#?) is on standard error, as in bash, between the menus
		assertTrue(err.contains("#? "), err);
		assertEquals(expectErr, err.replace("#? ", ""));
	}
	
	@Test
	public void testSelectStatent06() throws Exception{
		String words = "one two three four five six seven eight nine ten";
		
		String cmd = 
				 "select fname in "+words+";\n"
				 + "do\n"
				 + "	echo \"you picked $fname ($REPLY)\"\n"
				 + "	break;\n"
				 + "done\n"
				;
		
		
		String expectOut = 	"you picked two (2)\n";
		// as bash lays out the menu (on standard error)
		String expectErr = 
				  "1) one\t    3) three   5) five\t  7) seven   9) nine\n"
				+ "2) two\t    4) four    6) six\t  8) eight  10) ten\n";
		String stdin = "2\n";
		showError=false;
		ExecuteResult res = executeCommand(cmd,stdin);
		showError=true;
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		// the prompt (#?) is on standard error, as in bash, between the menus
		assertTrue(err.contains("#? "), err);
		assertEquals(expectErr, err.replace("#? ", ""));
	}

	@Test
	public void testSelectStatent07() throws Exception{
		StringBuilder buf = new StringBuilder();
		for(int idx=1; idx <=110; idx++) {
			buf.append(""+idx+" ");
		}
		String words = buf.toString();
		
		String cmd = 
				 "select fname in "+words+";\n"
				 + "do\n"
				 + "	echo \"you picked $fname ($REPLY)\"\n"
				 + "	break;\n"
				 + "done\n"
				;
		
		
		String expectOut = 	"you picked 20 (20)\n";
		// as bash lays out the menu (on standard error)
		String expectErr = 
				  " 1) 1\t   15) 15    29) 29    43) 43\t 57) 57\t   71) 71    85) 85    99) 99\n"
				+ " 2) 2\t   16) 16    30) 30    44) 44\t 58) 58\t   72) 72    86) 86   100) 100\n"
				+ " 3) 3\t   17) 17    31) 31    45) 45\t 59) 59\t   73) 73    87) 87   101) 101\n"
				+ " 4) 4\t   18) 18    32) 32    46) 46\t 60) 60\t   74) 74    88) 88   102) 102\n"
				+ " 5) 5\t   19) 19    33) 33    47) 47\t 61) 61\t   75) 75    89) 89   103) 103\n"
				+ " 6) 6\t   20) 20    34) 34    48) 48\t 62) 62\t   76) 76    90) 90   104) 104\n"
				+ " 7) 7\t   21) 21    35) 35    49) 49\t 63) 63\t   77) 77    91) 91   105) 105\n"
				+ " 8) 8\t   22) 22    36) 36    50) 50\t 64) 64\t   78) 78    92) 92   106) 106\n"
				+ " 9) 9\t   23) 23    37) 37    51) 51\t 65) 65\t   79) 79    93) 93   107) 107\n"
				+ "10) 10\t   24) 24    38) 38    52) 52\t 66) 66\t   80) 80    94) 94   108) 108\n"
				+ "11) 11\t   25) 25    39) 39    53) 53\t 67) 67\t   81) 81    95) 95   109) 109\n"
				+ "12) 12\t   26) 26    40) 40    54) 54\t 68) 68\t   82) 82    96) 96   110) 110\n"
				+ "13) 13\t   27) 27    41) 41    55) 55\t 69) 69\t   83) 83    97) 97\n"
				+ "14) 14\t   28) 28    42) 42    56) 56\t 70) 70\t   84) 84    98) 98\n";
		String stdin = "20\n";
		showError=false;
		ExecuteResult res = executeCommand(cmd,stdin);
		showError=true;
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		// the prompt (#?) is on standard error, as in bash, between the menus
		assertTrue(err.contains("#? "), err);
		assertEquals(expectErr, err.replace("#? ", ""));
	}
	
}
