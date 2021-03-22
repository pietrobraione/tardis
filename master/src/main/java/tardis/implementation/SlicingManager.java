package tardis.implementation;

import static tardis.implementation.Util.shorten;
import static tardis.implementation.Util.stringifyPathCondition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jbse.mem.Clause;
import jbse.mem.ClauseAssume;
import jbse.mem.ClauseAssumeReferenceSymbolic;
import jbse.val.Any;
import jbse.val.Expression;
import jbse.val.NarrowingConversion;
import jbse.val.Primitive;
import jbse.val.PrimitiveSymbolicApply;
import jbse.val.PrimitiveSymbolicMember;
import jbse.val.PrimitiveSymbolicMemberArray;
import jbse.val.PrimitiveSymbolicMemberArrayLength;
import jbse.val.ReferenceSymbolic;
import jbse.val.Simplex;
import jbse.val.Symbolic;
import jbse.val.Term;
import jbse.val.Value;
import jbse.val.WideningConversion;

/**
 * Class that applies a slicing procedure to a
 * path conditions starting from the last clause, i.e., a 
 * transitive closure of the dependencies between the clauses of a path condition
 * in relation to the last clause of the path condition itself, where the dependencies
 * are managed according to variables and origins in a transitive way.
 * 
 * @author Matteo Modonato
 * @author Pietro Braione
 */

public class SlicingManager {
    public static String[][] slice(List<Clause> path) {
        final Object[] clauseArray = shorten(path).toArray();
        final String pathConditionToString = stringifyPathCondition(shorten(path));
        //split pc clauses into array
        final String[] generalArray = pathConditionToString.split(" && ");
        final String[] specificArray = pathConditionToString.split(" && ");
        //generate general clauses
        for (int i = 0; i < generalArray.length; i++){
            generalArray[i] = generalArray[i].replaceAll("[0-9]", "");
        }
        
        final HashSet<String> variableSet = new HashSet<>();
        final String[] clauseArrayInput = new String[clauseArray.length];
        final ArrayList<HashSet<String>> originSets = new ArrayList<>();

        //fills originSets: set of origins of each clause
        for (Object c : clauseArray) {
            final HashSet<String> set = new HashSet<>();
            if (c instanceof ClauseAssume) {
                final Primitive p = ((ClauseAssume) c).getCondition();
                getContainerPrimitive(p, set);
                originSets.add(set);
            } else if (c instanceof ClauseAssumeReferenceSymbolic) {
                final String ref = ((ClauseAssumeReferenceSymbolic) c).getReference().asOriginString();
                set.add(splitByDot(ref));
                originSets.add(set);
            } else {
                set.add(c.toString());
                originSets.add(set);
            }
        }


        //adds to variableSet the variables of the last condition of the path condition
        final Pattern pattern = Pattern.compile("\\{(.*?\\d)\\}");
        if (clauseArray.length > 0) {
            final Matcher matcher = pattern.matcher(clauseArray[clauseArray.length - 1].toString());
            while (matcher.find()) {
                variableSet.add(matcher.group(0));
            }
            variableSet.addAll(originSets.get(originSets.size() - 1));
            clauseArrayInput[clauseArray.length - 1] = clauseArray[clauseArray.length - 1].toString();
        }

        //iterates PC conditions clauseArray.length - 1 times to detect indirect dependencies
        for (int l = 0; l < clauseArray.length - 1; ++l) {
            //iterates all PC conditions except the last one
            for (int m = 0; m < clauseArray.length - 1; ++m) {
                final HashSet<String> supportSet = new HashSet<>();
                //adds the origins of the condition I'm analyzing to the supportSet
                supportSet.addAll(originSets.get(m));
                final Matcher matcherLoop = pattern.matcher(clauseArray[m].toString());
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
        final List<String> valuesSpecific = new ArrayList<>();
        final List<String> valuesGeneral = new ArrayList<>();
        for (int k = 0; k < clauseArrayInput.length; ++k) {
            if (clauseArrayInput[k] != null) { 
                valuesSpecific.add(specificArray[k]);
                valuesGeneral.add(generalArray[k]);
            }
        }
        final String[] specificArrayOutput = valuesSpecific.toArray(new String[valuesSpecific.size()]);
        final String[] generalArrayOutput = valuesGeneral.toArray(new String[valuesGeneral.size()]);

        final String[][] output = {specificArrayOutput, generalArrayOutput};
        return output;
    }

    /**
     * Finds containers of all elements of the ClauseAssume clause and add them to the set;
     * Workflow: if operand is an expression, go deeper
     */
    
    public static void getContainerPrimitive(Primitive p, HashSet<String> set) {
        if (p instanceof Simplex || p instanceof Any || p instanceof Term) {
            //do nothing, they have no container
        } else if (p instanceof PrimitiveSymbolicMemberArrayLength || p instanceof PrimitiveSymbolicMemberArray) {
            //special case for arrays
            set.add(p.toString());
        } else if (p instanceof PrimitiveSymbolicMember) { //PrimitiveSymbolicMemberField
            set.add(((PrimitiveSymbolicMember) p).getContainer().asOriginString());
        } else if (p instanceof Expression) {
            getContainerExpression((Expression) p, set);
        } else if (p instanceof PrimitiveSymbolicApply) {
            getContainerPrimitiveSymbolicApply((PrimitiveSymbolicApply) p, set);
        } else if (p instanceof WideningConversion) {
            getContainerPrimitive(((WideningConversion) p).getArg(), set);
        } else if (p instanceof NarrowingConversion) {
            getContainerPrimitive(((NarrowingConversion) p).getArg(), set);
        } else { //PrimitiveSymbolicHashCode, PrimitiveSymbolicLocalVariable
            set.add(splitByDot(((Symbolic) p).asOriginString()));
        }
    }
    
    public static void getContainerExpression(Expression p, HashSet<String> set) {
        if (p.isUnary()) {
            final Primitive operand = p.getOperand();
            getContainerPrimitive(operand, set);
        } else {
            final Primitive firstOperand = p.getFirstOperand();
            getContainerPrimitive(firstOperand, set);
            final Primitive secondOperand = p.getSecondOperand();
            getContainerPrimitive(secondOperand, set);
        }
    }

    public static void getContainerPrimitiveSymbolicApply(PrimitiveSymbolicApply p, HashSet<String> set) {
        for (Value arg : p.getArgs()) {
            if (arg instanceof Primitive) {
                getContainerPrimitive((Primitive) arg, set);
            } else if (arg instanceof ReferenceSymbolic) {
                set.add(splitByDot(((ReferenceSymbolic) arg).asOriginString()));
            }
        }
    }

    public static String splitByDot(String originString) {
        final int index = originString.lastIndexOf('.');
        if (index == -1) {
            return originString;
        } else {
            return originString.substring(0, index);
        }
    }
}
