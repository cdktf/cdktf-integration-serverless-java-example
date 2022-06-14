package com.mycompany.app.lambda.table;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.google.gson.JsonObject;

import java.util.List;

public class PostController {

    static AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
    private DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(client);

    public List<Post> getAllPosts(){
        List<Post> posts = dynamoDBMapper.scan(Post.class, new DynamoDBScanExpression());
        return posts;
    }
    /*
    public Post getPost(String id){
        dynamoDBMapper.load(Post.class,id);
    }
     */

    //not sure if this parsing is going to work
    public void addPost(JsonObject jsonObject){
        Post post = new Post();
        post.setAuthor(jsonObject.get("author").toString());
        post.setContent(jsonObject.get("content").toString());
        dynamoDBMapper.save(post);
    }

}
