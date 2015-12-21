varying vec4 texcoord;
uniform sampler2D composite;

void main(){
	if(texture2D(composite, texcoord.st).a < 0.1)
		discard;
		
	gl_FragDepth = gl_FragCoord.z;
	gl_FragColor = vec4(1,gl_FragCoord.z,gl_FragCoord.z,1);
	
}