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

    public NodeJSFunction(Construct scope, String id, String path) throws ScriptException, IOException {
        super(scope,id);

        File file = new File(path);
        Path workingDirectory = Paths.get(System.getProperty("user.dir"), path);
        // need base name https://stackoverflow.com/questions/4545937/java-splitting-the-filename-into-a-base-and-extension

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        // everything up to and excluding lambda
        engine.put("workingDirectory", Paths.get(String.valueOf(file.getParentFile())).toString());
        engine.put("entryPoints", Paths.get(file.getName()).toString());
        String distPath = engine.eval(Files.newBufferedReader(Paths.get(System.getProperty("user.dir"),"lib","nodejs-function.js"), StandardCharsets.UTF_8)).toString();

        // write this so if there isn't an array it won't break
        String basename = Paths.get(file.getName()).toString().split("\\.(?=[^\\.]+$)")[0];

        this.bundledPath = Paths.get(distPath, basename+"js").toString();

        TerraformAssetConfig.Builder assetConfig = new TerraformAssetConfig.Builder();
        this.asset = new TerraformAsset(this, "lambda-asset", assetConfig.path(distPath).type(AssetType.ARCHIVE).build());

    }
}
