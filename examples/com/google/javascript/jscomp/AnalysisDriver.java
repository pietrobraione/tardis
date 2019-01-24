package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;

import jbse.meta.Analysis;

public class AnalysisDriver {
	static int BUG01_EXEC_COUNT = 0;
	static void traversingBug01Location() {
			BUG01_EXEC_COUNT++;
	}
	public void driver_RemoveUnusedVars_process(Node root, boolean removeGlobals,
		      boolean preserveFunctionExpressionNames,
		      boolean modifyCallSites) {
		Analysis.assumeClassNotInitialized("com/google/common/collect/ImmutableSet");
		Analysis.assumeClassNotInitialized("java/lang/AbstractStringBuilder");
		Analysis.assumeClassNotInitialized("com/google/common/collect/EmptyImmutableList");
		Analysis.assumeClassNotInitialized("com/google/common/collect/Iterators");
		
		AbstractCompiler compiler = new StubCompiler();
		RemoveUnusedVars rootBug = new RemoveUnusedVars(compiler, removeGlobals, preserveFunctionExpressionNames, modifyCallSites);
		
		/* Abstracted as the driver's parameters:
		Node n4_Name = Node.newString(Token.NAME, new String("p"));
		Node n3_ParamList = new Node(Token.PARAM_LIST, n4_Name);
		Node n2_Fun = new Node(Token.FUNCTION, Node.newString(new String("")), n3_ParamList, new Node(Token.BLOCK));
		Node n1_Block = new Node(Token.BLOCK, n2_Fun);
		 */
		try{
			rootBug.process(null, root/*, null*/);
			Analysis.ass3rt(BUG01_EXEC_COUNT == 0);
		} catch (Exception e) {
			//Analysis.assume(false);
		}
		
		/*Node fun = root.getFirstChild();
		if (fun.getType() == Token.FUNCTION) {
			Node params = fun.getChildAtIndex(1);
			if (params.getType() == Token.PARAM_LIST) {				
				assertEquals(1, params.getChildCount());		
			}
		}*/
		
	}
}
