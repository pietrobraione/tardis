package avlTest;

public class RangeHeight {
    private final int lower;
    private final int upper;
    private final boolean isPositiveInfinity;
        
    /**
     * Unbounded range constructor; 
     * the range is [-1, +oo), the 
     * maximum allowed for height.
     */
    public RangeHeight() {
    	this.lower = -1;
    	this.upper = 0; //doesn't mind
    	this.isPositiveInfinity = true;
    }
    
    /**
     * Unbounded range constructor.
     *  
     * @param low lower bound; the range is [low, +oo).
     */
    public RangeHeight(int low) {
    	this.lower = low;
    	this.upper = 0; //doesn't mind
    	this.isPositiveInfinity = true;
    }

    /**
     * Bounded range constructor.
     * 
     * @param low lower bound.
     * @param up upper bound; the range is [low, up].
     */
    public RangeHeight(int low, int up) {
    	this.lower = low;
    	this.upper = up;
    	this.isPositiveInfinity = false;
    }
    
    /**
     * Empty range factory method.
     * 
     * @return an empty range.
     */
    public static RangeHeight rangeEmpty() {
    	return new RangeHeight(0, -1);
    }
   
    public boolean isEmpty() {
    	return (!isPositiveInfinity && lower > upper);
    }
    
    private static int max(int a, int b) {
    	return (a > b ? a : b);
    }
    
    private static int min(int a, int b) {
    	return (a < b ? a : b);
    }
    
    public RangeHeight maxAndIncrement(RangeHeight other) {
    	if (this.isEmpty() || other.isEmpty()) {
    		return rangeEmpty();
    	}
    	final int newLower = max(this.lower, other.lower) + 1;
    	if (this.isPositiveInfinity || other.isPositiveInfinity) {
    		return new RangeHeight(newLower);
    	} else {
        	final int newUpper = max(this.upper, other.upper) + 1;
    		return new RangeHeight(newLower, newUpper);
    	}
    }
    
    public boolean doesNotContain(int i) {
    	return (i < lower || (!isPositiveInfinity && i > upper));
    }

	public int distance(RangeHeight other) {
    	if (this.isEmpty() || other.isEmpty()) {
    		return Integer.MAX_VALUE; //means infinite distance
    	}
    	if (this.isPositiveInfinity && other.isPositiveInfinity) {
        	return 0; //they intersect
    	}
    	final int maxLower = max(this.lower, other.lower);
    	final int minUpper = (this.isPositiveInfinity ? other.upper : 
    						 (other.isPositiveInfinity ? this.upper : min(this.upper, other.upper)));
    	return (maxLower <= minUpper ? 0 : maxLower - minUpper);
	}
	
	@Override
	public String toString() {
		return "[" + (this.isPositiveInfinity ? (this.lower + ", " + "+oo)") : (this.lower <= this.upper ? (this.lower + ", " + this.upper + "]") : "]"));
	}
}