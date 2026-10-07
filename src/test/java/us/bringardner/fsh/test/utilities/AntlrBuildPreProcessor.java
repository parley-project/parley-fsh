package us.bringardner.fsh.test.utilities;

import java.io.File;
import java.io.IOException;

public class AntlrBuildPreProcessor {


	public static void main(String[] args) throws IOException {
		File file = new File(".").getCanonicalFile();

		String out=file.getAbsolutePath()+"/generated/us/bringardner/fsh/parser";
		String src = file.getAbsolutePath()+"/Antlr4";

		String [] arg2 = {"-listener","-visitor","-o",out,"-package","us.bringardner.fsh.parser",src+"/FileSourceShPreProcessor.g4"};
		org.antlr.v4.Tool.main(arg2);

	}


}
