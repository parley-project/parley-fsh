package us.bringardner.shell.antlr;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.filesource.sh.FileSourceShParser.Char_classContext;
import us.bringardner.filesource.sh.FileSourceShParser.Compare_primeContext;
import us.bringardner.filesource.sh.FileSourceShParser.File_testContext;
import us.bringardner.filesource.sh.FileSourceShParser.Regular_expressionContext;
import us.bringardner.filesource.sh.FileSourceShParser.Rx_patternContext;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class ComparePrime {

	private Compare_primeContext ctx;
	/*


compare
    : TRUE
    | FALSE
    | NUMBER    
    | string    
    | left=compare EQUALITY right=compare
    | left=compare NOT_EQ right=compare
    | left=compare LT_EQ right=compare
    | left=compare GT_EQ right=compare
    | left=compare LT right=compare
    | left=compare GT right=compare
    | expression
    ;

	 */
	public ComparePrime(Compare_primeContext ctx2) {

		this.ctx = ctx2;
	}

	public Object getValue(ShellContext sc) {
		Object ret = false;
		if( ctx.file_test()!=null) {
			ret = getValue(ctx.file_test(),sc);
		} else if( ctx.boolean_()!=null) {
			ret = ctx.boolean_().TRUE() != null;
		} else if( ctx.NUMBER()!=null) {
			String tmp = ctx.NUMBER().getText();
			if( tmp.indexOf('.')>=0) {
				ret = Double.parseDouble(tmp);
			} else {
				ret = Integer.parseInt(tmp);
			}			
		} else if( ctx.string()!=null) {
			String stringVal = ctx.string().getText();
			stringVal = stringVal.substring(1,stringVal.length()-1);
			if( ctx.string().SQ_STRING()!=null) {
				ret = stringVal;
			} else if( ctx.string().DQ_STRING()!=null) {
				ret = FileSourceShPreProcessorVisitorImpl.processString(stringVal, sc);
			}else {
				throw new RuntimeException("Invalide string ="+ctx.string());
			}			
		} else if( ctx.expression()!=null) {
			Expression exp = new Expression(ctx.expression());
			ret = exp.evaluate(sc);
		} else if( ctx.left!=null && ctx.right!=null) {
			Object left = new ComparePrime(ctx.left).getValue(sc);
			Object right = new ComparePrime(ctx.right).getValue(sc);
			if( ctx.EQUALITY()!=null) {
				ret = isEq(left,right);
			} else if( ctx.NOT_EQ()!=null) {
				ret = isNotEq(left,right);
			} else if( ctx.LT_EQ()!=null) {
				ret = isLtEq(left,right);
			} else if( ctx.GT_EQ()!=null) {
				ret = isGtEq(left,right);
			} else if( ctx.LT()!=null) {
				ret = isLt(left,right);
			} else if( ctx.GT()!=null) {
				ret = isGt(left,right);
			} else {
				throw new RuntimeException("Invlid compare, no op ="+ctx);
			}
		} else if( ctx.left!=null && ctx.RX_EQUALITY()!=null && ctx.regular_expression()!=null) {
			Object val = new ComparePrime(ctx.left).getValue(sc);
			String rx  = getValue(ctx.regular_expression(),sc);
			Pattern p = Pattern.compile(rx);
			Matcher m = p.matcher(val.toString());
			ret = m.find();
			//System.out.println("ret="+ret+" val='"+val+"' rx2='"+rx2+"'");
		}  else {
			throw new RuntimeException("Invlid compare, missing left or right ="+ctx);
		}
		return ret;
	}

	/*
regular_expression:	rx_pattern+ ;
	 */
	private String getValue(Regular_expressionContext rx, ShellContext sc) {
		StringBuilder ret = new StringBuilder();
		if( rx.rx_pattern()!=null) {
			for(Rx_patternContext p : rx.rx_pattern()) {
				ret.append(getValue(p,sc));
			}
		}
		
		return ret.toString();
	}

	/*

rx_pattern
    : ESC
    | variable
    | string  
    | ID
    | DOLLAR
    | NOT
    | regex    
    | STAR 
    | QUESTION
    | NUMBER
    | char_class_list
    | '(' rx_pattern+ ')'
    ;

	 */

	private String getValue(Rx_patternContext p,ShellContext sc) {
		StringBuilder ret = new StringBuilder();
		if(p.variable()!=null ) {
			ret.append(""+sc.getVariable(p.variable()));
		} else if(p.string()!=null ) {
			String stringVal = p.string().getText();
			stringVal = stringVal.substring(1,stringVal.length()-1);
			if( p.string().SQ_STRING()!=null) {
				ret.append(stringVal);
			} else if( p.string().DQ_STRING()!=null) {
				ret.append(FileSourceShPreProcessorVisitorImpl.processString(stringVal, sc));
			}
		} else if( p.char_class_list()!=null ) {
			for(Char_classContext p2 : p.char_class_list().char_class()) {
				ret.append(getValue(p2,sc));
			}
		} else {
			ret.append(p.getText());
		}
		
		return ret.toString();
	}
	
	private String getValue(Char_classContext ctx, ShellContext sc) {
		String ret = ctx.getText();
		int idx = ret.indexOf(":^");
		if( idx > 0 ) {
			String left = ret.substring(0,idx+1);
			String right = ret.substring(idx+2);
			String tmp = left+right;
			String tmp2 = ShellCommand.posixToJava(tmp);
			ret = "[^"+tmp2+"]";
		} else {
			ret = ShellCommand.posixToJava(ret);
		}
		return ret;
	}

	private Boolean getValue(File_testContext file_test, ShellContext sc) {
		Boolean ret = null;
		try {

			String opStr = ctx.file_test().op.getText().trim();
			
			char op = opStr.toString().charAt(1);
			Argument a = new Argument(ctx.file_test().target);
			Object val = a.getValue(sc);
			FileSource file = sc.getFileSource(""+val);
			
			switch (op) {

			/*
-a file
True if file exists.
			 */
			case 'a':
				ret = file.exists();
			break;
			/*
-b file
True if file exists and is a block special file.
			 */
			case 'b':ret=false;
			break;
			/*
-c file
True if file exists and is a character special file.
			 */
			case 'c':
				ret = false;
				break;
			/*
-d file
True if file exists and is a directory.
			 */
			case 'd':
				ret = file.isDirectory();
				break;
			/*
-e file
True if file exists.
			 */
			case 'e':
				ret = file.exists();
				break;
				
			/*
-f file
True if file exists and is a regular file.
			 */
			case 'f':
				ret = file.isFile();
				break;
			/*
-g file
True if file exists and its set-group-id bit is set.
			 */
			case 'g':ret = false;break;
			/*
-h file
True if file exists and is a symbolic link.
			 */
			case 'h':
				if( file.exists()) {
				FileSource link = file.getLinkedTo();
				ret = link != null;
				}
				break;
			/*
-k file
True if file exists and its "sticky" bit is set.
			 */
			case 'k':ret=false;break;
			/*
-p file
True if file exists and is a named pipe (FIFO).
			 */
			case 'p':ret=false;break;
			/*
-r file
True if file exists and is readable.
			 */
			case 'r':
				ret = file.canRead();
				break;
			/*
-s file
True if file exists and has a size greater than zero.
			 */
			case 's':
				ret = file.length()>0;
				break;
			/*
-t fd
True if file descriptor fd is open and refers to a terminal.
			 */
			case 't':
				//TODO: True if file descriptor fd is open and refers to a terminal.
				ret = false;
				break;
			/*
-u file
True if file exists and its set-user-id bit is set.
			 */
			case 'u':
				ret = false;
				break;
			/*
-w file
True if file exists and is writable.
			 */
			case 'w':
				ret = file.canWrite();
				break;
			/*
-x file
True if file exists and is executable.
			 */
			case 'x':
				ret = file.canExecute();
				break;
			/*
-G file
True if file exists and is owned by the effective group id.
			 */
			case 'G':
				//TODO: True if file exists and is owned by the effective group id.
				break;
			/*
-L file
True if file exists and is a symbolic link.
			 */
			case 'L':
				if( file.exists()) {
					FileSource link = file.getLinkedTo();
					ret = link != null;
					}
				break;
			/*
-N file
True if file exists and has been modified since it was last read.
			 */
			case 'N':
				//TODO: True if file exists and has been modified since it was last read.
				break;
			/*
-O file
True if file exists and is owned by the effective user id.
			 */
			case 'O':
				//TODO: True if file exists and is owned by the effective user id.
				break;
			/*
-S file
True if file exists and is a socket.
			 */
			case 'S':ret = false;break;
			/*
file1 -ef file2
True if file1 and file2 refer to the same device and inode numbers.

file1 -nt file2
True if file1 is newer (according to modification date) than file2, or if file1 exists and file2 does not.

file1 -ot file2
True if file1 is older than file2, or if file2 exists and file1 does not.

-o optname
True if the shell option optname is enabled. The list of options appears in the description of the -o option to the set builtin (see The Set Builtin).

-v varname
True if the shell variable varname is set (has been assigned a value).
			 */
			case 'v':break;
			/*
-R varname
True if the shell variable varname is set and is a name reference.
			 */
			case 'R':break;
			/*
-z string
True if the length of string is zero.
			 */
			case 'z':break;
			/*
-n string
string
True if the length of string is non-zero.
			 */
			case 'n':break;
			/*

			 */
			default:
				sc.stderr.println("Unexpected value: " + op);
			}
		} catch (IOException e) {
		}	
		return ret;
	}

	private Object isGt(Object left, Object right) {
		Object ret = false;

		if (left instanceof Double || right instanceof Double) {
			double d1 = toDouble(left);
			double d2 = toDouble(right);
			ret = d1 > d2;
		} else if (left instanceof Integer) {
			int i1 = (Integer) left;
			int i2 = toInt(right);
			ret = i1 > i2;
		}  else if (left instanceof String) {
			String s1 = (String)left;
			String s2 = ""+right;
			ret = s1.compareTo(s2) > 0;
		} else if (left instanceof Boolean) {
			Boolean b1 = (Boolean)left;
			Boolean b2 = toBoolean(right);
			ret = b1.compareTo(b2) > 0;			
		} 

		return ret;
	}

	private boolean toBoolean(Object right) {
		if (right instanceof Boolean) {
			return (Boolean)right;			
		} else if (right instanceof Number) {
			return ((Number)right).doubleValue()!=0.0;			
		} else if (right instanceof String) {
			return ((String)right).equalsIgnoreCase("true");			
		}
		return false;
	}

	private int toInt(Object right) {
		if (right instanceof Number) {
			Number n = (Number) right;
			return n.intValue();
		} else if (right instanceof String) {			
			return Integer.parseInt(((String)right));
		} else if (right instanceof Boolean) {			
			return ((Boolean)right)?1:0;
		} else {
			throw new RuntimeException("What to do with type?"+right);
		}		
	}

	private double toDouble(Object right) {
		if (right instanceof Number) {
			Number n = (Number) right;
			return n.doubleValue();
		} else if (right instanceof String) {			
			return Double.parseDouble((String)right);
		} else if (right instanceof Boolean) {			
			return ((Boolean)right)?1.0:0.0;
		} else {
			throw new RuntimeException("What to do with type?"+right);
		}
	}

	private Object isLt(Object left, Object right) {
		Object ret = false;

		if (left instanceof Double || right instanceof Double) {
			double d1 = toDouble(left);
			double d2 = toDouble(right);
			ret = d1 < d2;
		} else if (left instanceof Integer) {
			int i1 = (Integer) left;
			int i2 = toInt(right);
			ret = i1 < i2;
		}  else if (left instanceof String) {
			String s1 = (String)left;
			String s2 = ""+right;
			ret = s1.compareTo(s2) < 0;
		} else if (left instanceof Boolean) {
			Boolean b1 = (Boolean)left;
			Boolean b2 = toBoolean(right);
			ret = b1.compareTo(b2) < 0;			
		} 

		return ret;
	}

	private Object isGtEq(Object left, Object right) {
		Object ret = false;

		if (left instanceof Double || right instanceof Double) {
			double d1 = toDouble(left);
			double d2 = toDouble(right);
			ret = d1 >= d2;
		} else if (left instanceof Integer) {
			int i1 = (Integer) left;
			int i2 = toInt(right);
			ret = i1 >= i2;
		}  else if (left instanceof String) {
			String s1 = (String)left;
			String s2 = ""+right;
			ret = s1.compareTo(s2) >= 0;
		} else if (left instanceof Boolean) {
			Boolean b1 = (Boolean)left;
			Boolean b2 = toBoolean(right);
			ret = b1.compareTo(b2) >= 0;			
		} 

		return ret;
	}

	private Object isLtEq(Object left, Object right) {
		Object ret = false;

		if (left instanceof Double || right instanceof Double) {
			double d1 = toDouble(left);
			double d2 = toDouble(right);
			ret = d1 <= d2;
		} else if (left instanceof Integer) {
			int i1 = (Integer) left;
			int i2 = toInt(right);
			ret = i1 <= i2;
		}  else if (left instanceof String) {
			String s1 = (String)left;
			String s2 = ""+right;
			ret = s1.compareTo(s2) <= 0;
		} else if (left instanceof Boolean) {
			Boolean b1 = (Boolean)left;
			Boolean b2 = toBoolean(right);
			ret = b1.compareTo(b2) <= 0;			
		} 

		return ret;

	}

	private Object isNotEq(Object left, Object right) {
		Object ret = false;

		if (left instanceof Double || right instanceof Double) {
			double d1 = toDouble(left);
			double d2 = toDouble(right);
			ret = d1 != d2;
		} else if (left instanceof Integer) {
			int i1 = (Integer) left;
			int i2 = toInt(right);
			ret = i1 != i2;
		}  else if (left instanceof String) {
			String s1 = (String)left;
			String s2 = ""+right;
			ret = s1.compareTo(s2) != 0;
		} else if (left instanceof Boolean) {
			Boolean b1 = (Boolean)left;
			Boolean b2 = toBoolean(right);
			ret = b1.compareTo(b2) != 0;			
		} 

		return ret;

	}

	private Object isEq(Object left, Object right) {
		Object ret = false;

		if (left instanceof Double || right instanceof Double) {
			double d1 = toDouble(left);
			double d2 = toDouble(right);
			ret = d1 == d2;
		} else if (left instanceof Integer) {
			int i1 = (Integer) left;
			int i2 = toInt(right);
			ret = i1 == i2;
		}  else if (left instanceof String) {
			String s1 = (String)left;
			String s2 = ""+right;
			ret = s1.compareTo(s2) == 0;
		} else if (left instanceof Boolean) {
			Boolean b1 = (Boolean)left;
			Boolean b2 = toBoolean(right);
			ret = b1.compareTo(b2) == 0;			
		} else {
			throw new RuntimeException("Left is unknown type ="+left);
		}

		return ret;

	}

	public boolean evaluate(ShellContext sc) {
		Object val = getValue(sc);
		boolean ret = toBoolean(val)==true;
		return ret;
	}


}
