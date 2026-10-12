package ai.generalcontracts.startup;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestPathResolver {
    private RequestPathResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && requestUri.startsWith(contextPath)
            ? requestUri.substring(contextPath.length())
            : requestUri;
    }
}
