#version 150

uniform sampler2D Sampler0;

in vec2 uv;
in vec3 rel;
in vec3 light;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, uv);
    if (tex.a < 0.1) discard;
    vec3 c = tex.rgb * light;
    float amount = 1.0 - exp(-pow(length(rel) / 1500.0, 1.25));
    fragColor = vec4(mix(c, vec3(0.73, 0.84, 0.94), amount), 1.0);
}
