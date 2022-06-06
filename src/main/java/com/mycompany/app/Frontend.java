package com.mycompany.app;


import com.hashicorp.cdktf.Resource;
import com.hashicorp.cdktf.TerraformOutput;
import com.hashicorp.cdktf.TerraformOutputConfig;
import com.hashicorp.cdktf.providers.aws.cloudfront.*;
import com.hashicorp.cdktf.providers.aws.s3.S3Bucket;
import com.hashicorp.cdktf.providers.aws.s3.S3BucketPolicy;
import com.hashicorp.cdktf.providers.aws.s3.S3BucketPolicyConfig;
import com.hashicorp.cdktf.providers.aws.s3.S3BucketWebsite;
import com.hashicorp.cdktf.providers.local.File;
import com.hashicorp.cdktf.providers.local.FileConfig;
import org.json.JSONObject;
import software.constructs.Construct;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

// Make sure there is only one instance of this class
    // Look into Singleton pattern
public class Frontend extends Resource {

    public Frontend(Construct scope, String id, String environment, String apiEndPoint){
        super(scope, id);

        //Creating s3 instance - adding prefix and tags
        S3Bucket bucket = new S3Bucket(this, "bucket");
        bucket.setBucketPrefix("sls-example-frontend-"+ environment);
        bucket.setTags(new HashMap<>() {{
            put("hc-internet-facing", "true");
        }});
        // Maybe Issue is here? Is deprecated arg - no other options tho - check diff version of cdktf
        bucket.putWebsite(S3BucketWebsite.builder()
                .indexDocument("index.html")
                .errorDocument("index.html")
                .build()
        );

        new S3BucketPolicy(this, "s3_policy", S3BucketPolicyConfig.builder()
                .bucket(bucket.getId())
                .policy((new JSONObject()
                        .put("Version", "2012-10-17")
                        .put("Id", "PolicyForWebsiteEndpointsPublicContent")
                        .put("Statement",new HashMap<String,Object>(){{
                            put("Sid", "PublicRead");
                            put("Effect", "Allow");
                            put("Principal", "*");
                            put("Action",new String[]{"s3:GetObject"});
                            put("Resource", new String[]{bucket.getArn()+"/*",bucket.getArn()});
                        }})
                ).toString())
                .build()
        );

        String s3_ORIGIN_ID = "s3Origin";
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

        // Error occurring here - Missing resource schema -> stems from Unsupported attribute coming from apiEndPoint
        new File(this,"env", FileConfig.builder()
                .filename(Paths.get(System.getProperty("user.dir"), "frontend","code", ".env.production.local").toString())
                .content("S3_BUCKET_FRONTEND"+"="+bucket.getBucket()+"\n"+"REACT_APP_API_ENDPOINT"+"="+ apiEndPoint)
                .build()
        );

        new TerraformOutput(this, "frontend_domainname", TerraformOutputConfig.builder()
                .value(cf.getDomainName())
                .build()
        ).addOverride("value", "https://"+cf.getDomainName());
    }
    
    
}
