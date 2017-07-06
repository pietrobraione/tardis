package exec;


public class TestCase {
	private String className;
	private String parameterSignature;
	private String methodName;
	
	public TestCase(){
		this.className = "";
		this.parameterSignature = "";
		this.methodName = "";
	}
	
	public TestCase(Options o){
		this.className = o.getTestMethod().get(0);
		this.parameterSignature = o.getTestMethod().get(1);
		this.methodName = o.getTestMethod().get(2);
	}
	
	public TestCase(String cn, String ps, String mn){
		this.className = cn;
		this.parameterSignature = ps;
		this.methodName = mn;
	}
	
	//copy constructor
	public TestCase(TestCase otherTc){
		this.className = otherTc.getClassName();
		this.parameterSignature = otherTc.getParameterSignature();
		this.methodName = otherTc.getMethodName();
	}
	
	public void setClassName(String cName){
		this.className = cName;
	}
	
	public String getClassName(){
		return this.className;
	}
	
	public void setParameterSignature(String parSig){
		this.parameterSignature = parSig;
	}
	
	public String getParameterSignature(){
		return this.parameterSignature;
	}
	
	public void setMethodName(String mName){
		this.methodName = mName;
	}
	
	public String getMethodName(){
		return this.methodName;
	}
	

}
