#version 150

// A soft rounded pill in real screen pixels: faint glass fill, a thin bright rim, and a glow outside it.
// Worked out premultiplied, handed back as ordinary colour and alpha so it blends the same way as everything else in the menus.
uniform vec2 Half;
uniform float Radius;
uniform vec4 FillTop;
uniform vec4 FillBottom;
uniform vec4 Rim;
uniform vec4 Glow;
uniform float GlowSize;

in vec2 p;

out vec4 fragColor;

void main() {
    vec2 q = abs(p) - Half + Radius;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - Radius;
    float inside = clamp(0.5 - d, 0.0, 1.0);
    float band = inside * clamp(d + 1.75, 0.0, 1.0);
    float t = clamp((p.y + Half.y) / (2.0 * Half.y), 0.0, 1.0);
    vec4 fill = mix(FillTop, FillBottom, t);
    // glow outside, fill inside, rim over the edge (a touch stronger at the top, as if lit from above)
    float glow = Glow.a * exp(-max(d, 0.0) / GlowSize) * (1.0 - inside);
    vec3 rgb = Glow.rgb * glow + fill.rgb * fill.a * inside;
    float a = glow + fill.a * inside;
    float rimA = Rim.a * band * mix(1.15, 0.7, t);
    rgb = Rim.rgb * rimA + rgb * (1.0 - rimA);
    a = rimA + a * (1.0 - rimA);
    fragColor = vec4(a > 0.0001 ? rgb / a : vec3(0.0), a);
}
