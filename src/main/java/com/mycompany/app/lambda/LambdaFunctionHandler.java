package com.mycompany.app.lambda;

import com.mycompany.app.lambda.table.*;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mycompany.app.lambda.table.Post;

import java.io.*;

public class LambdaFunctionHandler implements RequestStreamHandler{

    private static final String DYNAMODB_TABLE_NAME = System.getenv("DYNAMODB_TABLE_NAME");
    private static final PostController controller = new PostController();

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        JsonObject response = new JsonObject();

        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        DynamoDB dynamoDb = new DynamoDB(client);

        try{
            JsonObject event = (JsonObject) JsonParser.parseReader(reader);

            String method = event.get("requestContext").getAsJsonObject().get("http").getAsJsonObject().get("method").toString();
            String path = event.get("rawPath").toString();

            if(path == "/posts"){
                switch (method){
                    case "GET":
                        response = jsonResponse(this.getAllPosts(event));
                    case "POST":
                        response = jsonResponse(this.postPost(event));
                    case "OPTIONS":
                        JsonObject res = new JsonObject();
                        res.addProperty("statusCode",200);
                        response = res;
                    default:
                        JsonObject notSupported = new JsonObject();
                        notSupported.addProperty("statusCode",405);
                        JsonObject body = new JsonObject();
                        body.addProperty("error", "Method "+method+" not supported on "+path);
                        notSupported.add("body", body);
                        response = jsonResponse(notSupported);
                }
            }
        } catch (Exception e){
            System.err.println(e.getLocalizedMessage());
            JsonObject res = new JsonObject();
            res.addProperty("statusCode",405);

            JsonObject body = new JsonObject();
            body.addProperty("error", "request failed:" + e.getLocalizedMessage());
            res.add("body", body);
            response = res;
        }
    }

    public JsonObject jsonResponse(JsonObject response){
        JsonObject res = new JsonObject();

        res.addProperty("statusCode", "200");
        for(String key : response.keySet()){
            res.add(key, response.get(key));
        }

        JsonObject header = new JsonObject();
        header.addProperty("Content-Type","application/json");
        for(String key: response.get("header").getAsJsonObject().keySet()){
            header.add(key, response.get("header").getAsJsonObject().get("key"));
        }

        res.add("headers", header);

        JsonObject body = new JsonObject();
        for(String key: response.get("body").getAsJsonObject().keySet()){
            body.add(key, response.get("body").getAsJsonObject().get(key));
        }
        res.add("body",body);

        return res;
    }

    // make look nice
    public JsonObject getAllPosts(JsonObject event){
        JsonObject res = new JsonObject();
        JsonObject i = new JsonObject();
        i.addProperty("data", this.controller.getAllPosts().toArray().toString());
        res.add("body", i);
        return res;
    }

    /*
    public JsonObject getPost(String id, JsonObject event){
        return null;
    }
    */


    public JsonObject postPost(JsonObject event){
        controller.addPost(event);
        return null;
    }
}
