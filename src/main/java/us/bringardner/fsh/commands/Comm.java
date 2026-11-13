package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** comm, modeled on the GNU comm: compares two sorted files line by line. */
public class Comm extends ShellCommand{
	static String name = "comm";
	static String help = "comm [-123i] FILE1 FILE2\n"
			+ "\tCompare the sorted files FILE1 and FILE2 line by line. The output has three columns, separated\n"
			+ "\tby tabs: the lines only in FILE1, the lines only in FILE2, and the lines in both.\n"
			+ "\t-1 -2 -3   leave out that column\n"
			+ "\t-i         ignore case when comparing\n"
			+ "\t--output-delimiter=STR   separate the columns with STR\n"
			+ "\tOne of the files can be - for standard input.";

	public Comm() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "123i", "o", false, "output-delimiter=o");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.size() != 2 ) {
			ctx.stderr.println("comm: "+(a.operands.size() < 2 ? "missing operand" : "extra operand '"+a.operands.get(2)+"'"));
			return 1;
		}
		int [] st = {0};
		List<String> one = TextFiles.readLines(ctx, name, a.operands.subList(0, 1), st);
		int s1 = st[0];
		List<String> two = TextFiles.readLines(ctx, name, a.operands.subList(1, 2), st);
		if( s1 != 0 || st[0] != 0 ) {
			return 1;
		}
		String sep = a.value('o') == null ? "\t" : a.value('o');
		boolean fold = a.has('i');
		StringBuilder out = new StringBuilder();
		int i = 0, j = 0;
		while( i < one.size() || j < two.size()) {
			int cmp;
			if( i >= one.size()) {
				cmp = 1;
			} else if( j >= two.size()) {
				cmp = -1;
			} else {
				cmp = fold ? one.get(i).compareToIgnoreCase(two.get(j)) : one.get(i).compareTo(two.get(j));
			}
			if( cmp < 0 ) {
				if( !a.has('1')) {
					out.append(one.get(i)).append('\n');
				}
				i++;
			} else if( cmp > 0 ) {
				if( !a.has('2')) {
					out.append(a.has('1') ? "" : sep).append(two.get(j)).append('\n');
				}
				j++;
			} else {
				if( !a.has('3')) {
					out.append(a.has('1') ? "" : sep).append(a.has('2') ? "" : sep).append(one.get(i)).append('\n');
				}
				i++;
				j++;
			}
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return 0;
	}
}
