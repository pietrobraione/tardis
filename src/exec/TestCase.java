package exec;

import java.util.*;

import jbse.mem.Clause;

public class TestCase {
	private String className;
	private String parameterSignature;
	private String methodName;
	private Collection<Clause> pathCondition;
	
	public TestCase(){
		this.className = "";
		this.parameterSignature = "";
		this.methodName = "";
		this.pathCondition = null;
	}
	
	public TestCase(Options o){
		this.className = o.getTestMethod().get(0);
		this.parameterSignature = o.getTestMethod().get(1);
		this.methodName = o.getTestMethod().get(2);
		this.pathCondition = null;
	}
	
	public TestCase(Options o,  Collection<Clause> path){
		this.className =  o.getTestMethod().get(0);
		this.parameterSignature = o.getTestMethod().get(1);
		this.methodName = o.getTestMethod().get(2);
		this.pathCondition = path;
	}
	
	public TestCase(String cn, String ps, String mn){
		this.className = cn;
		this.parameterSignature = ps;
		this.methodName = mn;
		this.pathCondition = null;
	}
	
	public TestCase(String cn, String ps, String mn, Collection<Clause> path){
		this.className = cn;
		this.parameterSignature = ps;
		this.methodName = mn;
		this.pathCondition = path;
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
	
	public void setPathC(Collection<Clause> pc){
		this.pathCondition = pc;
	}
	
	public Collection<Clause> getPathC(){
		return this.pathCondition;
	}
}
