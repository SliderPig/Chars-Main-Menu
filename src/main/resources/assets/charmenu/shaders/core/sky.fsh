#version 150

uniform sampler2D Sampler0;
uniform vec3 SunDir;
uniform float Time;

in vec3 ray;

out vec4 fragColor;

void main() {
    vec3 d = normalize(ray);
    float y = max(d.y, 0.0);
    // bright blue overhead, pale and misty at the horizon
    vec3 c = mix(vec3(0.73, 0.84, 0.94), vec3(0.42, 0.66, 0.92), smoothstep(0.0, 0.28, y));
    c = mix(c, vec3(0.22, 0.46, 0.83), smoothstep(0.18, 0.85, y));
    // the sun warms its side of the sky
    float along = max(dot(normalize(vec3(d.x, 0.0, d.z)), normalize(vec3(SunDir.x, 0.0, SunDir.z))), 0.0);
    c = mix(c, vec3(0.98, 0.90, 0.82), pow(along, 3.0) * 0.65 * (1.0 - smoothstep(0.0, 0.30, y)));
    float s = max(dot(d, SunDir), 0.0);
    c += vec3(1.0, 0.92, 0.78) * (pow(s, 7.0) * 0.14 + pow(s, 48.0) * 0.26 + pow(s, 900.0) * 1.4);

    // soft clouds: two layers of the same picture sliding across at different speeds
    if (d.y > 0.0) {
        vec2 p = d.xz / (d.y + 0.11);
        float a = texture(Sampler0, p * 0.055 + vec2(Time * 0.00125, Time * 0.0004)).r;
        float b = texture(Sampler0, p * 0.14 + vec2(-Time * 0.0009, Time * 0.0016)).g;
        float shape = a * 0.72 + b * 0.28;
        float cover = smoothstep(0.50, 0.74, shape);
        float thick = smoothstep(0.58, 0.92, shape);
        vec3 cloud = mix(vec3(1.0, 0.985, 0.97), vec3(0.72, 0.77, 0.86), thick * 0.75);
        cloud = mix(cloud, vec3(1.0, 0.95, 0.88), pow(s, 6.0) * 0.5);
        float fade = smoothstep(0.015, 0.20, d.y);
        c = mix(c, cloud, cover * fade * 0.93);
    }
    fragColor = vec4(c, 1.0);
}
