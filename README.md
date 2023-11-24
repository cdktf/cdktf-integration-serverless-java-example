# CDK for Terraform Serverless Application in Java

_This repository was created for demo purposes and will not be kept up-to-date with future releases of CDK for Terraform (CDKTF); as such, it has been archived and is no longer supported in any way by HashiCorp. You are welcome to try out the archived version of the code in this example project, but there are no guarantees that it will continue to work with newer versions of CDKTF. We do not recommend directly using this sample code in production projects without extensive testing, and HashiCorp disclaims any and all liability resulting from use of this code._

-----

This repository contains an end to end serverless web app hosted on AWS and deployed with [CDK for Terraform](https://cdk.tf) in Java. In more application specific terms, we are deploying serverless infrastructure for a web app that has a list of posts and a modal to create a new post by specifying author and content. For more information regarding setup and the features of CDKTF [please refer to these docs](https://www.terraform.io/cdktf).

## Local Usage

### Prerequisites

In order to run this example you must have CDKTF and it's prerequisites installed. For further explanation please see [this quick start demo](https://learn.hashicorp.com/tutorials/terraform/cdktf-install?in=terraform/cdktf).

Additionally an AWS account and [AWS credentials configured for use with Terraform](https://learn.hashicorp.com/tutorials/terraform/cdktf-install?in=terraform/cdktf) are needed.

### To Deploy

First run `mvn install` in the root directory of the project to install all the needed packages. Then cd to the `lambda` folder and run `npm install` to install everything needed for our AWS lambda function. The same thing applies to the `lib` directory, run `npm install` in there to install the required dependencies for building our lambda function.

Lastly, in the root directory of the example `cdktf deploy` can be runned with the stacks that you wish to deploy e.g `cdktf deploy posts-dev frontend-dev` for deploying the dev environement or `cdktf deploy posts-prod frontend-prod` for deploying the production environment.

## Techstack

Frontend: React, Create React App, statically hosted via AWS S3 + CloudFront
Backend API: AWS Lambda + API Gateway + DynamoDB

## Application

### Stacks

We will have two primary Stacks– PostsStack and FrontendStack

The Post and Frontend class encapsulate the finer details of infrastructure provisioned for each respective Stack. The first parameter denotes the scope of the infrastructure being provision– we use `this` to tie the infrastructure contained in Post/Frontend to the Stack in which it is contained, the same is true with `AwsProvider`.

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

PostsStack and FrontendStack are static nested classes within main.java, which is the entry point for all infrastructure definitions provided by `cdktf init --template=java`.

In using different nested Stacks to separate aspects of our infrastructure we allow for separation in state management of the frontend and backend– making alteration and redeployment of a specific piece of infrastructure a simpler process. Additionally, the nested nature of these Stacks allows for the instantiation of the same resource multiple times throughout.

For example…

```java
# In the main method of Main.java

PostsStack postsDev = new PostsStack(app, "posts-dev", "development",
FrontendStack frontendDev = new FrontendStack(app, "frontend-dev", "development", postsDev.posts.getApiEndPoint());

PostsStack postsProd = new PostsStack(app, "posts-prod", "production", "");
FrontendStack frontendProd = new FrontendStack(app, "frontend-prod", "production", postsProd.posts.getApiEndPoint());
```

Here we created separate instances of the infrastructure for the frontend and backend with different naming of the resources in each application environment (by ways of the environment param), with the ease of adding additional as needed.

### Posts

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

In PostsApi we create our Lambda function and Apigateway, along with the needed permissions and IAM role. NodeJSFunction calls a script to bundle the Lambda function, the path to the bundled Lambda function is then contained in a TerraformAsset within NodeJSFunction. With this we are able to provide the path to the bundled Lambda implementation as well as the asset’s hash to our provisioned Lambda.

Here we see a use of the environment variable– the one that was initially given in main.java. With this we provide greater description for the resources in each environment as well as avoiding naming conflicts.

```java
       IamRole role = new IamRole(this, "lambda-exec", IamRoleConfig.builder()
               .name("sls-example-post-api-lambda-exec-" + environment + (userSuffix != null ? userSuffix : ""))
			 //...
```

It is also in the IAM Role that we define certain policies for the role. It is important to note that policies that are denoted as taking Strings (in IamRole and elsewhere) are really JSON strings. For this I used JSONObject from org.json to build each JSON, then using the toString() method to provide the JSON string to the policy.

For example…

```java
IamRole role = new IamRole(this, "lambda-exec", IamRoleConfig.builder()
               //...
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
			//...
```

Now we get into the details of our Lambda function. It is here that we provide the Lambda with the role we created above as well as the Dynamodb table from the Storage object created alongside this PostsApi object in the Post class. We also provide other needed details (name of handler, runtime, local path to lambda implementation, ect.).

```java
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
```

Our API Gateway will sit between our Frontend and Lambda function– both routing requests to our Lambda as well as returning the appropriate result. We give the API Gateway our Lambda function defined as its target.

```java
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
```

We then pass the API Gateway’s endpoint to the PostApi object– this will be later given to our Frontend.

```java
this.endPoint = api.getApiEndpoint();
```

Finally we provide Permissions to our Lambda and additional policy for our IAM Role.

```java
new IamRolePolicyAttachment(this, "lambda-managed-policy", IamRolePolicyAttachmentConfig.builder()
               .policyArn("arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole")
               .role(role.getName())
               .build()
       );

//...

new LambdaPermission(this, "apigw-lambda", LambdaPermissionConfig.builder()
               .functionName(lambda.getFunctionName())
               .action("lambda:InvokeFunction")
               .principal("apigateway.amazonaws.com")
               .sourceArn(api.getExecutionArn()+"/*/*")
               .build()
       );
```

### Frontend

In the Frontend class we provision a S3 Bucket as well as a Cloudfront distribution for our React app to be statically hosted.

```java
S3Bucket bucket = new S3Bucket(this, "bucket");
       bucket.setBucketPrefix("sls-example-frontend-"+ environment);
       bucket.setTags(new HashMap<>() {{
           put("hc-internet-facing", "true");
       }});
       bucket.putWebsite(S3BucketWebsite.builder()
               .indexDocument("index.html")
               .errorDocument("index.html")
               .build()
       );
```

The Cloudfront Distribution speeds up the distribution of our Frontend content and reduces the load on our S3 Bucket by caching its contents. It is here we define the behavior and permission of this cache as well as provide the endpoint of the S3 Bucket we defined above.

```java
CloudfrontDistribution cf = new CloudfrontDistribution(this,"cf", CloudfrontDistributionConfig.builder()
               .comment("Serverless example frontend for env="+ environment)
               .enabled(true)
               .defaultCacheBehavior(CloudfrontDistributionDefaultCacheBehavior.builder()
                       .allowedMethods(Arrays.asList("DELETE","GET","HEAD","OPTIONS","PATCH","POST","PUT"))
                       .cachedMethods(Arrays.asList("GET", "HEAD"))
                       .targetOriginId(s3_ORIGIN_ID)
                       .viewerProtocolPolicy("redirect-to-https")
                       .forwardedValues(CloudfrontDistributionDefaultCacheBehaviorForwardedValues.builder()
                               .queryString(false)
                               .cookies(CloudfrontDistributionDefaultCacheBehaviorForwardedValuesCookies.builder()
                                       .forward("none")
                                       .build()
                               )
                               .build()
                       )
                       .build()
               )
               .origin(List.of(
                       CloudfrontDistributionOrigin.builder()
                               .originId(s3_ORIGIN_ID)
                               .domainName(bucket.getWebsiteEndpoint())
                               .customOriginConfig(CloudfrontDistributionOriginCustomOriginConfig.builder()
                                       .originProtocolPolicy("http-only")
                                       .httpPort(80)
                                       .httpsPort(443)
                                       .originSslProtocols(Arrays.asList("TLSv1.2", "TLSv1.1", "TLSv1"))
                                       .build()
                               )
                               .build()
               ))
               .defaultRootObject("index.html")
               .restrictions(CloudfrontDistributionRestrictions.builder()
                       .geoRestriction(CloudfrontDistributionRestrictionsGeoRestriction.builder()
                               .restrictionType("none")
                               .build()
                       )
                       .build()
               )
               .viewerCertificate(CloudfrontDistributionViewerCertificate.builder()
                       .cloudfrontDefaultCertificate(true)
                       .build()
               )
               .build()
       );
```

The file `env.production.local` provides the S3 Bucket and Backend endpoints to our React app.

```java
new File(this,"env", FileConfig.builder()
               .filename(Paths.get(System.getProperty("user.dir"), "frontend","code", ".env.production.local").toString())
               .content("S3_BUCKET_FRONTEND"+"="+bucket.getBucket()+"\n"+"REACT_APP_API_ENDPOINT"+"="+ apiEndPoint)
               .build()
       );
```

Finally we create a TerraformOutput that gives us the domain name of the application’s frontend.

```java
new TerraformOutput(this, "frontend_domainname", TerraformOutputConfig.builder()
               .value(cf.getDomainName())
               .build()
       ).addOverride("value", "https://"+cf.getDomainName());
```

## License

[Mozilla Public License v2.0](https://github.com/hashicorp/cdktf-integration-serverless-java-example/blob/main/LICENSE)
