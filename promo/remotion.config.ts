import { Config } from "@remotion/cli/config";

Config.setEntryPoint("./src/index.ts");
// The app's own font files, so the animation and the app share one copy.
Config.setPublicDir("../app/src/main/res/font");
