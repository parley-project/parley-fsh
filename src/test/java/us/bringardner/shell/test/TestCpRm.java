package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import us.bringardner.shell.test.AbstractConsoleTest.ExecuteResult;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class TestCpRm extends AbstractConsoleTest{

	public static String fileDate;
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("CpTestFiles");
	}
	
		
	@Test
	@Order(1)
	public void testCp_File2File() throws IOException {
		File file = new File("target/Co01.txt").getCanonicalFile();
		if( file.exists()) {
			assertTrue(file.delete(),"Can't delete existing file");
		}
		
		String source = "AbcFile.txt";
		String dest = "../target/Co01.txt";
		String cmd = "cp "+source+" "+dest;
		ExecuteResult res = executeCommand(cmd, "");
		
		assertEquals(0, res.exitCode,"Cp exit code");
		
		assertTrue(file.exists(),"File does not exist after cp");
		
		cmd = "rm "+dest;
		res = executeCommand(cmd, "");
		
		assertEquals(0, res.exitCode,"Rm exit code");		
		//file = new File("target/Co01.txt").getCanonicalFile();		
		assertFalse(file.exists(),"File still exist after rm");
		
	}

	@Test
	@Order(2)
	public void testCp_File2Dir() throws IOException {
		File dir = new File("target/CpTestDir").getCanonicalFile();
		if( !dir.exists()) {
			assertTrue(dir.mkdirs(),"Can't create "+dir);
		}
		
		File file = new File(dir,"Co01.txt").getCanonicalFile();
		if( file.exists()) {
			assertTrue(file.delete(),"Can't delete existing file");
		}
		
		String source = "AbcFile.txt";
		String dest = "../target/CpTestDir/Co01.txt";
		String cmd = "cp "+source+" "+dest;
		ExecuteResult res = executeCommand(cmd, "");
		
		
		assertEquals(0, res.exitCode,"Cp exit code");		
		assertTrue(file.exists(),"File does not exist after cp");
		cmd = "rm "+dest;
		res = executeCommand(cmd, "");
		
		
		assertEquals(0, res.exitCode,"Rm exit code");
		
		assertFalse(file.exists(),"File still exist after rm");
		
	}

	@Test
	@Order(3)
	public void testCp_MultiByWildcardFile2Dir() throws IOException {
		File dir = new File("target/CpTestDir").getCanonicalFile();
		if( !dir.exists()) {
			assertTrue(dir.mkdirs(),"Can't create "+dir);
		}
		
		for(File file : dir.listFiles()) {
			if( file.exists()) {
				assertTrue(file.delete(),"Can't delete existing file");
			}
		}
		
		String source = "Abc*";
		String dest = "../target/CpTestDir";
		String cmd = "cp "+source+" "+dest;

		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"Cp exit code");		
		
		assertEquals(4, dir.listFiles().length,"child count after cp");
		
		cmd = "rm -rf "+dest;
		res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"Rm 02 exit code only -rf should NOT fail");
		
		
		assertFalse(dir.exists(),"Dir still exist after rm");
		
	}
	
	@Test
	@Order(4)
	public void testCp_MultiByListFile2Dir() throws IOException {
		File dir = new File("target/CpTestDir").getCanonicalFile();
		if( !dir.exists()) {
			assertTrue(dir.mkdirs(),"Can't create "+dir);
		}
		
		for(File file : dir.listFiles()) {
			if( file.exists()) {
				assertTrue(file.delete(),"Can't delete existing file");
			}
		}
		
		String source = "AbcFile.js AbcFile.txt AbcFile.php";
		String dest = "../target/CpTestDir";
		String cmd = "cp "+source+" "+dest;
		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"Cp exit code");		
		
		assertEquals(3, dir.listFiles().length,"child count after cp");
		
		cmd = "rm -rf "+dest;
		res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"Rm 02 exit code only -rf should NOT fail");
		
		
		assertFalse(dir.exists(),"Dir still exist after rm");
		
	}

	@Test
	@Order(4)
	public void testCp_MultiByListFile2DirRm1by1() throws IOException {
		File dir = new File("target/CpTestDir").getCanonicalFile();
		if( !dir.exists()) {
			assertTrue(dir.mkdirs(),"Can't create "+dir);
		}
		
		for(File file : dir.listFiles()) {
			if( file.exists()) {
				assertTrue(file.delete(),"Can't delete existing file");
			}
		}
		
		String source = "AbcFile.js AbcFile.txt AbcFile.php";
		String dest = "../target/CpTestDir";
		String cmd = "cp "+source+" "+dest;

		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"Cp exit code");		
		
		assertEquals(3, dir.listFiles().length,"child count after cp");
		
		
		cmd = "rm  ../target/CpTestDir/AbcFile.txt";
		res = executeCommand(cmd, "");		
		assertEquals(0, res.exitCode,"Rm 01 exit code only txt should NOT fail");
		
		cmd = "rm  ../target/CpTestDir/AbcFile.php";
		res = executeCommand(cmd, "");		
		assertEquals(0, res.exitCode,"Rm 02 exit code only php should NOT fail");
		
		cmd = "rm  ../target/CpTestDir/AbcFile.js";
		res = executeCommand(cmd, "");		
		assertEquals(0, res.exitCode,"Rm 03 exit code only js should NOT fail");

		cmd = "rm -d "+dest;
		res = executeCommand(cmd, "");		
		assertEquals(0, res.exitCode,"Rm 04 exit code only dir should NOT fail");

		
		assertFalse(dir.exists(),"Dir still exist after rm");
		
	}

	@Test
	@Order(5)
	public void testCp_rm_i() throws IOException {
		File file = new File("target/Co01.txt").getCanonicalFile();
		if( file.exists()) {
			assertTrue(file.delete(),"Can't delete existing file");
		}
		
		String source = "AbcFile.txt";
		String dest = "../target/Co01.txt";
		String cmd = "cp "+source+" "+dest;

		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"Cp exit code");
		
		assertTrue(file.exists(),"File does not exist after cp");
		
		/*
		 * -i with no STDIN should prompt and slightly leave the file there 
		 */
		cmd = "rm -i "+dest;
		ExecuteResult result = executeCommand(cmd, "");
		String out = new String(result.bao.toByteArray());
		String err = new String(result.bae.toByteArray());
		assertEquals(0, result.exitCode,"Rm exit code");
		assertTrue(out.startsWith("remove"),"First part of the prompt is wrong");
		assertTrue(out.endsWith("CpTestFiles/../target/Co01.txt?"),"Last part of the prompt is wrong");
		
		assertEquals("", err,"Err should be empty");
		assertTrue(file.exists(),"File should exist after rm");
		
		result = executeCommand(cmd, "y\n");
		out = new String(result.bao.toByteArray());
		err = new String(result.bae.toByteArray());
		assertEquals(0, result.exitCode,"Rm exit code");		
		assertTrue(out.startsWith("remove"),"First part of the prompt is wrong");
		assertTrue(out.endsWith("CpTestFiles/../target/Co01.txt?"),"Last part of the prompt is wrong");
		
		assertEquals("", err,"Err should be empty");
		assertEquals(0, result.exitCode,"Rm exit code");		
		assertFalse(file.exists(),"File still exist after rm");
		
	}
}
