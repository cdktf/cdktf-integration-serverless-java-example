package com.mycompany.app.posts;

import com.hashicorp.cdktf.Resource;
import imports.aws.dynamodb.DynamodbTable;
import imports.aws.dynamodb.DynamodbTableAttribute;
import imports.aws.dynamodb.DynamodbTableConfig;
import software.constructs.Construct;

import java.util.List;

public class PostsStorage extends Resource {

    private final DynamodbTable table;

    public PostsStorage(Construct scope, String id, String environment, String userSuffix){
        super(scope,id);

        this.table =  new DynamodbTable(this, "table", DynamodbTableConfig.builder()
                .name("sls-posts-" +  environment + (userSuffix != null ? userSuffix : ""))
                .billingMode("PAY_PER_REQUEST")
                .hashKey("id")
                .rangeKey("postedAt")
                .attribute(List.of(
                        DynamodbTableAttribute.builder().name("id").type("S").build(),
                        DynamodbTableAttribute.builder().name("postedAt").type("S").build()
                        )
                )
                .build()
        );
    }

    public DynamodbTable getTable(){
        return this.table;
    }
}
