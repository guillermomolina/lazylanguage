/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.guillermomolina.lazylanguage.parser;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.guillermomolina.lazylanguage.LLLanguage;
import com.guillermomolina.lazylanguage.nodes.LLExpressionNode;
import com.guillermomolina.lazylanguage.nodes.LLRootNode;
import com.guillermomolina.lazylanguage.nodes.LLStatementNode;
import com.guillermomolina.lazylanguage.nodes.controlflow.LLBlockNode;
import com.guillermomolina.lazylanguage.nodes.controlflow.LLBreakNode;
import com.guillermomolina.lazylanguage.nodes.controlflow.LLContinueNode;
import com.guillermomolina.lazylanguage.nodes.controlflow.LLDebuggerNode;
import com.guillermomolina.lazylanguage.nodes.controlflow.LLFunctionBodyNode;
import com.guillermomolina.lazylanguage.nodes.controlflow.LLIfNode;
import com.guillermomolina.lazylanguage.nodes.controlflow.LLReturnNode;
import com.guillermomolina.lazylanguage.nodes.controlflow.LLWhileNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLAddNodeGen;
import com.guillermomolina.lazylanguage.nodes.expression.LLBigIntegerLiteralNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLDivNodeGen;
import com.guillermomolina.lazylanguage.nodes.expression.LLEqualNodeGen;
import com.guillermomolina.lazylanguage.nodes.expression.LLFunctionLiteralNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLInvokeNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLLessOrEqualNodeGen;
import com.guillermomolina.lazylanguage.nodes.expression.LLLessThanNodeGen;
import com.guillermomolina.lazylanguage.nodes.expression.LLLogicalAndNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLLogicalNotNodeGen;
import com.guillermomolina.lazylanguage.nodes.expression.LLLogicalOrNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLLongLiteralNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLMulNodeGen;
import com.guillermomolina.lazylanguage.nodes.expression.LLParenExpressionNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLReadPropertyNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLReadPropertyNodeGen;
import com.guillermomolina.lazylanguage.nodes.expression.LLStringLiteralNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLSubNodeGen;
import com.guillermomolina.lazylanguage.nodes.expression.LLWritePropertyNode;
import com.guillermomolina.lazylanguage.nodes.expression.LLWritePropertyNodeGen;
import com.guillermomolina.lazylanguage.nodes.local.LLReadArgumentNode;
import com.guillermomolina.lazylanguage.nodes.local.LLReadLocalVariableNode;
import com.guillermomolina.lazylanguage.nodes.local.LLReadLocalVariableNodeGen;
import com.guillermomolina.lazylanguage.nodes.local.LLWriteLocalVariableNode;
import com.guillermomolina.lazylanguage.nodes.local.LLWriteLocalVariableNodeGen;
import com.guillermomolina.lazylanguage.nodes.util.LLUnboxNodeGen;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Helper class used by the LL {@link Parser} to create nodes. The code is
 * factored out of the automatically generated parser to keep the attributed
 * grammar of LL small.
 */
public class LLNodeFactory extends LazyLanguageParserBaseVisitor<Node> {

    /**
     * Local variable names that are visible in the current block. Variables are not
     * visible outside of their defining block, to prevent the usage of undefined
     * variables. Because of that, we can decide during parsing if a name references
     * a local variable or is a function name.
     */
    static class LexicalScope {
        protected final LexicalScope outer;
        protected final Map<String, FrameSlot> locals;
        protected final boolean inLoop;

        LexicalScope(LexicalScope outer, boolean inLoop) {
            this.outer = outer;
            this.inLoop = inLoop;
            this.locals = new HashMap<>();
            if (outer != null) {
                locals.putAll(outer.locals);
            }
        }
    }

    /* State while parsing a source unit. */
    private final Source source;
    private final Map<String, RootCallTarget> allFunctions;

    /* State while parsing a function. */
    private int functionStartPos;
    private String functionName;
    private int functionBodyStartPos; // includes parameter list
    private FrameDescriptor frameDescriptor;

    /* State while parsing a block. */
    private LexicalScope lexicalScope;
    private final LLLanguage language;

    public LLNodeFactory(LLLanguage language, Source source) {
        this.language = language;
        this.source = source;
        this.allFunctions = new HashMap<>();
    }

    public Map<String, RootCallTarget> getAllFunctions() {
        return allFunctions;
    }

    private static Interval srcFromContext(ParserRuleContext ctx) {
        int a = ctx.start.getStartIndex();
        int b = ctx.stop.getStopIndex();
        return new Interval(a, b);
    }

    private void setSourceFromContext(LLStatementNode node, ParserRuleContext ctx) {
        Interval sourceInterval = srcFromContext(ctx);
        assert sourceInterval != null;
        if (node == null) {
            throw new LLParseError(source, ctx, "Node is null");
        }
        assert node != null;
        node.setSourceSection(sourceInterval.a, sourceInterval.length());
    }

    @Override
    public Node visitFunction(LazyLanguageParser.FunctionContext ctx) {
        assert functionStartPos == 0;
        assert functionName == null;
        assert functionBodyStartPos == 0;
        assert frameDescriptor == null;
        assert lexicalScope == null;

        Token nameToken = ctx.IDENTIFIER().getSymbol();
        Token bodyStartToken = ctx.block().getStart();

        functionStartPos = nameToken.getStartIndex();
        functionName = nameToken.getText();
        functionBodyStartPos = bodyStartToken.getStartIndex();
        frameDescriptor = new FrameDescriptor();
        startBlock(false);

        int parameterCount = 0;
        List<LLStatementNode> methodNodes = new ArrayList<>();
        if(ctx.functionParameters() != null) {
            for (TerminalNode nameNode : ctx.functionParameters().IDENTIFIER()) {
                final LLReadArgumentNode readArg = new LLReadArgumentNode(parameterCount);
                final LLExpressionNode stringLiteral = createStringLiteral(nameNode.getSymbol(), false);
                LLExpressionNode assignment = createAssignment(stringLiteral, readArg, parameterCount);
                methodNodes.add(assignment);
                parameterCount++;
            }    
        }

        if (ctx.block() == null) {
            // a state update that would otherwise be performed by finishBlock
            lexicalScope = lexicalScope.outer;
        } else {
            final LLStatementNode methodBlock = finishBlock(methodNodes, ctx.block());
            assert lexicalScope == null : "Wrong scoping of blocks in parser";

            final LLFunctionBodyNode functionBodyNode = new LLFunctionBodyNode(methodBlock);
            setSourceFromContext(functionBodyNode, ctx);
            Interval sourceInterval = srcFromContext(ctx);
            SourceSection functionSrc = source.createSection(sourceInterval.a, sourceInterval.length());
            final LLRootNode rootNode = new LLRootNode(language, frameDescriptor, functionBodyNode, functionSrc,
                    functionName);
            allFunctions.put(functionName, Truffle.getRuntime().createCallTarget(rootNode));
        }

        functionStartPos = 0;
        functionName = null;
        functionBodyStartPos = 0;
        frameDescriptor = null;
        lexicalScope = null;

        return null;
    }

    public void startBlock(boolean inLoop) {
        lexicalScope = new LexicalScope(lexicalScope, inLoop);
    }

    public LLStatementNode finishBlock(List<LLStatementNode> bodyNodes, LazyLanguageParser.BlockContext ctx) {
        for (LazyLanguageParser.StatementContext statement : ctx.statement()) {
            bodyNodes.add((LLStatementNode) visit(statement));
        }

        lexicalScope = lexicalScope.outer;

        if (containsNull(bodyNodes)) {
            return null;
        }

        List<LLStatementNode> flattenedNodes = new ArrayList<>(bodyNodes.size());
        flattenBlocks(bodyNodes, flattenedNodes);
        for (LLStatementNode statement : flattenedNodes) {
            if (statement.hasSource() && !isHaltInCondition(statement)) {
                statement.addStatementTag();
            }
        }
        LLBlockNode blockNode = new LLBlockNode(flattenedNodes.toArray(new LLStatementNode[flattenedNodes.size()]));
        setSourceFromContext(blockNode, ctx);
        return blockNode;
    }

    private static boolean isHaltInCondition(LLStatementNode statement) {
        return (statement instanceof LLIfNode) || (statement instanceof LLWhileNode);
    }

    private void flattenBlocks(Iterable<? extends LLStatementNode> bodyNodes, List<LLStatementNode> flattenedNodes) {
        for (LLStatementNode n : bodyNodes) {
            if (n instanceof LLBlockNode) {
                flattenBlocks(((LLBlockNode) n).getStatements(), flattenedNodes);
            } else {
                flattenedNodes.add(n);
            }
        }
    }

    @Override
    public Node visitExpressionStatement(LazyLanguageParser.ExpressionStatementContext ctx) {
        return visit(ctx.expression());
    }

    public LLExpressionNode createMemberExpression(LazyLanguageParser.MemberExpressionContext ctx, LLExpressionNode r,
     LLExpressionNode assignmentReceiver, LLExpressionNode assignmentName) {
        LLExpressionNode nestedAssignmentName = null;
        LLExpressionNode receiver = r;
        LLExpressionNode result = null;
        if (ctx.LPAREN() != null) {
            if (receiver == null) {
                receiver = createRead(assignmentName);
            }
            List<LLExpressionNode> parameters = new ArrayList<>();
            if(ctx.parameterList() != null) {
                for (LazyLanguageParser.ExpressionContext expression : ctx.parameterList().expression()) {
                    parameters.add((LLExpressionNode) visit(expression));
                }    
            }
            result = createCall(receiver, parameters, ctx.RPAREN().getSymbol());
        } else if (ctx.ASSIGN() != null) {
            if (assignmentName == null) {
                throw new LLParseError(source, ctx.expression(), "invalid assignment target");
            } 
            result = (LLExpressionNode)visit(ctx.expression());
            if (assignmentReceiver == null) {
                result = createAssignment(assignmentName, result);
            } else {
                result = createWriteProperty(assignmentReceiver, assignmentName, result);
            }
        } else if (ctx.DOT() != null) {
            if (receiver == null) {
                receiver = createRead(assignmentName);
            }
            nestedAssignmentName = createStringLiteral(ctx.IDENTIFIER().getSymbol(), false);
            result = createReadProperty(receiver, nestedAssignmentName);
        } else /* array member expression */ {
            if (receiver == null) {
                receiver = createRead(assignmentName);
            }
            nestedAssignmentName = (LLExpressionNode)visit(ctx.expression());
            result = createReadProperty(receiver, nestedAssignmentName);
        }
        if (ctx.memberExpression() != null) {
            return createMemberExpression(ctx.memberExpression(), result, receiver, nestedAssignmentName);
        }
        return result;
    }



    @Override
    public Node visitExpression(LazyLanguageParser.ExpressionContext ctx) {
        LLExpressionNode leftNode = null;
        for (final LazyLanguageParser.LogicTermContext context : ctx.logicTerm()) {
            final LLExpressionNode rightNode = (LLExpressionNode) visit(context);
            leftNode = leftNode == null ? rightNode : createBinary(ctx, ctx.op, leftNode, rightNode);
        }
        return leftNode;
    }

    @Override
    public Node visitLogicTerm(LazyLanguageParser.LogicTermContext ctx) {
        LLExpressionNode leftNode = null;
        for (final LazyLanguageParser.LogicFactorContext context : ctx.logicFactor()) {
            final LLExpressionNode rightNode = (LLExpressionNode) visit(context);
            leftNode = leftNode == null ? rightNode : createBinary(ctx, ctx.op, leftNode, rightNode);
        }
        return leftNode;
    }

    @Override
    public Node visitLogicFactor(LazyLanguageParser.LogicFactorContext ctx) {
        LLExpressionNode leftNode = null;
        for (final LazyLanguageParser.ArithmeticContext context : ctx.arithmetic()) {
            final LLExpressionNode rightNode = (LLExpressionNode) visit(context);
            leftNode = leftNode == null ? rightNode : createBinary(ctx, ctx.op, leftNode, rightNode);
        }
        return leftNode;
    }

    @Override
    public Node visitArithmetic(LazyLanguageParser.ArithmeticContext ctx) {
        LLExpressionNode leftNode = null;
        for (final LazyLanguageParser.TermContext context : ctx.term()) {
            final LLExpressionNode rightNode = (LLExpressionNode) visit(context);
            leftNode = leftNode == null ? rightNode : createBinary(ctx, ctx.op, leftNode, rightNode);
        }
        return leftNode;
    }

    @Override
    public Node visitTerm(LazyLanguageParser.TermContext ctx) {
        LLExpressionNode leftNode = null;
        for (final LazyLanguageParser.FactorContext context : ctx.factor()) {
            final LLExpressionNode rightNode = (LLExpressionNode) visit(context);
            leftNode = leftNode == null ? rightNode : createBinary(ctx, ctx.op, leftNode, rightNode);
        }
        return leftNode;
    }

    @Override
    public Node visitFactor(LazyLanguageParser.FactorContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            LLExpressionNode assignmentName = createStringLiteral(ctx.IDENTIFIER().getSymbol(), false);
            if (ctx.memberExpression() != null) {
                return createMemberExpression(ctx.memberExpression(), null, null, assignmentName);
            } else {
                return createRead(assignmentName);
            }
        } else if (ctx.STRING_LITERAL() != null) {
            return createStringLiteral(ctx.STRING_LITERAL().getSymbol(), true);
        } else if (ctx.NUMERIC_LITERAL() != null) {
            return createNumericLiteral(ctx.NUMERIC_LITERAL().getSymbol());
        }
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        LLExpressionNode expressionNode = (LLExpressionNode) visit(ctx.expression());
        return createParenExpression(expressionNode, start, length);
    }

    @Override
    public Node visitDebuggerStatement(LazyLanguageParser.DebuggerStatementContext ctx) {
        final LLDebuggerNode debuggerNode = new LLDebuggerNode();
        setSourceFromContext(debuggerNode, ctx);
        return debuggerNode;
    }

    @Override
    public Node visitBreakStatement(LazyLanguageParser.BreakStatementContext ctx) {
        final LLBreakNode breakNode = new LLBreakNode();
        setSourceFromContext(breakNode, ctx);
        return breakNode;
    }

    @Override
    public Node visitContinueStatement(LazyLanguageParser.ContinueStatementContext ctx) {
        final LLContinueNode continueNode = new LLContinueNode();
        setSourceFromContext(continueNode, ctx);
        return continueNode;
    }

    @Override
    public Node visitWhileStatement(LazyLanguageParser.WhileStatementContext ctx) {
        LLExpressionNode conditionNode = (LLExpressionNode)visit(ctx.condition);
        LLStatementNode blockNode = (LLExpressionNode)visit(ctx.block());

        if (conditionNode == null || blockNode == null) {
            return null;
        }

        conditionNode.addStatementTag();
        final LLWhileNode whileNode = new LLWhileNode(conditionNode, blockNode);
        setSourceFromContext(whileNode, ctx);
        return whileNode;
    }

    @Override
    public Node visitIfStatement(LazyLanguageParser.IfStatementContext ctx) {
        LLExpressionNode conditionNode = (LLExpressionNode)visit(ctx.condition);
        LLStatementNode thenPartNode = (LLExpressionNode)visit(ctx.then);
        LLStatementNode elsePartNode = null;
        
        if(ctx.ELSE() != null) {
            elsePartNode = (LLExpressionNode)visit(ctx.block(1));
        }

        if (conditionNode == null || thenPartNode == null) {
            return null;
        }

        conditionNode.addStatementTag();
        final LLIfNode ifNode = new LLIfNode(conditionNode, thenPartNode, elsePartNode);
        setSourceFromContext(ifNode, ctx);
        return ifNode;
    }
    
    @Override
    public Node visitReturnStatement(LazyLanguageParser.ReturnStatementContext ctx) {
        LLExpressionNode valueNode = null;
        if(ctx.expression() != null) {
            valueNode = (LLExpressionNode)visit(ctx.expression());
        }
        final LLReturnNode returnNode = new LLReturnNode(valueNode);
        setSourceFromContext(returnNode, ctx);
        return returnNode;
    }

    /**
     * Returns the corresponding subclass of {@link LLExpressionNode} for binary
     * expressions. </br>
     * These nodes are currently not instrumented.
     *
     * @param opToken   The operator of the binary expression
     * @param leftNode  The left node of the expression
     * @param rightNode The right node of the expression
     * @return A subclass of LLExpressionNode using the given parameters based on
     *         the given opToken. null if either leftNode or rightNode is null.
     */
    public LLExpressionNode createBinary(ParserRuleContext ctx, Token opToken, LLExpressionNode leftNode,
            LLExpressionNode rightNode) {
        if (leftNode == null || rightNode == null) {
            return null;
        }
        final LLExpressionNode leftUnboxed = LLUnboxNodeGen.create(leftNode);
        final LLExpressionNode rightUnboxed = LLUnboxNodeGen.create(rightNode);

        final LLExpressionNode result;
        switch (opToken.getText()) {
            case "+":
                result = LLAddNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "*":
                result = LLMulNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "/":
                result = LLDivNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "-":
                result = LLSubNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "<":
                result = LLLessThanNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "<=":
                result = LLLessOrEqualNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case ">":
                result = LLLogicalNotNodeGen.create(LLLessOrEqualNodeGen.create(leftUnboxed, rightUnboxed));
                break;
            case ">=":
                result = LLLogicalNotNodeGen.create(LLLessThanNodeGen.create(leftUnboxed, rightUnboxed));
                break;
            case "==":
                result = LLEqualNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "!=":
                result = LLLogicalNotNodeGen.create(LLEqualNodeGen.create(leftUnboxed, rightUnboxed));
                break;
            case "&&":
                result = new LLLogicalAndNode(leftUnboxed, rightUnboxed);
                break;
            case "||":
                result = new LLLogicalOrNode(leftUnboxed, rightUnboxed);
                break;
            default:
                throw new LLParseError(source, ctx, "unexpected operation: " + opToken.getText());
        }

        int start = leftNode.getSourceCharIndex();
        int length = rightNode.getSourceEndIndex() - start;
        result.setSourceSection(start, length);
        result.addExpressionTag();

        return result;
    }

    /**
     * Returns an {@link LLInvokeNode} for the given parameters.
     *
     * @param functionNode   The function being called
     * @param parameterNodes The parameters of the function call
     * @param finalToken     A token used to determine the end of the
     *                       sourceSelection for this call
     * @return An LLInvokeNode for the given parameters. null if functionNode or any
     *         of the parameterNodes are null.
     */
    public LLExpressionNode createCall(LLExpressionNode functionNode, List<LLExpressionNode> parameterNodes,
            Token finalToken) {
        if (functionNode == null || containsNull(parameterNodes)) {
            return null;
        }

        final LLExpressionNode result = new LLInvokeNode(functionNode,
                parameterNodes.toArray(new LLExpressionNode[parameterNodes.size()]));

        final int startPos = functionNode.getSourceCharIndex();
        final int endPos = finalToken.getStartIndex() + finalToken.getText().length();
        result.setSourceSection(startPos, endPos - startPos);
        result.addExpressionTag();

        return result;
    }

    /**
     * Returns an {@link LLWriteLocalVariableNode} for the given parameters.
     *
     * @param nameNode  The name of the variable being assigned
     * @param valueNode The value to be assigned
     * @return An LLExpressionNode for the given parameters. null if nameNode or
     *         valueNode is null.
     */
    public LLExpressionNode createAssignment(LLExpressionNode nameNode, LLExpressionNode valueNode) {
        return createAssignment(nameNode, valueNode, null);
    }

    /**
     * Returns an {@link LLWriteLocalVariableNode} for the given parameters.
     *
     * @param nameNode      The name of the variable being assigned
     * @param valueNode     The value to be assigned
     * @param argumentIndex null or index of the argument the assignment is
     *                      assigning
     * @return An LLExpressionNode for the given parameters. null if nameNode or
     *         valueNode is null.
     */
    public LLExpressionNode createAssignment(LLExpressionNode nameNode, LLExpressionNode valueNode,
            Integer argumentIndex) {
        if (nameNode == null || valueNode == null) {
            return null;
        }

        String name = ((LLStringLiteralNode) nameNode).executeGeneric(null);
        FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(name, argumentIndex, FrameSlotKind.Illegal);
        lexicalScope.locals.put(name, frameSlot);
        final LLExpressionNode result = LLWriteLocalVariableNodeGen.create(valueNode, frameSlot, nameNode);

        if (valueNode.hasSource()) {
            final int start = nameNode.getSourceCharIndex();
            final int length = valueNode.getSourceEndIndex() - start;
            result.setSourceSection(start, length);
        }
        result.addExpressionTag();

        return result;
    }

    /**
     * Returns a {@link LLReadLocalVariableNode} if this read is a local variable or
     * a {@link LLFunctionLiteralNode} if this read is global. In LL, the only
     * global names are functions.
     *
     * @param nameNode The name of the variable/function being read
     * @return either:
     *         <ul>
     *         <li>A LLReadLocalVariableNode representing the local variable being
     *         read.</li>
     *         <li>A LLFunctionLiteralNode representing the function
     *         definition.</li>
     *         <li>null if nameNode is null.</li>
     *         </ul>
     */
    public LLExpressionNode createRead(LLExpressionNode nameNode) {
        if (nameNode == null) {
            return null;
        }

        String name = ((LLStringLiteralNode) nameNode).executeGeneric(null);
        final LLExpressionNode result;
        final FrameSlot frameSlot = lexicalScope.locals.get(name);
        if (frameSlot != null) {
            /* Read of a local variable. */
            result = LLReadLocalVariableNodeGen.create(frameSlot);
        } else {
            /*
             * Read of a global name. In our language, the only global names are functions.
             */
            result = new LLFunctionLiteralNode(name);
        }
        result.setSourceSection(nameNode.getSourceCharIndex(), nameNode.getSourceLength());
        result.addExpressionTag();
        return result;
    }

    public LLExpressionNode createStringLiteral(Token literalToken, boolean removeQuotes) {
        /* Remove the trailing and ending " */
        String literal = literalToken.getText();
        if (removeQuotes) {
            assert literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"");
            literal = literal.substring(1, literal.length() - 1);
        }

        final LLStringLiteralNode result = new LLStringLiteralNode(literal.intern());
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public LLExpressionNode createNumericLiteral(Token literalToken) {
        LLExpressionNode result;
        try {
            /* Try if the literal is small enough to fit into a long value. */
            result = new LLLongLiteralNode(Long.parseLong(literalToken.getText()));
        } catch (NumberFormatException ex) {
            /* Overflow of long value, so fall back to BigInteger. */
            result = new LLBigIntegerLiteralNode(new BigInteger(literalToken.getText()));
        }
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public LLExpressionNode createParenExpression(LLExpressionNode expressionNode, int start, int length) {
        if (expressionNode == null) {
            return null;
        }

        final LLParenExpressionNode result = new LLParenExpressionNode(expressionNode);
        result.setSourceSection(start, length);
        return result;
    }

    /**
     * Returns an {@link LLReadPropertyNode} for the given parameters.
     *
     * @param receiverNode The receiver of the property access
     * @param nameNode     The name of the property being accessed
     * @return An LLExpressionNode for the given parameters. null if receiverNode or
     *         nameNode is null.
     */
    public LLExpressionNode createReadProperty(LLExpressionNode receiverNode, LLExpressionNode nameNode) {
        if (receiverNode == null || nameNode == null) {
            return null;
        }

        final LLExpressionNode result = LLReadPropertyNodeGen.create(receiverNode, nameNode);

        final int startPos = receiverNode.getSourceCharIndex();
        final int endPos = nameNode.getSourceEndIndex();
        result.setSourceSection(startPos, endPos - startPos);
        result.addExpressionTag();

        return result;
    }

    /**
     * Returns an {@link LLWritePropertyNode} for the given parameters.
     *
     * @param receiverNode The receiver object of the property assignment
     * @param nameNode     The name of the property being assigned
     * @param valueNode    The value to be assigned
     * @return An LLExpressionNode for the given parameters. null if receiverNode,
     *         nameNode or valueNode is null.
     */
    public LLExpressionNode createWriteProperty(LLExpressionNode receiverNode, LLExpressionNode nameNode,
            LLExpressionNode valueNode) {
        if (receiverNode == null || nameNode == null || valueNode == null) {
            return null;
        }

        final LLExpressionNode result = LLWritePropertyNodeGen.create(receiverNode, nameNode, valueNode);

        final int start = receiverNode.getSourceCharIndex();
        final int length = valueNode.getSourceEndIndex() - start;
        result.setSourceSection(start, length);
        result.addExpressionTag();

        return result;
    }

    /**
     * Creates source description of a single token.
     */
    private static void srcFromToken(LLStatementNode node, Token token) {
        node.setSourceSection(token.getStartIndex(), token.getText().length());
    }

    /**
     * Checks whether a list contains a null.
     */
    private static boolean containsNull(List<?> list) {
        for (Object e : list) {
            if (e == null) {
                return true;
            }
        }
        return false;
    }

}