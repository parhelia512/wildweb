package cz.wildweb.server.router;

import cz.wildweb.api.HttpFilter;
import cz.wildweb.api.HttpHandler;
import cz.wildweb.api.HttpRequestInternal;

import java.util.*;

public class HttpRouter {

    private HttpRoute root = new HttpRoute();

    public void register(String method, String url, HttpHandler handler) {
        StringTokenizer tokenizer = new StringTokenizer(url, "/");
        HttpRoute node = this.root;
        while(tokenizer.hasMoreTokens()) {
            node = node.node(tokenizer.nextToken());
        }
        node.handler(method, handler);
    }

    public HttpHandler match(HttpRequestInternal request) {
        HttpUrlHolder match = this.root.match(new HttpUrlHolder(request.uri()), request);
        if(match == null) return null;
        for(int i = 0; i < match.variables().length; i++) {
            request.attribute(match.variables()[i], match.values()[i]);
        }
        request.splat(match.splat());
        return match.handler();
    }

    public void before(String method, String url, HttpFilter filter) {

    }

    public void after(String method, String url, HttpFilter filter) {

    }

}
