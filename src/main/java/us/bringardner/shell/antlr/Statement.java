package us.bringardner.shell.antlr;

import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssociativeArrayValueContext;
import us.bringardner.filesource.sh.FileSourceShParser.BraceArgListContext;
import us.bringardner.filesource.sh.FileSourceShParser.BraceExpansionContext;
import us.bringardner.filesource.sh.FileSourceShParser.BraceRangeContext;
import us.bringardner.filesource.sh.FileSourceShParser.Redirect_oneContext;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessStream;
import us.bringardner.shell.Console.FileDiscriptor;
import us.bringardner.shell.Console.Option;
import us.bringardner.shell.ShellContext;

public abstract class Statement {

	private enum RedirectOperator {

		Input("<"),
		Output(">"),
		OutoutNoCLobber(">|"),
		Append(">>"),
		Duplicate(">&"),
		StdinToStdOut2("&&"),
		AppendStdinAndStdout("&>>"),
		Move("<&"),
		OpenReadWrite("<>"),

		UnKnown("UnKnown");

		public final String label;
		private RedirectOperator(String label) {
			this.label = label;			
		}

		public static RedirectOperator find(String name) {
			for(RedirectOperator o : values()) {
				if( o.label.equals(name)) {
					return o;
				}
			}
			return UnKnown;
		}

	};

	public void configureRedirect(ShellContext ctx, RerdirectImpl redirectStart) throws IOException {

		if(redirectStart!=null) {
			Integer fid1 = null;
			Integer fid2 = null;

			if( redirectStart.fid!=null) {
				fid1 = Integer.parseInt(""+redirectStart.fid.getValue(ctx));
			}


			for(Redirect_oneContext redirect : redirectStart.context.redirect_one()) {
				String text = redirect.getText();
				boolean closeAfterDup = false;
				if( redirect.redirectionOperator() ==null) {
					throw new IOException("No redirect operator");
				} else {
					String pathText = null;
					if( redirect.args !=null) {
						ArgumentContext tmp = redirect.args;
						Argument a = new Argument(tmp);
						pathText=""+a.getValue(ctx);
						if( pathText.endsWith("-")) {
							closeAfterDup = true;
							pathText = pathText.substring(0,pathText.length()-1);
						}
						try {
							fid2 = Integer.parseInt(pathText);
						} catch (Exception e) {
						}
					} else {
						throw new IOException("configureRedirect no argument for path or toid");
					}


					String op = redirect.redirectionOperator().getText();
					RedirectOperator rdop = RedirectOperator.find(op);
					switch (rdop) {
					case Input: 
						FileSource file = ctx.getFileSource(pathText);
						ctx.stdin = file.getInputStream();
						if( fid1 != null) {
							FileDiscriptor fd = new FileDiscriptor(fid1, ctx.stdin,file);
							ctx.console.setFileDistcriptor(fd);								
						}
						break;
					case Output:
						file = ctx.getFileSource(pathText);
						if(file.exists() && !ctx.console.options.contains(Option.NoClobberRedirect)) {
							throw new IOException(file.getAbsolutePath()+" exists and no clobber is set");
						}
						ctx.stdout = new PrintStream( file.getOutputStream());
						if( fid1 != null) {
							FileDiscriptor fd = new FileDiscriptor(fid1, ctx.stdout,file);
							ctx.console.setFileDistcriptor(fd);								
						}
						break;
					case Append:
						file = ctx.getFileSource(pathText);
						ctx.stdout = new PrintStream( file.getOutputStream(true));
						if( fid1 != null) {
							FileDiscriptor fd = new FileDiscriptor(fid1, ctx.stdout,file);
							ctx.console.setFileDistcriptor(fd);								
						}

						break;

					case Duplicate:
						Integer fid = fid1!=null?fid1:fid2;
						if( fid == null) {
							throw new IOException("No file descriptor " );
						}

						FileDiscriptor tmp = ctx.console.getFileDistcriptor(fid);
						if( tmp == null) {
							throw new IOException("bad fid="+fid);
						}

						ctx.stdout = tmp.getOut();	
						if(closeAfterDup) {
							ctx.console.closeFileDistcriptor(fid);
						}


						break;
					case AppendStdinAndStdout:
						/*
						 * 	&>>word
						 *	This is semantically equivalent to
						 *	>>word 2>&1
						 */
						//throw new IOException("RedirectOperator Unexpected value: " + rdop);
						file = ctx.getFileSource(pathText);
						ctx.stderr = ctx.stdout = new PrintStream( file.getOutputStream(true));
						if( fid1 != null) {
							FileDiscriptor fd = new FileDiscriptor(fid1, ctx.stdout,file);
							ctx.console.setFileDistcriptor(fd);								
						}

						break;
					case Move: 
						if( fid2 == null) {
							throw new IOException("RedirectOperator Unexpected value: fid2= null " + text);	
						}
						FileDiscriptor fd = ctx.console.getFileDistcriptor(fid2);
						if( fd == null) {
							throw new IOException("bad file descriptor = "+fid2);
						}
						if( fd.getIn() == null) {
							throw new IOException("bad file descriptor (not input) = "+fid2);
						}
						ctx.stdin = fd.getIn();
						break;
					case OpenReadWrite:
						file = ctx.getFileSource(pathText);

						if(!file.exists()) {
							if(!file.createNewFile()) {
								throw new IOException("Could not create file "+file);
							}
						}
						IRandomAccessStream rad = file.getRandomAccessStream("rw");
						InputStream in = new InputStream() {

							@Override
							public int read() throws IOException {
								return rad.read();
							}
							@Override
							public int read(byte[] b) throws IOException {
								return rad.read(b);
							}
							@Override
							public int read(byte[] b, int off, int len) throws IOException {
								return rad.read(b, off, len);
							}

						};

						OutputStream out = new OutputStream() {

							@Override
							public void write(int b) throws IOException {
								rad.write(b);
							}
							@Override
							public void write(byte[] b) throws IOException {
								rad.write(b);
							}
							@Override
							public void write(byte[] b, int off, int len) throws IOException {
								rad.write(b, off, len);
							}

						};
						fd = new FileDiscriptor(fid1, in,rad);
						fd.setOut(new PrintStream(out));
						ctx.console.setFileDistcriptor(fd);

						break;

					default:
						throw new IOException("RedirectOperator Unexpected value: " + rdop);
					}
				}
			}
		}
	}

	public static boolean testEq(Object a, Object b) {
		if( a == null && b == null) {
			return true;
		}

		if( a != null ) {
			return a.equals(b);
		}

		if( b != null) {
			return b.equals(a);
		}
		return false;
	}

	protected ParserRuleContext context;
	protected Argument [] args=new Argument[0];
	private List<ArgumentContext> argCtx;


	public List<ArgumentContext> getArgCtx() {
		return argCtx;
	}

	public void setArgCtx(List<ArgumentContext> argCtx) {
		this.argCtx = argCtx;
	}

	public Statement(ParserRuleContext context) {
		this.context = context;
		if(context==null) {
			throw new RuntimeException("Null context not allowed in "+getClass());
		}
	}

	public ParserRuleContext getContext() {
		return context;
	}

	public void setContext(ParserRuleContext context) {
		this.context = context;
	}

	public Argument[] getArgs() {
		return args;
	}

	public void setArgs(Argument[] args, List<ArgumentContext> argCtx) {
		this.args = args;
		this.argCtx = argCtx;
	}

	public final int process(ShellContext ctx) throws IOException{
		int ret = 0;
		while(ctx.isPaused()) {
			try {
				Thread.sleep(10);
			} catch (Exception e) {
			}
		}

		/*
		if( context!=null) {
			// combine any arguments with no WS between them. mainly path each / is an arg:-(
			List<ParseTree> kids = context.children;
			
			int cnt = 0;
			List<List<Integer>> merge = new ArrayList<List<Integer>>(); 
			List<Integer> list = new ArrayList<Integer>();
			for (int idx = 0; idx < kids.size(); idx++) {
				ParseTree kid = kids.get(idx);
				if (kid instanceof ArgumentContext) {
					list.add(cnt++);					
				} else if (kid instanceof TerminalNode) {
					int sym = ((TerminalNode)kid).getSymbol().getType();
					if( sym == FileSourceShParser.WS) {
						if( list.size()>1) {
							merge.add(list);
						}
						list = new ArrayList<Integer>();
					}
				} else {
					list = new ArrayList<Integer>();
				}
			}
			if( list.size()>1) {
				merge.add(list);
			}
			if( !merge.isEmpty()) {
				System.out.println("final merger "+merge);
			}
		}
		*/
		
		// do brace expansion
		List<Argument> tmp = new ArrayList<Argument>();
		for(Argument a : args) {
			if( a.context.braceExpansion()!=null) {
				tmp.addAll( visit(a.context.braceExpansion(),ctx));
			} else {
				tmp.add(a);
			}
		}

		if( tmp.size() != args.length) {
			args = tmp.toArray(new Argument[tmp.size()]);
		}
		ctx.enterStatement(this);

		try {
			ret = execute(ctx);
		} finally {

			ctx.exitStatement(ret,this);
		}
		return ret;
	}

	//	braceExpansion: white* prefix=associativeArrayValue? LCURLY (braceRange|braceArgList) RCURLY suffix=associativeArrayValue?			
	private List<Argument> visit(BraceExpansionContext exp, ShellContext ctx) throws IOException {

		List<Argument> ret = new ArrayList<>();
		List<String> newArgs=null;
		String prefix = "";
		String suffix = "";
		if( exp.prefix!=null) {
			prefix = Argument.visit(exp.prefix, ctx);
		}
		if( exp.suffix!=null) {
			suffix = Argument.visit(exp.suffix, ctx);
		}

		if( exp.braceRange()!=null) {
			newArgs = visit(exp.braceRange(),ctx);
		} else if(exp.braceArgList()!=null) {
			newArgs = visit(exp.braceArgList(),ctx);
		} else {
			throw new IOException("Invalid brace expantion "+exp);
		}
		for(String val : newArgs) {
			ret.add(new Argument(prefix+val+suffix));
		}
		return ret;
	}


	//	braceArgList:associativeArrayValue (COMMA associativeArrayValue)*;
	private List<String> visit(BraceArgListContext args, ShellContext ctx) throws IOException {
		List<String> ret = new ArrayList<>();
		for(AssociativeArrayValueContext arg : args.associativeArrayValue()) {
			ret.add(Argument.visit(arg, ctx));
		}

		return ret;
	}

	//	braceRange: start=associativeArrayValue DOT_DOT end=associativeArrayValue (DOT_DOT incr=associativeArrayValue);
	private List<String> visit(BraceRangeContext range, ShellContext ctx) throws IOException {
		List<String>  ret = new ArrayList<>();
		String startStr = Argument.visit(range.start, ctx);
		String endStr   = Argument.visit(range.end, ctx);
		boolean isChar = Character.isLetter(startStr.charAt(0));
		int start = isChar?startStr.charAt(0): Integer.parseInt( startStr);
		int end = isChar?endStr.charAt(0): Integer.parseInt( endStr);

		int inc = 1;
		if( !isChar ) {
			if( range.incr !=null) {
				String tmp = Argument.visit(range.incr, ctx);
				inc = Math.abs(Integer.parseInt(tmp));
			}
		}


		if(start < end) {
			for(int idx=start; idx <=end; idx += inc) {
				if( isChar) {
					ret.add(""+((char)idx));
				} else {
					ret.add(""+idx);
				}
			}
		} else {
			for(int idx=start; idx >=end; idx -= inc) {
				if( isChar) {
					ret.add(""+((char)idx));
				} else {
					ret.add(""+idx);
				}
			}
		}

		return ret;
	}

	public String toString(ShellContext ctx) {
		StringBuilder ret = new StringBuilder();
		for(Argument a : getArgs()) {
			try {
				ret.append(""+a.getValue(ctx));
			} catch (IOException e) {
				ret.append(e.toString());
			}
		}
		return ret.toString();
	}

	public Point getLine() {
		return new Point(context.getStart().getLine(),context.getStart().getCharPositionInLine());
	}

	protected abstract int execute(ShellContext ctx) throws IOException;
}
