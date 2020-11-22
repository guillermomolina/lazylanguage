/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * The parser and lexer need to be generated using "mx create-ll-parser".
 */

grammar LazyLanguage;

@parser::header
{
// DO NOT MODIFY - generated from LazyLanguage.g4 using "mx create-ll-parser"

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.RootCallTarget;
import com.guillermomolina.ll.LLLanguage;
import com.guillermomolina.ll.nodes.LLExpressionNode;
import com.guillermomolina.ll.nodes.LLRootNode;
import com.guillermomolina.ll.nodes.LLStatementNode;
import com.guillermomolina.ll.parser.LLParseError;
}

@lexer::header
{
// DO NOT MODIFY - generated from LazyLanguage.g4 using "mx create-ll-parser"
}

@parser::members
{
private LLNodeFactory factory;
private Source source;

private static final class BailoutErrorListener extends BaseErrorListener {
    private final Source source;
    BailoutErrorListener(Source source) {
        this.source = source;
    }
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        throwParseError(source, line, charPositionInLine, (Token) offendingSymbol, msg);
    }
}

public void SemErr(Token token, String message) {
    assert token != null;
    throwParseError(source, token.getLine(), token.getCharPositionInLine(), token, message);
}

private static void throwParseError(Source source, int line, int charPositionInLine, Token token, String message) {
    int col = charPositionInLine + 1;
    String location = "-- line " + line + " col " + col + ": ";
    int length = token == null ? 1 : Math.max(token.getStopIndex() - token.getStartIndex(), 0);
    throw new LLParseError(source, line, col, length, String.format("Error(s) parsing script:%n" + location + message));
}

public static Map<String, RootCallTarget> parseLL(LLLanguage language, Source source) {
    LazyLanguageLexer lexer = new LazyLanguageLexer(CharStreams.fromString(source.getCharacters().toString()));
    LazyLanguageParser parser = new LazyLanguageParser(new CommonTokenStream(lexer));
    lexer.removeErrorListeners();
    parser.removeErrorListeners();
    BailoutErrorListener listener = new BailoutErrorListener(source);
    lexer.addErrorListener(listener);
    parser.addErrorListener(listener);
    parser.factory = new LLNodeFactory(language, source);
    parser.source = source;
    parser.factory.visit( parser.lazylanguage());
    return parser.factory.getAllFunctions();
}
}

// parser




lazylanguage
:
function function* EOF
;


function
:
'function'
name=IDENTIFIER
s='('
                                                /*{ factory.startFunction($IDENTIFIER, $s); }*/
(
    IDENTIFIER                                  /*{ factory.addFormalParameter($IDENTIFIER); }*/
    (
        ','
        IDENTIFIER                              /*{ factory.addFormalParameter($IDENTIFIER); }*/
    )*
)?
')'
body=block[false]                               /*{ factory.finishFunction($body.result); }*/
;


block [boolean inLoop] /*returns [LLStatementNode result]*/
:                                               /*{ factory.startBlock();
                                                  List<LLStatementNode> body = new ArrayList<>(); }*/
s='{'
(
    statement[inLoop]                           /*{ body.add($statement.result); }*/
)*
e='}'
                                                /*{ $result = factory.finishBlock(body, $s.getStartIndex(), $e.getStopIndex() - $s.getStartIndex() + 1); }*/
;


statement [boolean inLoop] /*returns [LLStatementNode result]*/
:
(
    whileStatement                             /*{ $result = $whileStatement.result; }*/
|
    b='break'                                   /*{ if (inLoop) { $result = factory.createBreak($b); } else { SemErr($b, "break used outside of loop"); } }*/
    ';'
|
    c='continue'                                /*{ if (inLoop) { $result = factory.createContinue($c); } else { SemErr($c, "continue used outside of loop"); } }*/
    ';'
|
    ifStatement[inLoop]                        /*{ $result = $ifStatement.result; }*/
|
    returnStatement                            /*{ $result = $returnStatement.result; }*/
|
    expression ';'                              /*{ $result = $expression.result; }*/
|
    d='debugger'                                /*{ $result = factory.createDebugger($d); }*/
    ';'
)
;


whileStatement /*returns [LLStatementNode result]*/
:
w='while'
'('
condition=expression
')'
body=block[true]                                /* { $result = factory.createWhile($w, $condition.result, $body.result); } */
;


ifStatement [boolean inLoop] /*returns [LLStatementNode result]*/
:
i='if'
'('
condition=expression
')'
then=block[inLoop]                              /* { LLStatementNode elsePart = null; } */
(
    'else'
    block[inLoop]                               /* { elsePart = $block.result; } */
)?                                              /* { $result = factory.createIf($i, $condition.result, $then.result, elsePart); } */
;


returnStatement /*returns [LLStatementNode result]*/
:
r='return'                                      /* { LLExpressionNode value = null; } */
(
    expression                                  /* { value = $expression.result; } */
)?                                              /* { $result = factory.createReturn($r, value); } */
';'
;


expression /*returns [LLExpressionNode result]*/
:
logicTerm                                      /* { $result = $logicTerm.result; } */
(
    op='||'
    logicTerm                                  /* { $result = factory.createBinary($op, $result, $logicTerm.result); } */
)*
;


logicTerm /*returns [LLExpressionNode result]*/
:
logicFactor                                    /* { $result = $logicFactor.result; } */
(
    op='&&'
    logicFactor                                /* { $result = factory.createBinary($op, $result, $logicFactor.result); } */
)*
;


logicFactor /*returns [LLExpressionNode result]*/
:
arithmetic                                      /* { $result = $arithmetic.result; } */
(
    op=('<' | '<=' | '>' | '>=' | '==' | '!=' )
    arithmetic                                  /* { $result = factory.createBinary($op, $result, $arithmetic.result); } */
)?
;


arithmetic /*returns [LLExpressionNode result]*/
:
term                                            /* { $result = $term.result; } */
(
    op=('+' | '-')
    term                                        /* { $result = factory.createBinary($op, $result, $term.result); } */
)*
;


term /*returns [LLExpressionNode result]*/
:
factor                                          /* { $result = $factor.result; } */
(
    op=('*' | '/')
    factor                                      /* { $result = factory.createBinary($op, $result, $factor.result); } */
)*
;


factor /*returns [LLExpressionNode result]*/
:
(
    IDENTIFIER                                  /* { LLExpressionNode assignmentName = factory.createStringLiteral($IDENTIFIER, false); } */
    (
        memberExpression[null, null, null] /* { $result = $memberExpression.result; } */
    |
                                                /* { $result = factory.createRead(assignmentName); } */
    )
|
    STRING_LITERAL                              /* { $result = factory.createStringLiteral($STRING_LITERAL, true); } */
|
    NUMERIC_LITERAL                             /* { $result = factory.createNumericLiteral($NUMERIC_LITERAL); } */
|
    s='('
    expr=expression
    e=')'                                       /* { $result = factory.createParenExpression($expr.result, $s.getStartIndex(), $e.getStopIndex() - $s.getStartIndex() + 1); } */
)
;


memberExpression [LLExpressionNode r, LLExpressionNode assignmentReceiver, LLExpressionNode assignmentName] /*returns [LLExpressionNode result]*/
:                                               /* { LLExpressionNode receiver = r;
                                                  LLExpressionNode nestedAssignmentName = null; } */
(
    '('                                         /* { List<LLExpressionNode> parameters = new ArrayList<>();
                                                  if (receiver == null) {
                                                      receiver = factory.createRead(assignmentName);
                                                  } } */
    (
        expression                              /* { parameters.add($expression.result); } */
        (
            ','
            expression                          /* { parameters.add($expression.result); } */
        )*
    )?
    e=')'
                                                /* { $result = factory.createCall(receiver, parameters, $e); } */
|
    '='
    expression                                  /* { if (assignmentName == null) {
                                                      SemErr($expression.start, "invalid assignment target");
                                                  } else if (assignmentReceiver == null) {
                                                      $result = factory.createAssignment(assignmentName, $expression.result);
                                                  } else {
                                                      $result = factory.createWriteProperty(assignmentReceiver, assignmentName, $expression.result);
                                                  } } */
|
    '.'                                         /* { if (receiver == null) {
                                                       receiver = factory.createRead(assignmentName);
                                                  } } */
    IDENTIFIER
                                                /* { nestedAssignmentName = factory.createStringLiteral($IDENTIFIER, false);
                                                  $result = factory.createReadProperty(receiver, nestedAssignmentName); }
|
    '['                                         /* { if (receiver == null) {
                                                      receiver = factory.createRead(assignmentName);
                                                  } } */
    expression
                                                /* { nestedAssignmentName = $expression.result;
                                                  $result = factory.createReadProperty(receiver, nestedAssignmentName); } */
    ']'
)
(
    memberExpression[null, null, null]*/ /* { $result = $memberExpression.result; } */
)?
;

// lexer

WS : [ \t\r\n\u000C]+ -> skip;
COMMENT : '/*' .*? '*/' -> skip;
LINE_COMMENT : '//' ~[\r\n]* -> skip;

fragment LETTER : [A-Z] | [a-z] | '_' | '$';
fragment NON_ZERO_DIGIT : [1-9];
fragment DIGIT : [0-9];
fragment HEX_DIGIT : [0-9] | [a-f] | [A-F];
fragment OCT_DIGIT : [0-7];
fragment BINARY_DIGIT : '0' | '1';
fragment TAB : '\t';
fragment STRING_CHAR : ~('"' | '\\' | '\r' | '\n');

IDENTIFIER : LETTER (LETTER | DIGIT)*;
STRING_LITERAL : '"' STRING_CHAR* '"';
NUMERIC_LITERAL : '0' | NON_ZERO_DIGIT DIGIT*;

