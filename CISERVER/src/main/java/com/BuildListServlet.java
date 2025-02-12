package com;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class BuildListServlet extends HttpServlet {
    
    // Use thread-safe date format
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws IOException {
        
        List<BuildStatus> statuses = FileBuildStatusStore.getStatuses();
        
        resp.setContentType("text/html; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        
        try (PrintWriter out = resp.getWriter()) {
            writeHtmlHeader(out);
            writeBuildsTable(out, statuses);
            writeHtmlFooter(out);
        }
    }

    private void writeHtmlHeader(PrintWriter out) {
        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("  <meta charset=\"UTF-8\">");
        out.println("  <meta http-equiv='refresh' content='5'>"); // Reduced refresh rate
        out.println("  <title>CI Server - All Builds</title>");
        out.println("  <style>");
        out.println("    table {border-collapse: collapse; width: 100%;}");
        out.println("    th, td {padding: 8px; text-align: left; border-bottom: 1px solid #ddd;}");
        out.println("    tr:hover {background-color: #f5f5f5;}");
        out.println("    .success {color: #4CAF50;}");
        out.println("    .failure {color: #f44336;}");
        out.println("  </style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<h1>CI Build History</h1>");
    }

    private void writeBuildsTable(PrintWriter out, List<BuildStatus> statuses) {
        out.println("<table>");
        out.println("  <thead>");
        out.println("    <tr>");
        out.println("      <th>ID</th>");
        out.println("      <th>Repository</th>");
        out.println("      <th>Commit SHA</th>");
        out.println("      <th>Branch</th>");
        out.println("      <th>Status</th>");
        out.println("      <th>Date</th>");
        out.println("    </tr>");
        out.println("  </thead>");
        out.println("  <tbody>");

        for (BuildStatus status : statuses) {
            String rowClass = status.isSuccess() ? "success" : "failure";
            String dateString = DATE_FORMAT.get().format(new Date(status.getTimestamp()));
            
            out.println("    <tr class=\"" + rowClass + "\">");
            out.println("      <td><a href=\"/buildDetail?id=" + status.getId() + "\">" 
                        + status.getId() + "</a></td>");
            out.println("      <td>" + escapeHtml(status.getRepoName()) + "</td>");
            out.println("      <td><code>" + escapeHtml(status.getCommitSHA()) + "</code></td>");
            out.println("      <td>" + escapeHtml(status.getBranch()) + "</td>");
            out.println("      <td>" + (status.isSuccess() ? "Success" : "Failure") + "</td>");
            out.println("      <td>" + dateString + "</td>");
            out.println("    </tr>");
        }
        
        out.println("  </tbody>");
        out.println("</table>");
    }

    private void writeHtmlFooter(PrintWriter out) {
        out.println("</body>");
        out.println("</html>");
    }

    // Basic HTML escaping for security
    private String escapeHtml(String input) {
        return input == null ? "" : input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}