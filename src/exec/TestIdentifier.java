package exec;

public class TestIdentifier {
	private int testCount;
	
	public TestIdentifier() {
		this.testCount = 1;
	}
	
	public void testCountAdd(int amount) {
		this.testCount += amount;
	}
	public int getTestCount() {
		return this.testCount;
	}
}
