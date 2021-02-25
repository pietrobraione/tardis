package tardis.implementation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jbse.mem.ClauseAssume;
import jbse.mem.ClauseAssumeAliases;
import jbse.mem.ClauseAssumeExpands;
import jbse.mem.ClauseAssumeNull;
import jbse.val.Expression;
import jbse.val.NarrowingConversion;
import jbse.val.Primitive;
import jbse.val.PrimitiveSymbolicMember;
import jbse.val.PrimitiveSymbolicMemberArray;
import jbse.val.PrimitiveSymbolicMemberArrayLength;
import jbse.val.Simplex;
import jbse.val.Symbolic;
import jbse.val.SymbolicMember;
import jbse.val.WideningConversion;

/**
 * Class that applies the Slicing procedure to the concrete and
 * abstract path conditions starting from the last clause.
 */

public class SlicingManager {

	public static Object[] Slicing(String[] PathToStringArray, Object[] clauseArray, String[] generalArray) {

		HashSet<String> variableSet = new HashSet<String>();
		String[] clauseArrayInput = new String[clauseArray.length];
		ArrayList<HashSet<String>> originSets = new ArrayList<HashSet<String>>();
		Pattern pattern = Pattern.compile("\\{(.*?\\d)\\}");

		//fills originSets: set of origins of each clause
		for (Object c : clauseArray) {
			if (c instanceof ClauseAssume) {
				HashSet<String> set = new HashSet<String>();
				final Primitive p = ((ClauseAssume) c).getCondition();
				if (p instanceof Symbolic) {
					if (p instanceof Expression) {
						originSets.add(getContainer (p, set));
					}
				}
			}
			else if (c instanceof ClauseAssumeExpands) {
				HashSet<String> set = new HashSet<String>();
				String ref = ((ClauseAssumeExpands) c).getReference().asOriginString();
				set.add(splitByDot(ref));
				originSets.add(set);
			}
			else if (c instanceof ClauseAssumeAliases) {
				HashSet<String> set = new HashSet<String>();
				String ref = ((ClauseAssumeAliases) c).getReference().asOriginString();
				set.add(splitByDot(ref));
				originSets.add(set);
			}
			else if (c instanceof ClauseAssumeNull) {
				HashSet<String> set = new HashSet<String>();
				String ref = ((ClauseAssumeNull) c).getReference().asOriginString();
				set.add(splitByDot(ref));
				originSets.add(set);
			}
			else {
				HashSet<String> set = new HashSet<String>();
				set.add(c.toString());
				originSets.add(set);
			}
		}


		//adds to variableSet the variables of the last condition of the path condition
		Matcher matcher = pattern.matcher(clauseArray[clauseArray.length-1].toString());
		while (matcher.find()) {
			variableSet.add(matcher.group(0));
		}
		variableSet.addAll(originSets.get(originSets.size()-1));
		clauseArrayInput[clauseArray.length-1] = clauseArray[clauseArray.length-1].toString();

		//iterates PC conditions clauseArray.length - 1 times to detect indirect dependencies
		for (int l=0; l < clauseArray.length - 1; l++) {
			//iterates all PC conditions except the last one
			for (int m=0; m < clauseArray.length - 1; m++) {
				HashSet<String> supportSet = new HashSet<String>();
				//adds the origins of the condition I'm analyzing to the supportSet
				supportSet.addAll(originSets.get(m));
				Matcher matcherLoop = pattern.matcher(clauseArray[m].toString());
				//adds the variable in a support set for each match
				//then checks if at least one element of the support set is common with the variable set
				//if yes, I add the entire support set to the variable set
				while (matcherLoop.find()) {
					supportSet.add(matcherLoop.group(0));
					for (String variable : supportSet) {
						if (variableSet.contains(variable)) {
							clauseArrayInput[m] = clauseArray[m].toString();
							variableSet.addAll(supportSet);	
						}
					}
				}
			}
		}

		//removes null elements of clauseArrayInput
		//deletes of general clauses of generalArray in the same position of null values in clauseArrayInput
		List<String> values1 = new ArrayList<String>();
		List<String> values2 = new ArrayList<String>();
		for(int k=0; k<clauseArrayInput.length; k++) {
			if(clauseArrayInput[k] != null) { 
				values1.add(PathToStringArray[k]);
				values2.add(generalArray[k]);
			}
		}
		String[] specificArrayOutput = values1.toArray(new String[values1.size()]);
		String[] generalArrayOutput = values2.toArray(new String[values2.size()]);

		Object[] output = new Object[]{specificArrayOutput, generalArrayOutput};
		return output;

	}

    /**
     * Finds containers of all elements of the ClauseAssume clause and add them to the set;
     * Workflow: if operand is an expression, go deeper
     */
	public static HashSet<String> getContainer (Primitive p, HashSet<String> set) {
		if (((Expression) p).isUnary()) {
			Primitive op = ((Expression) p).getOperand();
			if (op instanceof Simplex) {
				//do nothing
			}
			else if (op instanceof PrimitiveSymbolicMemberArrayLength || op instanceof PrimitiveSymbolicMemberArray) {
				set.add(op.toString());
			}
			else if (op instanceof WideningConversion) {
				getContainerWideningConversion(op, set);
			}
			else if (op instanceof NarrowingConversion) {
				getContainerNarrowingConversion (op, set);
			}
			else if (op instanceof Expression) {
				getContainer (op, set);
			}
			else {
				try {
					set.add(((SymbolicMember) op).getContainer().asOriginString());
				}
				catch (Exception e) {
					//give up
					//set.add(((Symbolic) op).asOriginString());
					set.add(splitByDot(((Symbolic) op).asOriginString()));
				}
			}
		}
		else {
			Primitive firstOp = ((Expression) p).getFirstOperand();
			Primitive secondOp = ((Expression) p).getSecondOperand();
			if (firstOp instanceof Simplex) {
				//do nothing
			}
			else if (firstOp instanceof PrimitiveSymbolicMemberArrayLength || firstOp instanceof PrimitiveSymbolicMemberArray) {
				set.add(firstOp.toString());
			}
			else if (firstOp instanceof WideningConversion) {
				getContainerWideningConversion(firstOp, set);
			}
			else if (firstOp instanceof NarrowingConversion) {
				getContainerNarrowingConversion (firstOp, set);
			}
			else if (firstOp instanceof Expression) {
				getContainer (firstOp, set);
			}
			else {
				try {
					set.add(((SymbolicMember) firstOp).getContainer().asOriginString());
				}
				catch (Exception e) {
					//give up
					//set.add(((Symbolic) firstOp).asOriginString());
					set.add(splitByDot(((Symbolic) firstOp).asOriginString()));
				}
			}

			if (secondOp instanceof Simplex) {
				//do nothing
			}
			else if (secondOp instanceof PrimitiveSymbolicMemberArrayLength || secondOp instanceof PrimitiveSymbolicMemberArray) {
				set.add(secondOp.toString());
			}
			else if (secondOp instanceof WideningConversion) {
				getContainerWideningConversion(secondOp, set);
			}
			else if (secondOp instanceof NarrowingConversion) {
				getContainerNarrowingConversion (secondOp, set);
			}
			else if (secondOp instanceof Expression) {
				getContainer (secondOp, set);
			}
			else {
				try {
					set.add(((SymbolicMember) secondOp).getContainer().asOriginString());
				}
				catch (Exception e) {
					//give up
					//set.add(((Symbolic) secondOp).asOriginString());
					set.add(splitByDot(((Symbolic) secondOp).asOriginString()));
				}
			}
		}
		return set;
	}
	
	//find containers of all elements of the ClauseAssume clause in the case of the operator is instance of WideningConversion
	public static HashSet<String> getContainerWideningConversion (Primitive operator, HashSet<String> set) {
		if (((WideningConversion) operator).getArg() instanceof PrimitiveSymbolicMemberArrayLength || ((WideningConversion) operator).getArg() instanceof PrimitiveSymbolicMemberArray) {
			set.add(operator.toString());
		}
		else if (((WideningConversion) operator).getArg() instanceof WideningConversion) {
			getContainerWideningConversion(((WideningConversion) operator).getArg(), set);
		}
		else if (((WideningConversion) operator).getArg() instanceof NarrowingConversion) {
			getContainerNarrowingConversion(((WideningConversion) operator).getArg(), set);
		}
		else if (((WideningConversion) operator).getArg() instanceof Expression) {
			getContainer (((WideningConversion) operator).getArg(), set);
		}
		else {
			try {
				set.add(((PrimitiveSymbolicMember) ((WideningConversion) operator).getArg()).getContainer().asOriginString());
			}
			catch (Exception e) {
				//give up
				//set.add(((Symbolic) operator).asOriginString());
				set.add(splitByDot(((Symbolic) operator).asOriginString()));
			}
		}
		return set;
	}
	
	//find containers of all elements of the ClauseAssume clause in the case of the operator is instance of NarrowingConversion
	public static HashSet<String> getContainerNarrowingConversion (Primitive operator, HashSet<String> set) {
		if (((NarrowingConversion) operator).getArg() instanceof PrimitiveSymbolicMemberArrayLength || ((NarrowingConversion) operator).getArg() instanceof PrimitiveSymbolicMemberArray) {
			set.add(operator.toString());
		}
		else if (((NarrowingConversion) operator).getArg() instanceof NarrowingConversion) {
			getContainerNarrowingConversion(((NarrowingConversion) operator).getArg(), set);
		}
		else if (((NarrowingConversion) operator).getArg() instanceof WideningConversion) {
			getContainerWideningConversion(((NarrowingConversion) operator).getArg(), set);
		}
		else if (((NarrowingConversion) operator).getArg() instanceof Expression) {
			getContainer (((NarrowingConversion) operator).getArg(), set);
		}
		else {
			try {
				set.add(((PrimitiveSymbolicMember) ((NarrowingConversion) operator).getArg()).getContainer().asOriginString());
			}
			catch (Exception e) {
				//give up
				//set.add(((Symbolic) operator).asOriginString());
				set.add(splitByDot(((Symbolic) operator).asOriginString()));
			}
		}
		return set;
	}
	
	public static String splitByDot (String op) {
		int index=op.lastIndexOf('.');
		if (index == -1) {
			return op;
		}
		else {
			return op.substring(0,index);
		}
	}
}
