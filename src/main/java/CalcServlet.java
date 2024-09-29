import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/calc/*")
public class CalcServlet extends HttpServlet {

    private final String invalidRequest = "Invalid resource path";
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String path = request.getPathInfo();

        if (path == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidRequest);
            return;
        }

        String body = request.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual).trim();

        if (path.equals("/expression")) {
            if (!isValidExpression(body)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid expression format");
                return;
            }

            boolean isNew = session.getAttribute("expression") == null;
            session.setAttribute("expression", body);
            response.setStatus(isNew ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_OK);
            response.setHeader("Location", request.getRequestURL().toString());
        } else if (path.matches("/[a-z]")) {

            String variableName = path.substring(1);
            try {
                int value = parseVariableValue(body, session);
                if (value < -10000 || value > 10000) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Variable value out of range");
                    return;
                }
                boolean isNew = session.getAttribute(variableName) == null;
                session.setAttribute(variableName, value);
                response.setStatus(isNew ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_OK);
                response.setHeader("Location", request.getRequestURL().toString());
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid variable value format");
            }
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidRequest);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String path = request.getPathInfo();

        if (path.equals("/result")) {

            String expression = (String) session.getAttribute("expression");
            if (expression == null || expression.isEmpty()) {


                response.sendError(HttpServletResponse.SC_CONFLICT, "No expression set");
                return;
            }

            if(!isValidExpression(expression)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid expression format");
            }


            try {
                Map<String, Integer> variables = getVariablesFromSession(session);
                int result = evaluateExpression(expression, variables);

                if (result < -10000 || result > 10000) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Result out of range");
                    return;
                }

                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(String.valueOf(result));
            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
            }
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidRequest);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String path = request.getPathInfo();

        if (path.equals("/expression")) {

            session.removeAttribute("expression");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } else if (path.matches("/[a-z]")) {
            String variableName = path.substring(1);
            session.removeAttribute(variableName);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidRequest);
        }
    }


    private int parseVariableValue(String body, HttpSession session) throws NumberFormatException {
        if (body.matches("-?\\d+")) {
            return Integer.parseInt(body);
        } else if (body.matches("[a-z]")) {
            Integer value = (Integer) session.getAttribute(body);
            if (value == null) {
                throw new NumberFormatException("Variable not set");
            }
            return value;
        } else {
            throw new NumberFormatException("Invalid format");
        }
    }

    private Map<String, Integer> getVariablesFromSession(HttpSession session) {
        Map<String, Integer> variables = new HashMap<>();

        session.getAttributeNames().asIterator().forEachRemaining(name -> {
            if (name.matches("[a-z]")) {
                variables.put(name, (Integer) session.getAttribute(name));
            }
        });
        return variables;
    }


    private int evaluateExpression(String expression, Map<String, Integer> variables) throws Exception {

        Parser parser = new Parser(variables);
        return parser.parse(expression);
    }


    private boolean isValidExpression(String expression) {

        if (!expression.matches("[\\d\\s+\\-*/()a-z]+")) {
            return false;
        }

        if (!checkBalancedParentheses(expression)) {
            return false;
        }

        return !expression.matches(".*[+\\-*/]{2,}.*");
    }

    private boolean checkBalancedParentheses(String expression) {
        int balance = 0;
        for (char c : expression.toCharArray()) {
            if (c == '(') balance++;
            else if (c == ')') balance--;
            if (balance < 0) return false;
        }
        return balance == 0;
    }
}
