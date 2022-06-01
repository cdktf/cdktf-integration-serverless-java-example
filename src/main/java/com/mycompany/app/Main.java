package com.mycompany.app;
import com.mycompany.app.posts.*;
import software.constructs.Construct;
import com.hashicorp.cdktf.App;
import com.hashicorp.cdktf.NamedRemoteWorkspace;
import com.hashicorp.cdktf.RemoteBackend;
import com.hashicorp.cdktf.RemoteBackendProps;
import com.hashicorp.cdktf.TerraformStack;
import com.hashicorp.cdktf.providers.aws.AwsProvider;
import com.hashicorp.cdktf.providers.aws.AwsProviderConfig;
import com.hashicorp.cdktf.providers.local.LocalProvider;

public class Main {

    static class FrontendStack extends TerraformStack{
        public FrontendStack(Construct scope, String name, String environment ,String apiEndPoint){
            super(scope,name);

            new AwsProvider(this, "aws", AwsProviderConfig.builder()
                    .region("eu-central-1")
                    .build()
            );

            new LocalProvider(this, "local");

            new Frontend(this, "frontend", environment, apiEndPoint);
        }
    }

    static class PostsStack extends TerraformStack{

        public Posts posts;
        // Might need to look into ability to restrict env like dans example, same with user
        public PostsStack(Construct scope, String id, String environment, String user){
            super(scope, id);

            new AwsProvider(this, "aws", AwsProviderConfig.builder()
                    .region("eu-central-1")
                    .build()
            );

            this.posts = new Posts(this, "posts", environment, user);
        }
    }

    public static void main(String[] args) {
        final App app = new App();
        PostsStack posts = new PostsStack(app, "posts-dev","development", System.getenv("CDKTF_USER"));
        FrontendStack frontend = new FrontendStack(app, "frontend-dev", "development", posts.posts.getApiEndPoint());

        new RemoteBackend(posts, RemoteBackendProps.builder()
                .organization("terraform-demo-mad")
                .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                .build()
        );

        new RemoteBackend(frontend, RemoteBackendProps.builder()
                .organization("terraform-demo-mad")
                .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                .build()
        );

        app.synth();
    }
}