package com.mycompany.app.posts;

import com.hashicorp.cdktf.providers.aws.dynamodb_table.DynamodbTable;
import com.hashicorp.cdktf.providers.aws.dynamodb_table.DynamodbTableAttribute;
import com.hashicorp.cdktf.providers.aws.dynamodb_table.DynamodbTableConfig;
import software.constructs.Construct;

import java.util.List;

public class PostsStorage extends Construct {

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
