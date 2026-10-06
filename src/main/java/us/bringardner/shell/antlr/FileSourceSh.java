package us.bringardner.shell.antlr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import us.bringardner.filesource.sh.FileSourceShLexer;
import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.shell.Console;
import us.bringardner.shell.ShellContext;

public class FileSourceSh {

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	public static List<Statement> parse(String preProcessedCode) throws Exception {
		AtomicReference<Exception> error = new AtomicReference<>();
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(preProcessedCode));
		lexer.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,int charPositionInLine, String msg, RecognitionException e) {
				error.set(e);
			}
		});
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		//parser.removeErrorListeners();
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,int charPositionInLine, String msg, RecognitionException e) {
				if( e == null ) {
					error.set(new RuntimeException(msg));
				} else {
					error.set(e);
				}
			}

		});

		FileSourceShVisitorImpl visitor = new FileSourceShVisitorImpl();
		List<Statement> stmts = visitor.visitScript(FileSourceShVisitorImpl.parseFast(parser, FileSourceShParser::script));
		Exception e = error.get();
		if( e != null ) {
			throw e;
		}

		return stmts;
	}

	
	public static String execute(String code,String stdIn,Console console) {
		ShellContext ctx = new ShellContext(console);
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();	
		ctx.stdout=(new PrintStream(bao));
		ctx.stderr=(new PrintStream(bae));
		if( stdIn !=null) {
			System.setIn(new ByteArrayInputStream(stdIn.getBytes()));
		}
		
		executeUnprocessed(code, ctx);
		
		StringBuilder ret = new StringBuilder();
		if( bao.size()>0) {
			ret.append(new String(bao.toByteArray()));
		}
		if( bae.size()>0) {
			ret.append(new String(bae.toByteArray()));
		}
		
		return ret.toString();
	}
	
	public static void executeUnprocessed(String code,ShellContext ctx)  {
		
		ctx.enterCommand();
		try {
			String pp = ctx.console.preProcess(code, ctx);
			List<Statement> stmts = parse(pp);
			for(Statement stmt : stmts) {
				int ec = stmt.process(ctx);
				if(ec !=0) {					
					ctx.stderr.println(stmt.toString()+" exited with code="+ec);
					return;
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			ctx.stderr.println("Parsing error e="+e);			
		} finally {
			ctx.exitCommand();
		} 
		
	}

	public static String execute(String code) {
		Console console = new Console();
		
		return execute(code, null, console);
	}


}
