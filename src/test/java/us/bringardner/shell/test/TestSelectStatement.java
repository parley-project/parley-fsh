package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
		
		
		String expectOut = 	"#? you picked two (2)\n";
		String expectErr = 	
				  "1) one              \n"
				+ "2) two              \n"
				+ "3) three            \n"
				+ "4) four             \n"
				+ "5) five             \n";
		String stdin = "2\n";
		
		ExecuteResult res = executeCommand(cmd,stdin);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		assertEquals(expectErr, err);
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
		
		
		String expectOut = 	"#? #? you picked two (2)\n";
		String expectErr = 	
				  "1) one              \n"
				+ "2) two              \n"
				+ "3) three            \n"
				+ "4) four             \n"
				+ "5) five             \n"
				+ "1) one              \n"
				+ "2) two              \n"
				+ "3) three            \n"
				+ "4) four             \n"
				+ "5) five             \n"
				+ "";
		String stdin = "\n2\n";
		
		ExecuteResult res = executeCommand(cmd,stdin);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		assertEquals(expectErr, err);
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
		
		
		String expectOut = 	"#? you picked  (test)\n";
		String expectErr = 	
				  "1) one              \n"
				+ "2) two              \n"
				+ "3) three            \n"
				+ "4) four             \n"
				+ "5) five             \n";
		String stdin = "test\n";
		
		ExecuteResult res = executeCommand(cmd,stdin);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		assertEquals(expectErr, err);
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
		
		
		String expectOut = 	"#? you picked AbcFile.php ( 2 )\n";
		String expectErr = 	
				    "1) AbcFile.js                 \n"
				  + "2) AbcFile.php                \n"
				  + "3) AbcFile.properties         \n"
				  + "4) Folder01                   \n"
				  + "5) Hotel California.txt       \n"
				  + "";
		String stdin = "2\n";
		
		ExecuteResult res = executeCommand(cmd,stdin);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		assertEquals(expectErr, err);
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
		
		//System.out.println(cmd);
		String expectOut = 	"#? you picked Folder01/AbcFile.properties (2)\n";
		String expectErr = 	
				  "1) Folder01/AbcFile.php                \n"
				+ "2) Folder01/AbcFile.properties         \n"
				+ "3) Folder01/Folder01abc.1              \n"
				+ "4) Folder01/Folder01def.2              \n"
				+ "5) Folder01/Hotel California.txt       \n";
		String stdin = "2\n";
		
		ExecuteResult res = executeCommand(cmd,stdin);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		assertEquals(expectErr, err);
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
		
		
		String expectOut = 	"#? you picked two (2)\n";
		String expectErr = 	
				    " 1) one         \n"
				  + " 2) two         \n"
				  + " 3) three       \n"
				  + " 4) four        \n"
				  + " 5) five        \n"
				  + " 6) six         \n"
				  + " 7) seven       \n"
				  + " 8) eight       \n"
				  + " 9) nine        \n"
				  + "10) ten         \n"
				  + "";
		String stdin = "2\n";
		
		ExecuteResult res = executeCommand(cmd,stdin);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		assertEquals(expectErr, err);
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
		
		
		String expectOut = 	"#? you picked 20 (20)\n";
		String expectErr = 	
				      "  1) 1          15) 15         29) 29         43) 43         57) 57         71) 71         85) 85         99) 99        \n"
				    + "  2) 2          16) 16         30) 30         44) 44         58) 58         72) 72         86) 86        100) 100       \n"
				    + "  3) 3          17) 17         31) 31         45) 45         59) 59         73) 73         87) 87        101) 101       \n"
				    + "  4) 4          18) 18         32) 32         46) 46         60) 60         74) 74         88) 88        102) 102       \n"
				    + "  5) 5          19) 19         33) 33         47) 47         61) 61         75) 75         89) 89        103) 103       \n"
				    + "  6) 6          20) 20         34) 34         48) 48         62) 62         76) 76         90) 90        104) 104       \n"
				    + "  7) 7          21) 21         35) 35         49) 49         63) 63         77) 77         91) 91        105) 105       \n"
				    + "  8) 8          22) 22         36) 36         50) 50         64) 64         78) 78         92) 92        106) 106       \n"
				    + "  9) 9          23) 23         37) 37         51) 51         65) 65         79) 79         93) 93        107) 107       \n"
				    + " 10) 10         24) 24         38) 38         52) 52         66) 66         80) 80         94) 94        108) 108       \n"
				    + " 11) 11         25) 25         39) 39         53) 53         67) 67         81) 81         95) 95        109) 109       \n"
				    + " 12) 12         26) 26         40) 40         54) 54         68) 68         82) 82         96) 96        110) 110       \n"
				    + " 13) 13         27) 27         41) 41         55) 55         69) 69         83) 83         97) 97        \n"
				    + " 14) 14         28) 28         42) 42         56) 56         70) 70         84) 84         98) 98        \n"
				    + "";
		String stdin = "20\n";
		
		ExecuteResult res = executeCommand(cmd,stdin);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expectOut, out);
		assertEquals(expectErr, err);
	}
	
}
