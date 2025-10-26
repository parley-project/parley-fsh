package us.bringardner.shell.antlr;

import java.awt.Point;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.File_addressContext;
import us.bringardner.filesource.sh.FileSourceShParser.RedirectContext;
import us.bringardner.filesource.sh.FileSourceShParser.Redirect_oneContext;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.Console.Option;
import us.bringardner.shell.ShellContext;

public abstract class Statement {

	public void configureRedirect(ShellContext ctx, RedirectContext redirectStart) throws IOException {

		if(redirectStart!=null && redirectStart.redirect_one()!=null) {


			for(Redirect_oneContext redirect : redirectStart.redirect_one()) {
				Integer fromId = null;
				Integer toId = null;
				File_addressContext fileAddress = redirect.file_address();
				if( fileAddress !=null) {
					if( fileAddress.fromId!=null) {
						try {
							fromId = Integer.parseInt(fileAddress.fromId.getText());
						} catch (Exception e) {				
						}
					}
					if( fileAddress.toId!=null) {
						try {
							toId = Integer.parseInt(fileAddress.toId.getText());
						} catch (Exception e) {				
						}
					}
					if( fromId !=null || toId!=null) {
						throw new RuntimeException("CommandStatement configureRedirect from or to id what to do? from= "+fromId+" t="+toId);
					}
				}

				if( redirect.redirectionOperator() !=null) {
					String op = redirect.redirectionOperator().getText();
					int id = 1;
					if( op.charAt(0) == '1') {
						id = 1;
						op = op.substring(1);
					} else if( op.charAt(0) == '2') {
						id = 2;
						op = op.substring(1);
					}

					String pathText = null;
					if( redirect.path() !=null) {
						pathText = redirect.path().getText(); 
					} else if( redirect.ID() !=null) {
						pathText = redirect.ID().getText(); 
					} else {
						throw new IOException("configureRedirect no path or ID");
					}

					FileSource file = ctx.getFileSource(pathText);
					if( file.isDirectory()) {
						throw new RuntimeException("Cannot redirect to or from a directory ="+file);
					}

					if( op.equals("<")) {
						// only one input type
						if( !file.exists() ) {
							throw new RuntimeException("CommandStatement configureRedirect input file does not exists ="+file);
						}
						if( id != 1 ) {
							throw new RuntimeException("CommandStatement configureRedirect input file id=0... what to do ="+file);
						}

						ctx.stdin = file.getInputStream();
					} else {
						OutputStream out = null;
						OutputStream err = null;

						if( op.equals(">")) {
							if(file.exists() && !ctx.console.options.contains(Option.NoClobberRedirect)) {
								throw new RuntimeException(file.getAbsolutePath()+" exists and no clobber is set");
							}
							if( id == 1) {
								out = file.getOutputStream();
							} else {
								err = file.getOutputStream();
							}

						} else if( op.equals(">|")) {			
							if( id == 1) {
								out = file.getOutputStream();
							} else {
								err = file.getOutputStream();
							}

						} else if( op.equals(">&") || op.equals("&>")) {
							//This is semantically equivalent to >word 2>&1
							out = file.getOutputStream();
							err = out;
						} else if( op.equals(">>")) {
							if( id == 1) {
								out = file.getOutputStream(true);
							} else {
								err = file.getOutputStream(true);
							}

						} else if( op.equals("&>>")) {
							out = file.getOutputStream(true);
							err = out;
						} else {
							throw new RuntimeException("Unknown redirect op = "+op);
						}
						if( out !=null) {
							ctx.stdout = new PrintStream(out);
						}
						if( err !=null) {
							ctx.stderr = new PrintStream(err);
						}
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

	public void setArgs(Argument[] args) {
		this.args = args;
	}

	public final int process(ShellContext ctx) throws IOException{
		int ret = 0;
		while(ctx.isPaused()) {
			try {
				Thread.sleep(10);
			} catch (Exception e) {
			}
		}
		ctx.enterStatement(this);

		try {
			ret = execute(ctx);
		} finally {

			ctx.exitStatement(ret,this);
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
