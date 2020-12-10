import static TerraformEnvironmentStage.PLAN
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class GithubRepo {

    private static String myRepoSlug
    private static String myRepoHost
    private static String githubTokenEnvVar = "GITHUB_TOKEN"
    private static final int MAX_COMMENT_LENGTH = 65535

    GithubRepo(String repoSlug, String repoHost, String tokenEnvVar) {
      if(repoSlug != null) {
        this.myRepoSlug = repoSlug
      }
      if(repoHost != null) {
        this.myRepoHost = repoHost
      }
      if(tokenEnvVar != null) {
        this.githubTokenEnvVar = tokenEnvVar
      }
    }

    public static setRepoSlug(String newRepoSlug) {
        this.myRepoSlug = newRepoSlug
    }

    public static setRepoHost(String newRepoHost) {
        this.myRepoHost = newRepoHost
    }

    public static setGithubTokenEnvVar(String githubTokenEnvVar) {
        this.githubTokenEnvVar = githubTokenEnvVar
    }

    public String getRepoSlug() {
        if (myRepoSlug != null) {
            return myRepoSlug
        }

        def parsedScmUrl = Jenkinsfile.instance.getParsedScmUrl()
        def organization = parsedScmUrl['organization']
        def repo = parsedScmUrl['repo']

        return "${organization}/${repo}"
    }

    public String getRepoHost() {
        if (myRepoHost != null) {
            return myRepoHost
        }

        def parsedScmUrl = Jenkinsfile.instance.getParsedScmUrl()
        def protocol = parsedScmUrl['protocol']
        def domain = parsedScmUrl['domain']

        // We cannot post using the git protocol, change to https
        if (protocol == "git") {
            protocol = "https"
        }

        return "${protocol}://${domain}"
    }

    public String getBranchName() {
        return Jenkinsfile.instance.getEnv().BRANCH_NAME
    }

    public boolean isPullRequest() {
        def branchName = getBranchName()

        return branchName.startsWith('PR-')
    }

    public String getPullRequestNumber() {
        def branchName = getBranchName()

        return branchName.replace('PR-', '')
    }

    public String getPullRequestCommentUrl() {
        def repoHost = getRepoHost()
        def repoSlug = getRepoSlug()
        def pullRequestNumber = getPullRequestNumber()

        return "${repoHost}/api/v3/repos/${repoSlug}/issues/${pullRequestNumber}/comments".toString()
    }

    public postPullRequestComment(String commentBody) {
        def pullRequestUrl = getPullRequestCommentUrl()
        return postPullRequestComment(pullRequestUrl, commentBody)
    }

    public postPullRequestComment(String pullRequestUrl, String commentBody) {
        def closure = { ->
            echo "Creating comment in GitHub"
            // GitHub can't handle comments of 65536 or longer; chunk
            commentBody.split("(?<=\\G.{${MAX_COMMENT_LENGTH}})").each { chunk ->
                def data = JsonOutput.toJson([body: chunk])
                def tmpDir = pwd(tmp: true)
                def bodyPath = "${tmpDir}/body.txt"
                writeFile(file: bodyPath, text: data)

                def cmd = "curl -H \"Authorization: token \$${githubTokenEnvVar}\" -X POST -d @${bodyPath} -H 'Content-Type: application/json' -D comment.headers ${pullRequestUrl}"

                def output = sh(script: cmd, returnStdout: true).trim()

                def headers = readFile('comment.headers').trim()
                if (! headers.contains('HTTP/1.1 201 Created')) {
                    error("Creating GitHub comment failed: ${headers}\n")
                }
                // ok, success
                def decoded = new JsonSlurper().parseText(output)
                echo "Created comment ${decoded.id} - ${decoded.html_url}"
            }
        }

        closure.delegate = Jenkinsfile.original
        closure()
    }

    public static void reset() {
        this.myRepoSlug = null
        this.myRepoHost = null
        this.githubTokenEnvVar = "GITHUB_TOKEN"
    }
}
