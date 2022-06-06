package com.mycompany.app.posts;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.hashicorp.cdktf.Resource;
import com.hashicorp.cdktf.TerraformAsset;
import com.hashicorp.cdktf.AssetType;
import com.hashicorp.cdktf.TerraformAssetConfig;



import software.constructs.Construct;

public class NodeJSFunction extends Resource {

    // Readonly
    private final TerraformAsset asset;
    private final String bundledPath;

    public NodeJSFunction(Construct scope, String id, String path){
        super(scope,id);

        File file = new File(path);

        String workingDirectory = Paths.get(String.valueOf(file.getParentFile())).toString();
        String distPath = NodeJSFunction.bundle(workingDirectory,Paths.get(file.getName()).toString());
        
        String basename = Paths.get(file.getName()).toString();
        
        // Changes file extension to js
        int indexDot = basename.lastIndexOf(".");
        if((indexDot > 0 && indexDot < basename.length()-1) && basename.substring(indexDot, basename.length()) != ".ts"){
            basename = basename.subSequence(0, indexDot) + ".js";
        } else {
            throw new RuntimeException("Last part of path is a directory or is not a .ts File");
        }

        this.bundledPath = Paths.get(distPath, basename).toString();
        this.asset = new TerraformAsset(this, "lambda-asset", TerraformAssetConfig.builder()
                .path(distPath)
                .type(AssetType.ARCHIVE)
                .build()
        );
    }

    public TerraformAsset getAsset(){
        return this.asset;
    }

    public String getBundledPath(){
        return this.bundledPath;
    }

    static String bundle(String workingDirectory, String entryPoint){
        ProcessBuilder builder = new ProcessBuilder("node", "nodejs-function.js");
        Map<String, String> env = builder.environment();
        env.put("WORKING_DIRECTORY", workingDirectory);
        env.put("ENTRY_POINT", entryPoint);
        builder.directory(new File(System.getProperty("user.dir"),"lib"));
        builder.redirectErrorStream(true);
        Process p = null;
        String distPath = "";
        try {
            p = builder.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            distPath = r.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return distPath;
    }
}
