package exec;

import java.util.Collection;
import java.util.stream.Collectors;

import jbse.bc.Opcodes;
import jbse.mem.Clause;
import jbse.mem.ClauseAssumeClassInitialized;
import jbse.mem.ClauseAssumeClassNotInitialized;

class Util {
	static Collection<Clause> shorten(Collection<Clause> pc) {
		return pc.stream().filter(x -> !(x instanceof ClauseAssumeClassInitialized || x instanceof ClauseAssumeClassNotInitialized)).collect(Collectors.toList());
	}
	
	static boolean bytecodeJump(byte currentBytecode) {
		return (currentBytecode == Opcodes.OP_IF_ACMPEQ ||
				currentBytecode == Opcodes.OP_IF_ACMPNE ||	
				currentBytecode == Opcodes.OP_IFNONNULL ||	
				currentBytecode == Opcodes.OP_IFNULL ||	
				currentBytecode == Opcodes.OP_IFEQ ||
				currentBytecode == Opcodes.OP_IFGE ||	
				currentBytecode == Opcodes.OP_IFGT ||	
				currentBytecode == Opcodes.OP_IFLE ||	
				currentBytecode == Opcodes.OP_IFLT ||	
				currentBytecode == Opcodes.OP_IFNE ||	
				currentBytecode == Opcodes.OP_IF_ICMPEQ ||	
				currentBytecode == Opcodes.OP_IF_ICMPGE ||	
				currentBytecode == Opcodes.OP_IF_ICMPGT ||	
				currentBytecode == Opcodes.OP_IF_ICMPLE ||	
				currentBytecode == Opcodes.OP_IF_ICMPLT ||	
				currentBytecode == Opcodes.OP_IF_ICMPNE ||	
				currentBytecode == Opcodes.OP_LOOKUPSWITCH ||	
				currentBytecode == Opcodes.OP_TABLESWITCH);

	}
	
	static boolean bytecodeBranch(byte currentBytecode) {
		return (bytecodeJump(currentBytecode) ||
				currentBytecode == Opcodes.OP_ALOAD ||
				currentBytecode == Opcodes.OP_ALOAD_0 ||
				currentBytecode == Opcodes.OP_ALOAD_1 ||
				currentBytecode == Opcodes.OP_ALOAD_2 ||
				currentBytecode == Opcodes.OP_ALOAD_3 ||
				currentBytecode == Opcodes.OP_IALOAD ||
				currentBytecode == Opcodes.OP_LALOAD ||
				currentBytecode == Opcodes.OP_FALOAD ||
				currentBytecode == Opcodes.OP_DALOAD ||
				currentBytecode == Opcodes.OP_AALOAD ||
				currentBytecode == Opcodes.OP_BALOAD ||
				currentBytecode == Opcodes.OP_CALOAD ||
				currentBytecode == Opcodes.OP_SALOAD ||
				currentBytecode == Opcodes.OP_IASTORE ||
				currentBytecode == Opcodes.OP_LASTORE ||
				currentBytecode == Opcodes.OP_FASTORE ||
				currentBytecode == Opcodes.OP_DASTORE ||
				currentBytecode == Opcodes.OP_AASTORE ||
				currentBytecode == Opcodes.OP_BASTORE ||
				currentBytecode == Opcodes.OP_CASTORE ||
				currentBytecode == Opcodes.OP_LCMP ||
				currentBytecode == Opcodes.OP_FCMPL ||
				currentBytecode == Opcodes.OP_FCMPG ||
				currentBytecode == Opcodes.OP_DCMPL ||
				currentBytecode == Opcodes.OP_DCMPG ||
				currentBytecode == Opcodes.OP_GETSTATIC ||
				currentBytecode == Opcodes.OP_GETFIELD ||
				currentBytecode == Opcodes.OP_NEWARRAY ||
				currentBytecode == Opcodes.OP_ANEWARRAY ||
				currentBytecode == Opcodes.OP_MULTIANEWARRAY);
	}
}
