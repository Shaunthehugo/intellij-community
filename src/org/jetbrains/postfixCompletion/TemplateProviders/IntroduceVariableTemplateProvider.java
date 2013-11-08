package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.refactoring.introduce.inplace.*;
import com.intellij.refactoring.introduceVariable.*;
import com.intellij.refactoring.ui.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "var",
  description = "Introduces variable for expression",
  example = "var x = expr;")
public class IntroduceVariableTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull PostfixTemplateAcceptanceContext context, @NotNull List<LookupElement> consumer) {

    // todo: support expressions
    // todo: setup selection before refactoring? or context?
    // todo: disable when qualifier type is unknown (what about broken, but fixable exprs?)
    // todo: make it works on types
    // todo: 1 + new T.var  ????

    for (PrefixExpressionContext expressionContext : context.expressions)
      if (expressionContext.canBeStatement) {

        if (expressionContext.expression instanceof PsiReferenceExpression) {
          final PsiElement element = ((PsiReferenceExpression) expressionContext.expression).resolve();
          if (element instanceof PsiType) {
            return;
          }
        }

        // todo: disable when qualifier resolves to type
        // todo: disable when only one expr and it is unresolved

        consumer.add(new IntroduceVarLookupElement(expressionContext));
        break;
      }
  }

  private static class IntroduceVarLookupElement extends StatementPostfixLookupElement<PsiExpressionStatement> {
    public IntroduceVarLookupElement(@NotNull PrefixExpressionContext context) {
      super("var", context);
    }

    @NotNull @Override protected PsiExpressionStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiFile context) {
      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement) factory.createStatementFromText("expr", context);

      expressionStatement.getExpression().replace(expression);
      return expressionStatement;
    }

    @Override public void handleInsert(@NotNull final InsertionContext context) {
      // execute insertion without undo manager enabled
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override public void run() {
          IntroduceVarLookupElement.super.handleInsert(context);
        }
      });
    }

    @Override protected void postProcess(
      @NotNull InsertionContext context, @NotNull PsiExpressionStatement statement) {
      IntroduceVariableHandler handler;
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        handler = getMockHandler();
      } else {
        handler = new IntroduceVariableHandler();
      }

      handler.invoke(context.getProject(), context.getEditor(), statement.getExpression());
    }

    @NotNull private IntroduceVariableHandler getMockHandler() {
      return new IntroduceVariableHandler() {
        // mock default settings
        @Override public IntroduceVariableSettings getSettings(
          Project project, Editor editor, final PsiExpression expr, PsiExpression[] occurrences,
          TypeSelectorManagerImpl typeSelectorManager, boolean declareFinalIfAll, boolean anyAssignmentLHS,
          InputValidator validator, PsiElement anchor, OccurrencesChooser.ReplaceChoice replaceChoice) {
          return new IntroduceVariableSettings() {
            @Override public String getEnteredName() { return "foo"; }
            @Override public boolean isReplaceAllOccurrences() { return false; }
            @Override public boolean isDeclareFinal() { return false; }
            @Override public boolean isReplaceLValues() { return false; }
            @Override public PsiType getSelectedType() { return expr.getType(); }
            @Override public boolean isOK() { return true; }
          };
        }
      };
    }
  }
}
