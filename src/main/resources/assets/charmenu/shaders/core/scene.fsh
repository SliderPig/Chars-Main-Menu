#version 150

uniform sampler2D Sampler0;
uniform vec3 SunDir;
uniform float AlphaCut;

in vec4 tint;
in vec2 uv;
in vec3 rel;
in vec3 light;

out vec4 fragColor;

vec3 fog(vec3 colour, vec3 r) {
    float dist = length(r);
    float amount = 1.0 - exp(-pow(dist / 1500.0, 1.25));
    vec3 d = r / max(dist, 0.001);
    float s = pow(max(dot(normalize(vec3(d.x, 0.0, d.z)), normalize(vec3(SunDir.x, 0.0, SunDir.z))), 0.0), 3.0);
    vec3 haze = mix(vec3(0.73, 0.84, 0.94), vec3(0.98, 0.90, 0.82), s * 0.65);
    return mix(colour, haze, amount);
}

void main() {
    vec4 tex = texture(Sampler0, uv);
    if (tex.a < AlphaCut) discard;
    vec3 c = tex.rgb * tint.rgb * light;
    // a soft shoulder so sunlit pale wood never burns out to flat white
    c = c / (1.0 + max(max(c.r, c.g), c.b) * 0.16) * 1.13;
    fragColor = vec4(fog(c, rel), AlphaCut < 0.01 ? 1.0 : tex.a);
}
