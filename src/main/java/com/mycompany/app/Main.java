package com.mycompany.app;
import com.mycompany.app.posts.Posts;
import software.constructs.Construct;
import com.hashicorp.cdktf.App;
import com.hashicorp.cdktf.NamedRemoteWorkspace;
import com.hashicorp.cdktf.RemoteBackend;
import com.hashicorp.cdktf.RemoteBackendProps;
import com.hashicorp.cdktf.TerraformStack;
import com.hashicorp.cdktf.providers.aws.AwsProvider;
import com.hashicorp.cdktf.providers.aws.AwsProviderConfig;
import com.hashicorp.cdktf.providers.local.LocalProvider;

import java.util.Arrays;

public class Main {

    static class FrontendStack extends TerraformStack{
        public FrontendStack(Construct scope, String name, String environment , String apiEndPoint){
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
        
        public PostsStack(Construct scope, String name, String environment, String user){
            super(scope, name);

            new AwsProvider(this, "aws", AwsProviderConfig.builder()
                    .region("eu-central-1")
                    .build()
            );

            this.posts = new Posts(this, "posts", environment, user);
        }
    }

    static class PreviewStack extends TerraformStack{

        public PreviewStack(Construct scope, String name, String previewBuildIdentifier){
            super(scope,name);

            Posts posts = new Posts(this, "posts", previewBuildIdentifier, null);
            new Frontend(this, "frontend", previewBuildIdentifier, posts.getApiEndPoint());

        }
    }

    public static void main(String[] args) throws Exception {
        final App app = new App();

        Boolean USE_REMOTE_BACKEND = (System.getenv("USE_REMOTE_BACKEND") == "true");
        if(System.getenv("PREVIEW_BUILD_IDENTIFIER") != null){
            if(Arrays.stream(new String[]{"development", "production"}).anyMatch(System.getenv("PREVIEW_BUILD_IDENTIFIER")::equals)){
                throw new Exception("environment variable PREVIEW_BUILD_IDENTIFIER may not be set to development or production but it was set to"+System.getenv("PREVIEW_BUILD_IDENTIFIER"));
            }

            new PreviewStack(app, "preview", System.getenv("PREVIEW_BUILD_IDENTIFIER"));
        } else {

            PostsStack postsDev = new PostsStack(app, "posts-dev", "test2development", System.getenv("CDKTF_USER"));
            if (USE_REMOTE_BACKEND) {
                new RemoteBackend(postsDev, RemoteBackendProps.builder()
                        .organization("terraform-demo-mad")
                        .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                        .build()
                );
            }


            FrontendStack frontendDev = new FrontendStack(app, "frontend-dev", "test2development", postsDev.posts.getApiEndPoint());
            if (USE_REMOTE_BACKEND) {
                new RemoteBackend(frontendDev, RemoteBackendProps.builder()
                        .organization("terraform-demo-mad")
                        .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                        .build()
                );
            }

            PostsStack postsProd = new PostsStack(app, "posts-prod", "production", "");
            if (USE_REMOTE_BACKEND) {
                new RemoteBackend(postsProd, RemoteBackendProps.builder()
                        .organization("terraform-demo-mad")
                        .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                        .build()
                );
            }

            FrontendStack frontendProd = new FrontendStack(app, "frontend-prod", "production", postsProd.posts.getApiEndPoint());
            if (USE_REMOTE_BACKEND) {
                new RemoteBackend(frontendProd, RemoteBackendProps.builder()
                        .organization("terraform-demo-mad")
                        .workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"))
                        .build()
                );
            }

            app.synth();
        }
    }
}