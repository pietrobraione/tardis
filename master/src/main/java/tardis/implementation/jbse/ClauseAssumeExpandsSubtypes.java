package tardis.implementation.jbse;

import static jbse.common.Type.className;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jbse.common.exc.InvalidInputException;
import jbse.mem.Clause;
import jbse.mem.ClauseAssumeReferenceSymbolic;
import jbse.mem.ClauseVisitor;
import jbse.val.ReferenceSymbolic;

/**
 * A {@link Clause} used in generated path constraint
 * to signify an assumption that some {@link ReferenceSymbolic} 
 * is resolved by expansion to an instance of a subclass
 * of its static type, with the exclusion of a set of 
 * forbidden expansions.
 * 
 * @author Pietro Braione
 *
 */
public final class ClauseAssumeExpandsSubtypes extends ClauseAssumeReferenceSymbolic {    
    /**
     * The set of the class names of the expansions that are forbidden. 
     * Used only if the last clause in the path condition of {@link #postState}
     * is an expands clause.
     */
    private final HashSet<String> forbiddenExpansions;
    
	/**
	 * Constructor.
	 * 
	 * @param referenceSymbolic a {@link ReferenceSymbolic}. 
	 *        It must not be {@code null}.
	 * @throws InvalidInputException if {@code referenceSymbolic == null || forbiddenExpansions == null}.
	 */
	public ClauseAssumeExpandsSubtypes(ReferenceSymbolic referenceSymbolic, Set<String> forbiddenExpansions) throws InvalidInputException { 
		super(referenceSymbolic);
		if (forbiddenExpansions == null) {
			throw new InvalidInputException("Tried to create a ClauseAssumeExpandsSubtypes with null forbiddenExpansions");
		}
		this.forbiddenExpansions = new HashSet<>(forbiddenExpansions); //safety copy
	}
	
	/**
	 * Returns the set of the class names of the
	 * forbidden expansions.
	 * 
	 * @return an unmodifiable {@link Set}{@code <}{@link String}{@code >}.
	 */
	public Set<String> forbiddenExpansions() {
		return Collections.unmodifiableSet(this.forbiddenExpansions);
	}

	@Override
	public void accept(ClauseVisitor v) throws Exception {
		//does nothing - no visitor behavior
	}

	@Override
	public String toString() {
		final ReferenceSymbolic r = getReference();
	    final String excluded =  this.forbiddenExpansions.isEmpty() ? "" : (" excluded " + this.forbiddenExpansions.stream().collect(Collectors.joining(", ")));
		return r.toString() + " expands to subclass of " + className(this.getReference().getStaticType()) + excluded;
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 2969 * result + this.forbiddenExpansions.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ClauseAssumeExpandsSubtypes other = (ClauseAssumeExpandsSubtypes) obj;
		if (!this.forbiddenExpansions.equals(other.forbiddenExpansions)) {
			return false;
		}
		return true;
	}

	@Override
	public ClauseAssumeExpandsSubtypes clone() {
		return (ClauseAssumeExpandsSubtypes) super.clone();
	}
}
