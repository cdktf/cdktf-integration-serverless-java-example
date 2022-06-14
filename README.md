#CDK for Terraform Serverless Application in Java

This repository contains an end to end serverless web app hosted on AWS and deployed with [CDK for Terraform](https://cdk.tf) in Java. In more application specific terms, we are deploying serverless infrastructure for a web app that has a list of posts and a modal to create a new post by specifying author and content. For more information regarding setup and the features of CDKTF [please refer to these docs](https://www.terraform.io/cdktf).

##Techstack

Frontend: React, Create React App, statically hosted via AWS S3 + CloudFront 
Backend API: AWS Lambda + API Gateway + DynamoDB

##Application

###Initial Setup

To start off we call `cdktf init --template=java` in an empty directory to create the initial project setup. This provides the `main.java` file in the src/…/app folder that will serve as the entry point for all of our infrastructure definitions. 

Your main.java file will roughly look like this…

```java
public class Main extends TerraformStack
{
    public Main(final Construct scope, final String id) {
        super(scope, id);

        // define resources here
    }

    public static void main(String[] args) {
        final App app = new App();
        Main stack = new Main(app, "test1");
        new RemoteBackend(stack, RemoteBackendProps.builder().hostname("app.terraform.io").organization("cdktf-lang-demos").workspaces(new NamedRemoteWorkspace("test1")).build());
        app.synth();
    }
}
```

We will be using multiple stacks in this example. As such, we will not be using the constructor of Main– instead we will use nested classes that have constructors of their own.

###Stacks 

We will have two primary Stacks– PostsStack and FrontendStack

For both Stacks, the Post and Frontend class encapsulate the finer details of infrastructure provisioned in each Stack.

```java
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
``` 

```java
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
```
In using nested classes to separate aspects of our infrastructure we create useful abstractions that both improve readability as well as modularity. This modularity comes into play when creating different environments for this application, namely development and production.

For example…

```java
PostsStack postsDev = new PostsStack(app, "posts-dev", "development", 
FrontendStack frontendDev = new FrontendStack(app, "frontend-dev", "development", postsDev.posts.getApiEndPoint());
 
PostsStack postsProd = new PostsStack(app, "posts-prod", "production", "");
FrontendStack frontendProd = new FrontendStack(app, "frontend-prod", "production", postsProd.posts.getApiEndPoint());```
```
Here we create separate instances of the infrastructure for the frontend and backend that allows for different naming of the resources in each application environment, with the ease of adding additional as needed. 

###Posts

The Posts class melds two elements together– the Dynamodb table coming from PostsStorage and our Lambda function and Apigateway coming from PostsApi that takes our new Dynamodb table for setting up the Lambda function environment. 

```java
public class Posts extends Resource {
 
   private final String apiEndPoint;
 
   public Posts(Construct scope, String id, String environment, String userSuffix){
       super(scope,id);
 
       PostsStorage storage = new PostsStorage(this, "storage", environment, userSuffix);
 
       PostsApi postsApi = new PostsApi(this, "api", environment, storage.getTable(), userSuffix);
 
       this.apiEndPoint = postsApi.getEndPoint();
   }
 
   public String getApiEndPoint(){
       return this.apiEndPoint;
   }
}
```

In PostsApi we create our Lambda function and Apigateway, along with the needed permissions and IAM role. NodeJSFunction calls a script to bundle the Lambda function, the path to the Lambda is then contained in a TerraformAsset within NodeJSFunction. With this we are able to provide the path to the bundled Lambda implementation as well as the asset’s hash to our provisioned Lambda. 

```java
public class PostsApi extends Resource {
 
   private final String endPoint;
 
   public PostsApi(Construct scope, String id, String environment, DynamodbTable table, String userSuffix){
       super(scope,id);
 
       NodeJSFunction code = new NodeJSFunction(this, "code", Paths.get(System.getProperty("user.dir"), "lambda", "index.ts").toString());
       IamRole role = new IamRole(this, "lambda-exec", IamRoleConfig.builder()
               .name("sls-example-post-api-lambda-exec-" + environment + (userSuffix != null ? userSuffix : ""))
               .assumeRolePolicy((new JSONObject()
                       .put("Version", "2012-10-17")
                       .put("Statement", new HashMap <String,Object>() {{
                           put("Action", "sts:AssumeRole");
                           put("Principal", new HashMap<String, Object>(){{
                               put("Service","lambda.amazonaws.com");
                           }});
                           put("Effect","Allow");
                           put("Sid", "");
                       }})).toString())
               .inlinePolicy(List.of(IamRoleInlinePolicy.builder()
                       .name("AllowDynamoDB")
                       .policy((new JSONObject()
                               .put("Version","2012-10-17")
                               .put("Statement", new HashMap<String,Object>(){{
                                   put("Action", List.of("dynamodb:Scan", "dynamodb:Query", "dynamodb:BatchGetItem","dynamodb:GetItem", "dynamodb:PutItem"));
                                   put("Resource", table.getArn());
                                   put("Effect", "Allow");
                               }})
 
                       ).toString())
                       .build()
               ))
               .build()
       );
 
       new IamRolePolicyAttachment(this, "lambda-managed-policy", IamRolePolicyAttachmentConfig.builder()
               .policyArn("arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole")
               .role(role.getName())
               .build()
       );
       LambdaFunction lambda = new LambdaFunction(this, "api", LambdaFunctionConfig.builder()
               .functionName("sls-example-posts-api-" + environment + (userSuffix != null ? userSuffix : ""))
               .handler("index.handler")
               .runtime("nodejs14.x")
               .role(role.getArn())
               .filename(code.getAsset().getPath())
               .sourceCodeHash(code.getAsset().getAssetHash())
               .environment(LambdaFunctionEnvironment.builder()
                       .variables(new HashMap<>(){{
                           put("DYNAMODB_TABLE_NAME",table.getName());
                       }})
                       .build()
               )
               .build()
       );
 
       Apigatewayv2Api api = new Apigatewayv2Api(this, "api-gw", Apigatewayv2ApiConfig.builder()
               .name("sls-example-posts-" + environment + (userSuffix != null ? userSuffix : ""))
               .protocolType("HTTP")
               .target(lambda.getArn())
               .corsConfiguration(Apigatewayv2ApiCorsConfiguration.builder()
                       .allowOrigins(List.of("*"))
                       .allowMethods(List.of("*"))
                       .allowHeaders(List.of("content-type"))
                       .build()
               )
               .build()
       );
 
       new LambdaPermission(this, "apigw-lambda", LambdaPermissionConfig.builder()
               .functionName(lambda.getFunctionName())
               .action("lambda:InvokeFunction")
               .principal("apigateway.amazonaws.com")
               .sourceArn(api.getExecutionArn()+"/*/*")
               .build()
       );
 
       this.endPoint = api.getApiEndpoint();
   }
 
   public String getEndPoint(){
       return this.endPoint;
   }
}
```

###Frontend

In the Frontend class we provision a S3 Bucket as well as a Cloudfront distribution for our React app to be statically hosted.

```java
S3Bucket bucket = new S3Bucket(this, "bucket");
       bucket.setBucketPrefix("sls-example-frontend-"+ environment);
       bucket.setTags(new HashMap<>() {{
           //...
       }});
       bucket.putWebsite(S3BucketWebsite.builder()
               //...
       );
```
```java
CloudfrontDistribution cf = new CloudfrontDistribution(this,"cf", CloudfrontDistributionConfig.builder()
               .comment("Serverless example frontend for env="+ environment)
               .enabled(true)
               .defaultCacheBehavior(CloudfrontDistributionDefaultCacheBehavior.builder()
                       //...
                       .build()
                       
               )
               .origin(List.of(
                       CloudfrontDistributionOrigin.builder()
                               //...
                               )
                               .build()
               ))
               .defaultRootObject("index.html")
               .restrictions(CloudfrontDistributionRestrictions.builder()
                       //...
                       )
                       .build()
               )
               .viewerCertificate(CloudfrontDistributionViewerCertificate.builder()
                       //...
                       .build()
               )
               .build()
       );
```

The file `env.production.local` provides the S3 Bucket and Backend endpoints to our React app. Finally we create a TerraformOutput that gives us the domain name of the application’s frontend.

It is important to note that policies within each resource’s configuration take Strings– yet these are really JSON strings. For this I used JSONObject from org.json to build each JSON, then using the toString() method to provide the JSON string to the policy.
