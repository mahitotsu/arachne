package io.arachne.strands.tool.builtin;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class CalculatorExpressionEvaluator {

    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;

    BigDecimal evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("calculator requires a non-blank 'expression' field.");
        }
        Parser parser = new Parser(expression);
        BigDecimal value = parser.parseExpression();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Unexpected token '" + parser.currentCharacter() + "'.");
        }
        return value;
    }

    String format(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return normalized.toPlainString();
    }

    private BigDecimal applyFunction(String functionName, List<BigDecimal> arguments, Parser parser) {
        return switch (functionName) {
            case "abs" -> requireSingleArgument(functionName, arguments, parser).abs();
            case "round" -> round(arguments, parser);
            case "min" -> min(arguments, parser);
            case "max" -> max(arguments, parser);
            default -> throw parser.error("Unknown function: " + functionName);
        };
    }

    private BigDecimal requireSingleArgument(String functionName, List<BigDecimal> arguments, Parser parser) {
        if (arguments.size() != 1) {
            throw parser.error(functionName + " requires exactly 1 argument.");
        }
        return arguments.get(0);
    }

    private BigDecimal round(List<BigDecimal> arguments, Parser parser) {
        if (arguments.size() != 1 && arguments.size() != 2) {
            throw parser.error("round requires 1 or 2 arguments.");
        }
        BigDecimal value = arguments.get(0);
        int scale = 0;
        if (arguments.size() == 2) {
            scale = integerArgument(arguments.get(1), "round scale", parser);
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal min(List<BigDecimal> arguments, Parser parser) {
        if (arguments.size() < 2) {
            throw parser.error("min requires at least 2 arguments.");
        }
        BigDecimal minimum = arguments.get(0);
        for (int index = 1; index < arguments.size(); index++) {
            if (arguments.get(index).compareTo(minimum) < 0) {
                minimum = arguments.get(index);
            }
        }
        return minimum;
    }

    private BigDecimal max(List<BigDecimal> arguments, Parser parser) {
        if (arguments.size() < 2) {
            throw parser.error("max requires at least 2 arguments.");
        }
        BigDecimal maximum = arguments.get(0);
        for (int index = 1; index < arguments.size(); index++) {
            if (arguments.get(index).compareTo(maximum) > 0) {
                maximum = arguments.get(index);
            }
        }
        return maximum;
    }

    private int integerArgument(BigDecimal value, String label, Parser parser) {
        try {
            return value.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException e) {
            throw parser.error(label + " must be an integer.");
        }
    }

    private final class Parser {

        private final String expression;
        private int index;

        private Parser(String expression) {
            this.expression = expression;
        }

        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            boolean parsing = true;
            while (parsing) {
                skipWhitespace();
                if (match('+')) {
                    value = value.add(parseTerm());
                    continue;
                }
                if (match('-')) {
                    value = value.subtract(parseTerm());
                    continue;
                }
                parsing = false;
            }
            return value;
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseUnary();
            boolean parsing = true;
            while (parsing) {
                skipWhitespace();
                if (match('*')) {
                    value = value.multiply(parseUnary());
                    continue;
                }
                if (match('/')) {
                    BigDecimal divisor = parseUnary();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw error("Division by zero is not allowed.");
                    }
                    value = value.divide(divisor, DIVISION_CONTEXT);
                    continue;
                }
                if (match('%')) {
                    BigDecimal divisor = parseUnary();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw error("Division by zero is not allowed.");
                    }
                    value = value.remainder(divisor);
                    continue;
                }
                parsing = false;
            }
            return value;
        }

        private BigDecimal parseUnary() {
            skipWhitespace();
            if (match('+')) {
                return parseUnary();
            }
            if (match('-')) {
                return parseUnary().negate();
            }
            return parsePrimary();
        }

        private BigDecimal parsePrimary() {
            skipWhitespace();
            if (match('(')) {
                BigDecimal value = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw error("Missing closing ')'.");
                }
                return value;
            }
            if (isNumberStart()) {
                return parseNumber();
            }
            if (isIdentifierStart()) {
                return parseFunction();
            }
            if (isAtEnd()) {
                throw error("Unexpected end of expression.");
            }
            throw error("Unexpected token '" + currentCharacter() + "'.");
        }

        private BigDecimal parseFunction() {
            String functionName = parseIdentifier();
            skipWhitespace();
            if (!match('(')) {
                throw error("Unknown identifier: " + functionName);
            }
            List<BigDecimal> arguments = new ArrayList<>();
            skipWhitespace();
            if (!match(')')) {
                while (true) {
                    arguments.add(parseExpression());
                    skipWhitespace();
                    if (match(')')) {
                        break;
                    }
                    if (!match(',')) {
                        throw error("Expected ',' or ')' after function argument.");
                    }
                }
            }
            return applyFunction(functionName, arguments, this);
        }

        private String parseIdentifier() {
            int start = index;
            while (!isAtEnd()) {
                char current = expression.charAt(index);
                if (!Character.isLetterOrDigit(current) && current != '_') {
                    break;
                }
                index++;
            }
            return expression.substring(start, index);
        }

        private BigDecimal parseNumber() {
            int start = index;
            boolean hasDigits = false;
            while (!isAtEnd() && Character.isDigit(expression.charAt(index))) {
                index++;
                hasDigits = true;
            }
            if (!isAtEnd() && expression.charAt(index) == '.') {
                index++;
                while (!isAtEnd() && Character.isDigit(expression.charAt(index))) {
                    index++;
                    hasDigits = true;
                }
            }
            if (!hasDigits) {
                throw error("Expected a number.");
            }
            return new BigDecimal(expression.substring(start, index));
        }

        private boolean isNumberStart() {
            if (isAtEnd()) {
                return false;
            }
            char current = expression.charAt(index);
            if (Character.isDigit(current)) {
                return true;
            }
            return current == '.' && (index + 1) < expression.length() && Character.isDigit(expression.charAt(index + 1));
        }

        private boolean isIdentifierStart() {
            return !isAtEnd() && Character.isLetter(expression.charAt(index));
        }

        private boolean match(char expected) {
            if (isAtEnd() || expression.charAt(index) != expected) {
                return false;
            }
            index++;
            return true;
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }

        private boolean isAtEnd() {
            return index >= expression.length();
        }

        private char currentCharacter() {
            return expression.charAt(index);
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " At position " + index + ".");
        }
    }
}