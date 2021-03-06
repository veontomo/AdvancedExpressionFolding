package com.intellij.advancedExpressionFolding;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

import java.util.List;

public class ShiftLeft extends Operation implements ArithmeticExpression {
    public ShiftLeft(PsiElement element, TextRange textRange, List<Expression> operands) {
        super(element, textRange, "<<", 20, operands);
    }

    @Override
    protected Operation copy(List<Expression> newOperands) {
        return new ShiftLeft(element, textRange, operands);
    }
}
