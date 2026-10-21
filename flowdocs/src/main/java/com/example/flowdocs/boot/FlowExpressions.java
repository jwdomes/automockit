package com.example.flowdocs.boot;

import java.lang.reflect.Method;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

/** Parses and evaluates condition tests and note templates against a method's arguments. */
public final class FlowExpressions {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final TemplateParserContext TEMPLATE = new TemplateParserContext();
    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();
    private static final Method HAS_TEXT;

    static {
        try {
            HAS_TEXT = StringUtils.class.getMethod("hasText", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private FlowExpressions() {
    }

    public static Expression parseCondition(String test) {
        return PARSER.parseExpression(test);
    }

    public static Expression parseNote(String template) {
        return PARSER.parseExpression(template, TEMPLATE);
    }

    /** Arguments are available as {@code #parameterName}; {@code #hasText(String)} is registered. */
    public static EvaluationContext context(Method method, Object[] arguments) {
        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(null, method, arguments, PARAMETER_NAMES);
        context.registerFunction("hasText", HAS_TEXT);
        return context;
    }
}
