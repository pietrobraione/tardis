package exec;

import java.util.Collection;
import java.util.HashSet;

final class CoverageSet {
	private final HashSet<String> coverage = new HashSet<>();
	
	public synchronized void addAll(Collection<? extends String> coverageInfo) {
		this.coverage.addAll(coverageInfo);
	}
	
	public synchronized boolean covers(String branch) {
		return this.coverage.contains(branch);
	}

	public synchronized int size() {
		return this.coverage.size();
	}
}
