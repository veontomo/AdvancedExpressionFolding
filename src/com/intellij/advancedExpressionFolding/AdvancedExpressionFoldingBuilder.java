package com.intellij.advancedExpressionFolding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AdvancedExpressionFoldingBuilder extends FoldingBuilderEx {

    private static final FoldingDescriptor[] NO_DESCRIPTORS = new FoldingDescriptor[0];

    private static Set<String> supportedMethods = new HashSet<String>() {
        {
            add("add");
            add("multiply");
            add("divide");
            add("subtract");
            add("remainder");
            add("scaleByPowerOfTen");
            add("pow");
            add("min");
            add("max");
            add("negate");
            add("plus");
            add("abs");
            add("valueOf");
            add("equals");
            add("and");
            add("gcd");
            add("not");
            add("or");
            add("shiftLeft");
            add("shiftRight");
            add("signum");
            add("xor");
            add("andNot");
            add("mod");
            add("modInverse");
            add("modPow");
            add("acos");
            add("asin");
            add("atan");
            add("atan2");
            add("cbrt");
            add("ceil");
            add("cos");
            add("cosh");
            add("floor");
            add("log");
            add("log10");
            add("log1p");
            add("random");
            add("rint");
            add("round");
            add("sin");
            add("sinh");
            add("sqrt");
            add("tan");
            add("tanh");
            add("toDegrees");
            add("toRadians");
            add("ulp");
            add("hypot");
            add("exp");
            add("expm1");
            add("append");
            add("substring");
            add("subList");
            add("contains");
            add("containsKey");
            add("get");
            add("isPresent");
            add("charAt");
            add("put");
            add("set");
            add("asList");
            add("singletonList");
            add("addAll");
            add("removeAll");
            add("remove");
            add("collect");
            add("stream");
        }
    };

    private static Set<String> supportedClasses = new HashSet<String>() {
        {
            add("java.math.BigDecimal");
            add("java.math.BigInteger");
            add("java.lang.Math");
            add("java.lang.Long");
            add("java.lang.Integer");
            add("java.lang.Float");
            add("java.lang.Double");
            add("java.lang.Character");
            add("java.lang.String");
            add("java.lang.StringBuilder");
            add("java.lang.AbstractStringBuilder");
            add("java.util.List");
            add("java.util.ArrayList");
            add("java.util.Map");
            add("java.util.HashMap");
            add("java.util.Map");
            add("java.util.HashMap");
            add("java.util.Set");
            add("java.util.HashSet");
            add("java.lang.Object");
            add("java.util.Arrays");
            add("java.util.Optional");
            add("java.util.Collection");
            add("java.util.Collections");
            add("java.util.Objects");
            add("java.util.stream.Stream");
        }
    };

    private static Set<String> unsupportedClassesMethodsExceptions = new HashSet<String>() {
        {
            add("equals");
            add("compareTo");
        }
    };

    private static Set<String> supportedPrimitiveTypes = new HashSet<String>() {
        {
            add("int");
            add("long");
            add("float");
            add("double");
            add("char");
            add("java.lang.String");
        }
    };

    private static Set<String> supportedBinaryOperators = new HashSet<String>() {
        {
            add("+");
            add("-");
            add("*");
            add("/");
        }
    };

    private static Map<String, Object> supportedConstants = new HashMap<String, Object>() {
        {
            put("ZERO", 0);
            put("ONE", 1);
            put("TEN", 10);
            put("PI", "π");
            put("E", "\uD835\uDC52");
        }
    };

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement element, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> allDescriptors = null;
        try {
            FoldingGroup group = FoldingGroup.newGroup(AdvancedExpressionFoldingBuilder.class.getName());
            allDescriptors = null;
            Expression expression = getExpression(element, document,false);
            if (expression != null) {
                expression = expression.simplify();
                final String text = expression.format();
                if (!text.replaceAll("\\s+", "")
                        .equals(document.getText(expression.getTextRange()).replaceAll("\\s+", ""))) {
                    allDescriptors = new ArrayList<>();
                    if (expression.supportsFoldRegions(document, true)) {
                        Collections.addAll(allDescriptors, expression.buildFoldRegions(expression.getElement(), document));
                    } else {
                        allDescriptors.add(new FoldingDescriptor(element.getNode(),
                                expression.getTextRange(),
                                group) {
                            @Nullable
                            @Override
                            public String getPlaceholderText() {
                                return text;
                            }
                        });
                    }
                }
            }
            if (expression == null || !expression.getTextRange().equals(element.getTextRange())) {
                for (PsiElement child : element.getChildren()) {
                    FoldingDescriptor[] descriptors = buildFoldRegions(child, document, quick);
                    if (descriptors.length > 0) {
                        if (allDescriptors == null) {
                            allDescriptors = new ArrayList<>();
                        }
                        allDescriptors.addAll(Arrays.asList(descriptors));
                    }
                }
            }
        } catch (IndexNotReadyException e) {
            // ignore
        }
        return allDescriptors != null ? allDescriptors.toArray(NO_DESCRIPTORS) : NO_DESCRIPTORS;
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode astNode) {
        return null;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode astNode) {
        try {
            PsiElement element = astNode.getPsi();
            Expression expression = getExpression(element, PsiDocumentManager.getInstance(astNode.getPsi().getProject()).getDocument(astNode.getPsi().getContainingFile()), true);
            AdvancedExpressionFoldingSettings settings = AdvancedExpressionFoldingSettings.getInstance();
            return expression != null && (settings.isArithmeticExpressionsCollapse() && expression instanceof ArithmeticExpression
                        || settings.isComparingExpressionsCollapse() && expression instanceof ComparingExpression
                        || settings.isSlicingExpressionsCollapse() && expression instanceof SlicingExpression
                        || settings.isConcatenationExpressionsCollapse() && expression instanceof ConcatenationExpression
                        || settings.isRangeExpressionsCollapse() && expression instanceof RangeExpression
                        || settings.isGetExpressionsCollapse() && expression instanceof GetExpression
                        || settings.isCheckExpressionsCollapse() && expression instanceof CheckExpression
                        || settings.isCastExpressionsCollapse() && expression instanceof CastExpression
                        || settings.isVarExpressionsCollapse() && expression instanceof VariableDeclaration
                        || settings.isGetSetExpressionsCollapse() && expression instanceof GettersSetters
            )
                    && expression.isCollapsedByDefault();
        } catch (IndexNotReadyException e) {
            return false;
        }
    }

    private static Expression getForStatementExpression(PsiForStatement element, @Nullable Document document) {
        PsiJavaToken lParenth = element.getLParenth();
        PsiJavaToken rParenth = element.getRParenth();
        PsiStatement initialization = element.getInitialization();
        PsiStatement update = element.getUpdate();
        PsiExpression condition = element.getCondition();
        if (lParenth != null && rParenth != null
                && initialization instanceof PsiDeclarationStatement
                && ((PsiDeclarationStatement) initialization).getDeclaredElements().length == 1
                && ((PsiDeclarationStatement) initialization).getDeclaredElements()[0] instanceof PsiVariable
                && update != null && update.getChildren().length == 1
                && update.getChildren()[0] instanceof PsiPostfixExpression
                && ((PsiPostfixExpression)update.getChildren()[0]).getOperand() instanceof PsiReferenceExpression
                && ((PsiPostfixExpression)update.getChildren()[0]).getOperationSign().getText().equals("++")
                && ((PsiPostfixExpression)update.getChildren()[0]).getOperand().getReference() != null
                && condition instanceof PsiBinaryExpression
                && ((PsiBinaryExpression) condition).getLOperand() instanceof PsiReferenceExpression
                && ((PsiBinaryExpression) condition).getLOperand().getReference() != null
                && ((PsiBinaryExpression) condition).getROperand() != null) {
            @SuppressWarnings("ConstantConditions")
            PsiVariable updateVariable = (PsiVariable) ((PsiPostfixExpression) update.getChildren()[0]).getOperand().getReference().resolve();
            @SuppressWarnings("ConstantConditions")
            PsiVariable conditionVariable = (PsiVariable) ((PsiBinaryExpression) condition).getLOperand().getReference().resolve();
            if (updateVariable == ((PsiDeclarationStatement) initialization).getDeclaredElements()[0]
                    && updateVariable == conditionVariable
                    && ("int".equals(updateVariable.getType().getCanonicalText())
                    || "long".equals(updateVariable.getType().getCanonicalText()))) {
                Optional<PsiElement> identifier = Stream.of(((PsiDeclarationStatement) initialization).getDeclaredElements()[0].getChildren())
                        .filter(c -> c instanceof PsiIdentifier).findAny();
                Variable variable = new Variable(identifier.get(), identifier.get().getTextRange(), identifier.get().getText());
                Expression start = getExpression(
                        ((PsiVariable) ((PsiDeclarationStatement) initialization).getDeclaredElements()[0])
                                .getInitializer(), document, true);
                Expression end = getExpression(((PsiBinaryExpression) condition).getROperand(), document, true);
                String sign = ((PsiBinaryExpression) condition).getOperationSign().getText();
                    /*String type = updateVariable.getType().getCanonicalText();*/
                if (variable != null && start != null && end != null && ("<".equals(sign) || "<=".equals(sign))) {
                    if (element.getBody() instanceof PsiBlockStatement
                            && ((PsiBlockStatement) element.getBody()).getCodeBlock().getStatements().length > 0
                            && ((PsiBlockStatement) element.getBody()).getCodeBlock().getStatements()[0] instanceof PsiDeclarationStatement
                            && ((PsiDeclarationStatement) ((PsiBlockStatement) element.getBody()).getCodeBlock()
                            .getStatements()[0]).getDeclaredElements().length == 1) {
                        if (start instanceof NumberLiteral && ((NumberLiteral) start).getNumber().equals(0)) {
                            PsiVariable declaration = (PsiVariable) ((PsiDeclarationStatement) ((PsiBlockStatement) element.getBody())
                                    .getCodeBlock()
                                    .getStatements()[0]).getDeclaredElements()[0];
                            PsiIdentifier variableName = declaration.getNameIdentifier();
                            PsiExpression initializer = declaration.getInitializer();
                            if (initializer instanceof PsiArrayAccessExpression
                                    && ((PsiArrayAccessExpression) initializer).getIndexExpression() instanceof PsiReferenceExpression
                                    && ((PsiReferenceExpression) ((PsiArrayAccessExpression) initializer)
                                    .getIndexExpression()).isReferenceTo(conditionVariable)
                                    && ((PsiBinaryExpression) condition).getROperand() instanceof PsiReferenceExpression
                                    && ((PsiReferenceExpression) ((PsiBinaryExpression) condition).getROperand()).getQualifierExpression() instanceof PsiReferenceExpression
                                    && ((PsiReferenceExpression) ((PsiReferenceExpression) ((PsiBinaryExpression) condition)
                                    .getROperand()).getQualifierExpression()).isReferenceTo(((PsiReferenceExpression) ((PsiArrayAccessExpression) initializer).getArrayExpression()).resolve())) {
                                PsiExpression arrayExpression = ((PsiArrayAccessExpression) initializer)
                                        .getArrayExpression();
                                List<PsiElement> references = SyntaxTraverser.psiTraverser(element.getBody()).filter(e -> e instanceof PsiReferenceExpression
                                        && ((PsiReferenceExpression) e).isReferenceTo(conditionVariable)).toList();
                                if (references.size() == 1) {
                                    return new ForEachStatement(element, TextRange.create(
                                            element.getInitialization().getTextRange().getStartOffset(),
                                            declaration.getTextRange().getEndOffset()),
                                            declaration.getTextRange(), variableName.getTextRange(),
                                            arrayExpression.getTextRange(),
                                            variableName.getText(), arrayExpression.getText());
                                } else {
                                    PsiIdentifier indexName = conditionVariable.getNameIdentifier();
                                    AdvancedExpressionFoldingSettings settings = AdvancedExpressionFoldingSettings.getInstance();
                                    boolean isFinal = calculateIfFinal(declaration) && calculateIfFinal(updateVariable);
                                    return new ForEachIndexedStatement(element, TextRange.create(
                                            element.getInitialization().getTextRange().getStartOffset() - 1,
                                            declaration.getTextRange().getEndOffset()),
                                            declaration.getTextRange(),
                                            indexName.getTextRange(), variableName.getTextRange(),
                                            arrayExpression.getTextRange(),
                                            indexName.getText(),
                                            variableName.getText(),
                                            arrayExpression.getText(), settings.isVarExpressionsCollapse(),
                                            isFinal);
                                }
                            } else if (initializer instanceof PsiMethodCallExpression
                                    && ((PsiMethodCallExpression) initializer).getArgumentList().getExpressions().length == 1
                                    && ((PsiMethodCallExpression) initializer).getArgumentList().getExpressions()[0] instanceof PsiReferenceExpression
                                    && ((PsiReferenceExpression) ((PsiMethodCallExpression) initializer).getArgumentList().getExpressions()[0]).isReferenceTo(conditionVariable)
                                    && ((PsiBinaryExpression) condition).getROperand() instanceof PsiMethodCallExpression
                                    && ((PsiMethodCallExpression) ((PsiBinaryExpression) condition).getROperand()).getMethodExpression().getQualifierExpression() instanceof PsiReferenceExpression
                                    && ((PsiMethodCallExpression) initializer).getMethodExpression().getQualifierExpression() instanceof PsiReferenceExpression
                                    && ((PsiReferenceExpression) ((PsiMethodCallExpression) ((PsiBinaryExpression) condition)
                                    .getROperand()).getMethodExpression().getQualifierExpression()).isReferenceTo(
                                    ((PsiReferenceExpression) ((PsiMethodCallExpression) initializer).getMethodExpression()
                                            .getQualifierExpression()).resolve())) {
                                PsiExpression arrayExpression = ((PsiMethodCallExpression) initializer).getMethodExpression().getQualifierExpression();
                                List<PsiElement> references = SyntaxTraverser.psiTraverser(element.getBody()).filter(e -> e instanceof PsiReferenceExpression
                                        && ((PsiReferenceExpression) e).isReferenceTo(conditionVariable)).toList();
                                if (references.size() == 1) {
                                    return new ForEachStatement(element, TextRange.create(
                                            element.getInitialization().getTextRange().getStartOffset(),
                                            declaration.getTextRange().getEndOffset()),
                                            declaration.getTextRange(), variableName.getTextRange(),
                                            arrayExpression.getTextRange(),
                                            variableName.getText(), arrayExpression.getText());
                                } else {
                                    PsiIdentifier indexName = conditionVariable.getNameIdentifier();
                                    AdvancedExpressionFoldingSettings settings = AdvancedExpressionFoldingSettings.getInstance();
                                    boolean isFinal = calculateIfFinal(declaration) && calculateIfFinal(updateVariable);
                                    return new ForEachIndexedStatement(element, TextRange.create(
                                            element.getInitialization().getTextRange().getStartOffset() - 1,
                                            declaration.getTextRange().getEndOffset()),
                                            declaration.getTextRange(),
                                            indexName.getTextRange(), variableName.getTextRange(),
                                            arrayExpression.getTextRange(),
                                            indexName.getText(),
                                            variableName.getText(),
                                            arrayExpression.getText(), settings.isVarExpressionsCollapse(),
                                            isFinal);
                                }
                            }

                        }
                    }
                    int startOffset = lParenth.getTextRange().getStartOffset() + 1;
                    int endOffset = rParenth.getTextRange().getEndOffset() - 1;
                    ForStatement expression = new ForStatement(element, TextRange.create(startOffset, endOffset), variable,
                            start, true, end, "<=".equals(sign));
                    return expression.simplify(true);
                }
            }
        }
        return null;
    }

    public static Expression getExpression(PsiElement element, @Nullable Document document, boolean synthetic) {
        if (element != null) {
            return CachedValuesManager.getCachedValue(element,
                    () -> CachedValueProvider.Result.create(calculateExpression(element, document, synthetic),
                            PsiModificationTracker.MODIFICATION_COUNT));
        }
        return null;
    }

        // 💩💩💩 Define the AdvancedExpressionFoldingProvider extension point
    private static Expression calculateExpression(PsiElement element, @Nullable Document document, boolean synthetic) {
        if (element instanceof PsiForStatement) {
            Expression expression = getForStatementExpression((PsiForStatement) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiIfStatement) {
            Expression expression = getIfExpression((PsiIfStatement) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiArrayAccessExpression) {
            Expression expression = getArrayAccessExpression((PsiArrayAccessExpression) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiMethodCallExpression) {
            Expression expression = getMethodCallExpression((PsiMethodCallExpression) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiReferenceExpression) {
            Expression expression = getReferenceExpression((PsiReferenceExpression) element);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiNewExpression) {
            Expression expression = getNewExpression((PsiNewExpression) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiLiteralExpression) {
            Expression expression = getLiteralExpression((PsiLiteralExpression) element);
            if (expression != null) {
                return expression;
            }
        } if (element instanceof PsiAssignmentExpression) {
            Expression expression = getAssignmentExpression((PsiAssignmentExpression) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiPolyadicExpression) {
            Expression expression = getPolyadicExpression((PsiPolyadicExpression) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiBinaryExpression) {
            Expression expression = getBinaryExpression((PsiBinaryExpression) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiConditionalExpression) {
            Expression expression = getConditionalExpression((PsiConditionalExpression) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiPrefixExpression) {
            Expression expression = getPrefixExpression((PsiPrefixExpression) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiParenthesizedExpression) {
            if (((PsiParenthesizedExpression) element).getExpression() instanceof PsiTypeCastExpression) {
                TypeCast typeCast = getTypeCastExpression(
                        (PsiTypeCastExpression) ((PsiParenthesizedExpression) element).getExpression(), document);
                if (typeCast != null) {
                    return new TypeCast(element, element.getTextRange(), typeCast.getObject());
                }
            }
            if (((PsiParenthesizedExpression) element).getExpression() != null) {
                Expression expression = getExpression(((PsiParenthesizedExpression) element).getExpression(), document,
                        synthetic);
                if (expression != null) {
                    return expression;
                }
            }
        }
        if (element instanceof PsiTypeCastExpression) {
            TypeCast expression = getTypeCastExpression((PsiTypeCastExpression) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (element instanceof PsiVariable &&
                (element.getParent() instanceof PsiDeclarationStatement
                        || element.getParent() instanceof PsiForeachStatement)) {
            Expression expression = getVariableDeclaration((PsiVariable) element, document);
            if (expression != null) {
                return expression;
            }
        }
        if (synthetic && document != null) {
            ArrayList<Expression> children = new ArrayList<>();
            findChildExpressions(element, children, document);
            return new SyntheticExpressionImpl(element, element.getTextRange(), document.getText(element.getTextRange()), children);
        }
        return null;
    }

    private static Expression getArrayAccessExpression(PsiArrayAccessExpression element, Document document) {
        if (!(element.getParent() instanceof PsiAssignmentExpression
                && ((PsiAssignmentExpressionImpl) element.getParent()).getLExpression() == element)) {
            Expression indexExpression = getExpression(element.getIndexExpression(), document, false);
            Expression arrayExpression = getExpression(element.getArrayExpression(), document, true);
            if (indexExpression instanceof NumberLiteral && ((NumberLiteral) indexExpression).getNumber().equals(0)) {
                return new ArrayGet(element, element.getTextRange(), arrayExpression, ArrayGet.Style.FIRST);
            } else if (element.getIndexExpression() instanceof PsiBinaryExpression) {
                PsiBinaryExpression a2b = (PsiBinaryExpression) element.getIndexExpression();
                NumberLiteral position = getSlicePosition(element, arrayExpression, a2b, document);
                if (position != null && position.getNumber().equals(-1)) {
                    return new ArrayGet(element, element.getTextRange(), arrayExpression, ArrayGet.Style.LAST);
                }
            }
        }
        return null;
    }

    private static Expression getIfExpression(PsiIfStatement element, Document document) {
        if (element.getCondition() instanceof PsiBinaryExpression) {
            PsiBinaryExpression condition = (PsiBinaryExpression) element.getCondition();
            if (condition.getOperationSign().getText().equals("!=")
                    && element.getElseBranch() == null
                    && (condition.getLOperand().getType() == PsiType.NULL
                    && condition.getROperand() != null
                    || condition.getROperand() != null && condition.getROperand().getType() == PsiType.NULL)
                    && element.getThenBranch() != null) {
                PsiStatement thenStatement = element.getThenBranch();
                if (thenStatement.getChildren().length == 1 && thenStatement
                        .getChildren()[0] instanceof PsiCodeBlock) {
                    PsiStatement[] statements = ((PsiCodeBlock) thenStatement.getChildren()[0]).getStatements();
                    if (statements.length == 1) {
                        thenStatement = statements[0];
                    } else {
                        return null;
                    }
                }
                PsiElement qualifier = condition.getLOperand().getType() == PsiType.NULL
                        ? condition.getROperand()
                        : condition.getLOperand();
                if (qualifier instanceof PsiReferenceExpression
                        || (qualifier instanceof PsiMethodCallExpression
                        && ((PsiMethodCallExpression) qualifier).getMethodExpression().getReferenceName()
                        .startsWith("get")
                        && ((PsiMethodCallExpression) qualifier).getArgumentList().getExpressions().length == 0)) {
                    PsiElement r = findSameQualifier(thenStatement, qualifier);
                    if (r != null) {
                        return new ShortElvisExpression(element, element.getTextRange(),
                                getExpression(element.getCondition(), document, true),
                                getExpression(thenStatement, document, true),
                                Collections.singletonList(r.getTextRange()));
                    }
                }
            }
        }
        return null;
    }

    private static PsiElement findSameQualifier(PsiElement element, PsiElement qualifier) {
        if (element instanceof PsiStatement && element.getFirstChild() != null) {
            return findSameQualifier(element.getFirstChild(), qualifier);
        }
        if (equal(qualifier, element)) {
            return element;
        }
        if (element instanceof PsiMethodCallExpression && ((PsiMethodCallExpression) element).getMethodExpression().getQualifierExpression() != null) {
            return findSameQualifier(((PsiMethodCallExpression) element).getMethodExpression().getQualifierExpression(), qualifier);
        }
        if (element instanceof PsiReferenceExpression && ((PsiReferenceExpression) element).getQualifierExpression() != null) {
            return findSameQualifier(((PsiReferenceExpression) element).getQualifierExpression(),
                    qualifier);
        }
        return null;
    }

    private static Expression getConditionalExpression(PsiConditionalExpression element, Document document) {
        if (element.getCondition() instanceof PsiBinaryExpression) {
            PsiBinaryExpression condition = (PsiBinaryExpression) element.getCondition();
            if (condition.getOperationSign().getText().equals("!=")
                    && (condition.getLOperand().getType() == PsiType.NULL
                            && condition.getROperand() != null
            || condition.getROperand().getType() == PsiType.NULL)
                    && element.getThenExpression() != null
                    && element.getElseExpression() != null) {
                PsiElement qualifier = condition.getLOperand().getType() == PsiType.NULL
                        ? condition.getROperand()
                        : condition.getLOperand();
                if (qualifier instanceof PsiReferenceExpression
                        || (qualifier instanceof PsiMethodCallExpression
                        && ((PsiMethodCallExpression) qualifier).getMethodExpression().getReferenceName()
                        .startsWith("get")
                        && ((PsiMethodCallExpression) qualifier).getArgumentList().getExpressions().length == 0)) {
                    PsiReferenceExpression r = qualifier instanceof PsiReferenceExpression
                            ? ((PsiReferenceExpression) qualifier)
                            : ((PsiMethodCallExpression) qualifier).getMethodExpression();
                    List<PsiElement> references = SyntaxTraverser.psiTraverser(element.getThenExpression())
                            .filter(e ->
                                    e instanceof PsiReferenceExpression
                                            && !(e.getParent() instanceof PsiMethodCallExpression)
                                            && ((PsiReferenceExpression) e).isReferenceTo(r.resolve())
                                    || e instanceof PsiMethodCallExpression && ((PsiMethodCallExpression) e).getMethodExpression().isReferenceTo(r.resolve())
                            ).toList();
                    if (references.size() > 0) {
                        return new ElvisExpression(element, element.getTextRange(),
                                getExpression(element.getCondition(), document, true),
                                getExpression(element.getThenExpression(), document, true),
                                getExpression(element.getElseExpression(), document, true),
                                references.stream().map(PsiElement::getTextRange).collect(Collectors.toList()));
                    }
                }
            }
        }
        return null;
    }

    private static boolean equal(PsiElement e1, PsiElement e2) {
        // TODO: Use a cache for the resolved instance
        if (e2 instanceof PsiReferenceExpression && e1 instanceof PsiReferenceExpression) {
            return ((PsiReferenceExpression) e2).getReferenceName().equals(((PsiReferenceExpression)e1).getReferenceName())
                    && ((PsiReferenceExpression) e2).isReferenceTo(((PsiReferenceExpression)e1).resolve());
        } else if (e2 instanceof PsiMethodCallExpression && e1 instanceof PsiMethodCallExpression) {
            return equal(((PsiMethodCallExpression) e2).getMethodExpression(),
                    ((PsiMethodCallExpression) e1).getMethodExpression())
                    && equal(((PsiMethodCallExpression) e2).getMethodExpression().getQualifierExpression(),
                    ((PsiMethodCallExpression) e1).getMethodExpression().getQualifierExpression());
        }
        return false;
    }

    private static VariableDeclarationImpl getVariableDeclaration(PsiVariable element, Document document) {
        if (element.getName() != null && element.getTypeElement() != null
                && (element.getInitializer() != null || element.getParent() instanceof PsiForeachStatement)
                && element.getTextRange().getStartOffset() < element.getTypeElement().getTextRange().getEndOffset()) {
            boolean isFinal = calculateIfFinal(element);
            return new VariableDeclarationImpl(element, TextRange.create(
                    element.getTextRange().getStartOffset(),
                    element.getTypeElement().getTextRange().getEndOffset()),
                    element.getModifierList() != null && isFinal);
        }
        return null;
    }

    private static boolean calculateIfFinal(PsiVariable element) {
        boolean isFinal = element.getModifierList().hasExplicitModifier(PsiModifier.FINAL);
        if (!isFinal) {
            PsiElement body = element.getParent() instanceof PsiDeclarationStatement
                    ? element.getParent().getParent()
                    : element.getParent() instanceof PsiLoopStatement
                    ? ((PsiLoopStatement) element.getParent()).getBody()
                    : element.getParent();
            if (body instanceof PsiLoopStatement) {
                body = ((PsiLoopStatement) body).getBody();
            }
            List<PsiElement> references = SyntaxTraverser.psiTraverser(body)
                    .filter(e ->
                            e instanceof PsiAssignmentExpression
                                    && ((PsiAssignmentExpression) e).getLExpression() instanceof PsiReferenceExpression
                                    && ((PsiReferenceExpression) ((PsiAssignmentExpression) e).getLExpression()).isReferenceTo(element)
                                    || e instanceof PsiPostfixExpression
                                    && (((PsiPostfixExpression) e).getOperationSign().getText().equals("++")
                                    || ((PsiPostfixExpression) e).getOperationSign().getText().equals("--"))
                                    && ((PsiPostfixExpression) e).getOperand() instanceof PsiReferenceExpression
                                    && ((PsiReferenceExpression) ((PsiPostfixExpression) e).getOperand()).isReferenceTo(element)
                    ).toList();
            if (references.size() == 0) {
                isFinal = true;
            }
        }
        return isFinal;
    }

    private static void findChildExpressions(PsiElement element, List<Expression> expressions, @Nullable Document document) {
        for (PsiElement child : element.getChildren()) {
            Expression expression = getExpression(child, document, false);
            if (expression != null) {
                expressions.add(expression);
            }
            if (expression == null || !expression.getTextRange().equals(child.getTextRange())) {
                findChildExpressions(child, expressions, document);
            }
        }
    }

    private static TypeCast getTypeCastExpression(PsiTypeCastExpression expression, @Nullable Document document) {
        return expression.getOperand() != null
                ? new TypeCast(expression, expression.getTextRange(),
                getExpression(expression.getOperand(), document, true))
                : null;
    }

    private static Expression getLiteralExpression(PsiLiteralExpression element) {
        if (element.getType() != null) {
            if (supportedPrimitiveTypes.contains(element.getType().getCanonicalText())) {
                Object value = element.getValue();
                if (value instanceof Number) {
                    return new NumberLiteral(element, element.getTextRange(), (Number) value);
                } else if (value instanceof String) {
                    return new StringLiteral(element, element.getTextRange(), (String) value);
                } else if (value instanceof Character) {
                    return new CharacterLiteral(element, element.getTextRange(), (Character) value);
                }
            }
        }
        return null;
    }

    private static Expression getPrefixExpression(PsiPrefixExpression element, @Nullable Document document) {
        if (element.getOperand() != null) {
            if (element.getOperationSign().getText().equals("!")) {
                Expression operand = getExpression(element.getOperand(), document, true);
                if (operand instanceof Equal) {
                    return new NotEqual(element, element.getTextRange(), ((Equal) operand).getOperands());
                }
            } else if (element.getOperationSign().getText().equals("-")) {
                Expression operand = getExpression(element.getOperand(), document, true);
                if (operand != null) {
                    return new Negate(element, element.getTextRange(), Collections.singletonList(operand));
                }
            }
        }
        return null;
    }

    private static Expression getPolyadicExpression(PsiPolyadicExpression element, @Nullable Document document) {
        boolean add = true;
        boolean string = false;
        Expression[] operands = null;
        for (int i = 0; i < element.getOperands().length - 1; i++) {
            PsiExpression a = element.getOperands()[i];
            PsiExpression b = element.getOperands()[i + 1];
            PsiJavaToken token = element.getTokenBeforeOperand(b);
            if (token != null) {
                if ("&&".equals(token.getText())
                        && a instanceof PsiBinaryExpression
                        && b instanceof PsiBinaryExpression) {
                    Expression twoBinaryExpression = getAndTwoBinaryExpressions(element,
                            ((PsiBinaryExpression) a), ((PsiBinaryExpression) b), document);
                    if (twoBinaryExpression != null) {
                        return twoBinaryExpression;
                    }
                }
                if (add && "+".equals(token.getText())) {
                    if (operands == null) {
                        operands = new Expression[element.getOperands().length];
                    }
                    operands[i] = getExpression(element.getOperands()[i], document, true);
                    if (operands[i] instanceof StringLiteral) {
                        string = true;
                    }
                } else {
                    add = false;
                }
            }
        }
        if (add && operands != null) {
            operands[element.getOperands().length - 1] = getExpression(
                    element.getOperands()[element.getOperands().length - 1], document, true);
            if (operands[element.getOperands().length - 1] instanceof StringLiteral) {
                string = true;
            }
        }
        if (add && operands != null && string) {
            return new InterpolatedString(element, element.getTextRange(), Arrays.asList(operands));
        }
        if (element instanceof PsiBinaryExpression) {
            Expression binaryExpression = getBinaryExpression((PsiBinaryExpression) element, document);
            if (binaryExpression != null) {
                return binaryExpression;
            }
        }
        return null;
    }

    private static Expression getAndTwoBinaryExpressions(PsiElement parent, PsiBinaryExpression a, PsiBinaryExpression b, @Nullable Document document) {
        if ((a.getOperationSign().getText().equals("<") || a.getOperationSign().getText().equals("<="))
                && (b.getOperationSign().getText().equals(">") || b.getOperationSign().getText().equals(">="))
                && a.getLOperand() != null
                && a.getROperand() != null
                && b.getLOperand() != null
                && b.getROperand() != null) {
            Expression e1 = getExpression(a.getLOperand(), document, true);
            Expression e2 = getExpression(a.getROperand(), document, true);
            Expression e3 = getExpression(b.getLOperand(), document, true);
            Expression e4 = getExpression(b.getROperand(), document, true);
            if (e1 instanceof Variable && e3 instanceof Variable
                    && e1.equals(e3)
                    && e2 != null && e4 != null) {
                return new Range(parent, TextRange.create(a.getTextRange().getStartOffset(),
                        b.getTextRange().getEndOffset()), e1,
                        e4, b.getOperationSign().getText().equals(">="), e2, a.getOperationSign().getText().equals("<="));
            }
        }
        if ((a.getOperationSign().getText().equals(">") || a.getOperationSign().getText().equals(">="))
                && (b.getOperationSign().getText().equals("<") || b.getOperationSign().getText().equals("<="))
                && a.getLOperand() != null
                && a.getROperand() != null
                && b.getLOperand() != null
                && b.getROperand() != null) {
            Expression e1 = getExpression(a.getLOperand(), document, true);
            Expression e2 = getExpression(a.getROperand(), document, true);
            Expression e3 = getExpression(b.getLOperand(), document, true);
            Expression e4 = getExpression(b.getROperand(), document, true);
            if (e1 instanceof Variable && e3 instanceof Variable
                    && e1.equals(e3)
                    && e2 != null && e4 != null) {
                return new Range(parent, TextRange.create(a.getTextRange().getStartOffset(),
                        b.getTextRange().getEndOffset()), e1,
                        e2, a.getOperationSign().getText().equals(">="), e4, b.getOperationSign().getText().equals("<="));
            }
        }
        return null;
    }

    private static Expression getBinaryExpression(PsiBinaryExpression element, @Nullable Document document) {
        if (element.getLOperand() instanceof PsiMethodCallExpression
                && element.getROperand() instanceof PsiLiteralExpression
                || element.getROperand() instanceof PsiMethodCallExpression &&
                element.getLOperand() instanceof PsiLiteralExpression) {
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) (element
                    .getLOperand() instanceof PsiMethodCallExpression
                    ? element.getLOperand() : element.getROperand());

            PsiLiteralExpression literalExpression = (PsiLiteralExpression) (element
                    .getLOperand() instanceof PsiLiteralExpression
                    ? element.getLOperand() : element.getROperand());
            if (literalExpression.getText().equals("0") || literalExpression.getText().equals("-1") || literalExpression.getText().equals("1")) {
                Optional<PsiElement> identifier = Stream.of(methodCallExpression.getMethodExpression().getChildren())
                        .filter(c -> c instanceof PsiIdentifier).findAny();
                if (identifier.isPresent() && identifier.get().getText().equals("compareTo") && methodCallExpression.getArgumentList().getExpressions().length == 1) {
                    PsiMethod method = (PsiMethod) methodCallExpression.getMethodExpression().resolve();
                    if (method != null) {
                        PsiClass psiClass = method.getContainingClass();
                        if (psiClass != null && (supportedClasses.contains(eraseGenerics(psiClass.getQualifiedName()))
                                ||unsupportedClassesMethodsExceptions.contains(method.getName()))) {
                            Expression qualifier = methodCallExpression.getMethodExpression()
                                    .getQualifierExpression() != null ? getExpression(methodCallExpression.getMethodExpression()
                                    .getQualifierExpression(), document, true) : null;
                            if (qualifier != null) {
                                Expression argument = getExpression(methodCallExpression.getArgumentList()
                                        .getExpressions()[0], document, true);
                                if (argument != null) {
                                    switch (element.getOperationSign().getText()) {
                                        case "==":
                                            switch (literalExpression.getText()) {
                                                case "-1":
                                                    return new Less(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                                case "0":
                                                    return new Equal(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                                case "1":
                                                    return new Greater(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                            }
                                        case "!=":
                                            switch (literalExpression.getText()) {
                                                case "1":
                                                    return new LessEqual(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                                case "0":
                                                    return new NotEqual(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                                case "-1":
                                                    return new GreaterEqual(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                            }
                                        case "<":
                                            switch (literalExpression.getText()) {
                                                case "1":
                                                    return new LessEqual(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                                case "0":
                                                    return new Less(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                            }
                                        case ">":
                                            switch (literalExpression.getText()) {
                                                case "-1":
                                                    return new GreaterEqual(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                                case "0":
                                                    return new Greater(element, element.getTextRange(), Arrays.asList(qualifier, argument));
                                            }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (supportedBinaryOperators.contains(element.getOperationSign().getText())
                && element.getLOperand() != null && element.getROperand() != null) {
            Expression leftExpression = getExpression(element.getLOperand(), document, true);
            if (leftExpression != null) {
                Expression rightExpression = getExpression(element.getROperand(), document, true);
                if (rightExpression != null) {
                    switch (element.getOperationSign().getText()) {
                        case "+":
                            return new Add(element, element.getTextRange(), Arrays.asList(leftExpression, rightExpression));
                        case "-":
                            return new Subtract(element, element.getTextRange(), Arrays.asList(leftExpression, rightExpression));
                        case "*":
                            return new Multiply(element, element.getTextRange(), Arrays.asList(leftExpression, rightExpression));
                        case "/":
                            return new Divide(element, element.getTextRange(), Arrays.asList(leftExpression, rightExpression));
                    }
                }
            }
        }
        if ("&&".equals(element.getOperationSign().getText())
                && element.getLOperand() instanceof PsiBinaryExpression
                && element.getROperand() instanceof PsiBinaryExpression) {
            return getAndTwoBinaryExpressions(element,
                    ((PsiBinaryExpression) element.getLOperand()), ((PsiBinaryExpression) element.getROperand()), document);
        }
/*
        if ("!=".equals(element.getOperationSign().getText())
                && element.getROperand() != null && element.getLOperand() != null
                && (element.getLOperand().getType() == PsiType.NULL
                        || element.getROperand().getType() == PsiType.NULL)) {
            return new NotNullExpression(element.getTextRange(),
                    getExpression(element.getLOperand().getType() == PsiType.NULL
                            ? element.getROperand() : element.getLOperand(), document, true));
        }
*/
        return null;
    }

    private static Expression getAssignmentExpression(PsiAssignmentExpression element, @Nullable Document document) {
        Variable leftVariable = getVariableExpression(element.getLExpression());
        if (leftVariable != null && element.getRExpression() != null) {
            Expression leftExpression = getExpression(element.getRExpression(), document, true);
            if (leftExpression instanceof Operation) {
                Operation operation = (Operation) leftExpression;
                if (operation.getOperands().size() >= 2 && operation.getOperands().get(0).equals(leftVariable)) {
                    if (operation instanceof Add) {
                        return new AddAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().size() > 2 ?
                                        new Add(element, null, operation.getOperands()
                                                .subList(1, operation.getOperands().size())) : operation
                                        .getOperands().get(1)));
                    } else if (operation instanceof Subtract) {
                        return new SubtractAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().size() > 2 ?
                                        new Add(element, null, operation.getOperands()
                                                .subList(1, operation.getOperands().size())) : operation
                                        .getOperands().get(1)));
                    } else if (operation instanceof And) {
                        return new AndAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().size() > 2 ?
                                        new And(element, null, operation.getOperands()
                                                .subList(1, operation.getOperands().size())) : operation
                                        .getOperands().get(1)));
                    } else if (operation instanceof Or) {
                        return new AndAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().size() > 2 ?
                                        new Or(element, null, operation.getOperands()
                                                .subList(1, operation.getOperands().size())) : operation
                                        .getOperands().get(1)));
                    } else if (operation instanceof Xor) {
                        return new AndAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().size() > 2 ?
                                        new Xor(element, null, operation.getOperands()
                                                .subList(1, operation.getOperands().size())) : operation
                                        .getOperands().get(1)));
                    } else if (operation instanceof Multiply) {
                        return new MultiplyAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().size() > 2 ?
                                        new Multiply(element, null, operation.getOperands()
                                                .subList(1, operation.getOperands().size())) : operation
                                        .getOperands().get(1)));
                    } else if (operation instanceof Divide) {
                        return new DivideAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().size() > 2 ?
                                        new Multiply(element, null, operation.getOperands()
                                                .subList(1, operation.getOperands().size())) : operation
                                        .getOperands().get(1)));
                    } else if (operation instanceof ShiftRight && operation.getOperands().size() == 2) {
                        return new ShiftRightAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().get(1)));
                    } else if (operation instanceof ShiftLeft && operation.getOperands().size() == 2) {
                        return new ShiftLeftAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().get(1)));
                    } else if (operation instanceof Remainder && operation.getOperands().size() == 2) {
                        return new RemainderAssign(element, element.getTextRange(),
                                Arrays.asList(leftVariable, operation.getOperands().get(1)));
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static Expression getNewExpression(PsiNewExpression element, @Nullable Document document) {
        if (element.getType() != null && supportedClasses
                .contains(eraseGenerics(element.getType().getCanonicalText()))) {
            if (element.getArgumentList() != null && element.getArgumentList().getExpressions().length == 1) {
                if (element.getArgumentList().getExpressions()[0] instanceof PsiLiteralExpression){
                    return getConstructorExpression(element, element.getArgumentList().getExpressions()[0],
                            eraseGenerics(element.getType().getCanonicalText()));
                } else if (element.getArgumentList().getExpressions()[0] instanceof PsiReferenceExpression) {
                    return getReferenceExpression(
                            (PsiReferenceExpression) element.getArgumentList().getExpressions()[0], true);
                }
            } else if (element.getArgumentList() != null && element.getArgumentList().getExpressions().length == 0) {
                switch (eraseGenerics(element.getType().getCanonicalText())) {
                    case "java.lang.String":
                    case "java.lang.StringBuilder":
                        return new StringLiteral(element, element.getTextRange(), "");
                    case "java.util.ArrayList":
                        return new ListLiteral(element, element.getTextRange(), Collections.emptyList());
                }
            }
        }
        if (element.getType() != null && element.getArrayInitializer() != null) {
            return new ArrayLiteral(element, element.getTextRange(),
                    Arrays.stream(element.getArrayInitializer().getInitializers())
                            .map(i -> getExpression(i, document, true)).collect(
                            Collectors.toList()));
        }
        return null;
    }

    @Nullable
    private static Expression getReferenceExpression(PsiReferenceExpression element) {
        return getReferenceExpression(element, false);
    }

    @Nullable
    private static Expression getReferenceExpression(PsiReferenceExpression element, boolean copy) {
        Optional<PsiElement> found = Optional.empty();
        for (PsiElement c : element.getChildren()) {
            if (c instanceof PsiIdentifier) {
                found = Optional.of(c);
                break;
            }
        }
        Optional<PsiElement> identifier = found;
        if (identifier.isPresent()) {
            Object constant = supportedConstants.get(identifier.get().getText());
            if (constant != null) {
                if (isSupportedClass(element) && constant instanceof Number) {
                    return new NumberLiteral(element, element.getTextRange(), (Number) constant);
                } else if (isSupportedClass(element) && constant instanceof String) {
                    return new Variable(element, element.getTextRange(), (String) constant, copy);
                }
            } else {
                Expression variable = getVariableExpression(element, copy);
                if (variable != null) return variable;
            }
        }
        return null;
    }

    @Nullable
    private static Variable getVariableExpression(PsiElement element) {
        return getVariableExpression(element, false);
    }

    @Nullable
    private static Variable getVariableExpression(PsiElement element, boolean copy) {
        PsiReference reference = element.getReference();
        if (reference != null) {
            PsiElement e = reference.resolve();
            if (e instanceof PsiVariable && ((PsiVariable)e).getName().equals(element.getText())) {
                PsiVariable variable = (PsiVariable) e;
                if (supportedClasses.contains(eraseGenerics(variable.getType().getCanonicalText()))) {
                    return new Variable(element, element.getTextRange(), variable.getName(), copy);
                } else if (supportedPrimitiveTypes
                        .contains(eraseGenerics(variable.getType().getCanonicalText()))) {
                    return new Variable(element, element.getTextRange(), variable.getName(), copy);
                }
            }
        }
        return null;
    }

    private static String eraseGenerics(String signature) {
        String re = "<[^<>]*>";
        Pattern p = Pattern.compile(re);
        Matcher m = p.matcher(signature);
        while (m.find()) {
            signature = m.replaceAll("");
            m = p.matcher(signature);
        }
        return signature;
    }

    private static boolean isSupportedClass(PsiElement element) {
        PsiReference reference = element.getReference();
        if (reference != null) {
            PsiElement e = reference.resolve();
            if (e instanceof PsiField) {
                PsiField field = (PsiField) e;
                PsiClass psiClass = field.getContainingClass();
                if (psiClass != null && psiClass.getQualifiedName() != null) {
                    if (supportedClasses.contains(eraseGenerics(psiClass.getQualifiedName()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    private static Expression getMethodCallExpression(PsiMethodCallExpression element, @Nullable Document document) {
        PsiReferenceExpression referenceExpression = element.getMethodExpression();
        Optional<PsiElement> identifier = Stream.of(referenceExpression.getChildren())
                .filter(c -> c instanceof PsiIdentifier).findAny();
        if (identifier.isPresent() && supportedMethods.contains(identifier.get().getText())) {
            PsiMethod method = (PsiMethod) referenceExpression.resolve();
            if (method != null) {
                PsiClass psiClass = method.getContainingClass();
                if (psiClass != null && psiClass.getQualifiedName() != null) {
                    String className = eraseGenerics(psiClass.getQualifiedName());
                    if ((supportedClasses.contains(className) || unsupportedClassesMethodsExceptions.contains(method.getName()))
                            && element.getMethodExpression().getQualifierExpression() != null) {
                        PsiExpression qualifier = element
                                .getMethodExpression().getQualifierExpression();
                        Expression qualifierExpression = getExpression(qualifier, document, true);
                        if (qualifierExpression != null) {
                            String methodName = identifier.get().getText();
                            if (methodName.equals("asList") || methodName.equals("singletonList")) {
                                if (!methodName.equals("asList") ||
                                        element.getArgumentList().getExpressions().length != 1 ||
                                        !(element.getArgumentList().getExpressions()[0].getType() instanceof PsiArrayType)) {
                                    return new ListLiteral(element, element.getTextRange(),
                                            Stream.of(element.getArgumentList().getExpressions())
                                                    .map(e -> getExpression(e, document, true)).collect(
                                                    Collectors.toList()));
                                }
                            } else if (element.getArgumentList().getExpressions().length == 1) {
                                PsiExpression argument = element.getArgumentList().getExpressions()[0];
                                Expression argumentExpression = getExpression(argument, document, true);
                                if (argumentExpression != null) {
                                    switch (methodName) {
                                        case "add":
                                            switch (className) {
                                                case "java.util.List":
                                                case "java.util.ArrayList":
                                                case "java.util.Set":
                                                case "java.util.HashSet":
                                                case "java.util.Map":
                                                case "java.util.HashMap":
                                                case "java.util.Collection":
                                                    return new AddAssignForCollection(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                            }
                                            return new Add(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "remove":
                                            if (method.getParameterList().getParameters().length == 1
                                                    && !method.getParameterList().getParameters()[0].getType().equals(PsiType.INT)) {
                                                return new RemoveAssignForCollection(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                            }
                                        case "subtract":
                                            return new Subtract(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, argumentExpression));
                                        case "multiply":
                                            return new Multiply(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, argumentExpression));
                                        case "divide":
                                            return new Divide(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, argumentExpression));
                                        case "remainder":
                                            return new Remainder(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, argumentExpression));
                                        case "mod":
                                            return new Remainder(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, argumentExpression));
                                        case "scaleByPowerOfTen":
                                            return new Multiply(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, new Pow(element, null, Arrays
                                                            .asList(new NumberLiteral(element, null, 10),
                                                                    argumentExpression))));
                                        case "andNot":
                                            return new And(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, new Not(element, null,
                                                            Collections.singletonList(argumentExpression))));
                                        case "modInverse":
                                            return new Remainder(element, element.getTextRange(),
                                                    Arrays.asList(new Pow(element, null,
                                                                    Arrays.asList(qualifierExpression, new NumberLiteral(element, null, -1))),
                                                            argumentExpression));
                                        case "pow":
                                            return new Pow(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "min":
                                            return new Min(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "max":
                                            return new Max(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "gcd":
                                            return new Gcd(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "and":
                                            return new And(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "or":
                                            return new Or(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "xor":
                                            return new Xor(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "shiftLeft":
                                            return new ShiftLeft(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, argumentExpression));
                                        case "shiftRight":
                                            return new ShiftRight(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, argumentExpression));
                                        case "equals":
                                            return new Equal(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "append":
                                            return new Append(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, argumentExpression));
                                        /*case "contains":
                                        case "containsKey":
                                            return new Contains(element.getTextRange(), qualifierExpression, argumentExpression);*/
                                        case "charAt":
                                            return new Get(element, element.getTextRange(), qualifierExpression,
                                                    argumentExpression, Get.Style.NORMAL);
                                        case "get":
                                            if (argumentExpression instanceof NumberLiteral && ((NumberLiteral) argumentExpression).getNumber().equals(0)) {
                                                return new Get(element, element.getTextRange(), qualifierExpression,
                                                        argumentExpression, Get.Style.FIRST);
                                            } else if (argument instanceof PsiBinaryExpression) {
                                                PsiBinaryExpression a2b = (PsiBinaryExpression) argument;
                                                NumberLiteral position = getSlicePosition(element, qualifierExpression, a2b, document);
                                                if (position != null && position.getNumber().equals(-1)) {
                                                    return new Get(element, element.getTextRange(), qualifierExpression,
                                                            argumentExpression, Get.Style.LAST);
                                                }
                                            }
                                            return new Get(element, element.getTextRange(), qualifierExpression,
                                                    argumentExpression, Get.Style.NORMAL);
                                        case "subList":
                                        case "substring":
                                            if (argument instanceof PsiBinaryExpression) {
                                                NumberLiteral position = getSlicePosition(element,
                                                        qualifierExpression, (PsiBinaryExpression) argument, document);
                                                if (position != null) {
                                                    return new Slice(element, element.getTextRange(),
                                                            Arrays.asList(qualifierExpression, position));
                                                }
                                            }
                                            return new Slice(element, element.getTextRange(),
                                                    Arrays.asList(qualifierExpression, argumentExpression));
                                        case "addAll":
                                            return new AddAssignForCollection(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "removeAll":
                                            return new RemoveAssignForCollection(element, element.getTextRange(), Arrays.asList(qualifierExpression, argumentExpression));
                                        case "collect":
                                            if (argument instanceof PsiMethodCallExpression
                                                    && ((PsiMethodCallExpression) argument).getMethodExpression().getReferenceName().startsWith("to")
                                                    && ((PsiMethodCallExpression) argument).getMethodExpression().getQualifierExpression() instanceof PsiReferenceExpression
                                                    && ((PsiReferenceExpression) ((PsiMethodCallExpression) argument).getMethodExpression().getQualifierExpression()).getReferenceName().equals("Collectors")) {
                                                Optional<PsiElement> i = Arrays.stream(((PsiMethodCallExpression) argument).getMethodExpression().getChildren()).filter(c -> c instanceof PsiIdentifier && c.getText().startsWith("to")).findAny();
                                                if (i.isPresent()) {
                                                    return new Collect(element, TextRange.create(identifier.get().getTextRange().getStartOffset(),
                                                            element.getTextRange().getEndOffset()), qualifierExpression,
                                                            TextRange.create(i.get().getTextRange().getStartOffset(),
                                                                    argument.getTextRange().getEndOffset()));
                                                }
                                            }
                                        case "stream":
                                            if (element.getParent() instanceof PsiReferenceExpression &&
                                                    ((PsiReferenceExpression) element.getParent()).getQualifierExpression() == element) {
                                                return new ArrayStream(element, TextRange.create(
                                                        element.getTextRange().getStartOffset(), element.getTextRange().getEndOffset()
                                                ), argumentExpression);
                                            }
                                    }
                                }
                            } else if (element.getArgumentList().getExpressions().length == 0) {
                                switch (methodName) {
                                    case "plus":
                                        return qualifierExpression;
                                    case "negate":
                                        return new Negate(element, element.getTextRange(), Collections.singletonList(qualifierExpression));
                                    case "not":
                                        return new Not(element, element.getTextRange(), Collections.singletonList(qualifierExpression));
                                    case "abs":
                                        return new Abs(element, element.getTextRange(), Collections.singletonList(qualifierExpression));
                                    case "signum":
                                        return new Signum(element, element.getTextRange(), Collections.singletonList(qualifierExpression));
                                    /*case "get":
                                        return new AssertNotNullExpression(element.getTextRange(),
                                                qualifierExpression);*/
                                    /*case "isPresent":
                                        return new NotNullExpression(element.getTextRange(),
                                                qualifierExpression);*/
                                    case "stream":
                                        if (element.getParent() instanceof PsiReferenceExpression && ((PsiReferenceExpression) element.getParent()).getQualifierExpression() == element) {
                                            return new StreamExpression(element, TextRange.create(identifier.get().getTextRange().getStartOffset(),
                                                    element.getTextRange().getEndOffset()));
                                        }
                                }
                            } else if (element.getArgumentList().getExpressions().length == 2) {
                                PsiExpression a1 = element.getArgumentList().getExpressions()[0];
                                PsiExpression a2 = element.getArgumentList().getExpressions()[1];
                                Expression a1Expression = getExpression(a1, document, true);
                                if (a1Expression != null) {
                                    Expression a2Expression = getExpression(a2, document, true);
                                    if (a2Expression != null) {
                                        switch (methodName) {
                                            case "put":
                                            case "set":
                                                return new Put(element, element.getTextRange(), qualifierExpression, a1Expression, a2Expression);
                                            case "atan2":
                                                return new Atan2(element, element.getTextRange(), Arrays.asList(qualifierExpression, a1Expression,
                                                        a2Expression));
                                            case "modPow":
                                                return new Remainder(element, element.getTextRange(),
                                                        Arrays.asList(new Pow(element, null,
                                                                        Arrays.asList(qualifierExpression, a1Expression)),
                                                                a2Expression));
                                            case "substring":
                                            case "subList":
                                                if (a1 instanceof PsiBinaryExpression) {
                                                    NumberLiteral p1 = getSlicePosition(element, qualifierExpression, (PsiBinaryExpression) a1, document);
                                                    if (p1 != null) {
                                                        if (a2Expression instanceof NumberLiteral) {
                                                            return new Slice(element, element.getTextRange(), Arrays.asList(qualifierExpression,
                                                                    p1, a2Expression));
                                                        } else if (a2 instanceof PsiBinaryExpression) {
                                                            NumberLiteral p2 = getSlicePosition(element, qualifierExpression, (PsiBinaryExpression) a2, document);
                                                            if (p2 != null) {
                                                                return new Slice(element, element.getTextRange(),
                                                                        Arrays.asList(qualifierExpression, p1, p2));
                                                            }
                                                        } else if (a2 instanceof PsiMethodCallExpression) {
                                                            PsiMethodCallExpression a2m = (PsiMethodCallExpression) a2;
                                                            PsiReferenceExpression a2me = a2m.getMethodExpression();
                                                            Optional<PsiElement> a2i = Stream.of(a2me.getChildren())
                                                                    .filter(c -> c instanceof PsiIdentifier).findAny();
                                                            if (a2i.isPresent() && (a2i.get().getText().equals("length") || a2i.get()
                                                                    .getText().equals("size"))) {
                                                                Expression a2qe = getExpression(a2me.getQualifierExpression(), document, true);
                                                                if (a2qe != null && a2qe.equals(qualifierExpression)) {
                                                                    return new Slice(element, element.getTextRange(), Arrays.asList(qualifierExpression, p1));
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                if (a2 instanceof PsiBinaryExpression)  {
                                                    PsiBinaryExpression a2b = (PsiBinaryExpression) a2;
                                                    NumberLiteral position = getSlicePosition(element, qualifierExpression, a2b, document);
                                                    if (position != null) {
                                                        return new Slice(element, element.getTextRange(), Arrays.asList(qualifierExpression, a1Expression,
                                                                position));
                                                    }
                                                } else if (a2 instanceof PsiMethodCallExpression) {
                                                    PsiMethodCallExpression a2m = (PsiMethodCallExpression) a2;
                                                    PsiReferenceExpression a2me = a2m.getMethodExpression();
                                                    Optional<PsiElement> a2i = Stream.of(a2me.getChildren())
                                                            .filter(c -> c instanceof PsiIdentifier).findAny();
                                                    if (a2i.isPresent() && (a2i.get().getText().equals("length") || a2i.get()
                                                            .getText().equals("size"))) {
                                                        Expression a2qe = getExpression(a2me.getQualifierExpression(), document, true);
                                                        if (a2qe != null && a2qe.equals(qualifierExpression)) {
                                                            return new Slice(element, element.getTextRange(), Arrays.asList(qualifierExpression, a1Expression));
                                                        }
                                                    }
                                                }
                                                return new Slice(element, element.getTextRange(), Arrays.asList(qualifierExpression, a1Expression, a2Expression));
                                        }
                                    }
                                }
                            }
                        }
                        if (element.getArgumentList().getExpressions().length == 1) {
                            PsiExpression argument = element.getArgumentList().getExpressions()[0];
                            if (method.getName().equals("valueOf") && argument instanceof PsiLiteralExpression) {
                                return getConstructorExpression(element, argument,
                                        className);
                            } else if (method.getName().equals("valueOf") && argument instanceof PsiReferenceExpression) {
                                return getReferenceExpression((PsiReferenceExpression) argument);
                            } else {
                                Expression argumentExpression = getExpression(argument, document, true);
                                if (argumentExpression != null) {
                                    switch (method.getName()) {
                                        case "abs":
                                            return new Abs(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "acos":
                                            return new Acos(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "asin":
                                            return new Asin(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "atan":
                                            return new Atan(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "cbrt":
                                            return new Cbrt(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "ceil":
                                            return new Ceil(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "cos":
                                            return new Cos(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "cosh":
                                            return new Cosh(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "floor":
                                            return new Floor(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "log":
                                            return new Log(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "log1p":
                                            return new Log(element, element.getTextRange(), Collections.singletonList(
                                                    new Add(element, null, Arrays.asList(argumentExpression, new NumberLiteral(element, null, 1)))));
                                        case "log10":
                                            return new Log10(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "rint":
                                            return new Rint(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "round":
                                            return new Round(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "sin":
                                            return new Sin(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "sinh":
                                            return new Sinh(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "Sqrt":
                                            return new Sqrt(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "tan":
                                            return new Tan(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "tanh":
                                            return new Tanh(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "toDegrees":
                                            return new ToDegrees(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "toRadians":
                                            return new ToRadians(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "ulp":
                                            return new Ulp(element, element.getTextRange(), Collections.singletonList(argumentExpression));
                                        case "exp":
                                            return new Pow(element, element.getTextRange(),
                                                    Arrays.asList(new Variable(element, null, (String) supportedConstants.get("E")),
                                                            argumentExpression));
                                        case "expm1":
                                            return new Subtract(element, element.getTextRange(), Arrays.asList(new Pow(element, null, Arrays
                                                    .asList(new Variable(element, null, (String) supportedConstants.get("E")),
                                                            argumentExpression)), new NumberLiteral(element, null, 1)));
                                    }
                                }
                            }
                        } else if (element.getArgumentList().getExpressions().length == 2) {
                            PsiExpression a1 = element.getArgumentList().getExpressions()[0];
                            Expression a1Expression = getExpression(a1, document, true);
                            PsiExpression a2 = element.getArgumentList().getExpressions()[1];
                            Expression a2Expression = getExpression(a2, document, true);
                            if (a1Expression != null && a2Expression != null) {
                                String methodName = identifier.get().getText();
                                switch (methodName) {
                                    case "min":
                                        return new Min(element, element.getTextRange(), Arrays.asList(a1Expression, a2Expression));
                                    case "max":
                                        return new Max(element, element.getTextRange(), Arrays.asList(a1Expression, a2Expression));
                                    case "pow":
                                        return new Pow(element, element.getTextRange(), Arrays.asList(a1Expression, a2Expression));
                                    case "hypot":
                                        return new Add(element, element.getTextRange(), Collections.singletonList(new Sqrt(element, null, Collections.singletonList(
                                                new Add(element, null, Arrays
                                                        .asList(new Pow(element, null, Arrays.asList(a1Expression, new NumberLiteral(element, null, 2))),
                                                                new Pow(element, null, Arrays.asList(a2Expression, new NumberLiteral(element, null, 2)))))))));
                                    case "addAll":
                                        return new AddAssignForCollection(element, element.getTextRange(), Arrays.asList(a1Expression, a2Expression));
                                    case "equals":
                                        return new Equal(element, element.getTextRange(), Arrays.asList(a1Expression, a2Expression));
                                }
                            }
                        } else if (element.getArgumentList().getExpressions().length == 0) {
                            switch (method.getName()) {
                                case "random":
                                    return new Random(element, element.getTextRange(), Collections.emptyList());
                            }
                        }
                    }
                }
            }

        }
        if (identifier.isPresent() && ((identifier.get().getText().startsWith("get") && identifier.get().getText().length() > 3)
                || (identifier.get().getText().startsWith("is") && identifier.get().getText().length() > 2))
                && element.getArgumentList().getExpressions().length == 0) {
            return new Getter(element, element.getTextRange(), TextRange.create(identifier.get().getTextRange().getStartOffset(),
                    element.getTextRange().getEndOffset()),
                    element.getMethodExpression().getQualifierExpression() != null
                            ? getExpression(element.getMethodExpression().getQualifierExpression(), document, true)
                            : null,
                    guessPropertyName(identifier.get().getText()));
        } else if (identifier.isPresent()
                && identifier.get().getText().startsWith("set")
                && identifier.get().getText().length() > 3
                && Character.isUpperCase(identifier.get().getText().charAt(3))
                && element.getArgumentList().getExpressions().length == 1
                && element.getParent() instanceof PsiStatement
                && (element.getMethodExpression().getQualifierExpression() == null
                    || !(element.getMethodExpression().getQualifierExpression() instanceof PsiMethodCallExpression)
                    || !(((PsiMethodCallExpression)element.getMethodExpression().getQualifierExpression()).getMethodExpression().getReferenceName().startsWith("set")))) {
            return new Setter(element, element.getTextRange(), TextRange.create(identifier.get().getTextRange().getStartOffset(),
                    element.getTextRange().getEndOffset()),
                    getExpression(element.getMethodExpression().getQualifierExpression(), document, true),
                    guessPropertyName(identifier.get().getText()),
                    getExpression(element.getArgumentList().getExpressions()[0], document, true));
        }
        return null;
    }

    protected static int findDot(Document document, int position, int i) {
        int offset = 0;
        while (Math.abs(offset) < 100 && position > 0 && position < document.getText().length() && !document.getText(TextRange.create(position, position + 1)).equals(".")) {
            position += i;
            offset += i;
        };
        return offset;
    }

    private static String guessPropertyName(String text) {
        StringBuilder sb = new StringBuilder();
        if (text.startsWith("get")) {
            sb.append(text.substring(3));
        } else if (text.startsWith("set")) {
            sb.append(text.substring(3));
        } else if (text.startsWith("is")) {
            sb.append(text.substring(2));
        } else {
            sb.append(text);
        }
        for (int i = 0; i < sb.length(); i++) {
            if (Character.isUpperCase(sb.charAt(i)) &&
                    (i == sb.length() - 1 || Character.isUpperCase(sb.charAt(i + 1)) || i == 0)) {
                sb.setCharAt(i, Character.toLowerCase(sb.charAt(i)));
            } else if (Character.isLowerCase(sb.charAt(i))) {
                break;
            }
        }
        return sb.toString();
    }

    @Nullable
    private static NumberLiteral getSlicePosition(PsiElement parent, Expression qualifierExpression, PsiBinaryExpression a2b, @Nullable Document document) {
        if (a2b.getOperationSign().getText().equals("-")
                && (a2b.getLOperand() instanceof PsiMethodCallExpression || a2b.getLOperand() instanceof PsiReferenceExpression)) {
            Expression s = getExpression(a2b.getROperand(), document, true);
            if (s instanceof NumberLiteral) {
                PsiReferenceExpression a2me = a2b.getLOperand() instanceof PsiMethodCallExpression
                        ? ((PsiMethodCallExpression) a2b.getLOperand()).getMethodExpression(): (PsiReferenceExpression) a2b.getLOperand();
                Optional<PsiElement> a2i = Stream.of(a2me.getChildren())
                        .filter(c -> c instanceof PsiIdentifier).findAny();
                if (a2i.isPresent() && (a2i.get().getText().equals("length")
                        || a2i.get().getText().equals("size")) && a2me.getQualifierExpression() != null) {
                    Expression a2qe = getExpression(
                            a2me.getQualifierExpression(), document, true);
                    if (a2qe != null && a2qe.equals(qualifierExpression)) {
                        return new NumberLiteral(parent,
                                TextRange.create(a2b.getOperationSign().getTextRange().getStartOffset(),
                                        a2b.getTextRange().getEndOffset()), -((NumberLiteral) s).getNumber().intValue());
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static Expression getConstructorExpression(PsiElement parent, PsiExpression argument, String classQualifiedNameNoGenerics) {
        Expression literalExpression = getLiteralExpression((PsiLiteralExpression) argument);
        if (literalExpression instanceof NumberLiteral) {
            return literalExpression;
        } else {
            try {
                String value = argument.getText();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if ("java.lang.Long".equals(classQualifiedNameNoGenerics)) {
                    return new NumberLiteral(parent, parent.getTextRange(), Long.valueOf(value));
                } else if ("java.lang.Integer".equals(classQualifiedNameNoGenerics)) {
                    return new NumberLiteral(parent, parent.getTextRange(), Integer.valueOf(value));
                } else if ("java.lang.Float".equals(classQualifiedNameNoGenerics)) {
                    return new NumberLiteral(parent, parent.getTextRange(), Float.valueOf(value));
                } else if ("java.lang.Double".equals(classQualifiedNameNoGenerics)) {
                    return new NumberLiteral(parent, parent.getTextRange(), Double.valueOf(value));
                } else if ("java.lang.StringBuilder".equals(classQualifiedNameNoGenerics)) {
                    return new StringLiteral(parent, parent.getTextRange(), value);
                } else if ("java.lang.String".equals(classQualifiedNameNoGenerics)) {
                    return new StringLiteral(parent, parent.getTextRange(), value);
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

}
