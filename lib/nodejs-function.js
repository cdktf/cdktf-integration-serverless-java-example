import * as path from "path";
import { buildSync } from "esbuild";

buildSync({
    entryPoints: [entryPoint],
    platform: "node",
    target: "es2018",
    bundle: true,
    format: "cjs",
    sourcemap: "external",
    outdir: "dist",
    absWorkingDir: workingDirectory,
  });
    
return path.join(workingDirectory, "dist")