package com.mycompany.app;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

import com.hashicorp.cdktf.providers.aws.s3.S3Bucket;
import com.hashicorp.cdktf.providers.aws.s3.S3BucketWebsite;
import com.hashicorp.cdktf.providers.aws.s3.S3BucketPolicy;
import com.hashicorp.cdktf.providers.aws.s3.S3BucketPolicyConfig;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistribution;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionDefaultCacheBehavior;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionConfig;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionDefaultCacheBehaviorForwardedValues;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionDefaultCacheBehaviorForwardedValuesCookies;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionOrigin;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionOriginCustomOriginConfig;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionRestrictions;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionRestrictionsGeoRestriction;
import com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionViewerCertificate;
import com.hashicorp.cdktf.Resource;
import com.hashicorp.cdktf.TerraformOutput;
import com.hashicorp.cdktf.TerraformOutputConfig;
import software.constructs.Construct;
// Potential runtime env issue
import com.hashicorp.cdktf.providers.local.File;
import com.hashicorp.cdktf.providers.local.FileConfig;


//For getting current directory - Paths might work better here
import java.nio.file.Path;
import java.nio.file.Paths;

// Make sure there is only one instance of this class
    // Look into Singleton pattern
public class Frontend extends Resource {

    private String environment;
    private String apiEndPoint;
    private final String S3_ORIGIN_ID = "s3Origin";

    public Frontend(Construct scope, String id, String environment, String apiEndPoint){
        super(scope, id);
        this.environment = environment;
        this.apiEndPoint = apiEndPoint;

        //Creating s3 instance - adding prefix and tags
        S3Bucket bucket = new S3Bucket(this, "bucket");
        bucket.setBucketPrefix("sls-example-frontend-"+this.environment);
        Map<String,String> tags = new HashMap<String,String>();
        tags.put("hc-internet-facing","true");
        bucket.setTags(tags);

        
        // Add configs for frontend
        S3BucketWebsite.Builder bucketWebsite = S3BucketWebsite.builder();
        bucketWebsite.indexDocument("index.html")
                     .errorDocument("index.html");
        bucket.putWebsite(bucketWebsite.build());
        
        S3BucketPolicyConfig.Builder policyConfig = new S3BucketPolicyConfig.Builder();
        policyConfig.bucket(bucket.getId());
        
        JSONObject JSONpolicy = new JSONObject();
        // Not safe change value type later
        Map<String,Object> statement = new HashMap<String,Object>();
        statement.put("Sid", "PublicRead");
        statement.put("Effect", "Allow");
        statement.put("Principal", "*");
        statement.put("Action",new String[]{"s3:GetObject"});
        String name = bucket.getArn()+"/*";
        String key = bucket.getArn();
        statement.put("Resource", new String[]{name,key});
        JSONpolicy.put("Version", "2012-10-17").put("Id", "PolicyForWebsiteEndpointsPublicContent").put("Statement",statement);

        policyConfig.policy(JSONpolicy.toString());
        new S3BucketPolicy(this, "s3_policy", policyConfig.build());
        
        
        // Cloudfront Distro
        com.hashicorp.cdktf.providers.aws.cloudfront.CloudfrontDistributionConfig.Builder cfBuilder = new CloudfrontDistributionConfig.Builder();
        cfBuilder.comment("Serverless example frontend for env="+this.environment);
        cfBuilder.enabled(true);
        CloudfrontDistributionDefaultCacheBehavior.Builder defaultCacheBehavior = new CloudfrontDistributionDefaultCacheBehavior.Builder();
        defaultCacheBehavior.allowedMethods(Arrays.asList("DELETE","GET","HEAD","OPTIONS","PATCH","POST","PUT"));
        defaultCacheBehavior.cachedMethods(Arrays.asList("GET", "HEAD"));
        defaultCacheBehavior.targetOriginId(S3_ORIGIN_ID);
        defaultCacheBehavior.viewerProtocolPolicy("redirect-to-https");
        CloudfrontDistributionDefaultCacheBehaviorForwardedValues.Builder forwardedValues = new CloudfrontDistributionDefaultCacheBehaviorForwardedValues.Builder();
        forwardedValues.queryString(false);
        CloudfrontDistributionDefaultCacheBehaviorForwardedValuesCookies.Builder cookies = new CloudfrontDistributionDefaultCacheBehaviorForwardedValuesCookies.Builder();
        cookies.forward("none");
        forwardedValues.cookies(cookies.build());
        defaultCacheBehavior.forwardedValues(forwardedValues.build());
        cfBuilder.defaultCacheBehavior(defaultCacheBehavior.build());
        CloudfrontDistributionOrigin.Builder origin = new CloudfrontDistributionOrigin.Builder();
        origin.originId(S3_ORIGIN_ID);
        origin.domainName(bucket.getWebsiteEndpoint());
        CloudfrontDistributionOriginCustomOriginConfig.Builder customOriginConfig = new CloudfrontDistributionOriginCustomOriginConfig.Builder();
        customOriginConfig.originProtocolPolicy("http-only");
        customOriginConfig.httpPort(80);
        customOriginConfig.httpsPort(443);
        customOriginConfig.originSslProtocols(Arrays.asList("TLSv1.2", "TLSv1.1", "TLSv1"));
        origin.customOriginConfig(customOriginConfig.build());
        cfBuilder.origin(List.of(origin.build()));
        cfBuilder.defaultRootObject("index.html");
        // Restrictions
        CloudfrontDistributionRestrictions.Builder restrictions = new CloudfrontDistributionRestrictions.Builder();
        CloudfrontDistributionRestrictionsGeoRestriction.Builder geoRestriction = new CloudfrontDistributionRestrictionsGeoRestriction.Builder();
        geoRestriction.restrictionType("none");
        restrictions.geoRestriction(geoRestriction.build());
        cfBuilder.restrictions(restrictions.build());
        CloudfrontDistributionViewerCertificate.Builder viewerCertificate = new CloudfrontDistributionViewerCertificate.Builder();
        viewerCertificate.cloudfrontDefaultCertificate(true);
        cfBuilder.viewerCertificate(viewerCertificate.build());
        CloudfrontDistribution cf = new CloudfrontDistribution(this,"cf", cfBuilder.build());

        FileConfig.Builder fileBuilder = new FileConfig.Builder();
        Path dir = Paths.get(System.getProperty("user.dir"),new String[]{"code", ".env.production.local"});
        fileBuilder.filename(dir.toString());
        fileBuilder.content("S3_BUCKET_FRONTEND"+"="+bucket.getBucket()+"\n"+"REACT_APP_API_ENDPOINT"+"="+this.apiEndPoint+"\n");
        new File(this,"env",fileBuilder.build());

        TerraformOutputConfig.Builder outputConfig = new TerraformOutputConfig.Builder();
        outputConfig.value(cf.getDomainName());
        new TerraformOutput(this, "frontend_domainname", outputConfig.build()).addOverride("value", "https://"+cf.getDomainName());
    }
    
    
}
