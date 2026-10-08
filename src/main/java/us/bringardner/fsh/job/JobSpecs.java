package us.bringardner.fsh.job;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Job specs: %1, %%, %+, %-, %?text (the command contains text), %text (the command starts
 * with text), or a job's process id.
 */
public final class JobSpecs {

	private JobSpecs() {
	}

	/** a spec that matches more than one job (%?ee) */
	public static class Ambiguous extends IOException {
		private static final long serialVersionUID = 1L;

		public Ambiguous(String spec) {
			// as bash says it: sl: ambiguous job spec
			super(spec.substring(1)+": ambiguous job spec");
		}
	}

	/**
	 * The job spec names in jm, or null if there is none.
	 * @throws Ambiguous if %text or %?text matches more than one job
	 */
	public static IJob find(JobManager jm, String spec) throws Ambiguous {
		if( spec.isEmpty()) {
			return null;
		}
		if( spec.charAt(0) != '%') {
			try {
				return jm.getJobByPid(Long.parseLong(spec));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		String rest = spec.substring(1);
		if( rest.isEmpty() || rest.equals("%") || rest.equals("+")) {
			return jm.current();
		}
		if( rest.equals("-")) {
			return jm.previous();
		}
		if( rest.chars().allMatch(Character::isDigit)) {
			try {
				return jm.getJob(Integer.parseInt(rest));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		boolean contains = rest.startsWith("?");
		String text = contains ? rest.substring(1) : rest;
		List<IJob> found = new ArrayList<IJob>();
		for(IJob job : jm.getJobs()) {
			String cmd = job.getCommandLine();
			if( contains ? cmd.contains(text) : cmd.startsWith(text)) {
				found.add(job);
			}
		}
		if( found.size()>1) {
			throw new Ambiguous(spec);
		}
		return found.isEmpty() ? null : found.get(0);
	}

	/** what bash says a spec is in its errors: current for %% and %+ */
	public static String describe(String spec) {
		return spec.equals("%") || spec.equals("%%") || spec.equals("%+") ? "current" : spec;
	}
}
