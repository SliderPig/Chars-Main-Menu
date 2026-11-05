#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraPos;
uniform vec3 SunDir;
uniform float Time;
uniform float Sway;

out vec4 tint;
out vec2 uv;
out vec3 rel;
out vec3 light;

void main() {
    vec3 p = Position;
    float w = Color.a;
    // the breeze: a slow roll across the whole scene with a quicker flutter on top. Depends only on position, so neighbouring blocks stay joined.
    if (w > 0.0 && Sway > 0.0) {
        float roll = sin(Time * 0.9 + p.x * 0.11 + p.z * 0.07) * 0.5 + 0.5;
        float flutter = sin(Time * 2.3 + p.x * 0.9 + p.y * 1.1 + p.z * 0.7);
        float flutter2 = sin(Time * 3.1 + p.z * 1.3 - p.y * 0.8);
        float amount = w * Sway * (0.035 + 0.055 * roll);
        p.x += flutter * amount;
        p.z += flutter2 * amount * 0.8;
        p.y += flutter2 * amount * 0.35;
    }
    vec4 view = ModelViewMat * vec4(p, 1.0);
    gl_Position = ProjMat * view;
    rel = p - CameraPos;
    uv = UV0;
    tint = vec4(Color.rgb, 1.0);

    // sky value: up to 8 of 15 is open sky around, the rest is direct sun that gets here
    vec2 lm = vec2(UV2) / 240.0;
    float open = clamp(lm.y * 15.0 / 8.0, 0.0, 1.0);
    float sun = smoothstep(8.5 / 15.0, 1.0, lm.y);
    float facing = dot(Normal, SunDir);
    float direct = w > 0.0 ? facing * 0.45 + 0.55 : max(facing, 0.0);
    vec3 ambient = vec3(0.50, 0.57, 0.68) * (0.80 + 0.20 * Normal.y) * (0.08 + 0.92 * open);
    vec3 sunlight = vec3(1.02, 0.92, 0.76) * sun * direct * 0.78;
    vec3 lamp = vec3(1.0, 0.70, 0.40) * pow(lm.x, 1.7) * 1.05;
    light = ambient + sunlight + lamp;
    // light glows through blossom and leaves, so their undersides stay rich instead of going dark
    if (w > 0.0) light = max(light, vec3(0.62, 0.50, 0.54));
}
