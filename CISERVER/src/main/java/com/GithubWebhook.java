package com;

import org.apache.commons.io.FileUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;


/*
 * Class for a servlet for handling webhook from github.s
 * 
 */
public class GithubWebhook extends HttpServlet {

    // Injected collaborators
    private ProcessExecutor processExecutor;
    private GitHubClient gitHubClient;

    // Default constructor used in production
    public GithubWebhook() {
        this(new DefaultProcessExecutor());
    }

    // Constructor for injecting mocks in tests
    public GithubWebhook(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    /*
     * A sample response for the browser. 
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().println("This servlet is up and running.");
    }


    /*
     * The webhook is handled here and the 
     * 
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Read the webhook payload from the request body
            String payload;
            try (InputStream inputStream = request.getInputStream()) {
                payload = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Default values if extraction fails
            String repoName = "unknown";
            String commitSHA = "unknown";
            String branch = "unknown";
            String ownerLogin = "unknown";
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(payload);
                commitSHA = root.path("after").asText();
                JsonNode repoNode = root.path("repository");
                repoName = repoNode.path("name").asText();
                ownerLogin = repoNode.path("owner").path("login").asText();
                branch = root.path("ref").asText();

                System.out.println("Owner Login: " + ownerLogin);
                System.out.println("Repository: " + repoName);
                System.out.println("Commit SHA (after): " + commitSHA);
                System.out.println("Branch: " + branch);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            int exitCode = 0;
            String buildErrorDetails = "";
            try {
                runBuildAtCommit(ownerLogin, repoName, commitSHA);

            } catch (Exception e) {
                buildErrorDetails = e.getMessage();
                exitCode = 1;
            }

            // Prepare build status information.
            boolean success = exitCode == 0;
            String statusString = success ? "success" : "failure";
            String details = success ? "Build succeeded." : "Build and/or tests failed: " + buildErrorDetails;

            // Create a BuildStatus object and save it.
            BuildStatus buildStatus = new BuildStatus(repoName, commitSHA, branch, success, details);
            FileBuildStatusStore.addStatus(buildStatus);

            System.out.println("Posting to GitHub.");
            gitHubClient.postStatus(ownerLogin, repoName, commitSHA, statusString);


            // Respond based on the build result
            if (exitCode == 0) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println("Build succeeded.");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().println("Build failed.");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().println("Error handling webhook: " + e.getMessage());
        }
    }

    /*
     * Function for cloning the repo and checking out the based on the listed SHA. 
     * 
     */
    protected void runBuildAtCommit(String owner, String repo, String commitSHA) throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null) {
            throw new Exception("GITHUB_TOKEN environment variable is not set.");
         }
        String cloneUrl = "https://" + token + "@github.com/" + owner + "/" + repo + ".git";
        File workspace = new File("workspace_" + System.currentTimeMillis());
        
        workspace.mkdirs();
        System.out.println(workspace.getAbsolutePath());
        try {
            // Clone the repository into the workspace.
            ProcessBuilder clonePB = new ProcessBuilder("git", "clone", cloneUrl, workspace.getAbsolutePath());
            ProcessResult cloneResult = processExecutor.execute(clonePB);
            if (cloneResult.getExitCode() != 0) {
                throw new Exception("Git clone failed: " + cloneResult.getOutput());
            }

            // Check out the specific commit.
            ProcessBuilder checkoutPB = new ProcessBuilder("git", "checkout", commitSHA);
            checkoutPB.directory(workspace);
            ProcessResult checkoutResult = processExecutor.execute(checkoutPB);
            if (checkoutResult.getExitCode() != 0) {
                throw new Exception("Git checkout of commit " + commitSHA + " failed: " + checkoutResult.getOutput());
            }
            // Run the Maven build (compile and test) in the workspace.
            runCompilePhase(workspace);
            runTestPhase(workspace);
        } finally {
            // delete the workspace (clone of the repository)
            try{
                FileUtils.deleteDirectory(workspace);
                System.out.println("Deleted workspace." + workspace.getAbsolutePath());
            } catch (IOException e) {
                System.out.println("Failed to delete workspace: " + e.getMessage());
            }
        }


    }

    
    /**
     * Runs the Maven compile phase using ProcessBuilder.
     * Executes "mvn -B clean compile" in the current directory.
     * If the process fails, throws an Exception including the full log output.
     */
    protected void runCompilePhase(File workspace) throws Exception {
        System.out.println("Starting compile phase...");
        ProcessBuilder pb = new ProcessBuilder("mvn", "-B", "-f", "lab2/CIserver/pom.xml", "clean", "compile");
        pb.directory(workspace);
        ProcessResult compileResult = processExecutor.execute(pb);
        System.out.println("Compile phase exit code: " + compileResult.getExitCode());
        System.out.println("Compile output:\n" + compileResult.getOutput());
        if (compileResult.getExitCode() != 0) {
            throw new Exception("Compilation failed with exit code " + compileResult.getExitCode() + "\n" + compileResult.getOutput());
        }
    }

    /**
     * Runs the Maven test phase using the injected ProcessExecutor.
     */
    protected void runTestPhase(File workspace) throws Exception {
        System.out.println("Starting test phase...");
        ProcessBuilder pb = new ProcessBuilder("mvn", "-B", "-f", "lab2/CIserver/pom.xml", "test");
        pb.directory(workspace);
        ProcessResult testResult = processExecutor.execute(pb);
        System.out.println("Test phase exit code: " + testResult.getExitCode());
        System.out.println("Test output:\n" + testResult.getOutput());
        if (testResult.getExitCode() != 0) {
            throw new Exception("Test phase failed with exit code " + testResult.getExitCode() + "\n" + testResult.getOutput());
        }
    }
}


