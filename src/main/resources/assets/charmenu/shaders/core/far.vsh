#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraPos;

out vec4 tint;
out vec3 rel;
out float height;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rel = Position - CameraPos;
    height = Position.y;
    tint = Color;
}
