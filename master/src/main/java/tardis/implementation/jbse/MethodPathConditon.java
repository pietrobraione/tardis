package tardis.implementation.jbse;

import java.util.List;

import jbse.mem.Clause;

final class MethodPathConditon {
	private final String method;
	private final List<Clause> pathCondition;
	
	public MethodPathConditon(String method, List<Clause> pathCondition) {
		this.method = method;
		this.pathCondition = pathCondition; //no safety clone!
	}
	
	String getMethod() {
		return this.method;
	}
	
	List<Clause> getPathCondition() {
		return this.pathCondition;
	}

	@Override
	public int hashCode() {
		final int prime = 17;
		int result = 1;
		result = prime * result + ((this.method == null) ? 0 : this.method.hashCode());
		result = prime * result + ((this.pathCondition == null) ? 0 : this.pathCondition.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final MethodPathConditon other = (MethodPathConditon) obj;
		if (this.method == null) {
			if (other.method != null) {
				return false;
			}
		} else if (!this.method.equals(other.method)) {
			return false;
		}
		if (this.pathCondition == null) {
			if (other.pathCondition != null) {
				return false;
			}
		} else if (!this.pathCondition.equals(other.pathCondition)) {
			return false;
		}
		return true;
	}
	
	
}
