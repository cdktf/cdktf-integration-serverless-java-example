package com.mycompany.app;

import software.constructs.Construct;

import com.hashicorp.cdktf.App;
import com.hashicorp.cdktf.NamedRemoteWorkspace;
import com.hashicorp.cdktf.RemoteBackend;
import com.hashicorp.cdktf.RemoteBackendProps;
import com.hashicorp.cdktf.TerraformStack;
import com.hashicorp.cdktf.providers.aws.AwsProvider;
import com.hashicorp.cdktf.providers.aws.AwsProviderConfig;
import com.hashicorp.cdktf.providers.local.LocalProvider;

public class Main extends TerraformStack
{
    //private String apiEndPoint;
    private String environment;

    public Main(final Construct scope, final String id, String environment){//, String apiEndPoint) {
        super(scope, id);

        AwsProviderConfig.Builder awsConfig = new AwsProviderConfig.Builder();
        awsConfig.region("eu-central-1");
        new AwsProvider(this, "aws", awsConfig.build());

        new LocalProvider(this, "local");

        new Frontend(this, "frontend", this.environment);//, this.apiEndPoint);
        // define resources here
    }

    public static void main(String[] args) {
        final App app = new App();
        Main frontendStack = new Main(app, "frontend", "deveolpment");
        
        RemoteBackendProps.Builder props = new RemoteBackendProps.Builder();
        props.organization("terraform-demo-mad");
        props.workspaces(new NamedRemoteWorkspace("cdktf-integration-serverless-java-example"));
        new RemoteBackend(frontendStack, props.build());

        app.synth();
    }
}