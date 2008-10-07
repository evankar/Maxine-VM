/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
/*VCSID=7721509a-68ce-4798-94fb-2ef3936cd334*/
package com.sun.max.vm.compiler.cir.optimize;

import com.sun.max.collect.*;
import com.sun.max.vm.compiler.cir.*;
import com.sun.max.vm.compiler.cir.transform.*;
import com.sun.max.vm.compiler.cir.variable.*;

/**
 * Propagate constant block call arguments into block bodies.
 *
 * Iff a block parameter has the same constant argument in all of its calls,
 * then we remove the parameter definition
 * and all corresponding argument passings
 * and replace all of its uses in block bodies by the argument.
 *
 * @author Bernd Mathiske
 */
public final class CirConstantBlockArgumentsPropagation {

    private CirConstantBlockArgumentsPropagation() {
    }

    private static int getArgumentIndex(CirVariable variable, Sequence<CirCall> calls) {
        int index = -1;
        for (CirCall call : calls) {
            final int i = com.sun.max.lang.Arrays.find(call.arguments(), variable);
            if (i < 0) {
                return -1;
            } else if (index < 0) {
                index = i;
            } else if (i != index) {
                return -1;
            }
        }
        return index;
    }

    /**
     * We have not (yet?) implemented translating closures with more than one exception continuation parameter (to DIR).
     * So for now we maintain the invariant that there is at most one cc and ce per closure parameters list.
     * Here, we filter out some cases where this would occur, losing the respective (continuation) constant propagation.
     *
     * (Causing more than one normal continuation parameter does not occur, because we always remove one as we add at most one.)
     *
     * @return whether these free variables would add up to having more than maximally one ce among the closure's parameters
     */
    private static boolean continuationParameterOverflow(CirClosure closure, Sequence<CirVariable> additionalParameters, Class parameterClass) {
        int n = 0;
        for (CirVariable variable : closure.parameters()) {
            if (parameterClass.isInstance(variable)) {
                n++;
            }
        }
        for (CirVariable variable : additionalParameters) {
            if (parameterClass.isInstance(variable)) {
                n++;
            }
        }
        return n > 1;
    }

    /**
     * Determines free variables in the continuation that is to be inlined by 'propagateConstantArgument'.
     * If such a variable is passed as an argument to the target block
     * at each of its calls at the exact same parameter position,
     * then the parameter and the variable are synonymous
     * and we substitute one for the other.
     * Otherwise we extend the parameter list of the block by the free variable
     * and pass it as an argument at every call.
     */
    private static boolean propagateFreeVariablesFromContinuation(CirClosure closure, Sequence<CirCall> calls, int index, CirContinuation continuation) {
        final DeterministicSet<CirVariable> freeVariables = CirFreeVariableSearch.run(continuation);
        if (closure.parameters()[index] instanceof CirNormalContinuationParameter) {
            if (continuationParameterOverflow(closure, freeVariables, CirExceptionContinuationParameter.class)) {
                return false;
            }
        } else {
            if (continuationParameterOverflow(closure, freeVariables, CirNormalContinuationParameter.class)) {
                return false;
            }
        }
        final AppendableSequence<CirVariable> remainingFreeVariables = new LinkSequence<CirVariable>();
        for (CirVariable variable : freeVariables) {
            final int i = getArgumentIndex(variable, calls);
            if (i >= 0) {
                CirBetaReduction.applySingle(continuation, variable, closure.parameters()[i]);
            } else {
                remainingFreeVariables.append(variable);
            }
        }
        if (!remainingFreeVariables.isEmpty()) {
            final CirVariable[] variables = Sequence.Static.toArray(remainingFreeVariables, CirVariable.class);
            closure.setParameters(com.sun.max.lang.Arrays.append(closure.parameters(), variables));
            for (CirCall call : calls) {
                call.setArguments(com.sun.max.lang.Arrays.append(call.arguments(), variables));
            }
        }
        return true;
    }

    private static boolean propagateConstantArgument(CirBlock block, Sequence<CirCall> calls, int index, CirValue argument) {
        final CirClosure closure = block.closure();
        final CirVariable parameter = closure.parameters()[index];
        if (argument instanceof CirContinuation) {
            switch (CirCount.apply(closure.body(), parameter)) {
                case 0: {
                    closure.removeParameter(index);
                    return true;
                }
                case 1: {
                    if (!propagateFreeVariablesFromContinuation(closure, calls, index, (CirContinuation) argument)) {
                        return false;
                    }
                    break;
                }
                default: {
                    return false;
                }
            }
        } else {
            assert !(argument instanceof CirClosure) : argument + " index " + index + " closure = " + closure; // TODO: can this happen?
        }
        CirBetaReduction.applySingle(closure, parameter, argument);
        closure.removeParameter(index);
        assert closure.verifyParameters();
        return true;
    }

    private static boolean haveEqualArgument(Iterable<CirCall> calls, int index, CirValue argument) {
        for (CirCall call : calls) {
            if (!call.arguments()[index].equals(argument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean apply(CirBlock block) {
        boolean propagatedAny = false;
        final LinkSequence<CirCall> calls = block.calls();
        if (calls != null) {
            final CirCall call = calls.first();
            final Iterable<CirCall> otherCalls = calls.tail();
            int i = 0;
            while (i < call.arguments().length) {
                final CirValue argument = call.arguments()[i];
                if (argument.isConstant() && haveEqualArgument(otherCalls, i, argument) && propagateConstantArgument(block, calls, i, argument)) {
                    for (CirCall c : calls) {
                        c.removeArgument(i);
                    }
                    propagatedAny = true;
                } else {
                    i++;
                }
            }
        }
        return propagatedAny;
    }

    public static boolean apply(Iterable<CirBlock> blocks) {
        boolean propagatedAny = false;
        for (CirBlock block : blocks) {
            if (apply(block)) {
                propagatedAny = true;
            }
        }
        return propagatedAny;
    }
}
