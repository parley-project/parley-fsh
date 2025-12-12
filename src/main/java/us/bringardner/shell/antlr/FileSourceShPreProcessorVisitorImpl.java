package us.bringardner.shell.antlr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import us.bringardner.filesource.sh.FileSourceShLexer;
import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.filesource.sh.FileSourceShParser.MathExpressionContext;
import us.bringardner.filesource.sh.FileSourceShPreProcessorBaseVisitor;
import us.bringardner.filesource.sh.FileSourceShPreProcessorLexer;
import us.bringardner.filesource.sh.FileSourceShPreProcessorParser;
import us.bringardner.filesource.sh.FileSourceShPreProcessorParser.Pp_parameterContext;
import us.bringardner.filesource.sh.FileSourceShPreProcessorParser.PpcodeContext;
import us.bringardner.filesource.sh.FileSourceShPreProcessorParser.PpcommandContext;
import us.bringardner.filesource.sh.FileSourceShPreProcessorParser.PpexprContext;
import us.bringardner.filesource.sh.FileSourceShPreProcessorParser.PpvariableContext;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.CommandSubstitutionStatement;

public class FileSourceShPreProcessorVisitorImpl extends FileSourceShPreProcessorBaseVisitor<Object>  {
	// process 
	static class PreprocessResult {
		List<ParserRuleContext> items ;
		Exception error;;		
	}

	public static PreprocessResult parse(String preProcessedCode)  {
		AtomicReference<Exception> error = new AtomicReference<>();
		FileSourceShPreProcessorLexer lexer = new FileSourceShPreProcessorLexer(CharStreams.fromString(preProcessedCode));
		lexer.removeErrorListeners();
		lexer.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,int charPositionInLine, String msg, RecognitionException e) {
				error.set(e);
			}
		});
		FileSourceShPreProcessorParser parser = new FileSourceShPreProcessorParser(new CommonTokenStream(lexer));
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
		PreprocessResult ret = new PreprocessResult();
		FileSourceShPreProcessorVisitorImpl visitor = new FileSourceShPreProcessorVisitorImpl();
		ret.items = visitor.visitPpcode(parser.ppcode());
		ret.error = error.get(); 

		return ret;
	}

	@Override
	public List<ParserRuleContext> visitPpcode(PpcodeContext ctx) {
		// ppcode: (ppcommand|ppexpr|ppvariable|pptext|pp_parameter|PPID)* EOF;

		List<ParserRuleContext> ret = new ArrayList<>();
		for(PpcommandContext item : ctx.ppcommand()) {
			ret.add(item);			
		}
		for(PpexprContext item : ctx.ppexpr()) {
			ret.add(item);			
		}
		for(PpvariableContext item : ctx.ppvariable()) {
			ret.add(item);			
		}
		for(Pp_parameterContext item : ctx.pp_parameter()) {
			ret.add(item);
		}

		

		Collections.sort(ret, new Comparator<ParserRuleContext>() {

			@Override
			public int compare(ParserRuleContext o1, ParserRuleContext o2) {
				return o1.start.getStartIndex()-o2.start.getStartIndex();
			}

		});
		return ret;
	}

	/*
	public static String processBraceExpansion(String code1, ShellContext sc) {
		if( code1.indexOf('{')<0 || code1.indexOf('}')<0) {
			return code1;
		}

		PreprocessResult result = parse(code1);
		if( result.items.size()==0) {
			return code1;
		}

		List<String> chunks = new ArrayList<>();
		List<ParserRuleContext> items = new ArrayList<>();
		for(int idx=0,sz=result.items.size(); idx < sz; idx++) {
			ParserRuleContext rule = result.items.get(idx);

			if (rule instanceof Pp_brace_expantionContext) {
				String txt = rule.getText();
				
				Pp_brace_expantionContext be = (Pp_brace_expantionContext)rule;
				items.add(be);
				Token tmp1 = be.increment;
				
				int inc = be.increment==null?1:Integer.parseInt(be.increment.getText());
				String pre = be.preamble == null ?"":be.preamble.getText();
				String post = be.postscript == null ?"":be.postscript.getText();
				StringBuilder buf = new StringBuilder();
				if( be.start.letter != null) {
					//   char
					char start = be.start.letter.getText().charAt(0);
					char end   = be.end.letter.getText().charAt(0);
					for(char c=start; c <= end; c+=inc) {
						if( !buf.isEmpty()) {
							buf.append(' ');
						}
						buf.append(pre+c+post);
					}
				} else {
					// numbers
					int start = Integer.parseInt(be.start.number.getText());
					int end   = Integer.parseInt(be.end.number.getText());
					for(int c=start; c <= end; c+=inc) {
						if( !buf.isEmpty()) {
							buf.append(' ');
						}
						buf.append(pre+c+post);
					}
				}
				chunks.add(buf.toString());	
			}
		}

		String ret = code1;
		if( !chunks.isEmpty()) {
			ret = merge(code1, items, chunks);
		}
		return   ret.toString();
	}
*/
	
	public static String processString(String code1, ShellContext sc) {
		if( !code1.contains("$")) {
			return code1;
		}

		PreprocessResult result = parse(code1);
		if( result.items.size()==0) {
			return code1;
		}

		//  execute and expand each item left to right
		List<String> chunks = new ArrayList<>();
		List<ParserRuleContext> items = new ArrayList<>();
		for(int idx=0,sz=result.items.size(); idx < sz; idx++) {
			ParserRuleContext rule = result.items.get(idx);

			//code: (command|expr|variable|text|ID)* EOF;
			if (rule instanceof PpcommandContext) {
				items.add(rule);
				PpcommandContext v = (PpcommandContext) rule;
				String cmd = rule.getText();
				if(v.pp_backtick_command()!=null) {
					cmd = cmd.substring(1,cmd.length()-1);
				} else if(v.pp_dollar_command()!=null) {
					cmd = cmd.substring(2,cmd.length()-1);
				} else {
					throw new RuntimeException("Invalid command ="+rule);
				}
				
				CommandSubstitutionStatement cs = new CommandSubstitutionStatement(rule);
				String val = null;
				if( cs.execute(cmd, sc)!=0) {
					val = "Expansion error "+cmd+" exitCode="+cs.getExitCode();
					if( !cs.getStderr().isBlank()) {
						val += "\n"+cs.getStderr();
					}
				} else {
					val = cs.getStdout();
				}
				
				chunks.add(val);
				

			} else if (rule instanceof PpexprContext) {
				items.add(rule);
				PpexprContext v = (PpexprContext) rule;
				String expStr = v.getText();
				expStr = expStr.replaceAll("[/]", ":^:");
				FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(expStr));
				FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
				FileSourceShVisitorImpl visitor = new FileSourceShVisitorImpl();
				MathExpressionContext me = parser.mathExpression();
				Expression exprCtx = visitor.visitMathExpression(me);

				Object val = exprCtx.evaluate(sc);
				chunks.add(""+val);
			} else if (rule instanceof PpvariableContext) {
				items.add(rule);
				PpvariableContext v = (PpvariableContext) rule;
				String name = v.getText();
				if(v.PPID()!=null) {
					name = v.PPID().getText();
				}

				String val = ""+sc.getVariable(name);
				chunks.add(val);				
			}  else if (rule instanceof Pp_parameterContext) {
				items.add(rule);
				String str = rule.getText();
				FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(str));
				FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
				Parameter p = new Parameter(parser.parameter());

				Object val = p.evaluate(sc);
				chunks.add(""+val);

			}
				


		}
		// plug in values right to left
		String ret = merge(code1, items, chunks);

		return ret;
	}

	private static String merge(String code1,List<ParserRuleContext> items,List<String> chunks) {
		String ret = code1;
		for(int idx=items.size()-1; idx >=0; idx--) {
			ParserRuleContext rule = items.get(idx);
			int start = rule.start.getStartIndex();
			int end = rule.stop.getStopIndex();
			String left  = ret.substring(0,start);
			String right = ret.substring(end+1);
			String chunk = chunks.get(idx);
			ret = left+chunk+right;
		}

		return ret;
	}



}
