package com.mycompany.app.posts;

import com.hashicorp.cdktf.Resource;
import com.hashicorp.cdktf.providers.aws.apigatewayv2.Apigatewayv2Api;
import com.hashicorp.cdktf.providers.aws.apigatewayv2.Apigatewayv2ApiConfig;
import com.hashicorp.cdktf.providers.aws.apigatewayv2.Apigatewayv2ApiCorsConfiguration;
import com.hashicorp.cdktf.providers.aws.dynamodb.DynamodbTable;
import com.hashicorp.cdktf.providers.aws.iam.*;
import com.hashicorp.cdktf.providers.aws.lambdafunction.*;
import software.constructs.Construct;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;


import org.json.JSONObject;

public class PostsApi extends Resource {

    private final String endPoint;

    public PostsApi(Construct scope, String id, String environment, DynamodbTable table, String userSuffix){
        super(scope,id);

        NodeJSFunction code = new NodeJSFunction(this, "code", Paths.get(System.getProperty("user.dir"), "lambda", "index.ts").toString());
        System.out.println((new JSONObject()
                .put("Version", "2012-10-17")
                .put("Statement", new HashMap <String,Object>() {{
                    put("Action", "sts.AssumeRole");
                    put("Principal", new HashMap<String, Object>(){{
                        put("Service","lambda.amazonaws.com");
                    }});
                    put("Effect","Allow");
                    put("Sid", "");
                }})).toString());
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
