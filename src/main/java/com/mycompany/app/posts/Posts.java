package com.mycompany.app.posts;

import com.hashicorp.cdktf.Resource;
import software.constructs.Construct;

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
