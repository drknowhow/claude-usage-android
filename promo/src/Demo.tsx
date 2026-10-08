import { useEffect, useState } from "react";
import {
  AbsoluteFill,
  Easing,
  continueRender,
  delayRender,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";

const GROUND = "#ECEBE6";
const INK = "#111111";
const INK2 = "#4A4A46";
const FAINT = "#CFCDC5";
const SIGNAL = "#A8370A";
const FONT = "JetBrains Mono";

const FACES: [string, string][] = [
  ["400", "jetbrains_mono_regular.ttf"],
  ["500", "jetbrains_mono_medium.ttf"],
  ["700", "jetbrains_mono_bold.ttf"],
];

const useFonts = () => {
  const [handle] = useState(() => delayRender("fonts"));
  useEffect(() => {
    Promise.all(
      FACES.map(([weight, file]) =>
        new FontFace(FONT, `url(${staticFile(file)})`, { weight }).load().then((f) => document.fonts.add(f)),
      ),
    ).then(() => continueRender(handle));
  }, [handle]);
};

const percentAt = (frame: number) =>
  Math.round(
    interpolate(frame, [0, 70, 95, 150], [8, 62, 62, 100], {
      easing: Easing.inOut(Easing.cubic),
      extrapolateRight: "clamp",
    }),
  );

const Bar = ({ percent, width, height }: { percent: number; width: number; height: number }) => {
  const filled = Math.round((percent / 100) * 20);
  const fill = percent >= 70 ? SIGNAL : INK;
  return (
    <div style={{ display: "flex", gap: 5, width }}>
      {Array.from({ length: 20 }, (_, i) => (
        <div key={i} style={{ flex: 1, height, background: i < filled ? fill : FAINT }} />
      ))}
    </div>
  );
};

const StatusBar = ({ glyph }: { glyph: string }) => (
  <div
    style={{
      display: "flex",
      alignItems: "center",
      gap: 18,
      padding: "10px 22px",
      background: INK,
      color: GROUND,
      borderRadius: 999,
      fontSize: 22,
      fontWeight: 500,
      width: "fit-content",
    }}
  >
    <span>09:41</span>
    <span style={{ fontWeight: 700, minWidth: 34 }}>{glyph}</span>
    <span style={{ display: "flex", gap: 3 }}>
      {[1, 1, 1, 0.4].map((o, i) => (
        <span key={i} style={{ width: 8, height: 12, background: GROUND, opacity: o }} />
      ))}
    </span>
  </div>
);

const Toast = ({ title, text, enter }: { title: string; text: string; enter: number }) => (
  <div
    style={{
      position: "absolute",
      right: 56,
      top: 70,
      width: 330,
      padding: "18px 20px",
      background: "#FFFFFF",
      borderRadius: 22,
      boxShadow: "0 10px 30px rgba(0,0,0,0.10)",
      opacity: enter,
      transform: `translateY(${(1 - enter) * -40}px)`,
      display: "flex",
      flexDirection: "column",
      gap: 6,
    }}
  >
    <span style={{ fontSize: 15, color: INK2 }}>Claude Usage · now</span>
    <span style={{ fontSize: 21, fontWeight: 700, color: INK }}>{title}</span>
    <span style={{ fontSize: 16, color: INK2 }}>{text}</span>
  </div>
);

export const Demo = () => {
  useFonts();
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = percentAt(frame);
  const at75 = spring({ frame: frame - 114, fps, config: { damping: 200 } });
  const at100 = spring({ frame: frame - 150, fps, config: { damping: 200 } });
  const outro = interpolate(frame, [165, 190], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <AbsoluteFill style={{ background: GROUND, color: INK, fontFamily: FONT }}>
      <AbsoluteFill style={{ opacity: 1 - outro, padding: "0 72px", justifyContent: "center", gap: 22 }}>
        <StatusBar glyph={p >= 100 ? "!!" : String(p)} />
        <span style={{ fontSize: 20, color: INK2, marginTop: 14 }}>session / 5h</span>
        <div style={{ display: "flex", alignItems: "flex-end", lineHeight: 0.85 }}>
          <span style={{ fontSize: 190, fontWeight: 500, letterSpacing: "-0.06em", color: p >= 90 ? SIGNAL : INK }}>{p}</span>
          <span style={{ fontSize: 54, fontWeight: 500, color: INK2, marginLeft: 6 }}>%</span>
        </div>
        <Bar percent={p} width={560} height={40} />
        <span style={{ fontSize: 20 }}>
          <span style={{ color: INK2 }}>resets </span>15:40 (in 2h14m)
        </span>
      </AbsoluteFill>

      {frame < 150 && <Toast title="session passed 75%" text="75% used · resets 15:40" enter={at75 * (1 - outro)} />}
      {frame >= 150 && <Toast title="session limit reached" text="100% used · resets 15:40" enter={at100 * (1 - outro)} />}

      <AbsoluteFill
        style={{ opacity: outro, alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 18 }}
      >
        <span style={{ fontSize: 72, fontWeight: 700, letterSpacing: "-0.02em" }}>claude usage</span>
        <Bar percent={62} width={420} height={22} />
        <span style={{ fontSize: 24, color: INK2 }}>status bar · shade card · widget · alerts</span>
        <span style={{ fontSize: 18, color: INK2 }}>android · unofficial · open source</span>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
