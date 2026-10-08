import { Composition } from "remotion";
import { Demo } from "./Demo";

export const RemotionRoot = () => (
  <Composition id="Demo" component={Demo} durationInFrames={240} fps={30} width={960} height={540} />
);
