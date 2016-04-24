#version 110

uniform sampler2D tex;
uniform float facing;
uniform float scale;
uniform vec2 norm;
uniform float flip;
uniform vec2 arc;
uniform float attwidth;

bool attenuate = (arc.x - arc.y != 0.0);
vec2 coord = gl_TexCoord[0].xy;

void main(void) {
	vec4 col = texture2D(tex, coord);
	vec2 vc = col.rg * 2.0 - 1.0;

	float cs = cos(facing * 0.0174533);
	float sn = sin(facing * 0.0174533);

	vc = vec2(vc.x * cs - vc.y * sn, vc.x * sn + vc.y * cs) * flip;

	float cutoff = 1.0;
	if (attenuate) {
		float angle = mod(atan(vc.y, vc.x), 6.2831853);
		if (angle < 0.0) {
			angle += 6.2831853;
		}

		if (arc.x > arc.y) {
			if (angle < arc.x && angle > arc.y) {
				cutoff = 0.0;
			} else if (angle < arc.x + attwidth && angle > arc.y - attwidth) {
				cutoff = 1.0 - max((arc.x + attwidth - angle) / attwidth, (angle - arc.y + attwidth) / attwidth);
			}
		} else {
			if (angle < arc.x || angle > arc.y) {
				cutoff = 0.0;
			} else if (angle < arc.x + attwidth) {
				cutoff = 1.0 - (arc.x + attwidth - angle) / attwidth;
			} else if (angle > arc.y - attwidth) {
				cutoff = 1.0 - (angle - arc.y + attwidth) / attwidth;
			}
		}
	}

	vc = (vc * 0.5) + 0.5;

	if (col.b < 0.005) {
		gl_FragColor = vec4(vc, ((col.b - norm.y) / norm.x) * scale * cutoff, 0.0);
	} else {
		gl_FragColor = vec4(vc, ((col.b - norm.y) / norm.x) * scale * cutoff, col.a * cutoff);
	}
}
