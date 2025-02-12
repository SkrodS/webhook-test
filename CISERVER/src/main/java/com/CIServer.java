package com;


import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class CIServer {


    public static Server createServer(int portnmbr){
        System.setProperty("org.eclipse.jetty.LEVEL", "DEBUG");
        Server server = new Server(portnmbr);

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        server.setHandler(handler);
        handler.addServlet(new ServletHolder(new GithubWebhook()), "/");
        handler.addServlet(new ServletHolder(new BuildListServlet()), "/builds");
        return server;
    }

    public static void main(String[] args) throws Exception {

        int portNmbr = 8080;
        Server server = createServer(portNmbr);

        server.start();
        server.join();
    }
}
