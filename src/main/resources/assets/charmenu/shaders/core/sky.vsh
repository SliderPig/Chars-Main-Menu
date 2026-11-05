#version 150

in vec3 Position;

uniform vec3 Forward;
uniform vec3 Right;
uniform vec3 Up;

out vec3 ray;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    ray = Forward + Right * Position.x + Up * Position.y;
}
