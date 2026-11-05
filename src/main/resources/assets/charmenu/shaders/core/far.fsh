#version 150

uniform vec3 SunDir;

in vec4 tint;
in vec3 rel;
in float height;

out vec4 fragColor;

void main() {
    float dist = length(rel);
    // mist lies low in the valleys and thins out up the mountain sides
    float amount = 1.0 - exp(-pow(dist / 1500.0, 1.25));
    float low = 1.0 - smoothstep(-20.0, 240.0, height);
    amount = clamp(amount * (0.72 + 0.5 * low), 0.0, 0.97);
    vec3 d = rel / max(dist, 0.001);
    float s = pow(max(dot(normalize(vec3(d.x, 0.0, d.z)), normalize(vec3(SunDir.x, 0.0, SunDir.z))), 0.0), 3.0);
    vec3 haze = mix(vec3(0.73, 0.84, 0.94), vec3(0.98, 0.90, 0.82), s * 0.65);
    fragColor = vec4(mix(tint.rgb, haze, amount), 1.0);
}
