import static TerraformEnvironmentStage.PLAN
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class GithubPRPlanPlugin implements TerraformPlanCommandPlugin, TerraformEnvironmentStagePlugin {

    private static GitHubRepo myRepo

    public static void init() {
        GithubPRPlanPlugin plugin = new GithubPRPlanPlugin()
        myRepo = new GitHubRepo()

        TerraformEnvironmentStage.addPlugin(plugin)
        TerraformPlanCommand.addPlugin(plugin)
    }

    public static withRepoSlug(String newRepoSlug) {
        myRepo.setRepoSlug(newRepoSlug)
        return this
    }

    public static withRepoHost(String newRepoHost) {
        myRepo.setRepoHost(newRepoHost)
        return this
    }

    public static withGithubTokenEnvVar(String githubTokenEnvVar) {
        myRepo.setGithubTokenEnvVar(githubTokenEnvVar)
        return this
    }

    @Override
    public void apply(TerraformEnvironmentStage stage) {
        stage.decorate(PLAN, addComment(stage.getEnvironment()))
    }

    @Override
    public void apply(TerraformPlanCommand command) {
        command.withArgument("-out=tfplan")
        command.withStandardErrorRedirection('plan.err')
        command.withSuffix('| tee plan.out')
    }

    public Closure addComment(String env) {
        return { closure ->
            closure()

            if (myRepo.isPullRequest()) {
                String comment = getCommentBody(env)
                myRepo.postPullRequestComment(comment)
            }
        }
    }

    public String readFile(String filename) {
        def original = Jenkinsfile.instance.original
        if (original.fileExists(filename)) {
            return original.readFile(filename)
        }

        return null
    }

    public String getPlanOutput() {
        def planOutput =  readFile('plan.out')
        def planError = readFile('plan.err')
        // Skip any file outputs when the file does not exist
        def outputs = [planOutput, planError] - null

        // Strip any ANSI color encodings and whitespaces
        def results = outputs.collect { output ->
            output.replaceAll(/\u001b\[[0-9;]+m/, '')
                  .replace(/^\[[0-9;]+m/, '')
                  .trim()
        }

        // Separate by STDERR header if plan.err is not empty
        results.findAll { it != '' }
               .join('\nSTDERR:\n')
    }

    public String getBuildResult() {
        Jenkinsfile.instance.original.currentBuild.currentResult
    }

    public String getBuildUrl() {
        Jenkinsfile.instance.original.build_url
    }

    public String getCommentBody(String environment) {
        def planOutput = getPlanOutput()
        def buildResult = getBuildResult()
        def buildUrl = getBuildUrl()
        def lines = []
        lines << "**Jenkins plan results for ${environment}** - ${buildResult} ( ${buildUrl} ):"
        lines << ''
        lines << '```'
        lines << planOutput
        lines << '```'
        lines << ''

        return lines.join('\n')
    }

    public static void reset() {
        myRepo.reset()
    }
}
