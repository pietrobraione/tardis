package exec;

import jbse.bc.Opcodes;

class Util {
	static boolean atJump(byte currentBytecode) {
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
}
