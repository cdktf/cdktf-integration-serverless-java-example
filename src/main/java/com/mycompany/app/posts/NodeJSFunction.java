package com.mycompany.app.posts;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.io.IOException;
import javax.script.*;

import com.hashicorp.cdktf.Resource;
import com.hashicorp.cdktf.TerraformAsset;
import com.hashicorp.cdktf.AssetType;
import com.hashicorp.cdktf.TerraformAssetConfig;



import software.constructs.Construct;

public class NodeJSFunction extends Resource {

    // Must be readable -> provide get methods
    // set as final for now, not sure if it's the right choice
    private final TerraformAsset asset;
    private final String bundledPath;

    public NodeJSFunction(Construct scope, String id, String path) {
        super(scope,id);

        File file = new File(path);

        // everything up to and excluding lambda
        String workingDirectory = Paths.get(String.valueOf(file.getParentFile())).toString();
        String distPath = NodeJSFunction.bundle(workingDirectory,Paths.get(file.getName()).toString());

        // write this so if there isn't an array it won't break
        String basename = Paths.get(file.getName()).toString().split("\\.(?=[^\\.]+$)")[0];
        this.bundledPath = Paths.get(distPath, basename+"js").toString();

        TerraformAssetConfig.Builder assetConfig = new TerraformAssetConfig.Builder();
        this.asset = new TerraformAsset(this, "lambda-asset", assetConfig.path(distPath).type(AssetType.ARCHIVE).build());
    }

    public TerraformAsset getAsset(){
        return this.asset;
    }

    public String getBundledPath(){
        return this.bundledPath;
    }

    static String bundle(String workingDirectory, String entryPoint){
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        engine.put("workingDirectory", workingDirectory);
        engine.put("entryPoints", entryPoint);
        String distPath = null;
        try{
            distPath = engine.eval(Files.newBufferedReader(Paths.get(System.getProperty("user.dir"),"lib","nodejs-function.js"), StandardCharsets.UTF_8)).toString();
        } catch(ScriptException scriptException) {
            System.out.println(scriptException.toString());
        } catch(IOException ioException) {
            ioException.printStackTrace();
        }
        return distPath;
    }
}
