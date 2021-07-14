package org.commonjava.indy.bind.jaxrs;

import org.apache.commons.lang3.StringUtils;

import javax.enterprise.context.ApplicationScoped;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;

@ApplicationScoped
public class RequestUrlRewritingFilter implements Filter {

    private static final String PROTOCOL_HTTP = "http:";
    private static final String PROTOCOL_HTTPS = "https:";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        final HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(httpServletRequest) {
            @Override
            public StringBuffer getRequestURL() {
                final StringBuffer originalUrl = ((HttpServletRequest) getRequest()).getRequestURL();
                String originalUrlStr = originalUrl.toString();
                if (originalUrl.indexOf(PROTOCOL_HTTP) != -1
                        && StringUtils.countMatches(originalUrlStr, "//") > 1) {
                    String temp = originalUrlStr.substring(PROTOCOL_HTTP.length() + 2);
                    return new StringBuffer(PROTOCOL_HTTP).append("//").append(temp.replace("//", "/"));
                } else if (originalUrl.indexOf(PROTOCOL_HTTPS) != -1
                        && StringUtils.countMatches(originalUrlStr, "//") > 1) {
                    String temp = originalUrlStr.substring(PROTOCOL_HTTPS.length() + 2);
                    return new StringBuffer(PROTOCOL_HTTPS).append("//").append(temp.replace("//", "/"));
                } else if (StringUtils.countMatches(originalUrlStr, "//") > 0) {
                    return new StringBuffer(originalUrlStr.replace("//", "/"));
                } else {
                    return originalUrl;
                }
            }
        };
        chain.doFilter(wrapped, response);
    }

}
