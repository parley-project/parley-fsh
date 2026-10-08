package us.bringardner.fsh.job;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Job specs: %1, %%, %+, %-, %?text, or a process id.
 */
public final class JobSpecs {

	private JobSpecs() {
	}

	/** the index of the job spec names in jm, or -1 */
	public static int parse(JobManager jm,String tmp) throws IOException {
		List<IJob> jobs = jm.getJobs();
		int jobSize = jobs.size();
		
		if( tmp.charAt(0) == '%') {
			tmp = tmp.substring(1);
			if(Character.isDigit(tmp.charAt(0)) ) {
				int i = Integer.parseInt(tmp)-1;
				if( i>=0 && i< jobSize) {
					return i;
				}
			} else {
				switch (tmp.charAt(0)){
				case '%':// current
				case '+':
					if( jobSize > 0 ) {
						return jobSize-1;
					}
					break;
				case '-':
					if( jobSize > 1 ) {
						return jobSize-2;
					}
					break;
				case '?'://Using ‘%?ce’, on the other hand, refers to any job containing the string ‘ce’ in its command line. If the prefix or substring matches more than one job, Bash reports an error.
					//kill: ee: ambiguous job spec
					String name  = tmp.substring(1);
					List<IJob> found = new ArrayList<IJob>();
					for(IJob job : jobs) {
						String cmd = job.getCommandLine();
						if( cmd.contains(name) ) {
							found.add(job);
						}
					}
					if( found.size()==0) {
						return -1;
					}
					if( found.size()>1) {
						throw new IOException("? "+name+": ambiguous job spec");
					}
					return found.get(0).getJobNumber();
				default:
					throw new IllegalArgumentException("Unexpected value: " + tmp.charAt(0));
				}
			}
		} else {
			try {
				int ret = Integer.parseInt(tmp);
				return ret;
			} catch (Exception e) {
			}
		}
		return -1;
	}

}
