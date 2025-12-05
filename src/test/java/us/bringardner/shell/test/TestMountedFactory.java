package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.memory.MemoryFileSource;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;
import us.bringardner.shell.MountFactory;
import us.bringardner.shell.RootFile;

public class TestMountedFactory extends AbstractConsoleTest {

	

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
	}



	private static void fillDir(FileSource dir,int i) throws IOException {
		for(int idx=0; idx < 10; idx++ ) {
			FileSource file = dir.getChild("Test-"+i+"-"+idx+".txt");
			try(OutputStream out = file.getOutputStream()) {
				out.write("0123456789".getBytes());
			}
		}

	}



	@Test
	public void testMount() throws IOException {
		MountFactory factory = new MountFactory();

		assertTrue(factory.mount(new MemoryFileSourceFactory(), "mem"),"Failed to mount mem");


		String expectName = "/mem";
		FileSource data = factory.createFileSource(expectName);
		assertEquals(RootFile.class, data.getClass());
		assertEquals(expectName, data.getAbsolutePath());
		assertTrue(data.exists(),expectName+" does not exists");


		expectName = "/Volumes/Data";
		data = factory.createFileSource(expectName);
		assertEquals(expectName, data.getAbsolutePath());
		assertTrue(data.exists(),expectName+" does not exists");



		expectName= "/mem/Volumes/Data";
		data = factory.createFileSource(expectName);
		assertEquals(expectName, data.getAbsolutePath());


		assertFalse(data.exists(),expectName+" Should not exists");
		assertTrue(data.mkdirs(),"Can't mkdirs for "+data);
		assertTrue(data.exists(),expectName+" does not exists");

		fillDir(data, 0);
		FileSource [] kids = data.listFiles();
		assertEquals(10, kids.length);


		for (int idx = 0; idx < kids.length; idx++) {
			String expect = "/mem/Volumes/Data/Test-0-"+idx+".txt";
			String actual = kids[idx].getAbsolutePath();
			assertEquals(expect, actual);
		}
	}


	@Test
	public void testMount2() throws Exception {
		MountFactory factory = new MountFactory();
		MemoryFileSourceFactory memoryFactory = new MemoryFileSourceFactory();
		assertTrue(factory.mount(memoryFactory, "mem"),"Failed to mount mem");


		String rootName1 = "/mem";
		FileSource root1 = factory.createFileSource(rootName1);
		FileSource dir1 = root1;

		assertEquals(RootFile.class, dir1.getClass());
		assertEquals(rootName1, dir1.getAbsolutePath());
		assertTrue(dir1.exists(),rootName1+" does not exists");
		int levels = 20;

		for(int level = 0; level < levels; level++) {
			String name = "Level"+level;
			FileSource child = dir1.getChild(name);
			assertTrue(child.mkdir());
			String path = child.getAbsolutePath();
			assertTrue(path.startsWith("/mem"));
			assertTrue(path.endsWith(name));
			dir1 = child;
		}

		fillDir(dir1,1);
		FileSource kids[] = dir1.listFiles(); 
		for (int idx = 0; idx < kids.length; idx++) {
			String path = kids[idx].getAbsolutePath();
			assertTrue(path.startsWith("/mem"));
			assertTrue(path.endsWith(".txt"));
		}

		FileSource dir = factory.createFileSource(rootName1);
		assertEquals(RootFile.class, dir.getClass());
		assertEquals(rootName1, dir.getAbsolutePath());
		assertTrue(dir.exists(),rootName1+" does not exists");

		for(int level = 0; level < levels; level++) {
			String name = "Level"+level;
			FileSource child = dir.getChild(name);
			assertTrue(child.exists());
			assertTrue(child.isDirectory());
			assertTrue(dir.isChildOfMine(child));
			String path = child.getAbsolutePath();
			assertTrue(path.startsWith("/mem"));
			assertTrue(path.endsWith(name));
			dir = child;
		}
		kids = dir.listFiles(); 
		for (int idx = 0; idx < kids.length; idx++) {
			String path = kids[idx].getAbsolutePath();
			assertTrue(path.startsWith("/mem"));
			assertTrue(path.endsWith(".txt"));
		}

		FileSource root = memoryFactory.listRoots()[0];
		String rootName = "/";
		assertEquals(MemoryFileSource.class, root.getClass());
		assertEquals(rootName, root.getAbsolutePath());
		assertTrue(root.exists(),rootName+" does not exists");
		FileSource mdir = root;
		for(int level = 0; level < levels; level++) {			
			String name = "Level"+level;
			kids = mdir.listFiles();
			assertEquals(1, kids.length);
			FileSource child = kids[0];
			assertTrue(child.exists());
			assertTrue(child.isDirectory());
			assertTrue(mdir.isChildOfMine(child));
			String path1 = child.getAbsolutePath();
			assertTrue(path1.startsWith("/Level0"));
			assertTrue(path1.endsWith(name));

			FileSource child2 = factory.createFileSource("/mem"+path1);
			assertTrue(child2.exists());
			assertTrue(child2.isDirectory());
			assertTrue(root1.isChildOfMine(child2));
			String path2 = child2.getAbsolutePath();
			assertTrue(path2.startsWith(rootName1));
			assertTrue(path2.endsWith(name));
			compare(child,child2);
			mdir = child;
			root1 = child2;
		}

	}


	private void compare(FileSource child1, FileSource child2) throws Exception {
		Method[] methods = FileSource.class.getDeclaredMethods();
		for(Method method : methods) {
			String name = method.getName();

			if( method.getParameterCount()==0) {
				if( !name.startsWith("getParent")) {
					if( !name.startsWith("list")) {
						if( !name.endsWith("Stream")) {
							if( !name.endsWith("Path")) {
								method.setAccessible(true);
								Object r1 = method.invoke(child1);
								Object r2 = method.invoke(child2);
								assertEquals(r1, r2,name);
							}
						}
					}
				}
			}

		}		
	}


	@Test
	public void testMount3() throws Exception {
		String code = "connect memory /mem\n";
		ExecuteResult res = executeCommand(code, "");

		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("memory connected as /mem\n",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);

		code = "cd /mem\n";

		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);

		code = 
				"for (( i=1; i<=10; i++ ))\n"
						+ "do  \n"
						+ " mkdir \"Level$i\"\n"
						+ " cd \"Level$i\"\n"
						//+ " pwd\n"
						+ "done"
						;

		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);

		code = "ls /mem/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9\n";
		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("Level10\n",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);


	}



	@Test
	public void testMkdir1() throws Exception {
		String code = "connect memory /mem2\n";
		ExecuteResult res = executeCommand(code, "");

		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("memory connected as /mem2\n",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);

		code = "cd /mem2\n";

		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);

		code = 
				"for (( i=1; i<=10; i++ ))\n"
						+ "do  \n"
						+ " mkdir -v \"Level$i\"\n"
						+ " cd \"Level$i\"\n"
						+ "done"
						;

		String expect = "mkdir: /mem2/Level1\n"
				+ "mkdir: /mem2/Level1/Level2\n"
				+ "mkdir: /mem2/Level1/Level2/Level3\n"
				+ "mkdir: /mem2/Level1/Level2/Level3/Level4\n"
				+ "mkdir: /mem2/Level1/Level2/Level3/Level4/Level5\n"
				+ "mkdir: /mem2/Level1/Level2/Level3/Level4/Level5/Level6\n"
				+ "mkdir: /mem2/Level1/Level2/Level3/Level4/Level5/Level6/Level7\n"
				+ "mkdir: /mem2/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8\n"
				+ "mkdir: /mem2/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9\n"
				+ "mkdir: /mem2/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9/Level10\n"
				;
		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals(expect,new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);

		code = "ls /mem2/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9\n";
		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("Level10\n",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);


	}



	@Test
	public void testMkdir2() throws Exception {
		String code = "connect memory /mem3\n";
		ExecuteResult res = executeCommand(code, "");

		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("memory connected as /mem3\n",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);

		code = "cd /mem3\n";

		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);

		code = 
				"  mkdir -pv /mem3/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9/Level10\n"

				;

		String expect = "mkdir: /mem3/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9/Level10\n"
				;
		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals(expect,new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);

		code = "ls /mem3/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9\n";
		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("Level10\n",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);


	}



	@Test
	public void testMkdir3() throws Exception {

		boolean tmp = showError;showError=false;
		String code = "connect memory /mem4\n";
		ExecuteResult res = executeCommand(code, "");

		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("memory connected as /mem4\n",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);


		code = "mkdir -v /mem4/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9/Level10\n"
				;

		String expect = "mkdir: could not create directory for /mem4/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9/Level10\n"				
				;
		res = executeCommand(code, "");
		showError=tmp;
		assertEquals(expect,new String(res.bae.toByteArray()));
		assertEquals("",new String(res.bao.toByteArray()));
		assertEquals(1, res.exitCode);

		code = "ls -l /mem4/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9\n";
		res = executeCommand(code, "");
		assertEquals("ls: /mem4/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9 no such file or directory\n",new String(res.bae.toByteArray()));
		assertEquals("",new String(res.bao.toByteArray()));
		assertEquals(1, res.exitCode);


	}



	@Test
	public void testMkdir5() throws Exception {
		String code = "connect memory /mem5\n";
		ExecuteResult res = executeCommand(code, "");
	
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("memory connected as /mem5\n",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
	
		code = "cd /mem5\n";
	
		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
	
		code = 
				"for (( i=1; i<=10; i++ ))\n"
						+ "do  \n"
						+ " mkdir \"Level$i\"\n"
						+ " cd \"Level$i\"\n"
						+ "done"
						;
	
		String expect = ""
				;
		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals(expect,new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
	
		code = "ls /mem5/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8/Level9\n";
		res = executeCommand(code, "");
		assertEquals("",new String(res.bae.toByteArray()));
		assertEquals("Level10\n",new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
	
	
	}
}
