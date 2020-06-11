import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.instanceOf
import static org.junit.Assert.assertThat
import static org.mockito.Mockito.mock;

import org.junit.Test
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import de.bechte.junit.runners.context.HierarchicalContextRunner

@RunWith(HierarchicalContextRunner.class)
class TargetPluginTest {
    @Before
    void resetJenkinsEnv() {
        Jenkinsfile.instance = mock(Jenkinsfile.class)
        when(Jenkinsfile.instance.getEnv()).thenReturn([:])
    }

    private configureJenkins(Map config = [:]) {
        Jenkinsfile.instance = mock(Jenkinsfile.class)
        when(Jenkinsfile.instance.getEnv()).thenReturn(config.env ?: [:])
    }

    public class Init {
        @After
        void resetPlugins() {
            TerraformPlanCommand.resetPlugins()
            TerraformApplyCommand.resetPlugins()
            TerraformEnvironmentStage.resetPlugins()
        }

        @Test
        void modifiesTerraformPlanCommand() {
            TargetPlugin.init()

            Collection actualPlugins = TerraformPlanCommand.getPlugins()
            assertThat(actualPlugins, hasItem(instanceOf(TargetPlugin.class)))
        }

        @Test
        void modifiesTerraformApplyCommand() {
            TargetPlugin.init()

            Collection actualPlugins = TerraformApplyCommand.getPlugins()
            assertThat(actualPlugins, hasItem(instanceOf(TargetPlugin.class)))
        }

        @Test
        void modifiesTerraformEnvironmentStageCommand() {
            TargetPlugin.init()

            Collection actualPlugins = TerraformEnvironmentStage.getPlugins()
            assertThat(actualPlugins, hasItem(instanceOf(TargetPlugin.class)))
        }
    }

    public class Apply {

        @Test
        void addsTargetArgumentToTerraformPlan() {
            TargetPlugin plugin = new TargetPlugin()
            TerraformPlanCommand command = new TerraformPlanCommand()
            configureJenkins(env: [
                'RESOURCE_TARGETS': 'aws_dynamodb_table.test-table-2,aws_dynamodb_table.test-table-3'
            ])

            plugin.apply(command)

            String result = command.toString()
            assertThat(result, containsString(" -target=aws_dynamodb_table.test-table-2 -target=aws_dynamodb_table.test-table-3"))
        }


        @Test
        void addsTargetArgumentToTerraformApply() {
            TargetPlugin plugin = new TargetPlugin()
            TerraformApplyCommand command = new TerraformApplyCommand()
            configureJenkins(env: [
                'RESOURCE_TARGETS': 'aws_dynamodb_table.test-table-2,aws_dynamodb_table.test-table-3'
            ])

            plugin.apply(command)

            String result = command.toString()
            assertThat(result, containsString(" -target=aws_dynamodb_table.test-table-2 -target=aws_dynamodb_table.test-table-3"))
        }

        @Test
        void decoratesTheTerraformEnvironmentStage()  {
            TargetPlugin plugin = new TargetPlugin()
            def environment = spy(new TerraformEnvironmentStage())
            configureJenkins(env: [
                'RESOURCE_TARGETS': 'aws_dynamodb_table.test-table-2,aws_dynamodb_table.test-table-3'
            ])

            plugin.apply(environment)

            verify(environment, times(1)).decorate(eq(TerraformEnvironmentStage.ALL), any(Closure.class))
        }
    }
}
