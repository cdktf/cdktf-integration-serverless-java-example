package com.mycompany.app.PostBackend;

import com.hashicorp.cdktf.Resource;
import com.hashicorp.cdktf.TerraformAsset;

import software.constructs.Construct;

public class NodeJSFunction extends Resource {

    private TerraformAsset asset;
    private String bundledPath;

    public NodeJSFunction(Construct scope, String id, String path){
        super(scope,id);

        

        

    }
    
}
