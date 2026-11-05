#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraPos;
uniform vec3 SunDir;

out vec2 uv;
out vec3 rel;
out vec3 light;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rel = Position - CameraPos;
    uv = UV0;
    float facing = max(dot(Normal, SunDir), 0.0);
    light = vec3(0.56, 0.60, 0.68) * (0.85 + 0.15 * Normal.y) + vec3(1.02, 0.92, 0.76) * facing * 0.62;
}
