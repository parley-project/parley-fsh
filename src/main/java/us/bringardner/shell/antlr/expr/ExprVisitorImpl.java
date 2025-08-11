package us.bringardner.shell.antlr.expr;

import java.util.concurrent.atomic.AtomicReference;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import us.bringardner.filesource.sh.ExprParser;
import us.bringardner.filesource.sh.ExprParser.ExprContext;
import us.bringardner.filesource.sh.ExprParserBaseVisitor;
import us.bringardner.filesource.sh.FileSourceShLexer;

public class ExprVisitorImpl extends ExprParserBaseVisitor<Object>  {

	public static Expr parse(String rawCode)  {
		AtomicReference<Exception> error = new AtomicReference<>();
		
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(rawCode));
		lexer.removeErrorListeners();
		lexer.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,int charPositionInLine, String msg, RecognitionException e) {
				error.set(e);
			}
		});
		
		ExprParser parser = new ExprParser(new CommonTokenStream(lexer));
		parser.removeErrorListeners();
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
		
		ExprVisitorImpl visitor = new ExprVisitorImpl();
		Expr ret = visitor.visitExpr(parser.expr());
		
		return ret;
	}
	
	@Override
	public Expr visitExpr(ExprContext ctx)  {
		//Expr ret = new Expr(ctx);
		throw new RuntimeException("Expr is not implemented");
	}



}
