package exec;

public class TestIdentifier {
	
	private int testCount;
	
	public TestIdentifier(){
		this.testCount = 0;
	}
	
	public void testIncrease(){
		this.testCount++;
	}
	public int getTestCount(){
		return this.testCount;
	}

}
