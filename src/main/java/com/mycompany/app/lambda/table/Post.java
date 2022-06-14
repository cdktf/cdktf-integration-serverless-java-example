package com.mycompany.app.lambda.table;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Date;

@DynamoDBTable(tableName = "Post")
public class Post {
    @DynamoDBHashKey(attributeName="id")
    String id;
    @DynamoDBRangeKey(attributeName="postedAt")
    String postedAt; //Hash Key
    @DynamoDBAttribute(attributeName="author")
    String author;
    @DynamoDBAttribute(attributeName="content")
    String content;

    /*
    public Post(String json){
        Gson gson = new Gson();
        Post request = gson.fromJson(json, Post.class);
        this.id = request.getId();
        this.postedAt = request.getPostedAt();
        this.author = request.getAuthor();
        this.content = request.getContent();
    }
    */

    public String toString(){
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    public void setId(String id){
        this.id = id;
    }

    public String getId(){
        return this.id;
    }

    public void setPostedAt(String postedAt){
        this.postedAt = postedAt.toString();
    }

    public String getPostedAt(){ return this.postedAt; }

    public void setAuthor(String author){
        this.author = author;
    }

    public String getAuthor(){
        return this.author;
    }

    public void setContent(String content){
        this.content = content;
    }

    public String getContent(){
        return this.content;
    }
}