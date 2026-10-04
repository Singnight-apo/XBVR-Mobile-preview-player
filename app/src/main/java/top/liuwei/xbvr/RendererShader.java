package top.liuwei.xbvr;

/** GLSL ES 1.00 sources. Fragment highp is optional; projection/color math is shared. */
final class RendererShader {
    private RendererShader(){}
    static boolean supportsHighp(int rangeMin,int rangeMax,int bits){return rangeMin!=0&&rangeMax!=0&&bits>0;}
    static String vertex(boolean highp){
        return "precision highp float;\nattribute vec2 position;\nvarying "+(highp?"highp":"mediump")+" vec2 pos;\nvoid main(){pos=position;gl_Position=vec4(position,0.0,1.0);}";
    }
    static String fragment(boolean highp){
        String precision=highp?"highp":"mediump";
        return "#extension GL_OES_EGL_image_external : require\nprecision "+precision+" float;\nuniform lowp samplerExternalOES video;\nvarying "+precision+" vec2 pos;\n"+BODY;
    }
    private static final String BODY="""
        uniform mat4 st;
        uniform float kind,stereoLayout,eye,capture,yaw,pitch,tangent,aspect,rotation,mirror,sourceAspect;
        uniform vec2 circle,center;
        const float PI=3.141592653589793;
        void main(){
          vec2 uv;
          if(kind<0.5){
            vec2 q=pos;
            if(aspect>sourceAspect)q.x*=aspect/sourceAspect;else q.y*=sourceAspect/aspect;
            uv=vec2(0.5+q.x*0.5,0.5-q.y*0.5);
          }else{
            vec3 d=normalize(vec3(pos.x*tangent*aspect,pos.y*tangent,1.0));
            d=vec3(d.x,cos(pitch)*d.y+sin(pitch)*d.z,-sin(pitch)*d.y+cos(pitch)*d.z);
            d=vec3(cos(yaw)*d.x+sin(yaw)*d.z,d.y,-sin(yaw)*d.x+cos(yaw)*d.z);
            if(kind>1.5){
              float angle=acos(clamp(d.z,-1.0,1.0));
              if(angle>capture*0.5){gl_FragColor=vec4(0.0,0.0,0.0,1.0);return;}
              vec2 q=length(d.xy)<0.000001?vec2(0.0):normalize(d.xy);
              q=vec2(cos(rotation)*q.x-sin(rotation)*q.y,sin(rotation)*q.x+cos(rotation)*q.y);
              uv=center+vec2(q.x*mirror,-q.y)*(angle/(capture*0.5))*circle;
            }else{
              float lon=atan(d.x,d.z);
              if(abs(lon)>capture*0.5){gl_FragColor=vec4(0.0,0.0,0.0,1.0);return;}
              uv=vec2(0.5+lon/capture,0.5-asin(clamp(d.y,-1.0,1.0))/PI);
            }
          }
          if(uv.x<0.0||uv.x>1.0||uv.y<0.0||uv.y>1.0){gl_FragColor=vec4(0.0,0.0,0.0,1.0);return;}
          if(stereoLayout>0.5&&stereoLayout<1.5)uv.x=(uv.x+eye)*0.5;
          if(stereoLayout>1.5)uv.y=(uv.y+eye)*0.5;
          // Logical top-left image UV to SurfaceTexture's bottom-left coordinates.
          vec2 mapped=(st*vec4(uv.x,1.0-uv.y,0.0,1.0)).xy;
          gl_FragColor=texture2D(video,mapped);
        }
        """;
}
